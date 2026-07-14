package com.devson.vedinsta.extractor

import com.devson.vedinsta.model.ExtractedMediaNode
import com.devson.vedinsta.model.ExtractedPost
import com.devson.vedinsta.model.MediaQuality
import com.devson.vedinsta.model.MediaVariant
import com.devson.vedinsta.model.QualityOption
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import java.io.IOException

/**
 * Extraction strategy using Instagram's public JSON/GraphQL API endpoints.
 */
class JsonApiStrategy : PublicExtractionStrategy {

    private val gson = Gson()
    private val client = UnauthenticatedNetworkModule.okHttpClient

    override suspend fun extractMedia(url: String, qualityPref: MediaQuality): ExtractedPost = withContext(Dispatchers.IO) {
        val shortcode = extractShortcode(url)
        if (shortcode.isEmpty()) {
            throw IllegalArgumentException("Invalid Instagram URL, shortcode could not be extracted.")
        }

        // Waterfall 1: Try GraphQL query (POST)
        try {
            return@withContext queryGraphQL(shortcode, qualityPref)
        } catch (e: Exception) {
            // Log or print warning and try the legacy fallback
            System.err.println("GraphQL query failed for $shortcode: ${e.message}. Trying legacy endpoint fallback...")
        }

        // Waterfall 2: Try legacy endpoint (GET)
        return@withContext queryLegacyEndpoint(shortcode, qualityPref)
    }

    private fun queryGraphQL(shortcode: String, qualityPref: MediaQuality): ExtractedPost {
        val formBody = FormBody.Builder()
            .add("doc_id", "8845758582119845")
            .add("variables", gson.toJson(mapOf("shortcode" to shortcode)))
            .add("server_timestamps", "true")
            .build()

        val request = Request.Builder()
            .url("https://www.instagram.com/graphql/query")
            .post(formBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code == 401 || response.code == 403 || response.code == 302) {
                    throw AuthRequiredException("GraphQL API returned auth error code: ${response.code}")
                }
                throw IOException("GraphQL query failed with code: ${response.code}")
            }

            val bodyString = response.body?.string() ?: throw IOException("Empty GraphQL response body")
            val contentType = response.body?.contentType()?.toString() ?: ""
            if (contentType.contains("text/html") || bodyString.contains("login")) {
                throw AuthRequiredException("Instagram redirected GraphQL request to login screen.")
            }

            val gqlResponse = gson.fromJson(bodyString, ShortcodeMediaResponse::class.java)
            val media = gqlResponse.data?.shortcodeMedia ?: throw IOException("No shortcode media found in GraphQL response.")

            val mediaList = mutableListOf<ExtractedMediaNode>()
            val edges = media.edgeSidecarToChildren?.edges
            if (!edges.isNullOrEmpty()) {
                edges.forEachIndexed { idx, edge ->
                    edge.node?.let { childNode ->
                        mediaList.add(mapGraphQLSidecarNode(childNode, qualityPref, idx + 1))
                    }
                }
            } else {
                mediaList.add(mapGraphQLMedia(media, qualityPref, 1))
            }

            return ExtractedPost(
                mediaList = mediaList,
                username = media.owner?.username ?: "unknown",
                caption = media.edgeMediaToCaption?.edges?.firstOrNull()?.node?.text ?: "",
                postId = shortcode
            )
        }
    }

    private fun queryLegacyEndpoint(shortcode: String, qualityPref: MediaQuality): ExtractedPost {
        val request = Request.Builder()
            .url("https://www.instagram.com/p/$shortcode/?__a=1&__d=dis")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code == 401 || response.code == 403 || response.code == 302) {
                    throw AuthRequiredException("Legacy API returned auth error code: ${response.code}")
                }
                throw IOException("Legacy API query failed with code: ${response.code}")
            }

            val bodyString = response.body?.string() ?: throw IOException("Empty legacy response body")
            val contentType = response.body?.contentType()?.toString() ?: ""
            if (contentType.contains("text/html") || bodyString.contains("login")) {
                throw AuthRequiredException("Instagram redirected legacy API request to login screen.")
            }

            val legacyResponse = gson.fromJson(bodyString, LegacyMediaResponse::class.java)
            val item = legacyResponse.items?.firstOrNull() ?: throw IOException("No items found in legacy response.")

            val mediaList = mutableListOf<ExtractedMediaNode>()
            val carouselItems = item.carouselMedia
            if (!carouselItems.isNullOrEmpty()) {
                carouselItems.forEachIndexed { idx, childItem ->
                    mediaList.add(mapLegacyCarouselMedia(childItem, qualityPref, idx + 1))
                }
            } else {
                mediaList.add(mapLegacyMedia(item, qualityPref, 1))
            }

            return ExtractedPost(
                mediaList = mediaList,
                username = item.user?.username ?: "unknown",
                caption = item.caption?.text ?: "",
                postId = shortcode
            )
        }
    }

    // Helper mappings for GraphQL response

    private fun mapGraphQLMedia(node: ShortcodeMedia, qualityPref: MediaQuality, index: Int): ExtractedMediaNode {
        val isVideo = node.isVideo == true
        val videoVersions = node.videoVersions ?: emptyList()
        val imageVersions = node.displayResources ?: emptyList()

        // Two-Endpoint Rule: last index goes to thumbnailUrl
        val thumbnailUrl = imageVersions.lastOrNull()?.url ?: node.displayUrl ?: ""

        val downloadUrls = if (isVideo) {
            val urls = videoVersions.mapNotNull { it.url }
            selectUrls(urls, qualityPref)
        } else {
            val urls = imageVersions.mapNotNull { it.url }
            selectUrls(urls, qualityPref)
        }

        val primaryUrl = downloadUrls.firstOrNull() ?: node.videoUrl ?: node.displayUrl ?: ""

        val downloadVariants = if (isVideo) {
            videoVersions.mapNotNull { v ->
                v.url?.let { MediaVariant(it, "${v.height ?: 1080}px") }
            }
        } else {
            imageVersions.mapNotNull { img ->
                img.url?.let { MediaVariant(it, "${img.width ?: 1080}px") }
            }
        }

        val qualities = if (isVideo) {
            videoVersions.mapNotNull { v ->
                v.url?.let { QualityOption(it, v.width, v.height) }
            }
        } else {
            imageVersions.mapNotNull { img ->
                img.url?.let { QualityOption(it, img.width, img.height) }
            }
        }

        val thumbnailQualities = imageVersions.mapNotNull { img ->
            img.url?.let { QualityOption(it, img.width, img.height) }
        }

        return ExtractedMediaNode(
            thumbnailUrl = thumbnailUrl,
            downloadUrls = downloadUrls,
            downloadVariants = downloadVariants,
            url = primaryUrl,
            type = if (isVideo) "video" else "image",
            width = if (isVideo) videoVersions.firstOrNull()?.width else imageVersions.firstOrNull()?.width,
            height = if (isVideo) videoVersions.firstOrNull()?.height else imageVersions.firstOrNull()?.height,
            index = index,
            qualities = qualities,
            thumbnailQualities = thumbnailQualities
        )
    }

    private fun mapGraphQLSidecarNode(node: SidecarNode, qualityPref: MediaQuality, index: Int): ExtractedMediaNode {
        val isVideo = node.isVideo == true
        val videoVersions = node.videoVersions ?: emptyList()
        val imageVersions = node.displayResources ?: emptyList()

        val thumbnailUrl = imageVersions.lastOrNull()?.url ?: node.displayUrl ?: ""

        val downloadUrls = if (isVideo) {
            val urls = videoVersions.mapNotNull { it.url }
            selectUrls(urls, qualityPref)
        } else {
            val urls = imageVersions.mapNotNull { it.url }
            selectUrls(urls, qualityPref)
        }

        val primaryUrl = downloadUrls.firstOrNull() ?: node.videoUrl ?: node.displayUrl ?: ""

        val downloadVariants = if (isVideo) {
            videoVersions.mapNotNull { v ->
                v.url?.let { MediaVariant(it, "${v.height ?: 1080}px") }
            }
        } else {
            imageVersions.mapNotNull { img ->
                img.url?.let { MediaVariant(it, "${img.width ?: 1080}px") }
            }
        }

        val qualities = if (isVideo) {
            videoVersions.mapNotNull { v ->
                v.url?.let { QualityOption(it, v.width, v.height) }
            }
        } else {
            imageVersions.mapNotNull { img ->
                img.url?.let { QualityOption(it, img.width, img.height) }
            }
        }

        val thumbnailQualities = imageVersions.mapNotNull { img ->
            img.url?.let { QualityOption(it, img.width, img.height) }
        }

        return ExtractedMediaNode(
            thumbnailUrl = thumbnailUrl,
            downloadUrls = downloadUrls,
            downloadVariants = downloadVariants,
            url = primaryUrl,
            type = if (isVideo) "video" else "image",
            width = if (isVideo) videoVersions.firstOrNull()?.width else imageVersions.firstOrNull()?.width,
            height = if (isVideo) videoVersions.firstOrNull()?.height else imageVersions.firstOrNull()?.height,
            index = index,
            qualities = qualities,
            thumbnailQualities = thumbnailQualities
        )
    }

    // Helper mappings for legacy API response

    private fun mapLegacyMedia(item: LegacyMediaItem, qualityPref: MediaQuality, index: Int): ExtractedMediaNode {
        val isVideo = item.mediaType == 2
        val videoVersions = item.videoVersions ?: emptyList()
        val imageVersions = item.imageVersions2?.candidates ?: emptyList()

        val thumbnailUrl = imageVersions.lastOrNull()?.url ?: ""

        val downloadUrls = if (isVideo) {
            val urls = videoVersions.mapNotNull { it.url }
            selectUrls(urls, qualityPref)
        } else {
            val urls = imageVersions.mapNotNull { it.url }
            selectUrls(urls, qualityPref)
        }

        val primaryUrl = downloadUrls.firstOrNull() ?: ""

        val downloadVariants = if (isVideo) {
            videoVersions.mapNotNull { v ->
                v.url?.let { MediaVariant(it, "${v.height ?: 1080}px") }
            }
        } else {
            imageVersions.mapNotNull { img ->
                img.url?.let { MediaVariant(it, "${img.width ?: 1080}px") }
            }
        }

        val qualities = if (isVideo) {
            videoVersions.mapNotNull { v ->
                v.url?.let { QualityOption(it, v.width, v.height) }
            }
        } else {
            imageVersions.mapNotNull { img ->
                img.url?.let { QualityOption(it, img.width, img.height) }
            }
        }

        val thumbnailQualities = imageVersions.mapNotNull { img ->
            img.url?.let { QualityOption(it, img.width, img.height) }
        }

        return ExtractedMediaNode(
            thumbnailUrl = thumbnailUrl,
            downloadUrls = downloadUrls,
            downloadVariants = downloadVariants,
            url = primaryUrl,
            type = if (isVideo) "video" else "image",
            width = if (isVideo) videoVersions.firstOrNull()?.width else imageVersions.firstOrNull()?.width,
            height = if (isVideo) videoVersions.firstOrNull()?.height else imageVersions.firstOrNull()?.height,
            index = index,
            qualities = qualities,
            thumbnailQualities = thumbnailQualities
        )
    }

    private fun mapLegacyCarouselMedia(child: LegacyCarouselMedia, qualityPref: MediaQuality, index: Int): ExtractedMediaNode {
        val isVideo = child.mediaType == 2
        val videoVersions = child.videoVersions ?: emptyList()
        val imageVersions = child.imageVersions2?.candidates ?: emptyList()

        val thumbnailUrl = imageVersions.lastOrNull()?.url ?: ""

        val downloadUrls = if (isVideo) {
            val urls = videoVersions.mapNotNull { it.url }
            selectUrls(urls, qualityPref)
        } else {
            val urls = imageVersions.mapNotNull { it.url }
            selectUrls(urls, qualityPref)
        }

        val primaryUrl = downloadUrls.firstOrNull() ?: ""

        val downloadVariants = if (isVideo) {
            videoVersions.mapNotNull { v ->
                v.url?.let { MediaVariant(it, "${v.height ?: 1080}px") }
            }
        } else {
            imageVersions.mapNotNull { img ->
                img.url?.let { MediaVariant(it, "${img.width ?: 1080}px") }
            }
        }

        val qualities = if (isVideo) {
            videoVersions.mapNotNull { v ->
                v.url?.let { QualityOption(it, v.width, v.height) }
            }
        } else {
            imageVersions.mapNotNull { img ->
                img.url?.let { QualityOption(it, img.width, img.height) }
            }
        }

        val thumbnailQualities = imageVersions.mapNotNull { img ->
            img.url?.let { QualityOption(it, img.width, img.height) }
        }

        return ExtractedMediaNode(
            thumbnailUrl = thumbnailUrl,
            downloadUrls = downloadUrls,
            downloadVariants = downloadVariants,
            url = primaryUrl,
            type = if (isVideo) "video" else "image",
            width = if (isVideo) videoVersions.firstOrNull()?.width else imageVersions.firstOrNull()?.width,
            height = if (isVideo) videoVersions.firstOrNull()?.height else imageVersions.firstOrNull()?.height,
            index = index,
            qualities = qualities,
            thumbnailQualities = thumbnailQualities
        )
    }

    private fun selectUrls(versions: List<String>, qualityPref: MediaQuality): List<String> {
        if (versions.isEmpty()) return emptyList()
        val size = versions.size
        return when (qualityPref) {
            MediaQuality.HIGH -> listOfNotNull(versions.firstOrNull())
            MediaQuality.LOW -> listOfNotNull(versions.lastOrNull())
            MediaQuality.MEDIUM -> listOfNotNull(versions.getOrNull(size / 2))
            MediaQuality.CUSTOM -> versions
        }
    }

    private fun extractShortcode(input: String): String {
        var sc = input.trim()
        if (sc.contains("instagram.com/")) {
            for (segment in listOf("/p/", "/reel/", "/reels/", "/tv/")) {
                if (sc.contains(segment)) {
                    sc = sc.substringAfter(segment).trimStart('/')
                    sc = sc.substringBefore('/')
                    sc = sc.substringBefore('?')
                    break
                }
            }
        }
        return sc
    }
}

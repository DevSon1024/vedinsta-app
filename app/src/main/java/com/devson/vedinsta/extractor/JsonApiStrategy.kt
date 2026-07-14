package com.devson.vedinsta.extractor

import com.devson.vedinsta.model.ExtractedMediaNode
import com.devson.vedinsta.model.ExtractedPost
import com.devson.vedinsta.model.MediaQuality
import com.devson.vedinsta.model.MediaVariant
import com.devson.vedinsta.model.QualityOption
import com.devson.vedinsta.model.ThumbnailQuality
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.Request
import java.io.IOException

/**
 * Extraction strategy using Instagram's public JSON/GraphQL API endpoints.
 */
class JsonApiStrategy : PublicExtractionStrategy {

    private val gson = Gson()
    private val client = UnauthenticatedNetworkModule.okHttpClient

    override suspend fun extractMedia(
        url: String,
        qualityPref: MediaQuality,
        thumbPref: ThumbnailQuality
    ): ExtractedPost = withContext(Dispatchers.IO) {
        val shortcode = extractShortcode(url)
        if (shortcode.isEmpty()) {
            throw IllegalArgumentException("Invalid Instagram URL, shortcode could not be extracted.")
        }

        // Waterfall 1: Try GraphQL query (POST)
        try {
            return@withContext queryGraphQL(shortcode, qualityPref, thumbPref)
        } catch (e: Exception) {
            // Log or print warning and try the legacy fallback
            System.err.println("GraphQL query failed for $shortcode: ${e.message}. Trying legacy endpoint fallback...")
        }

        // Waterfall 2: Try legacy endpoint (GET)
        return@withContext queryLegacyEndpoint(shortcode, qualityPref, thumbPref)
    }

    private fun ensureCookies() {
        val url = HttpUrl.Builder().scheme("https").host("www.instagram.com").build()
        val cookies = UnauthenticatedNetworkModule.cookieJar.loadForRequest(url)
        val hasCsrf = cookies.any { it.name == "csrftoken" }
        if (!hasCsrf) {
            val request = Request.Builder()
                .url("https://www.instagram.com/")
                .get()
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    response.body?.string() // Consume body to save cookies
                }
            } catch (e: Exception) {
                System.err.println("Failed to pre-fetch Instagram cookies: ${e.message}")
            }
        }
    }

    private fun queryGraphQL(shortcode: String, qualityPref: MediaQuality, thumbPref: ThumbnailQuality): ExtractedPost {
        ensureCookies()

        val variables = mapOf(
            "shortcode" to shortcode,
            "__relay_internal__pv__PolarisAIGMMediaWebLabelEnabledrelayprovider" to false
        )

        val formBody = FormBody.Builder()
            .add("doc_id", "27128499623469141")
            .add("variables", gson.toJson(variables))
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

            val gqlResponse = gson.fromJson(bodyString, PolarisPostRootResponse::class.java)
            val webInfo = gqlResponse.data?.webInfo ?: throw IOException("No web info found in GraphQL response.")
            val item = webInfo.items?.firstOrNull() ?: throw IOException("No items found in GraphQL response.")

            val mediaList = mutableListOf<ExtractedMediaNode>()
            val carouselItems = item.carouselMedia
            if (!carouselItems.isNullOrEmpty()) {
                carouselItems.forEachIndexed { idx, childItem ->
                    mediaList.add(mapLegacyCarouselMedia(childItem, qualityPref, thumbPref, idx + 1))
                }
            } else {
                mediaList.add(mapLegacyMedia(item, qualityPref, thumbPref, 1))
            }

            return ExtractedPost(
                mediaList = mediaList,
                username = item.user?.username ?: "unknown",
                caption = item.caption?.text ?: "",
                postId = shortcode
            )
        }
    }

    private fun queryLegacyEndpoint(shortcode: String, qualityPref: MediaQuality, thumbPref: ThumbnailQuality): ExtractedPost {
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
                    mediaList.add(mapLegacyCarouselMedia(childItem, qualityPref, thumbPref, idx + 1))
                }
            } else {
                mediaList.add(mapLegacyMedia(item, qualityPref, thumbPref, 1))
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

    private fun mapLegacyMedia(item: LegacyMediaItem, qualityPref: MediaQuality, thumbPref: ThumbnailQuality, index: Int): ExtractedMediaNode {
        val isVideo = item.mediaType == 2
        val videoVersions = item.videoVersions ?: emptyList()
        val imageVersions = item.imageVersions2?.candidates ?: emptyList()

        val thumbnailUrl = selectThumbnailUrl(imageVersions, thumbPref, qualityPref)

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

    private fun mapLegacyCarouselMedia(child: LegacyCarouselMedia, qualityPref: MediaQuality, thumbPref: ThumbnailQuality, index: Int): ExtractedMediaNode {
        val isVideo = child.mediaType == 2
        val videoVersions = child.videoVersions ?: emptyList()
        val imageVersions = child.imageVersions2?.candidates ?: emptyList()

        val thumbnailUrl = selectThumbnailUrl(imageVersions, thumbPref, qualityPref)

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

    private fun selectThumbnailUrl(versions: List<ImageVersion>, thumbPref: ThumbnailQuality, qualityPref: MediaQuality): String {
        if (versions.isEmpty()) return ""
        val size = versions.size
        val selected = when (thumbPref) {
            ThumbnailQuality.HIGHEST -> versions.firstOrNull()
            ThumbnailQuality.LOWEST -> versions.lastOrNull()
            ThumbnailQuality.MEDIUM -> versions.getOrNull(size / 2)
            ThumbnailQuality.SAME_AS_DOWNLOAD -> {
                when (qualityPref) {
                    MediaQuality.HIGH -> versions.firstOrNull()
                    MediaQuality.LOW -> versions.lastOrNull()
                    MediaQuality.MEDIUM -> versions.getOrNull(size / 2)
                    MediaQuality.CUSTOM -> versions.firstOrNull()
                }
            }
        }
        return selected?.url ?: ""
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

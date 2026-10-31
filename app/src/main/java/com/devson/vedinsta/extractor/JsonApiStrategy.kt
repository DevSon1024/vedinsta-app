package com.devson.vedinsta.extractor

import com.devson.vedinsta.model.ExtractedMediaNode
import com.devson.vedinsta.model.ExtractedPost
import com.devson.vedinsta.model.MediaQuality
import com.devson.vedinsta.model.MediaVariant
import com.devson.vedinsta.model.QualityOption
import com.devson.vedinsta.model.ThumbnailQuality
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.Request
import java.io.IOException

/**
 * Extraction strategy using Instagram's public JSON/GraphQL API endpoints and embedded webpage JSON.
 */
class JsonApiStrategy : PublicExtractionStrategy {

    companion object {
        private val scriptRegex = Regex("""<script[^>]*data-sjs[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
        private val appJsonRegex = Regex("""<script[^>]*type="application/json"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
        private val genericScriptRegex = Regex("""<script[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
        private val resolutionRegex = Regex("""[/_][ps](\d+)x(\d+)""")

        fun parseWebpageHtml(
            htmlBody: String,
            shortcode: String,
            qualityPref: MediaQuality,
            thumbPref: ThumbnailQuality
        ): ExtractedPost? {
            val parser = JsonApiStrategy()
            return parser.parseWebpageHtmlInternal(htmlBody, shortcode, qualityPref, thumbPref)
        }
    }

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

        // Waterfall 1: Modern Webpage Embedded JSON (Polaris/Relay) - Highest reliability for unauthenticated
        try {
            return@withContext queryWebpageEmbeddedJson(shortcode, qualityPref, thumbPref)
        } catch (e: AuthRequiredException) {
            throw e
        } catch (e: Exception) {
            System.err.println("Webpage embedded JSON failed for $shortcode: ${e.message}. Trying GraphQL query...")
        }

        // Waterfall 2: Try GraphQL query (POST)
        try {
            return@withContext queryGraphQL(shortcode, qualityPref, thumbPref)
        } catch (e: Exception) {
            System.err.println("GraphQL query failed for $shortcode: ${e.message}. Trying legacy endpoint fallback...")
        }

        // Waterfall 3: Try legacy endpoint (GET)
        return@withContext queryLegacyEndpoint(shortcode, qualityPref, thumbPref)
    }

    private fun queryWebpageEmbeddedJson(
        shortcode: String,
        qualityPref: MediaQuality,
        thumbPref: ThumbnailQuality
    ): ExtractedPost {
        val pageUrl = "https://www.instagram.com/p/$shortcode/"
        val request = Request.Builder()
            .url(pageUrl)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code == 401 || response.code == 403 || response.code == 302) {
                    throw AuthRequiredException("Webpage returned auth error code: ${response.code}")
                }
                throw IOException("Webpage request failed with code: ${response.code}")
            }

            val htmlBody = response.body?.string() ?: throw IOException("Empty webpage response body")
            if (response.request.url.encodedPath.contains("/accounts/login/") || htmlBody.contains("login_required")) {
                throw AuthRequiredException("Instagram redirected to login page.")
            }

            return parseWebpageHtmlInternal(htmlBody, shortcode, qualityPref, thumbPref)
                ?: throw IOException("No media data found in webpage HTML scripts.")
        }
    }

    internal fun parseWebpageHtmlInternal(
        htmlBody: String,
        shortcode: String,
        qualityPref: MediaQuality,
        thumbPref: ThumbnailQuality
    ): ExtractedPost? {
        val sjsMatches = scriptRegex.findAll(htmlBody).map { it.groupValues[1] }.toList()
        val appJsonMatches = if (sjsMatches.isEmpty()) {
            appJsonRegex.findAll(htmlBody).map { it.groupValues[1] }.toList()
        } else {
            emptyList()
        }
        val candidateScripts = (sjsMatches + appJsonMatches).ifEmpty {
            genericScriptRegex.findAll(htmlBody).map { it.groupValues[1] }.toList()
        }

        for (script in candidateScripts) {
            if (!script.contains("xig_polaris_media") &&
                !script.contains("xdt_shortcode_media") &&
                !script.contains("shortcode_media") &&
                !script.contains("xdt_api__v1__media__shortcode__web_info")
            ) {
                continue
            }

            val jsonElement = try {
                JsonParser.parseString(script)
            } catch (e: Exception) {
                null
            } ?: continue

            val mediaObj = findMediaJson(jsonElement) ?: continue

            if (mediaObj.has("gating_ruling") && !mediaObj.get("gating_ruling").isJsonNull) {
                throw AuthRequiredException("This post is restricted or requires login.")
            }

            // Check if format matches ShortcodeMedia
            if (mediaObj.has("edge_sidecar_to_children") ||
                (mediaObj.has("display_url") && !mediaObj.has("image_versions2"))
            ) {
                val scMedia = gson.fromJson(mediaObj, ShortcodeMedia::class.java)
                val mediaList = mutableListOf<ExtractedMediaNode>()
                val edges = scMedia.edgeSidecarToChildren?.edges
                if (!edges.isNullOrEmpty()) {
                    edges.forEachIndexed { idx, edge ->
                        edge.node?.let { childNode ->
                            mediaList.add(mapGraphQLSidecarNode(childNode, qualityPref, idx + 1))
                        }
                    }
                } else {
                    mediaList.add(mapGraphQLMedia(scMedia, qualityPref, 1))
                }
                return ExtractedPost(
                    mediaList = mediaList,
                    username = scMedia.owner?.username ?: "unknown",
                    caption = scMedia.edgeMediaToCaption?.edges?.firstOrNull()?.node?.text ?: "",
                    postId = shortcode
                )
            }

            // Otherwise format matches LegacyMediaItem
            val legacyItem = gson.fromJson(mediaObj, LegacyMediaItem::class.java)
            val mediaList = mutableListOf<ExtractedMediaNode>()
            val carouselItems = legacyItem.carouselMedia
            if (!carouselItems.isNullOrEmpty()) {
                carouselItems.forEachIndexed { idx, childItem ->
                    mediaList.add(mapLegacyCarouselMedia(childItem, qualityPref, thumbPref, idx + 1))
                }
            } else {
                mediaList.add(mapLegacyMedia(legacyItem, qualityPref, thumbPref, 1))
            }
            return ExtractedPost(
                mediaList = mediaList,
                username = legacyItem.user?.username ?: "unknown",
                caption = legacyItem.caption?.text ?: "",
                postId = shortcode
            )
        }
        return null
    }

    private fun findMediaJson(element: JsonElement): JsonObject? {
        if (element.isJsonObject) {
            val obj = element.asJsonObject
            if (obj.has("xig_polaris_media")) {
                val polaris = obj.get("xig_polaris_media")
                if (polaris.isJsonObject) {
                    val polarisObj = polaris.asJsonObject
                    if (polarisObj.has("if_not_gated_logged_out") && polarisObj.get("if_not_gated_logged_out").isJsonObject) {
                        return polarisObj.getAsJsonObject("if_not_gated_logged_out")
                    }
                    return polarisObj
                }
            }
            if (obj.has("xdt_shortcode_media") && obj.get("xdt_shortcode_media").isJsonObject) {
                return obj.getAsJsonObject("xdt_shortcode_media")
            }
            if (obj.has("shortcode_media") && obj.get("shortcode_media").isJsonObject) {
                return obj.getAsJsonObject("shortcode_media")
            }
            if (obj.has("xdt_api__v1__media__shortcode__web_info")) {
                val webInfo = obj.get("xdt_api__v1__media__shortcode__web_info")
                if (webInfo.isJsonObject) {
                    val items = webInfo.asJsonObject.getAsJsonArray("items")
                    if (items != null && items.size() > 0 && items[0].isJsonObject) {
                        return items[0].asJsonObject
                    }
                }
            }
            for (entry in obj.entrySet()) {
                val found = findMediaJson(entry.value)
                if (found != null) return found
            }
        } else if (element.isJsonArray) {
            for (item in element.asJsonArray) {
                val found = findMediaJson(item)
                if (found != null) return found
            }
        }
        return null
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
            .add("doc_id", "29632631486333815")
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
            val seen = mutableSetOf<String>()
            val variants = mutableListOf<MediaVariant>()
            videoVersions.forEach { v ->
                val vUrl = v.url ?: return@forEach
                val h = v.height ?: resolveVideoHeight(vUrl, item.originalHeight)
                val label = "${h}px"
                if (seen.add(label)) {
                    variants.add(MediaVariant(vUrl, label))
                }
            }
            if (variants.isEmpty() && primaryUrl.isNotEmpty()) {
                variants.add(MediaVariant(primaryUrl, "1080px"))
            }
            variants
        } else {
            val seen = mutableSetOf<String>()
            val variants = mutableListOf<MediaVariant>()
            imageVersions.forEachIndexed { idx, img ->
                val imgUrl = img.url ?: return@forEachIndexed
                val (w, h, label) = resolveCandidateResolution(imgUrl, idx, img.width, img.height, item.originalWidth, item.originalHeight)
                if (seen.add(label)) {
                    variants.add(MediaVariant(imgUrl, label))
                }
            }
            if (variants.isEmpty() && primaryUrl.isNotEmpty()) {
                variants.add(MediaVariant(primaryUrl, "1080px"))
            }
            variants
        }

        val qualities = if (isVideo) {
            videoVersions.mapNotNull { v ->
                val vUrl = v.url ?: return@mapNotNull null
                val h = v.height ?: resolveVideoHeight(vUrl, item.originalHeight)
                val w = v.width ?: item.originalWidth ?: (h * 9 / 16)
                QualityOption(vUrl, w, h)
            }
        } else {
            imageVersions.mapIndexedNotNull { idx, img ->
                val imgUrl = img.url ?: return@mapIndexedNotNull null
                val (w, h, _) = resolveCandidateResolution(imgUrl, idx, img.width, img.height, item.originalWidth, item.originalHeight)
                QualityOption(imgUrl, w, h)
            }
        }

        val thumbnailQualities = imageVersions.mapIndexedNotNull { idx, img ->
            val imgUrl = img.url ?: return@mapIndexedNotNull null
            val (w, h, _) = resolveCandidateResolution(imgUrl, idx, img.width, img.height, item.originalWidth, item.originalHeight)
            QualityOption(imgUrl, w, h)
        }

        val primaryWidth = if (isVideo) {
            videoVersions.firstOrNull()?.width ?: item.originalWidth
        } else {
            imageVersions.firstOrNull()?.let {
                resolveCandidateResolution(it.url ?: "", 0, it.width, it.height, item.originalWidth, item.originalHeight).first
            } ?: item.originalWidth
        }

        val primaryHeight = if (isVideo) {
            videoVersions.firstOrNull()?.height ?: item.originalHeight
        } else {
            imageVersions.firstOrNull()?.let {
                resolveCandidateResolution(it.url ?: "", 0, it.width, it.height, item.originalWidth, item.originalHeight).second
            } ?: item.originalHeight
        }

        return ExtractedMediaNode(
            thumbnailUrl = thumbnailUrl,
            downloadUrls = downloadUrls,
            downloadVariants = downloadVariants,
            url = primaryUrl,
            type = if (isVideo) "video" else "image",
            width = primaryWidth,
            height = primaryHeight,
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
            val seen = mutableSetOf<String>()
            val variants = mutableListOf<MediaVariant>()
            videoVersions.forEach { v ->
                val vUrl = v.url ?: return@forEach
                val h = v.height ?: resolveVideoHeight(vUrl, child.originalHeight)
                val label = "${h}px"
                if (seen.add(label)) {
                    variants.add(MediaVariant(vUrl, label))
                }
            }
            if (variants.isEmpty() && primaryUrl.isNotEmpty()) {
                variants.add(MediaVariant(primaryUrl, "1080px"))
            }
            variants
        } else {
            val seen = mutableSetOf<String>()
            val variants = mutableListOf<MediaVariant>()
            imageVersions.forEachIndexed { idx, img ->
                val imgUrl = img.url ?: return@forEachIndexed
                val (w, h, label) = resolveCandidateResolution(imgUrl, idx, img.width, img.height, child.originalWidth, child.originalHeight)
                if (seen.add(label)) {
                    variants.add(MediaVariant(imgUrl, label))
                }
            }
            if (variants.isEmpty() && primaryUrl.isNotEmpty()) {
                variants.add(MediaVariant(primaryUrl, "1080px"))
            }
            variants
        }

        val qualities = if (isVideo) {
            videoVersions.mapNotNull { v ->
                val vUrl = v.url ?: return@mapNotNull null
                val h = v.height ?: resolveVideoHeight(vUrl, child.originalHeight)
                val w = v.width ?: child.originalWidth ?: (h * 9 / 16)
                QualityOption(vUrl, w, h)
            }
        } else {
            imageVersions.mapIndexedNotNull { idx, img ->
                val imgUrl = img.url ?: return@mapIndexedNotNull null
                val (w, h, _) = resolveCandidateResolution(imgUrl, idx, img.width, img.height, child.originalWidth, child.originalHeight)
                QualityOption(imgUrl, w, h)
            }
        }

        val thumbnailQualities = imageVersions.mapIndexedNotNull { idx, img ->
            val imgUrl = img.url ?: return@mapIndexedNotNull null
            val (w, h, _) = resolveCandidateResolution(imgUrl, idx, img.width, img.height, child.originalWidth, child.originalHeight)
            QualityOption(imgUrl, w, h)
        }

        val primaryWidth = if (isVideo) {
            videoVersions.firstOrNull()?.width ?: child.originalWidth
        } else {
            imageVersions.firstOrNull()?.let {
                resolveCandidateResolution(it.url ?: "", 0, it.width, it.height, child.originalWidth, child.originalHeight).first
            } ?: child.originalWidth
        }

        val primaryHeight = if (isVideo) {
            videoVersions.firstOrNull()?.height ?: child.originalHeight
        } else {
            imageVersions.firstOrNull()?.let {
                resolveCandidateResolution(it.url ?: "", 0, it.width, it.height, child.originalWidth, child.originalHeight).second
            } ?: child.originalHeight
        }

        return ExtractedMediaNode(
            thumbnailUrl = thumbnailUrl,
            downloadUrls = downloadUrls,
            downloadVariants = downloadVariants,
            url = primaryUrl,
            type = if (isVideo) "video" else "image",
            width = primaryWidth,
            height = primaryHeight,
            index = index,
            qualities = qualities,
            thumbnailQualities = thumbnailQualities
        )
    }

    private fun resolveCandidateResolution(
        url: String,
        index: Int,
        width: Int?,
        height: Int?,
        originalWidth: Int?,
        originalHeight: Int?
    ): Triple<Int, Int, String> {
        if (width != null && height != null && width > 0 && height > 0) {
            val label = if (index == 0 && (originalWidth != null || originalHeight != null)) {
                "${maxOf(width, height)}px"
            } else {
                "${height}px"
            }
            return Triple(width, height, label)
        }
        val match = resolutionRegex.find(url)
        if (match != null) {
            val w = match.groupValues[1].toIntOrNull() ?: 1080
            val h = match.groupValues[2].toIntOrNull() ?: 1080
            return Triple(w, h, "${h}px")
        }
        if (index == 0) {
            val w = originalWidth ?: 1080
            val h = originalHeight ?: 1080
            val maxDim = maxOf(w, h)
            return Triple(w, h, "${maxDim}px")
        }
        return Triple(1080, 1080, "1080px")
    }

    private fun resolveVideoHeight(url: String, originalHeight: Int?): Int {
        if (originalHeight != null && originalHeight > 0) return originalHeight
        val match = Regex("""\.(\d{3,4})\.dash""").find(url)
        if (match != null) {
            val h = match.groupValues[1].toIntOrNull()
            if (h != null) return h
        }
        return 1080
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

package com.devson.vedinsta.extractor

import com.devson.vedinsta.model.ExtractedMediaNode
import com.devson.vedinsta.model.ExtractedPost
import com.devson.vedinsta.model.MediaQuality
import com.devson.vedinsta.model.MediaVariant
import com.devson.vedinsta.model.QualityOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import okhttp3.Request
import java.io.IOException

/**
 * Extraction strategy that scrapes public HTML pages as an ultimate fallback.
 * Used when the JSON API endpoints redirect to login or fail.
 */
class HtmlFallbackStrategy : PublicExtractionStrategy {

    private val client = UnauthenticatedNetworkModule.okHttpClient

    override suspend fun extractMedia(url: String, qualityPref: MediaQuality): ExtractedPost = withContext(Dispatchers.IO) {
        val shortcode = extractShortcode(url)
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTML request failed with code: ${response.code}")
            }

            val htmlBody = response.body?.string() ?: throw IOException("Empty HTML response body")
            val doc = Jsoup.parse(htmlBody)

            val videoUrl = doc.select("meta[property=og:video]").attr("content")
            val imageUrl = doc.select("meta[property=og:image]").attr("content")

            if (videoUrl.isEmpty() && imageUrl.isEmpty()) {
                throw IOException("No media found in HTML meta tags (both og:video and og:image are empty).")
            }

            val isVideo = videoUrl.isNotEmpty()
            val finalMediaUrl = if (isVideo) videoUrl else imageUrl
            val thumbnailUrl = if (imageUrl.isNotEmpty()) imageUrl else videoUrl

            // Override quality preference: HTML only provides one URL
            val downloadUrls = listOf(finalMediaUrl)
            val downloadVariants = listOf(MediaVariant(finalMediaUrl, "1080px"))
            val qualities = listOf(QualityOption(finalMediaUrl, 1080, 1080))
            val thumbnailQualities = listOf(QualityOption(thumbnailUrl, 1080, 1080))

            val mediaNode = ExtractedMediaNode(
                thumbnailUrl = thumbnailUrl,
                downloadUrls = downloadUrls,
                downloadVariants = downloadVariants,
                url = finalMediaUrl,
                type = if (isVideo) "video" else "image",
                width = 1080,
                height = 1080,
                index = 1,
                qualities = qualities,
                thumbnailQualities = thumbnailQualities
            )

            // Extract username from og:title or description
            val ogTitle = doc.select("meta[property=og:title]").attr("content")
            val username = if (ogTitle.isNotEmpty()) {
                val match = Regex("([^ ]+) on Instagram").find(ogTitle)
                    ?: Regex("by ([^ ]+)").find(ogTitle)
                match?.groupValues?.getOrNull(1) ?: "unknown"
            } else {
                "unknown"
            }

            // Extract caption from og:description
            val ogDescription = doc.select("meta[property=og:description]").attr("content")
            val caption = ogDescription.ifEmpty { "" }

            return@withContext ExtractedPost(
                mediaList = listOf(mediaNode),
                username = username,
                caption = caption,
                postId = shortcode
            )
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

package com.devson.vedinsta.repository

import android.content.Context
import android.util.Log
import com.devson.vedinsta.extractor.InstagramNativeExtractor
import com.devson.vedinsta.model.ExtractedMediaNode
import com.devson.vedinsta.model.MediaQuality
import com.devson.vedinsta.model.ThumbnailQuality
import com.devson.vedinsta.model.InstagramResponse
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaFetcherRepository(private val context: Context) {

    private val gson = Gson()

    /**
     * Executes native media extraction and returns the list of ExtractedMediaNode objects.
     */
    suspend fun fetchMedia(urlOrShortcode: String): com.devson.vedinsta.model.ExtractedPost = withContext(Dispatchers.IO) {
        try {
            val cookieFile = File(context.filesDir, "instagram_cookies.txt")
            val prefs = context.getSharedPreferences("VedInstaPrefs", Context.MODE_PRIVATE)
            val customUserAgent = prefs.getString("custom_user_agent", "") ?: ""
            val customIgAppId = prefs.getString("custom_ig_app_id", "") ?: ""
            val networkTimeoutSeconds = prefs.getInt("network_timeout_seconds", 15)

            val qualityPrefStr = prefs.getString("user_quality_preference", "HIGH") ?: "HIGH"
            val userQualityPreference = try {
                MediaQuality.valueOf(qualityPrefStr)
            } catch (e: Exception) {
                MediaQuality.HIGH
            }

            val thumbnailPrefStr = prefs.getString("thumbnail_quality_preference", "LOWEST") ?: "LOWEST"
            val thumbnailQualityPreference = try {
                ThumbnailQuality.valueOf(thumbnailPrefStr)
            } catch (e: Exception) {
                ThumbnailQuality.LOWEST
            }

            val resultJson = InstagramNativeExtractor.getMediaUrls(
                url = urlOrShortcode,
                cookieFilePath = cookieFile.absolutePath,
                userAgent = customUserAgent,
                appId = customIgAppId,
                timeoutSeconds = networkTimeoutSeconds,
                userQualityPreference = userQualityPreference,
                thumbnailQualityPreference = thumbnailQualityPreference
            )
            Log.d("MediaFetcherRepository", "getMediaUrls returned: $resultJson")

            val response = gson.fromJson(resultJson, InstagramResponse::class.java)
                ?: throw Exception("Failed to parse extractor response")

            if (response.status != "success") {
                val message = response.message ?: "Unknown error"
                throw Exception(message)
            }

            val username = response.username ?: "unknown"
            val caption = response.caption
            val postId = response.shortcode ?: "unknown"
            val results = response.media ?: emptyList()

            val firstError = results.firstOrNull()?.error
            if (firstError != null) {
                throw Exception(firstError)
            }

            com.devson.vedinsta.model.ExtractedPost(
                mediaList = results,
                username = username,
                caption = caption,
                postId = postId
            )
        } catch (e: Exception) {
            Log.e("MediaFetcherRepository", "Error fetching media via native extractor", e)
            throw e
        }
    }
}

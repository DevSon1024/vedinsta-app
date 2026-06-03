package com.devson.vedinsta.repository

import android.content.Context
import android.util.Log
import com.devson.vedinsta.extractor.InstagramNativeExtractor
import com.devson.vedinsta.model.MediaResult
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaFetcherRepository(private val context: Context) {

    private val gson = Gson()

    /**
     * Executes native media extraction and returns the list of MediaResult objects.
     */
    suspend fun fetchMedia(urlOrShortcode: String): com.devson.vedinsta.model.ExtractedPost = withContext(Dispatchers.IO) {
        try {
            val cookieFile = File(context.filesDir, "instagram_cookies.txt")
            val resultJson = InstagramNativeExtractor.getMediaUrls(urlOrShortcode, cookieFile.absolutePath)
            Log.d("MediaFetcherRepository", "getMediaUrls returned: $resultJson")

            val responseType = object : TypeToken<Map<String, Any>>() {}.type
            val responseMap: Map<String, Any> = gson.fromJson(resultJson, responseType)

            val status = responseMap["status"] as? String ?: "error"
            if (status != "success") {
                val message = responseMap["message"] as? String ?: "Unknown error"
                throw Exception(message)
            }

            val username = responseMap["username"] as? String ?: "unknown"
            val caption = responseMap["caption"] as? String
            val postId = responseMap["shortcode"] as? String ?: "unknown"

            val mediaJson = gson.toJson(responseMap["media"])
            val listType = object : TypeToken<List<MediaResult>>() {}.type
            val results: List<MediaResult> = gson.fromJson(mediaJson, listType)

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

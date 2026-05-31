package com.devson.vedinsta.repository

import android.content.Context
import android.util.Log
import com.chaquo.python.Python
import com.devson.vedinsta.model.MediaResult
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaFetcherRepository(private val context: Context) {

    private val gson = Gson()

    /**
     * Executes mo3.py using Chaquopy and returns the list of MediaResult objects.
     * Throws exception if Python execution fails or the script reports an error.
     */
    suspend fun fetchMedia(urlOrShortcode: String): List<MediaResult> = withContext(Dispatchers.IO) {
        try {
            // Ensure Python is initialized
            if (!Python.isStarted()) {
                throw Exception("Python is not started")
            }

            val python = Python.getInstance()
            val mo3Module = python.getModule("mo3")
            
            // Get absolute path to the cookies file
            val cookieFile = File(context.filesDir, "instagram_cookies.txt")
            
            // Call the get_media_urls method on mo3 directly
            val resultJson = mo3Module.callAttr("get_media_urls", urlOrShortcode, cookieFile.absolutePath).toString()
            Log.d("MediaFetcherRepository", "mo3.get_media_urls returned: $resultJson")

            val responseType = object : TypeToken<Map<String, Any>>() {}.type
            val responseMap: Map<String, Any> = gson.fromJson(resultJson, responseType)

            val status = responseMap["status"] as? String ?: "error"
            if (status != "success") {
                val message = responseMap["message"] as? String ?: "Unknown error"
                throw Exception(message)
            }

            val mediaJson = gson.toJson(responseMap["media"])
            val listType = object : TypeToken<List<MediaResult>>() {}.type
            val results: List<MediaResult> = gson.fromJson(mediaJson, listType)

            val firstError = results.firstOrNull()?.error
            if (firstError != null) {
                throw Exception(firstError)
            }

            results
        } catch (e: Exception) {
            Log.e("MediaFetcherRepository", "Error fetching media via mo3.py direct call", e)
            throw e
        }
    }
}

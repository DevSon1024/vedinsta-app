// src/main/java/com/devson/vedinsta/SettingsManager.kt
package com.devson.vedinsta

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

class SettingsManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("VedInstaPrefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_IMAGE_URI = "image_uri"
        const val KEY_VIDEO_URI = "video_uri"
    }

    var imageDirectoryUri: String?
        get() = prefs.getString(KEY_IMAGE_URI, null)
        set(value) = prefs.edit().putString(KEY_IMAGE_URI, value).apply()

    var videoDirectoryUri: String?
        get() = prefs.getString(KEY_VIDEO_URI, null)
        set(value) = prefs.edit().putString(KEY_VIDEO_URI, value).apply()

    fun getImagePathLabel(): String {
        return imageDirectoryUri?.let { uriString ->
            DocumentFile.fromTreeUri(context, Uri.parse(uriString))?.name
        } ?: "Default: Pictures/VedInsta/"
    }

    fun getVideoPathLabel(): String {
        return videoDirectoryUri?.let { uriString ->
            DocumentFile.fromTreeUri(context, Uri.parse(uriString))?.name
        } ?: "Default: Movies/VedInsta/"
    }
}
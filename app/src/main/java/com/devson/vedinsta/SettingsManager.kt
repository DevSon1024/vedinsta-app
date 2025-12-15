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
        const val KEY_GRID_COLUMNS = "grid_columns"
        const val KEY_DEFAULT_LINK_ACTION = "default_link_action"

        // Actions
        const val ACTION_ASK_EVERY_TIME = 0
        const val ACTION_DOWNLOAD_ALL = 1
        const val ACTION_OPEN_SELECTION = 2
    }

    var gridColumnCount: Int
        get() = prefs.getInt(KEY_GRID_COLUMNS, 3)
        set(value) = prefs.edit().putInt(KEY_GRID_COLUMNS, value.coerceIn(1, 6)).apply()

    var imageDirectoryUri: String?
        get() = prefs.getString(KEY_IMAGE_URI, null)
        set(value) = prefs.edit().putString(KEY_IMAGE_URI, value).apply()

    var videoDirectoryUri: String?
        get() = prefs.getString(KEY_VIDEO_URI, null)
        set(value) = prefs.edit().putString(KEY_VIDEO_URI, value).apply()

    var defaultLinkAction: Int
        get() = prefs.getInt(KEY_DEFAULT_LINK_ACTION, ACTION_ASK_EVERY_TIME)
        set(value) = prefs.edit().putInt(KEY_DEFAULT_LINK_ACTION, value).apply()

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

    fun getDefaultActionLabel(): String {
        return when (defaultLinkAction) {
            ACTION_DOWNLOAD_ALL -> "Download All Immediately"
            ACTION_OPEN_SELECTION -> "Open Selection Screen"
            else -> "Ask Every Time (Notification)"
        }
    }
}
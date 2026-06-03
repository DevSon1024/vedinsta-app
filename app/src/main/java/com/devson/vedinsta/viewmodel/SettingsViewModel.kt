package com.devson.vedinsta.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import com.devson.vedinsta.ui.theme.AppThemePalette
import com.devson.vedinsta.ui.theme.AppThemePaletteHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs: SharedPreferences =
        application.getSharedPreferences("VedInstaPrefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_IMAGE_URI = "image_uri"
        const val KEY_VIDEO_URI = "video_uri"
        const val KEY_GRID_COLUMNS = "grid_columns"
        const val KEY_DEFAULT_LINK_ACTION = "default_link_action"
        const val KEY_IS_LIST_VIEW = "is_list_view"

        // Actions
        const val ACTION_ASK_EVERY_TIME = 0
        const val ACTION_DOWNLOAD_ALL = 1
        const val ACTION_OPEN_SELECTION = 2
    }

    var gridColumnCount: Int
        get() = prefs.getInt(KEY_GRID_COLUMNS, 3)
        set(value) = prefs.edit().putInt(KEY_GRID_COLUMNS, value.coerceIn(1, 6)).apply()

    var isListView: Boolean
        get() = prefs.getBoolean(KEY_IS_LIST_VIEW, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_LIST_VIEW, value).apply()

    var imageDirectoryUri: String?
        get() = prefs.getString(KEY_IMAGE_URI, null)
        set(value) = prefs.edit().putString(KEY_IMAGE_URI, value).apply()

    var videoDirectoryUri: String?
        get() = prefs.getString(KEY_VIDEO_URI, null)
        set(value) = prefs.edit().putString(KEY_VIDEO_URI, value).apply()

    var defaultLinkAction: Int
        get() = prefs.getInt(KEY_DEFAULT_LINK_ACTION, ACTION_ASK_EVERY_TIME)
        set(value) = prefs.edit().putInt(KEY_DEFAULT_LINK_ACTION, value).apply()

    var appTheme: Int
        get() = prefs.getInt("app_theme", 0) // 0 = System, 1 = Light, 2 = Dark
        set(value) = prefs.edit().putInt("app_theme", value).apply()

    var maxNotificationsLimit: Int
        get() = prefs.getInt("max_notifications_limit", 0)
        set(value) = prefs.edit().putInt("max_notifications_limit", value).apply()

    fun getImagePathLabel(): String {
        return imageDirectoryUri?.let { uriString ->
            DocumentFile.fromTreeUri(getApplication(), Uri.parse(uriString))?.name
        } ?: "Default: Pictures/VedInsta/"
    }

    fun getVideoPathLabel(): String {
        return videoDirectoryUri?.let { uriString ->
            DocumentFile.fromTreeUri(getApplication(), Uri.parse(uriString))?.name
        } ?: "Default: Movies/VedInsta/"
    }

    fun getDefaultActionLabel(): String {
        return when (defaultLinkAction) {
            ACTION_DOWNLOAD_ALL -> "Download All Immediately"
            ACTION_OPEN_SELECTION -> "Open Selection Screen"
            else -> "Ask Every Time (Notification)"
        }
    }

    val favoritePostIds: Set<String>
        get() = prefs.getStringSet("favorite_post_ids", emptySet()) ?: emptySet()

    fun isFavorite(postId: String): Boolean {
        return favoritePostIds.contains(postId)
    }

    fun toggleFavorite(postId: String) {
        val favorites = prefs.getStringSet("favorite_post_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (favorites.contains(postId)) {
            favorites.remove(postId)
        } else {
            favorites.add(postId)
        }
        prefs.edit().putStringSet("favorite_post_ids", favorites).apply()
    }

    private val _isDarkTheme = MutableStateFlow<Boolean?>(
        if (prefs.contains("dark_theme")) prefs.getBoolean("dark_theme", false) else null
    )
    val isDarkTheme: StateFlow<Boolean?> = _isDarkTheme.asStateFlow()

    private val _isAmoledTheme = MutableStateFlow(
        prefs.getBoolean("is_amoled_theme", false)
    )
    val isAmoledTheme: StateFlow<Boolean> = _isAmoledTheme.asStateFlow()

    private val _dynamicColor = MutableStateFlow(
        prefs.getBoolean("dynamic_color", false)
    )
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _selectedPalette = MutableStateFlow(
        AppThemePaletteHelper.fromKey(prefs.getString("selected_palette", AppThemePalette.BLUE.name))
    )
    val selectedPalette: StateFlow<AppThemePalette> = _selectedPalette.asStateFlow()

    private val _isNavBarTransparent = MutableStateFlow(
        prefs.getBoolean("nav_bar_transparent", true)
    )
    val isNavBarTransparent: StateFlow<Boolean> = _isNavBarTransparent.asStateFlow()

    fun setDarkTheme(isDark: Boolean) {
        prefs.edit().putBoolean("dark_theme", isDark).apply()
        _isDarkTheme.value = isDark
    }

    fun resetDarkTheme() {
        prefs.edit().remove("dark_theme").apply()
        _isDarkTheme.value = null
    }

    fun setAmoledTheme(enabled: Boolean) {
        prefs.edit().putBoolean("is_amoled_theme", enabled).apply()
        _isAmoledTheme.value = enabled
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean("dynamic_color", enabled).apply()
        _dynamicColor.value = enabled
    }

    fun setSelectedPalette(palette: AppThemePalette) {
        prefs.edit().putString("selected_palette", palette.name).apply()
        _selectedPalette.value = palette
    }

    fun setNavBarTransparent(transparent: Boolean) {
        prefs.edit().putBoolean("nav_bar_transparent", transparent).apply()
        _isNavBarTransparent.value = transparent
    }
}

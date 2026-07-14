package com.devson.vedinsta.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devson.vedinsta.ui.theme.AppThemePalette
import com.devson.vedinsta.ui.theme.AppThemePaletteHelper
import com.devson.vedinsta.model.MediaQuality
import com.devson.vedinsta.model.ThumbnailQuality
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs: SharedPreferences =
        application.getSharedPreferences("VedInstaPrefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_IMAGE_URI = "image_uri"
        const val KEY_VIDEO_URI = "video_uri"
        const val KEY_GRID_COLUMNS = "grid_columns"
        const val KEY_DEFAULT_LINK_ACTION = "default_link_action"
        const val KEY_IS_LIST_VIEW = "is_list_view"

        // Filename template
        const val KEY_FILENAME_TEMPLATE = "filename_template"
        const val DEFAULT_FILENAME_TEMPLATE = "{username}_{milliseconds}"

        // Advanced Network Customization
        const val KEY_CUSTOM_USER_AGENT = "custom_user_agent"
        const val KEY_CUSTOM_IG_APP_ID = "custom_ig_app_id"
        const val KEY_NETWORK_TIMEOUT_SECONDS = "network_timeout_seconds"
        const val KEY_MAX_RETRIES = "max_retries"
        const val KEY_ACCEPT_LANGUAGE = "accept_language"
        const val KEY_X_ASBD_ID = "x_asbd_id"
        const val KEY_VIEWPORT_WIDTH = "viewport_width"
        const val KEY_MIN_JITTER_DELAY = "min_jitter_delay"
        const val KEY_MAX_JITTER_DELAY = "max_jitter_delay"

        // Actions
        const val ACTION_ASK_EVERY_TIME = 0
        const val ACTION_DOWNLOAD_ALL = 1
        const val ACTION_OPEN_SELECTION = 2

        // Sample values used to render the filename live preview
        private const val SAMPLE_USERNAME = "devson"
        private const val SAMPLE_MILLIS = "1698765432100"
        private const val SAMPLE_DATE = "2024-01-15"
        private const val SAMPLE_SHORT_ID = "B4xK9mN"
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

    private val _userQualityPreference = MutableStateFlow(MediaQuality.HIGH)
    val userQualityPreferenceFlow: StateFlow<MediaQuality> = _userQualityPreference.asStateFlow()

    var userQualityPreference: MediaQuality
        get() = _userQualityPreference.value
        set(value) {
            _userQualityPreference.value = value
            viewModelScope.launch(Dispatchers.IO) {
                prefs.edit().putString("user_quality_preference", value.name).apply()
            }
        }

    private val _thumbnailQuality = MutableStateFlow(ThumbnailQuality.LOWEST)
    val thumbnailQualityFlow: StateFlow<ThumbnailQuality> = _thumbnailQuality.asStateFlow()

    var thumbnailQuality: ThumbnailQuality
        get() = _thumbnailQuality.value
        set(value) {
            _thumbnailQuality.value = value
            viewModelScope.launch(Dispatchers.IO) {
                prefs.edit().putString("thumbnail_quality_preference", value.name).apply()
            }
        }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val qStr = prefs.getString("user_quality_preference", "HIGH") ?: "HIGH"
            val quality = try { MediaQuality.valueOf(qStr) } catch(e: Exception) { MediaQuality.HIGH }
            _userQualityPreference.value = quality

            val tStr = prefs.getString("thumbnail_quality_preference", "LOWEST") ?: "LOWEST"
            val tQuality = try { ThumbnailQuality.valueOf(tStr) } catch(e: Exception) { ThumbnailQuality.LOWEST }
            _thumbnailQuality.value = tQuality
        }
    }

    private val _customUserAgent = MutableStateFlow(
        prefs.getString(KEY_CUSTOM_USER_AGENT, "") ?: ""
    )
    val customUserAgentFlow: StateFlow<String> = _customUserAgent.asStateFlow()

    var customUserAgent: String
        get() = prefs.getString(KEY_CUSTOM_USER_AGENT, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_CUSTOM_USER_AGENT, value).apply()
            _customUserAgent.value = value
        }

    private val _customIgAppId = MutableStateFlow(
        prefs.getString(KEY_CUSTOM_IG_APP_ID, "") ?: ""
    )
    val customIgAppIdFlow: StateFlow<String> = _customIgAppId.asStateFlow()

    var customIgAppId: String
        get() = prefs.getString(KEY_CUSTOM_IG_APP_ID, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_CUSTOM_IG_APP_ID, value).apply()
            _customIgAppId.value = value
        }

    private val _acceptLanguage = MutableStateFlow(
        prefs.getString(KEY_ACCEPT_LANGUAGE, "") ?: ""
    )
    val acceptLanguageFlow: StateFlow<String> = _acceptLanguage.asStateFlow()

    var acceptLanguage: String
        get() = prefs.getString(KEY_ACCEPT_LANGUAGE, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_ACCEPT_LANGUAGE, value).apply()
            _acceptLanguage.value = value
        }

    private val _xAsbdId = MutableStateFlow(
        prefs.getString(KEY_X_ASBD_ID, "") ?: ""
    )
    val xAsbdIdFlow: StateFlow<String> = _xAsbdId.asStateFlow()

    var xAsbdId: String
        get() = prefs.getString(KEY_X_ASBD_ID, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_X_ASBD_ID, value).apply()
            _xAsbdId.value = value
        }

    private val _viewportWidth = MutableStateFlow(
        prefs.getString(KEY_VIEWPORT_WIDTH, "") ?: ""
    )
    val viewportWidthFlow: StateFlow<String> = _viewportWidth.asStateFlow()

    var viewportWidth: String
        get() = prefs.getString(KEY_VIEWPORT_WIDTH, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_VIEWPORT_WIDTH, value).apply()
            _viewportWidth.value = value
        }

    private val _minJitterDelay = MutableStateFlow(
        prefs.getLong(KEY_MIN_JITTER_DELAY, 3000L)
    )
    val minJitterDelayFlow: StateFlow<Long> = _minJitterDelay.asStateFlow()

    var minJitterDelay: Long
        get() = prefs.getLong(KEY_MIN_JITTER_DELAY, 3000L)
        set(value) {
            prefs.edit().putLong(KEY_MIN_JITTER_DELAY, value).apply()
            _minJitterDelay.value = value
        }

    private val _maxJitterDelay = MutableStateFlow(
        prefs.getLong(KEY_MAX_JITTER_DELAY, 8000L)
    )
    val maxJitterDelayFlow: StateFlow<Long> = _maxJitterDelay.asStateFlow()

    var maxJitterDelay: Long
        get() = prefs.getLong(KEY_MAX_JITTER_DELAY, 8000L)
        set(value) {
            prefs.edit().putLong(KEY_MAX_JITTER_DELAY, value).apply()
            _maxJitterDelay.value = value
        }

    var networkTimeoutSeconds: Int
        get() = prefs.getInt(KEY_NETWORK_TIMEOUT_SECONDS, 15)
        set(value) = prefs.edit().putInt(KEY_NETWORK_TIMEOUT_SECONDS, value).apply()

    var maxRetries: Int
        get() = prefs.getInt(KEY_MAX_RETRIES, 3)
        set(value) = prefs.edit().putInt(KEY_MAX_RETRIES, value).apply()

    // Filename template
    private val _filenameTemplate = MutableStateFlow(
        prefs.getString(KEY_FILENAME_TEMPLATE, DEFAULT_FILENAME_TEMPLATE) ?: DEFAULT_FILENAME_TEMPLATE
    )
    val filenameTemplate: StateFlow<String> = _filenameTemplate.asStateFlow()

    fun setFilenameTemplate(template: String) {
        prefs.edit().putString(KEY_FILENAME_TEMPLATE, template).apply()
        _filenameTemplate.value = template
    }

    /**
     * Substitutes sample values for every supported tag and returns a preview
     * filename string with the given extension appended.
     * Supported tags: {username}, {milliseconds}, {date}, {short_id}
     */
    fun buildFilenamePreview(template: String, ext: String = "mp4"): String {
        return template
            .replace("{username}", SAMPLE_USERNAME)
            .replace("{milliseconds}", SAMPLE_MILLIS)
            .replace("{date}", SAMPLE_DATE)
            .replace("{short_id}", SAMPLE_SHORT_ID)
            .plus(".$ext")
    }

    // Custom Filename Builder dialog state

    // Allowed characters: letters, digits, space, underscore, hyphen, and tag braces
    private val filenameAllowedRegex = Regex("^[a-zA-Z0-9 _\\-{}]+$")

    /** The TextFieldValue driving the custom builder dialog's OutlinedTextField. */
    private val _customFilenameInput = MutableStateFlow(TextFieldValue(""))
    val customFilenameInput: StateFlow<TextFieldValue> = _customFilenameInput.asStateFlow()

    /** True when the current input contains an invalid character. */
    private val _customFilenameIsError = MutableStateFlow(false)
    val customFilenameIsError: StateFlow<Boolean> = _customFilenameIsError.asStateFlow()

    /** True when the input is non-empty and passes validation - used to gate the Save button. */
    val isCustomFilenameValid: Boolean
        get() = _customFilenameInput.value.text.isNotBlank() && !_customFilenameIsError.value

    /**
     * Called on every keystroke in the custom builder dialog.
     * Validates against [filenameAllowedRegex] and updates the error state.
     */
    fun updateCustomFilenameInput(newValue: TextFieldValue) {
        _customFilenameInput.value = newValue
        _customFilenameIsError.value =
            newValue.text.isNotEmpty() && !filenameAllowedRegex.matches(newValue.text)
    }

    /**
     * Inserts [tag] at the current cursor position inside [customFilenameInput].
     * After insertion, the cursor is moved to the end of the injected tag text.
     */
    fun insertTagAtCursor(tag: String) {
        val current = _customFilenameInput.value
        val insertAt = current.selection.end.coerceIn(0, current.text.length)
        val newText = current.text.substring(0, insertAt) + tag + current.text.substring(insertAt)
        val newCursorPos = insertAt + tag.length
        val newValue = TextFieldValue(
            text = newText,
            selection = TextRange(newCursorPos)
        )
        _customFilenameInput.value = newValue
        _customFilenameIsError.value =
            newText.isNotEmpty() && !filenameAllowedRegex.matches(newText)
    }

    /**
     * Pre-fills the dialog input with an existing template (e.g. the currently saved one)
     * so the user can edit rather than start from scratch.
     */
    fun initCustomFilenameDialog(existingTemplate: String) {
        val text = existingTemplate.ifBlank { DEFAULT_FILENAME_TEMPLATE }
        _customFilenameInput.value = TextFieldValue(
            text = text,
            selection = TextRange(text.length) // cursor at end
        )
        _customFilenameIsError.value = false
    }

    var overshadowQuota: Boolean
        get() = prefs.getBoolean("overshadow_quota", false)
        set(value) = prefs.edit().putBoolean("overshadow_quota", value).apply()

    var overshadowRateLimit: Boolean
        get() = prefs.getBoolean("overshadow_rate_limit", false)
        set(value) = prefs.edit().putBoolean("overshadow_rate_limit", value).apply()


    suspend fun getImagePathLabel(): String = withContext(kotlinx.coroutines.Dispatchers.IO) {
        imageDirectoryUri?.let { uriString ->
            DocumentFile.fromTreeUri(getApplication(), Uri.parse(uriString))?.name
        } ?: "Default: Pictures/VedInsta/"
    }

    suspend fun getVideoPathLabel(): String = withContext(kotlinx.coroutines.Dispatchers.IO) {
        videoDirectoryUri?.let { uriString ->
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

    private val _isBlurEnabled = MutableStateFlow(
        prefs.getBoolean("is_blur_enabled", true)
    )
    val isBlurEnabled: StateFlow<Boolean> = _isBlurEnabled.asStateFlow()

    fun setBlurEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("is_blur_enabled", enabled).apply()
        _isBlurEnabled.value = enabled
    }

    private val _blurOpacity = MutableStateFlow(
        prefs.getFloat("blur_opacity", 0.5f)
    )
    val blurOpacity: StateFlow<Float> = _blurOpacity.asStateFlow()

    fun setBlurOpacity(opacity: Float) {
        prefs.edit().putFloat("blur_opacity", opacity).apply()
        _blurOpacity.value = opacity
    }

    private val _blurRadius = MutableStateFlow(
        prefs.getFloat("blur_radius", 20f)
    )
    val blurRadius: StateFlow<Float> = _blurRadius.asStateFlow()

    fun setBlurRadius(radius: Float) {
        prefs.edit().putFloat("blur_radius", radius).apply()
        _blurRadius.value = radius
    }

    private val securePrefs = com.devson.vedinsta.repository.SecurePreferences(getApplication())

    private val _isLoggedIn = MutableStateFlow(securePrefs.hasValidSession())
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    fun refreshLoginState() {
        _isLoggedIn.value = securePrefs.hasValidSession()
    }
}

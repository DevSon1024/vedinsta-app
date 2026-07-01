package com.devson.vedinsta.viewmodel

import android.app.Application
import android.util.Log
import android.webkit.CookieManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devson.vedinsta.extractor.InstagramNativeExtractor

import com.devson.vedinsta.repository.CookieFileWriter
import com.devson.vedinsta.repository.SecurePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class InstagramAuthState {
    object Idle : InstagramAuthState()
    object Checking : InstagramAuthState()
    object LoggedOut : InstagramAuthState()
    data class LoggedIn(val dsUserId: String, val username: String) : InstagramAuthState()
    data class SessionExpired(val dsUserId: String, val username: String, val reason: String) : InstagramAuthState()
    data class Error(val message: String) : InstagramAuthState()
}

class InstagramAuthViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val securePrefs = SecurePreferences(context)

    private val _authState = MutableStateFlow<InstagramAuthState>(InstagramAuthState.Idle)
    val authState: StateFlow<InstagramAuthState> = _authState.asStateFlow()

    // Emits true when Instagram returns a raw numeric user ID instead of a real username.
    // The UI should show a one-time dialog prompting the user to set a display name.
    private val _showRawIdDialog = MutableStateFlow(false)
    val showRawIdDialog: StateFlow<Boolean> = _showRawIdDialog.asStateFlow()

    init {
        checkLoginState()
    }

    /**
     * Checks if a valid session exists in secure storage.
     */
    fun checkLoginState() {
        viewModelScope.launch {
            _authState.value = InstagramAuthState.Checking
            val sessionId = securePrefs.getSessionId()
            val csrfToken = securePrefs.getCsrfToken()
            val dsUserId = securePrefs.getDsUserId()
            val cachedUsername = securePrefs.getUsername() ?: dsUserId ?: ""

            if (!sessionId.isNullOrEmpty() && !csrfToken.isNullOrEmpty() && !dsUserId.isNullOrEmpty()) {
                // Keep Netscape cookie file updated
                CookieFileWriter.writeCookies(context, sessionId, csrfToken, dsUserId)
                _authState.value = InstagramAuthState.LoggedIn(dsUserId, cachedUsername)
                
                // Fetch real username in background
                fetchRealUsernameInBackground(dsUserId)
            } else {
                _authState.value = InstagramAuthState.LoggedOut
            }
        }
    }

    /**
     * Extracts and saves cookies from raw WebView cookie string.
     */
    fun handleCookieString(cookieString: String?) {
        if (cookieString.isNullOrEmpty()) return

        viewModelScope.launch {
            val cookies = parseCookieString(cookieString)
            val sessionId = cookies["sessionid"]
            val csrfToken = cookies["csrftoken"]
            val dsUserId = cookies["ds_user_id"]
            val cachedUsername = securePrefs.getUsername() ?: dsUserId ?: ""

            if (!sessionId.isNullOrEmpty() && !csrfToken.isNullOrEmpty() && !dsUserId.isNullOrEmpty()) {
                // Save to secure preferences
                securePrefs.saveAuthCookies(sessionId, csrfToken, dsUserId)
                
                // Write cookie file in filesDir
                CookieFileWriter.writeCookies(context, sessionId, csrfToken, dsUserId, cookies)
                
                _authState.value = InstagramAuthState.LoggedIn(dsUserId, cachedUsername)
                
                // Fetch real username in background
                fetchRealUsernameInBackground(dsUserId)
            }
        }
    }

    /**
     * Fetches username of logged-in user in background and updates UI/cache.
     */
    private fun fetchRealUsernameInBackground(dsUserId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cookieFile = java.io.File(context.filesDir, "instagram_cookies.txt")
                val realUsername = InstagramNativeExtractor.getLoggedInUsername(cookieFile.absolutePath).trim()

                if (realUsername.isNotEmpty()) {
                    securePrefs.saveUsername(realUsername)

                    // Update state to use new username if currently LoggedIn
                    val currentState = _authState.value
                    if (currentState is InstagramAuthState.LoggedIn && currentState.dsUserId == dsUserId) {
                        _authState.value = InstagramAuthState.LoggedIn(dsUserId, realUsername)
                    }

                    // If Instagram returned a raw numeric ID, prompt the user to set a display name.
                    // This check fires only once per login - the dialog is dismissed after user interaction.
                    if (realUsername.all { it.isDigit() }) {
                        _showRawIdDialog.value = true
                    }
                }
            } catch (e: Exception) {
                Log.e("InstagramAuthViewModel", "Failed to fetch username in background", e)
            }
        }
    }

    /**
     * Dismisses the raw-ID dialog without changing the stored username.
     */
    fun dismissRawIdDialog() {
        _showRawIdDialog.value = false
    }

    /**
     * Saves a user-provided display name for the current session, overriding any
     * raw numeric ID returned by Instagram. Persists to EncryptedSharedPreferences
     * so it survives app restarts.
     */
    fun overrideSessionUsername(newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        securePrefs.saveUsername(trimmed)
        _showRawIdDialog.value = false
        val currentState = _authState.value
        if (currentState is InstagramAuthState.LoggedIn) {
            _authState.value = InstagramAuthState.LoggedIn(currentState.dsUserId, trimmed)
        }
    }

    /**
     * Explicitly sets session as expired (triggered by python script session errors).
     */
    fun notifySessionExpired(reason: String) {
        val dsUserId = securePrefs.getDsUserId() ?: "unknown"
        val username = securePrefs.getUsername() ?: dsUserId
        _authState.value = InstagramAuthState.SessionExpired(dsUserId, username, reason)
    }

    /**
     * Clears all session keys from SecurePreferences, CookieManager, and filesDir.
     */
    fun logout() {
        viewModelScope.launch {
            securePrefs.clearAuth()
            CookieFileWriter.clearCookies(context)
            
            // Clear system WebView cookies
            val cookieManager = CookieManager.getInstance()
            cookieManager.removeAllCookies(null)
            cookieManager.flush()
            
            _authState.value = InstagramAuthState.LoggedOut
        }
    }

    private fun parseCookieString(cookieString: String): Map<String, String> {
        val cookies = mutableMapOf<String, String>()
        val pairs = cookieString.split(";")
        for (pair in pairs) {
            val parts = pair.split("=", limit = 2)
            if (parts.size == 2) {
                val name = parts[0].trim()
                val value = parts[1].trim()
                cookies[name] = value
            }
        }
        return cookies
    }
}

package com.devson.vedinsta.viewmodel

import android.app.Application
import android.util.Log
import android.webkit.CookieManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chaquo.python.Python
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
                // Wait for Python to start up in the background if it's still initializing
                var retries = 0
                while (!Python.isStarted() && retries < 50) {
                    kotlinx.coroutines.delay(100)
                    retries++
                }
                if (Python.isStarted()) {
                    val python = Python.getInstance()
                    val mo3Module = python.getModule("mo3")
                    val cookieFile = java.io.File(context.filesDir, "instagram_cookies.txt")
                    val usernamePy = mo3Module.callAttr("get_logged_in_username", cookieFile.absolutePath)
                    val realUsername = usernamePy?.toString()?.trim() ?: ""
                    
                    if (realUsername.isNotEmpty()) {
                        securePrefs.saveUsername(realUsername)
                        
                        // Update state to use new username if currently LoggedIn
                        val currentState = _authState.value
                        if (currentState is InstagramAuthState.LoggedIn && currentState.dsUserId == dsUserId) {
                            _authState.value = InstagramAuthState.LoggedIn(dsUserId, realUsername)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("InstagramAuthViewModel", "Failed to fetch username in background", e)
            }
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

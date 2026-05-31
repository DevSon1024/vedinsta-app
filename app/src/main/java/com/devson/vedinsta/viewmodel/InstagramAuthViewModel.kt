package com.devson.vedinsta.viewmodel

import android.app.Application
import android.webkit.CookieManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devson.vedinsta.repository.CookieFileWriter
import com.devson.vedinsta.repository.SecurePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class InstagramAuthState {
    object Idle : InstagramAuthState()
    object Checking : InstagramAuthState()
    object LoggedOut : InstagramAuthState()
    data class LoggedIn(val dsUserId: String) : InstagramAuthState()
    data class SessionExpired(val dsUserId: String, val reason: String) : InstagramAuthState()
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

            if (!sessionId.isNullOrEmpty() && !csrfToken.isNullOrEmpty() && !dsUserId.isNullOrEmpty()) {
                // Keep Netscape cookie file updated
                CookieFileWriter.writeCookies(context, sessionId, csrfToken, dsUserId)
                _authState.value = InstagramAuthState.LoggedIn(dsUserId)
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

            if (!sessionId.isNullOrEmpty() && !csrfToken.isNullOrEmpty() && !dsUserId.isNullOrEmpty()) {
                // Save to secure preferences
                securePrefs.saveAuthCookies(sessionId, csrfToken, dsUserId)
                
                // Write cookie file in filesDir
                CookieFileWriter.writeCookies(context, sessionId, csrfToken, dsUserId, cookies)
                
                _authState.value = InstagramAuthState.LoggedIn(dsUserId)
            }
        }
    }

    /**
     * Explicitly sets session as expired (triggered by python script session errors).
     */
    fun notifySessionExpired(reason: String) {
        val dsUserId = securePrefs.getDsUserId() ?: "unknown"
        _authState.value = InstagramAuthState.SessionExpired(dsUserId, reason)
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

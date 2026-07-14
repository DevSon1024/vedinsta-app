package com.devson.vedinsta.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecurePreferences(private val context: Context) {

    companion object {
        private const val PREFS_FILE_NAME = "vedinsta_secure_prefs"
        private const val KEY_SESSION_ID = "ig_sessionid"
        private const val KEY_CSRF_TOKEN = "ig_csrftoken"
        private const val KEY_DS_USER_ID = "ig_ds_user_id"
        private const val KEY_USERNAME = "ig_username"
        private const val KEY_USER_AGENT = "ig_user_agent"
        private const val KEY_ACCEPT_LANGUAGE = "accept_language"
        private const val KEY_X_ASBD_ID = "x_asbd_id"
        private const val KEY_VIEWPORT_WIDTH = "viewport_width"
        private const val KEY_MIN_JITTER_DELAY = "min_jitter_delay"
        private const val KEY_MAX_JITTER_DELAY = "max_jitter_delay"
        private const val KEY_X_IG_APP_ID = "x_ig_app_id"
        private const val KEY_SUSPENSION_EXPIRY = "suspension_expiry"
        private const val KEY_X_IG_WWW_CLAIM = "x_ig_www_claim"
        private const val KEY_SESSION_ACTIVE = "session_active"
        private const val TAG = "SecurePreferences"
    }

    private val sharedPrefs: SharedPreferences? by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize EncryptedSharedPreferences. Recreating...", e)
            try {
                // Recover from keystore corruption by deleting shared preference XML
                context.deleteSharedPreferences(PREFS_FILE_NAME)
                
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                EncryptedSharedPreferences.create(
                    context,
                    PREFS_FILE_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (ex: Exception) {
                Log.e(TAG, "Critical error initializing secure prefs. Falling back to non-encrypted preferences.", ex)
                // Fallback to normal private shared preferences as last resort
                context.getSharedPreferences(PREFS_FILE_NAME + "_fallback", Context.MODE_PRIVATE)
            }
        }
    }

    fun saveAuthCookies(sessionId: String, csrfToken: String, dsUserId: String) {
        sharedPrefs?.edit()?.apply {
            putString(KEY_SESSION_ID, sessionId)
            putString(KEY_CSRF_TOKEN, csrfToken)
            putString(KEY_DS_USER_ID, dsUserId)
            apply()
        }
    }

    fun saveUsername(username: String) {
        sharedPrefs?.edit()?.apply {
            putString(KEY_USERNAME, username)
            apply()
        }
    }

    fun getSessionId(): String? = sharedPrefs?.getString(KEY_SESSION_ID, null)
    fun getCsrfToken(): String? = sharedPrefs?.getString(KEY_CSRF_TOKEN, null)
    fun getDsUserId(): String? = sharedPrefs?.getString(KEY_DS_USER_ID, null)
    fun getUsername(): String? = sharedPrefs?.getString(KEY_USERNAME, null)
    
    fun saveUserAgent(userAgent: String) {
        sharedPrefs?.edit()?.putString(KEY_USER_AGENT, userAgent)?.apply()
    }
    
    fun getUserAgent(): String? = sharedPrefs?.getString(KEY_USER_AGENT, null)

    fun saveAcceptLanguage(acceptLanguage: String) {
        sharedPrefs?.edit()?.putString(KEY_ACCEPT_LANGUAGE, acceptLanguage)?.apply()
    }

    fun getAcceptLanguage(): String? = sharedPrefs?.getString(KEY_ACCEPT_LANGUAGE, null)

    fun saveXAsbdId(xAsbdId: String) {
        sharedPrefs?.edit()?.putString(KEY_X_ASBD_ID, xAsbdId)?.apply()
    }

    fun getXAsbdId(): String? = sharedPrefs?.getString(KEY_X_ASBD_ID, null)

    fun saveViewportWidth(viewportWidth: String) {
        sharedPrefs?.edit()?.putString(KEY_VIEWPORT_WIDTH, viewportWidth)?.apply()
    }

    fun getViewportWidth(): String? = sharedPrefs?.getString(KEY_VIEWPORT_WIDTH, null)

    fun saveMinJitterDelay(minJitter: Long) {
        sharedPrefs?.edit()?.putLong(KEY_MIN_JITTER_DELAY, minJitter)?.apply()
    }

    fun getMinJitterDelay(): Long = sharedPrefs?.getLong(KEY_MIN_JITTER_DELAY, 3000L) ?: 3000L

    fun saveMaxJitterDelay(maxJitter: Long) {
        sharedPrefs?.edit()?.putLong(KEY_MAX_JITTER_DELAY, maxJitter)?.apply()
    }

    fun getMaxJitterDelay(): Long = sharedPrefs?.getLong(KEY_MAX_JITTER_DELAY, 8000L) ?: 8000L

    fun saveXIgAppId(xIgAppId: String) {
        sharedPrefs?.edit()?.putString(KEY_X_IG_APP_ID, xIgAppId)?.apply()
    }

    fun getXIgAppId(): String? = sharedPrefs?.getString(KEY_X_IG_APP_ID, null)

    fun clearAuth() {
        sharedPrefs?.edit()?.apply {
            remove(KEY_SESSION_ID)
            remove(KEY_CSRF_TOKEN)
            remove(KEY_DS_USER_ID)
            remove(KEY_USERNAME)
            remove(KEY_USER_AGENT)
            remove(KEY_ACCEPT_LANGUAGE)
            remove(KEY_X_ASBD_ID)
            remove(KEY_VIEWPORT_WIDTH)
            remove(KEY_MIN_JITTER_DELAY)
            remove(KEY_MAX_JITTER_DELAY)
            remove(KEY_X_IG_APP_ID)
            putBoolean(KEY_SESSION_ACTIVE, true)
            apply()
        }
    }

    fun saveSuspensionExpiry(expiryMs: Long) {
        sharedPrefs?.edit()?.putLong(KEY_SUSPENSION_EXPIRY, expiryMs)?.apply()
    }

    fun getSuspensionExpiry(): Long {
        return sharedPrefs?.getLong(KEY_SUSPENSION_EXPIRY, 0L) ?: 0L
    }

    fun isSuspended(): Boolean {
        return System.currentTimeMillis() < getSuspensionExpiry()
    }

    fun saveXIgWwwClaim(claim: String) {
        sharedPrefs?.edit()?.putString(KEY_X_IG_WWW_CLAIM, claim)?.apply()
    }

    fun getXIgWwwClaim(): String {
        return sharedPrefs?.getString(KEY_X_IG_WWW_CLAIM, "0") ?: "0"
    }

    fun hasValidSession(): Boolean {
        return !getSessionId().isNullOrEmpty()
    }

    fun setSessionActive(isActive: Boolean) {
        sharedPrefs?.edit()?.putBoolean(KEY_SESSION_ACTIVE, isActive)?.apply()
    }

    fun isSessionActive(): Boolean {
        return sharedPrefs?.getBoolean(KEY_SESSION_ACTIVE, true) ?: true
    }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        sharedPrefs?.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        sharedPrefs?.unregisterOnSharedPreferenceChangeListener(listener)
    }
}

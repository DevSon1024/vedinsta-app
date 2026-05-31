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

    fun getSessionId(): String? = sharedPrefs?.getString(KEY_SESSION_ID, null)
    fun getCsrfToken(): String? = sharedPrefs?.getString(KEY_CSRF_TOKEN, null)
    fun getDsUserId(): String? = sharedPrefs?.getString(KEY_DS_USER_ID, null)

    fun clearAuth() {
        sharedPrefs?.edit()?.apply {
            remove(KEY_SESSION_ID)
            remove(KEY_CSRF_TOKEN)
            remove(KEY_DS_USER_ID)
            apply()
        }
    }

    fun hasValidSession(): Boolean {
        return !getSessionId().isNullOrEmpty()
    }
}

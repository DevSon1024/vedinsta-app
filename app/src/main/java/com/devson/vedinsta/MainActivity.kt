package com.devson.vedinsta

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.devson.vedinsta.ui.theme.VedinstaTheme
import com.devson.vedinsta.viewmodel.InstagramAuthViewModel
import com.devson.vedinsta.viewmodel.MainViewModel
import com.devson.vedinsta.viewmodel.MediaExtractionViewModel
import com.devson.vedinsta.viewmodel.NotificationViewModel

class MainActivity : ComponentActivity() {

    private val authViewModel: InstagramAuthViewModel by viewModels()
    private val extractionViewModel: MediaExtractionViewModel by viewModels()
    private val mainViewModel: MainViewModel by viewModels()
    private val notificationViewModel: NotificationViewModel by viewModels()

    private val currentIntent = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        currentIntent.value = intent

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        val settingsManager = SettingsManager(this)

        setContent {
            var appThemeState by remember { mutableStateOf(settingsManager.appTheme) }
            val darkTheme = when (appThemeState) {
                1 -> false
                2 -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            VedinstaTheme(darkTheme = darkTheme) {
                MainAppScreen(
                    authViewModel = authViewModel,
                    extractionViewModel = extractionViewModel,
                    mainViewModel = mainViewModel,
                    notificationViewModel = notificationViewModel,
                    settingsManager = settingsManager,
                    intent = currentIntent.value,
                    onThemeChanged = { newTheme ->
                        appThemeState = newTheme
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        currentIntent.value = intent
    }
}

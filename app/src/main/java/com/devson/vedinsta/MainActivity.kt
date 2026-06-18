package com.devson.vedinsta

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import com.devson.vedinsta.ui.theme.NosvedPlayerTheme
import com.devson.vedinsta.ui.navigation.MainAppScreen
import com.devson.vedinsta.viewmodel.InstagramAuthViewModel
import com.devson.vedinsta.viewmodel.MainViewModel
import com.devson.vedinsta.viewmodel.MediaExtractionViewModel
import com.devson.vedinsta.viewmodel.NotificationViewModel
import com.devson.vedinsta.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {

    private val authViewModel: InstagramAuthViewModel by viewModels()
    private val extractionViewModel: MediaExtractionViewModel by viewModels()
    private val mainViewModel: MainViewModel by viewModels()
    private val notificationViewModel: NotificationViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val whatsAppViewModel: com.devson.vedinsta.viewmodel.WhatsAppViewModel by viewModels()
    private val favoriteStoriesViewModel: com.devson.vedinsta.viewmodel.FavoriteStoriesViewModel by viewModels()

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

        setContent {
            val isDark by settingsViewModel.isDarkTheme.collectAsStateWithLifecycle()
            val dynamicColor by settingsViewModel.dynamicColor.collectAsStateWithLifecycle()
            val selectedPalette by settingsViewModel.selectedPalette.collectAsStateWithLifecycle()
            val navBarTransparent by settingsViewModel.isNavBarTransparent.collectAsStateWithLifecycle()
            val isAmoledTheme by settingsViewModel.isAmoledTheme.collectAsStateWithLifecycle()

            NosvedPlayerTheme(
                forceDark = isDark,
                dynamicColor = dynamicColor,
                palette = selectedPalette,
                isNavBarTransparent = navBarTransparent,
                isAmoledTheme = isAmoledTheme
            ) {
                MainAppScreen(
                    authViewModel = authViewModel,
                    extractionViewModel = extractionViewModel,
                    mainViewModel = mainViewModel,
                    notificationViewModel = notificationViewModel,
                    settingsViewModel = settingsViewModel,
                    whatsAppViewModel = whatsAppViewModel,
                    favoriteStoriesViewModel = favoriteStoriesViewModel,
                    intent = currentIntent.value,
                    onThemeChanged = {
                        // Managed reactively by settingsViewModel!
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

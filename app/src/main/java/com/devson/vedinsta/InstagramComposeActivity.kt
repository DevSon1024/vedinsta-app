package com.devson.vedinsta

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.*
import com.devson.vedinsta.ui.InstagramLoginScreen
import com.devson.vedinsta.ui.MediaSelectionScreen
import com.devson.vedinsta.ui.theme.VedinstaTheme
import com.devson.vedinsta.viewmodel.InstagramAuthState
import com.devson.vedinsta.viewmodel.InstagramAuthViewModel
import com.devson.vedinsta.viewmodel.MediaExtractionViewModel

class InstagramComposeActivity : ComponentActivity() {

    private val authViewModel: InstagramAuthViewModel by viewModels()
    private val extractionViewModel: MediaExtractionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            VedinstaTheme {
                var currentScreen by remember { mutableStateOf("selection") }
                
                val authState by authViewModel.authState.collectAsState()
                
                // Automatically navigate back to selection screen when login succeeds
                LaunchedEffect(authState) {
                    if (authState is InstagramAuthState.LoggedIn && currentScreen == "login") {
                        currentScreen = "selection"
                    }
                }
                
                when (currentScreen) {
                    "login" -> {
                        InstagramLoginScreen(
                            authViewModel = authViewModel,
                            onBackClick = { currentScreen = "selection" }
                        )
                    }
                    "selection" -> {
                        MediaSelectionScreen(
                            authViewModel = authViewModel,
                            extractionViewModel = extractionViewModel,
                            onNavigateToLogin = { currentScreen = "login" }
                        )
                    }
                }
            }
        }
    }
}

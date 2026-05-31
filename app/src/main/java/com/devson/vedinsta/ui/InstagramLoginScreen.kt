package com.devson.vedinsta.ui

import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.devson.vedinsta.viewmodel.InstagramAuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstagramLoginScreen(
    authViewModel: InstagramAuthViewModel,
    onBackClick: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Instagram Sign In") },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )

        Box(modifier = Modifier.weight(1f)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        webViewInstance = this
                        
                        // Configure WebView settings for Instagram Web
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            userAgentString = "Mozilla/5.0 (Linux; Android 10; SM-G973F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.181 Mobile Safari/537.36"
                        }
                        
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                
                                url?.let { currentUrl ->
                                    // Check if we successfully landed on the home page or dashboard
                                    if (currentUrl == "https://www.instagram.com/" || 
                                        currentUrl.startsWith("https://www.instagram.com/?") ||
                                        currentUrl.contains("instagram.com/accounts/onetap") ||
                                        (!currentUrl.contains("accounts/login") && currentUrl.startsWith("https://www.instagram.com/"))
                                    ) {
                                        val cookieManager = CookieManager.getInstance()
                                        val cookies = cookieManager.getCookie("https://www.instagram.com")
                                        
                                        if (!cookies.isNullOrEmpty() && cookies.contains("sessionid")) {
                                            authViewModel.handleCookieString(cookies)
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Clear WebView cookies/cache before presenting login page to avoid automatic invalid redirects
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.removeAllCookies(null)
                        cookieManager.flush()
                        
                        loadUrl("https://www.instagram.com/accounts/login/")
                    }
                },
                update = {
                    // Updates can go here if needed
                }
            )

            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

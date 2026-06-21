package com.devson.vedinsta.ui.screen.setting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PrivacyPolicyScreen() {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Text(
            text = "Effective Date: June 1, 2026",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "VedInsta prioritizes your privacy above everything else. This policy outlines how your data is handled strictly within the application bounds.",
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        PrivacySectionCard(
            title = "1. Local On-Device Execution",
            content = "All post extraction and media downloading are performed directly on your Android device. The app uses a secure native media extractor to fetch video/image URLs. Your URLs, account data, or downloaded media are never transmitted to any external servers hosted by the developers."
        )

        Spacer(modifier = Modifier.height(16.dp))

        PrivacySectionCard(
            title = "2. Secure Session Storage",
            content = "If you authenticate your Instagram account to download private posts, your login cookies (sessionid, csrftoken, ds_user_id) are captured securely and stored on your device using EncryptedSharedPreferences. They are sent exclusively to Instagram's official API servers to fetch post information. We do not have any capability or access to view, read, or harvest these credentials."
        )

        Spacer(modifier = Modifier.height(16.dp))

        PrivacySectionCard(
            title = "3. Zero Tracking & Logs",
            content = "We do not use any analytics, telemetry, crash reporting, or user tracking services. Your download history, favorites list, and app usage details remain 100% private in a secure local Room database inside the app's internal sandbox. Once you uninstall the app, all stored data, files, and databases are completely deleted by the Android system."
        )

        Spacer(modifier = Modifier.height(16.dp))

        PrivacySectionCard(
            title = "4. Media Files & Gallery Access",
            content = "VedInsta saves downloaded media files directly into the public Downloads/VedInsta folder. The application requests standard storage or MediaStore permissions solely to read/write these files to your gallery. We do not access or read any other files on your device."
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun PrivacySectionCard(title: String, content: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = content,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

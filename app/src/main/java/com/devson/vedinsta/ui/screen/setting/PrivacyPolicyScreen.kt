package com.devson.vedinsta.ui.screen.setting

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onNavigateBack: () -> Unit) {
    var animateTrigger by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        animateTrigger = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp + 8.dp,
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp,
                    start = 16.dp,
                    end = 16.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                // Hero commitment Card
                PrivacyHeroCard()

                // Policy Sections
                AnimatedVisibility(
                    visible = animateTrigger,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { 40 })
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        
                        // Section 1: Data Collection & Extraction
                        PolicySection(
                            title = "DATA MINIMIZATION & EXTRACTION SECURITY",
                            icon = Icons.Default.Security
                        ) {
                            PolicyPointRow(
                                icon = Icons.Default.Lock,
                                title = "Zero Personal Data Collection",
                                description = "VedInsta does not collect, record, transmit, or share any personal information, download links, or telemetry."
                            )
                            PolicyPointSeparator()
                            PolicyPointRow(
                                icon = Icons.Default.Security,
                                title = "On-Device Native Extraction",
                                description = "All media extraction and downloading are executed entirely on your Android device via vanilla Kotlin code. No cloud servers are involved."
                            )                            
                        }

                        // Section 2: Account Session Protection
                        PolicySection(
                            title = "INSTAGRAM SESSIONS & ENCRYPTION",
                            icon = Icons.Default.Lock
                        ) {
                            PolicyPointRow(
                                icon = Icons.Default.Security,
                                title = "Encrypted Local Session Storage",
                                description = "If you authenticate your Instagram account to download private media, cookies are intercepted via WebView sandbox and saved using EncryptedSharedPreferences."
                            )
                            PolicyPointSeparator()
                            PolicyPointRow(
                                icon = Icons.Default.CheckCircle,
                                title = "Instagram Authentication",
                                description = "An Instagram account is required only to access high-quality downloads or private content. This login is processed entirely on-device, and your credentials are never sent to external servers."
                            )
                            PolicyPointSeparator()
                            PolicyPointRow(
                                icon = Icons.Default.CheckCircle,
                                title = "Direct API Requests Only",
                                description = "Session cookies are transmitted directly to Instagram's official API endpoints to download your posts. No intermediate servers ever receive your credentials."
                            )
                        }

                        // Section 3: History Logs & Room Database
                        PolicySection(
                            title = "DOWNLOAD HISTORY & LOCAL DATA",
                            icon = Icons.Default.History
                        ) {
                            PolicyPointRow(
                                icon = Icons.Default.Lock,
                                title = "Device-Bound Databases",
                                description = "Your download history, post metadata records, and download quota logs are stored only in secure local Room databases and Shared Preferences."
                            )
                            PolicyPointSeparator()
                            PolicyPointRow(
                                icon = Icons.Default.CheckCircle,
                                title = "Complete User Autonomy",
                                description = "You can clear your history, revoke account credentials, and erase app cache at any time. Uninstalling the app completely purges all local databases."
                            )
                        }

                        // Section 4: Child Safety & Storage Insets
                        PolicySection(
                            title = "CHILD ACCESSIBILITY & STABILITY",
                            icon = Icons.Default.Info
                        ) {
                            PolicyPointRow(
                                icon = Icons.Default.CheckCircle,
                                title = "Fully Family Safe",
                                description = "With absolutely no trackers, telemetry, or user profiling, the app is 100% safe for all users, conforming fully to children's privacy standards."
                            )
                            PolicyPointSeparator()
                            PolicyPointRow(
                                icon = Icons.Default.Info,
                                title = "Transparent Updates",
                                description = "Any future updates to this policy will be clearly versioned and presented in-app. Continued use constitutes awareness of any updated terms."
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Footer
                        Text(
                            text = "VedInsta\nCommitted to Open-Source and Privacy\nCreated by DevSon",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 32.dp)
                        )
                    }
                }
            }
        }

        // Translucent TopBar background overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars.add(WindowInsets(top = 64.dp)))
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.8f))
        ) {
            HorizontalDivider(
                modifier = Modifier.align(Alignment.BottomCenter),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            )
        }

        // Transparent TopAppBar on top of the translucent overlay
        TopAppBar(
            title = {
                Text(
                    text = "Privacy Policy",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
                navigationIconContentColor = MaterialTheme.colorScheme.onBackground
            ),
            modifier = Modifier.statusBarsPadding()
        )
    }
}

@Composable
private fun PrivacyHeroCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 28.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Elegant gradient icon backplate
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    modifier = Modifier.size(38.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }

            Text(
                text = "Privacy Commitment",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Text(
                text = "VedInsta is built from the ground up to respect your privacy. Local download operations run entirely on your device, and account authentication details remain securely encrypted in your local storage.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                PrivacyBadge(text = "Privacy Focused")
                PrivacyBadge(text = "Zero Trackers")
                PrivacyBadge(text = "Open Source")
            }
        }
    }
}

@Composable
private fun PrivacyBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 1.dp
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun PolicySection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Section Header Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }

        // Section Cards Content
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        }
    }
}

@Composable
private fun PolicyPointRow(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PolicyPointSeparator() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

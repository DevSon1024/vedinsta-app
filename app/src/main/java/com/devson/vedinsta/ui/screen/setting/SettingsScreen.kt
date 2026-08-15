package com.devson.vedinsta.ui.screen.setting

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.Coil
import coil.annotation.ExperimentalCoilApi
import com.devson.vedinsta.model.MediaQuality
import com.devson.vedinsta.model.ThumbnailQuality
import com.devson.vedinsta.viewmodel.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DecimalFormat

@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    onNavigateToAbout: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit,
    onNavigateToAdvancedSettings: () -> Unit,
    onNavigateToSecurityLimits: () -> Unit,
    onNavigateToStorageSettings: () -> Unit,
    onThemeChanged: (Int) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val isLoggedIn by settingsViewModel.isLoggedIn.collectAsStateWithLifecycle()
    val loggedInUsername = remember(isLoggedIn) { settingsViewModel.getLoggedInUsername() }

    LaunchedEffect(Unit) {
        settingsViewModel.refreshLoginState()
    }

    // - Link action dialog state
    var showLinkActionDialog by remember { mutableStateOf(false) }

    // - Quality dialog states
    val globalQuality by settingsViewModel.userQualityPreferenceFlow.collectAsStateWithLifecycle()
    val thumbnailQualityState by settingsViewModel.thumbnailQualityFlow.collectAsStateWithLifecycle()

    var showQualityDialog by remember { mutableStateOf(false) }
    var showThumbnailQualityDialog by remember { mutableStateOf(false) }

    // - Dynamic cache size calculation
    var cacheSizeFormatted by remember { mutableStateOf("Calculating...") }
    var isClearingCache by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }

    // Function to calculate and update cache size
    val refreshCacheSize: () -> Unit = {
        coroutineScope.launch(Dispatchers.IO) {
            val totalBytes = calculateTotalCacheSize(context)
            val formatted = formatByteSize(totalBytes)
            withContext(Dispatchers.Main) {
                cacheSizeFormatted = formatted
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshCacheSize()
    }

    // Labels for summaries and badges
    val linkActionBadge = remember(settingsViewModel.defaultLinkAction) {
        when (settingsViewModel.defaultLinkAction) {
            SettingsViewModel.ACTION_DOWNLOAD_ALL -> "Auto Download"
            SettingsViewModel.ACTION_OPEN_SELECTION -> "Open Selection"
            else -> "Ask Each Time"
        }
    }

    val downloadQualityBadge = remember(globalQuality) {
        when (globalQuality) {
            MediaQuality.HIGH -> "High"
            MediaQuality.MEDIUM -> "Medium"
            MediaQuality.LOW -> "Low"
            MediaQuality.CUSTOM -> "Custom"
        }
    }

    val thumbnailQualityBadge = remember(thumbnailQualityState) {
        when (thumbnailQualityState) {
            ThumbnailQuality.LOWEST -> "Lowest"
            ThumbnailQuality.MEDIUM -> "Medium"
            ThumbnailQuality.HIGHEST -> "Highest"
            ThumbnailQuality.SAME_AS_DOWNLOAD -> "Same as Download"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(contentPadding.calculateTopPadding() + 16.dp))

        // - Hero Account / Session Status Card
        SettingsSessionHeroCard(
            isLoggedIn = isLoggedIn,
            username = loggedInUsername
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 1. Appearance & Theme Section
        SettingsSection(title = "Appearance & Interface") {
            SettingsItemRow(
                title = "App Theme & Palette",
                subtitle = "Themes, color palettes, AMOLED & blur effects",
                icon = Icons.Default.Palette,
                iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                iconColor = MaterialTheme.colorScheme.primary,
                onClick = onNavigateToAppearance
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Downloads & Media Quality Section
        SettingsSection(title = "Downloads & Media") {
            SettingsItemRow(
                title = "Default Download Quality",
                subtitle = "Target resolution for downloaded media",
                badgeText = downloadQualityBadge,
                icon = Icons.Default.HighQuality,
                iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                iconColor = MaterialTheme.colorScheme.primary,
                onClick = { showQualityDialog = true }
            )

            HorizontalDivider(
                modifier = Modifier.padding(start = 56.dp, end = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )

            SettingsItemRow(
                title = "Thumbnail Quality",
                subtitle = "Resolution for media preview thumbnails",
                badgeText = thumbnailQualityBadge,
                icon = Icons.Default.PhotoLibrary,
                iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                iconColor = MaterialTheme.colorScheme.tertiary,
                onClick = { showThumbnailQualityDialog = true }
            )

            HorizontalDivider(
                modifier = Modifier.padding(start = 56.dp, end = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )

            SettingsItemRow(
                title = "When Sharing a Link",
                subtitle = "Action triggered on incoming shared Instagram link",
                badgeText = linkActionBadge,
                icon = Icons.Default.Link,
                iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                iconColor = MaterialTheme.colorScheme.secondary,
                onClick = { showLinkActionDialog = true }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Storage & Filenames Section
        SettingsSection(title = "Storage & Files") {
            SettingsItemRow(
                title = "Storage & Filenames",
                subtitle = "Custom save directories, templates & tags",
                icon = Icons.Default.FolderOpen,
                iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                iconColor = MaterialTheme.colorScheme.tertiary,
                onClick = onNavigateToStorageSettings
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4. Network & Security Section
        SettingsSection(title = "Network & Security") {
            val networkSubtitle = if (isLoggedIn) {
                "Custom user-agent, app ID, timeouts & jitter"
            } else {
                "Sign in to customize headers & bypass limits"
            }
            SettingsItemRow(
                title = "Advanced Network Settings",
                subtitle = networkSubtitle,
                badgeText = if (isLoggedIn) "Available" else "Requires Login",
                icon = Icons.Default.Dns,
                iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                iconColor = MaterialTheme.colorScheme.secondary,
                enabled = isLoggedIn,
                onClick = onNavigateToAdvancedSettings
            )

            HorizontalDivider(
                modifier = Modifier.padding(start = 56.dp, end = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )

            val securitySubtitle = if (isLoggedIn) {
                "Download throttling limits & quota statistics"
            } else {
                "Sign in to configure account safety limits"
            }
            SettingsItemRow(
                title = "Security & Safety Limits",
                subtitle = securitySubtitle,
                badgeText = if (isLoggedIn) "Protected" else "Requires Login",
                icon = Icons.Default.Security,
                iconContainerColor = MaterialTheme.colorScheme.errorContainer,
                iconColor = MaterialTheme.colorScheme.error,
                enabled = isLoggedIn,
                onClick = onNavigateToSecurityLimits
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 5. Cache & Maintenance Section
        SettingsSection(title = "Data & Maintenance") {
            SettingsItemRow(
                title = "Clear Application Cache",
                subtitle = "Frees thumbnail previews & temporary files",
                badgeText = cacheSizeFormatted,
                icon = Icons.Default.Delete,
                iconContainerColor = MaterialTheme.colorScheme.errorContainer,
                iconColor = MaterialTheme.colorScheme.error,
                onClick = {
                    showClearCacheDialog = true
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 6. About & Information Section
        SettingsSection(title = "About & Legal") {
            SettingsItemRow(
                title = "Privacy Policy",
                subtitle = "View VedInsta privacy conditions and terms",
                icon = Icons.Default.Security,
                iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                iconColor = MaterialTheme.colorScheme.primary,
                onClick = onNavigateToPrivacyPolicy
            )

            HorizontalDivider(
                modifier = Modifier.padding(start = 56.dp, end = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )

            SettingsItemRow(
                title = "About Application",
                subtitle = "Version, developer details & licenses",
                icon = Icons.Default.Info,
                iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                iconColor = MaterialTheme.colorScheme.secondary,
                onClick = onNavigateToAbout
            )
        }

        Spacer(modifier = Modifier.height(contentPadding.calculateBottomPadding() + 24.dp))
    }

    // - Modern Link Sharing Action Dialog
    if (showLinkActionDialog) {
        val linkOptions = listOf(
            DialogOption(
                title = "Ask Every Time",
                description = "Shows a notification with options when a link is shared",
                value = SettingsViewModel.ACTION_ASK_EVERY_TIME
            ),
            DialogOption(
                title = "Download All Immediately",
                description = "Directly downloads all photos and videos in the background",
                value = SettingsViewModel.ACTION_DOWNLOAD_ALL
            ),
            DialogOption(
                title = "Open Media Selection",
                description = "Opens carousel viewer to pick specific items to download",
                value = SettingsViewModel.ACTION_OPEN_SELECTION
            )
        )

        SingleChoiceSelectionDialog(
            title = "When Sharing a Link",
            options = linkOptions,
            selectedValue = settingsViewModel.defaultLinkAction,
            onSelect = { selectedVal ->
                settingsViewModel.defaultLinkAction = selectedVal
                showLinkActionDialog = false
            },
            onDismiss = { showLinkActionDialog = false }
        )
    }

    // - Modern Download Quality Dialog
    if (showQualityDialog) {
        val qualityOptions = listOf(
            DialogOption(
                title = "High Resolution (Highest)",
                description = "Downloads best available original stream quality",
                value = MediaQuality.HIGH
            ),
            DialogOption(
                title = "Medium Resolution",
                description = "Balanced visual fidelity and lower data usage",
                value = MediaQuality.MEDIUM
            ),
            DialogOption(
                title = "Low Resolution (Fastest)",
                description = "Fastest download speed with smaller file size",
                value = MediaQuality.LOW
            ),
            DialogOption(
                title = "Custom (Manual)",
                description = "Prompts to pick quality individually per media item",
                value = MediaQuality.CUSTOM
            )
        )

        SingleChoiceSelectionDialog(
            title = "Default Download Quality",
            options = qualityOptions,
            selectedValue = globalQuality,
            onSelect = { selectedVal ->
                settingsViewModel.userQualityPreference = selectedVal
                showQualityDialog = false
            },
            onDismiss = { showQualityDialog = false }
        )
    }

    // - Modern Thumbnail Quality Dialog
    if (showThumbnailQualityDialog) {
        val thumbOptions = listOf(
            DialogOption(
                title = "Lowest Resolution (Default)",
                description = "Fastest feed loading speed & lowest memory consumption",
                value = ThumbnailQuality.LOWEST
            ),
            DialogOption(
                title = "Medium Resolution",
                description = "Crisp preview clarity with moderate memory use",
                value = ThumbnailQuality.MEDIUM
            ),
            DialogOption(
                title = "Highest Resolution",
                description = "Maximum sharpness for preview thumbnails",
                value = ThumbnailQuality.HIGHEST
            ),
            DialogOption(
                title = "Same as Download Quality",
                description = "Mirrors selected download quality setting",
                value = ThumbnailQuality.SAME_AS_DOWNLOAD
            )
        )

        SingleChoiceSelectionDialog(
            title = "Default Thumbnail Quality",
            options = thumbOptions,
            selectedValue = thumbnailQualityState,
            onSelect = { selectedVal ->
                settingsViewModel.thumbnailQuality = selectedVal
                showThumbnailQualityDialog = false
            },
            onDismiss = { showThumbnailQualityDialog = false }
        )
    }

    // - Clear Cache Confirmation Dialog
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isClearingCache) showClearCacheDialog = false
            },
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.errorContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "Clear Application Cache?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Current cache size: $cacheSizeFormatted",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "This will delete temporary preview thumbnails and network cache. Downloaded files in your device gallery will NOT be affected.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isClearingCache) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "Clearing cache...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isClearingCache = true
                        performClearCache(
                            context = context,
                            scope = coroutineScope,
                            onComplete = {
                                isClearingCache = false
                                showClearCacheDialog = false
                                refreshCacheSize()
                            }
                        )
                    },
                    enabled = !isClearingCache,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Clear Now")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearCacheDialog = false },
                    enabled = !isClearingCache
                ) {
                    Text("Cancel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(24.dp)
        )
    }
}

// - Hero Session / Account Status Card
@Composable
private fun SettingsSessionHeroCard(
    isLoggedIn: Boolean,
    username: String?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isLoggedIn) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = if (isLoggedIn) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isLoggedIn) Icons.Default.CheckCircle else Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = if (isLoggedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isLoggedIn) {
                            if (!username.isNullOrBlank()) "@$username" else "Instagram Connected"
                        } else {
                            "Guest Mode"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Surface(
                        shape = CircleShape,
                        color = if (isLoggedIn) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(
                                        color = if (isLoggedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        shape = CircleShape
                                    )
                            )
                            Text(
                                text = if (isLoggedIn) "Active" else "Offline",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isLoggedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = if (isLoggedIn) {
                        "Session active - Private posts & story extraction enabled"
                    } else {
                        "Public posts supported. Sign in for private posts & stories"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// - Grouped Settings Section Container
@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

// - Single Settings Item Row
@Composable
private fun SettingsItemRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconContainerColor: Color,
    iconColor: Color,
    badgeText: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else 0.45f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .let { modifier ->
                if (enabled) {
                    modifier.clickable { onClick() }
                } else {
                    modifier
                }
            }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = iconContainerColor.copy(alpha = iconContainerColor.alpha * alpha),
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor.copy(alpha = iconColor.alpha * alpha),
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f * alpha),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (!badgeText.isNullOrBlank()) {
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alpha)
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f * alpha),
            modifier = Modifier.size(20.dp)
        )
    }
}

// - Dialog Data Model
private data class DialogOption<T>(
    val title: String,
    val description: String,
    val value: T
)

// - Modern Reusable Single Choice Selection Dialog
@Composable
private fun <T> SingleChoiceSelectionDialog(
    title: String,
    options: List<DialogOption<T>>,
    selectedValue: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                options.forEach { option ->
                    val isSelected = option.value == selectedValue
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelect(option.value) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        } else {
                            Color.Transparent
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onSelect(option.value) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = option.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(
                                    text = option.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(24.dp)
    )
}

// - Legacy Helper Composables (used by SecurityLimitsScreen & StorageSettingsScreen)
@Composable
fun SettingsCategoryHeader(title: String) {
    Text(
        text = title.uppercase(),
        color = MaterialTheme.colorScheme.primary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 6.dp)
    )
}

@Composable
fun SettingsClickableItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconContainerColor: Color,
    iconColor: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else 0.5f
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .let { cardModifier ->
                if (enabled) {
                    cardModifier.clickable { onClick() }
                } else {
                    cardModifier
                }
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f * alpha)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        iconContainerColor.copy(alpha = iconContainerColor.alpha * alpha),
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor.copy(alpha = iconColor.alpha * alpha),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f * alpha),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Arrow Right",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f * alpha),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun SettingsSwitchItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconContainerColor: Color,
    iconColor: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(iconContainerColor, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = subtitleColor,
                    fontSize = 12.sp
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}

// - Cache Utilities
@OptIn(ExperimentalCoilApi::class)
private fun calculateTotalCacheSize(context: Context): Long {
    var size = 0L
    try {
        context.cacheDir?.let { size += getFolderSize(it) }
        context.externalCacheDir?.let { size += getFolderSize(it) }
        val imageLoader = Coil.imageLoader(context)
        imageLoader.diskCache?.size?.let { size += it }
    } catch (_: Exception) {}
    return size
}

private fun getFolderSize(file: File?): Long {
    if (file == null || !file.exists()) return 0L
    if (file.isFile) return file.length()
    var length = 0L
    val files = file.listFiles() ?: return 0L
    for (f in files) {
        length += if (f.isDirectory) getFolderSize(f) else f.length()
    }
    return length
}

private fun formatByteSize(bytes: Long): String {
    if (bytes <= 0) return "0 KB"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    val df = DecimalFormat("#,##0.#")
    return "${df.format(bytes / Math.pow(1024.0, digitGroups.toDouble()))} ${units[digitGroups]}"
}

@OptIn(ExperimentalCoilApi::class)
private fun performClearCache(
    context: Context,
    scope: CoroutineScope,
    onComplete: () -> Unit
) {
    scope.launch(Dispatchers.IO) {
        try {
            context.cacheDir?.let { deleteDir(it) }
            context.externalCacheDir?.let { deleteDir(it) }
            val imageLoader = Coil.imageLoader(context)
            withContext(Dispatchers.Main) {
                imageLoader.memoryCache?.clear()
            }
            imageLoader.diskCache?.clear()

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Cache cleared successfully", Toast.LENGTH_SHORT).show()
                onComplete()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed to clear cache", Toast.LENGTH_SHORT).show()
                onComplete()
            }
        }
    }
}

private fun deleteDir(dir: File?): Boolean {
    if (dir != null && dir.isDirectory) {
        val children = dir.list() ?: return false
        for (i in children.indices) {
            deleteDir(File(dir, children[i]))
        }
    }
    return dir?.delete() ?: false
}

package com.devson.vedinsta.ui.screen.setting

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import coil.Coil
import com.devson.vedinsta.repository.DownloadQuotaManager
import com.devson.vedinsta.viewmodel.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    onNavigateToAbout: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit,
    onNavigateToAdvancedSettings: () -> Unit,
    onThemeChanged: (Int) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var imagePath by remember { mutableStateOf("Loading...") }
    var videoPath by remember { mutableStateOf("Loading...") }
    var linkActionLabel by remember { mutableStateOf(settingsViewModel.getDefaultActionLabel()) }
    var showLinkActionDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        imagePath = settingsViewModel.getImagePathLabel()
        videoPath = settingsViewModel.getVideoPathLabel()
    }

    // Folder Pickers

    // Folder Pickers
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.also { uri ->
                takePersistablePermissions(context, uri)
                settingsViewModel.imageDirectoryUri = uri.toString()
                coroutineScope.launch {
                    imagePath = settingsViewModel.getImagePathLabel()
                }
                Toast.makeText(context, "Image location set", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.also { uri ->
                takePersistablePermissions(context, uri)
                settingsViewModel.videoDirectoryUri = uri.toString()
                coroutineScope.launch {
                    videoPath = settingsViewModel.getVideoPathLabel()
                }
                Toast.makeText(context, "Video location set", Toast.LENGTH_SHORT).show()
            }
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

        // 1. Display Settings Category
        SettingsCategoryHeader("Display Settings")

        SettingsClickableItem(
            title = "App Theme",
            subtitle = "Theme selection, Palette & Navbar Transparency",
            icon = Icons.Default.Palette,
            iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
            iconColor = MaterialTheme.colorScheme.primary,
            onClick = onNavigateToAppearance
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Storage Location Category
        SettingsCategoryHeader("Storage Settings")
        
        SettingsClickableItem(
            title = "Images Save Location",
            subtitle = imagePath,
            icon = Icons.Default.Image,
            iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
            iconColor = MaterialTheme.colorScheme.tertiary,
            onClick = {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                imagePicker.launch(intent)
            }
        )

        SettingsClickableItem(
            title = "Videos Save Location",
            subtitle = videoPath,
            icon = Icons.Default.Movie,
            iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
            iconColor = MaterialTheme.colorScheme.tertiary,
            onClick = {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                videoPicker.launch(intent)
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Link Behavior Category
        SettingsCategoryHeader("Link Behaviors")

        SettingsClickableItem(
            title = "When Sharing a Link",
            subtitle = linkActionLabel,
            icon = Icons.Default.Link,
            iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
            iconColor = MaterialTheme.colorScheme.primary,
            onClick = { showLinkActionDialog = true }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Advanced Network settings
        SettingsCategoryHeader("Network Settings")
        SettingsClickableItem(
            title = "Advanced Network Settings",
            subtitle = "Custom user-agent, app ID, and connection timeouts",
            icon = Icons.Default.Dns,
            iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            iconColor = MaterialTheme.colorScheme.secondary,
            onClick = onNavigateToAdvancedSettings
        )

        Spacer(modifier = Modifier.height(16.dp))

        SettingsCategoryHeader("Security & Limits")
        var overshadowQuota by remember { mutableStateOf(settingsViewModel.overshadowQuota) }
        SettingsSwitchItem(
            title = "Overshadow Quota Limitation",
            subtitle = "Bypass download limits (Warning: increases risk of Instagram account flags)",
            icon = Icons.Default.Warning,
            iconContainerColor = MaterialTheme.colorScheme.errorContainer,
            iconColor = MaterialTheme.colorScheme.error,
            checked = overshadowQuota,
            onCheckedChange = {
                settingsViewModel.overshadowQuota = it
                overshadowQuota = it
            },
            subtitleColor = MaterialTheme.colorScheme.error
        )

        val quotaManager = remember { DownloadQuotaManager(context) }
        var quotaStats by remember { mutableStateOf(quotaManager.getQuotaStats()) }

        LaunchedEffect(overshadowQuota) {
            quotaStats = quotaManager.getQuotaStats()
            while (true) {
                delay(10000L)
                quotaStats = quotaManager.getQuotaStats()
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Download Quota Usage",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))

                QuotaProgressRow(
                    label = "Hourly Quota",
                    count = quotaStats.hourlyCount,
                    limit = quotaStats.hourlyLimit,
                    resetMs = quotaStats.hourlyResetMs
                )

                Spacer(modifier = Modifier.height(12.dp))

                QuotaProgressRow(
                    label = "Daily Quota",
                    count = quotaStats.dailyCount,
                    limit = quotaStats.dailyLimit,
                    resetMs = quotaStats.dailyResetMs
                )

                Spacer(modifier = Modifier.height(12.dp))

                QuotaProgressRow(
                    label = "Weekly Quota",
                    count = quotaStats.weeklyCount,
                    limit = quotaStats.weeklyLimit,
                    resetMs = quotaStats.weeklyResetMs
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4. Cache Management Category
        SettingsCategoryHeader("Cache & History")

        SettingsClickableItem(
            title = "Clear Cache",
            subtitle = "Clears media preview thumbnails & temp files",
            icon = Icons.Default.Delete,
            iconContainerColor = MaterialTheme.colorScheme.errorContainer,
            iconColor = MaterialTheme.colorScheme.error,
            onClick = {
                clearApplicationCache(context, coroutineScope)
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 5. General Info Category
        SettingsCategoryHeader("About")

        SettingsClickableItem(
            title = "Privacy Policy",
            subtitle = "View VedInsta privacy conditions",
            icon = Icons.Default.Security,
            iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
            iconColor = MaterialTheme.colorScheme.primary,
            onClick = onNavigateToPrivacyPolicy
        )

        SettingsClickableItem(
            title = "About Application",
            subtitle = "Version, developer info, license",
            icon = Icons.Default.Info,
            iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            iconColor = MaterialTheme.colorScheme.secondary,
            onClick = onNavigateToAbout
        )
        Spacer(modifier = Modifier.height(contentPadding.calculateBottomPadding() + 16.dp))
    }

    // Shared Link Action Dialog Selection
    if (showLinkActionDialog) {
        val options = listOf(
            "Request Action (Default)",
            "Download All Immediately",
            "Open Media Selection"
        )
        val currentSelection = when(settingsViewModel.defaultLinkAction) {
            SettingsViewModel.ACTION_ASK_EVERY_TIME -> 0
            SettingsViewModel.ACTION_DOWNLOAD_ALL -> 1
            SettingsViewModel.ACTION_OPEN_SELECTION -> 2
            else -> 0
        }

        AlertDialog(
            onDismissRequest = { showLinkActionDialog = false },
            title = { Text("When sharing a link:") },
            text = {
                Column {
                    options.forEachIndexed { index, option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val newAction = when(index) {
                                        0 -> SettingsViewModel.ACTION_ASK_EVERY_TIME
                                        1 -> SettingsViewModel.ACTION_DOWNLOAD_ALL
                                        2 -> SettingsViewModel.ACTION_OPEN_SELECTION
                                        else -> SettingsViewModel.ACTION_ASK_EVERY_TIME
                                    }
                                    settingsViewModel.defaultLinkAction = newAction
                                    linkActionLabel = settingsViewModel.getDefaultActionLabel()
                                    showLinkActionDialog = false
                                }
                                .padding(vertical = 12.dp)
                        ) {
                            RadioButton(
                                selected = (index == currentSelection),
                                onClick = null // Click handled by Row
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(option, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLinkActionDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.error)
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

}

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
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Arrow Right",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
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

private fun takePersistablePermissions(context: Context, uri: Uri) {
    try {
        val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, takeFlags)
    } catch (e: SecurityException) {
        // Log error
    }
}

private fun clearApplicationCache(context: Context, scope: CoroutineScope) {
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
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed to clear cache", Toast.LENGTH_SHORT).show()
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

@Composable
fun QuotaProgressRow(
    label: String,
    count: Int,
    limit: Int,
    resetMs: Long
) {
    val progress = (count.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
    val progressColor = when {
        progress >= 0.9f -> MaterialTheme.colorScheme.error
        progress >= 0.7f -> MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
        else -> MaterialTheme.colorScheme.primary
    }

    val resetText = if (resetMs <= 0L) {
        "No active limit"
    } else {
        val remainingMs = resetMs - System.currentTimeMillis()
        if (remainingMs <= 0L) {
            "Resetting..."
        } else {
            val totalMinutes = remainingMs / (60 * 1000L)
            if (totalMinutes >= 24 * 60) {
                val days = totalMinutes / (24 * 60)
                "Reset in ${days}d"
            } else if (totalMinutes >= 60) {
                val hours = totalMinutes / 60
                "Reset in ${hours}h"
            } else {
                val mins = totalMinutes.coerceAtLeast(1)
                "Reset in ${mins}m"
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$count / $limit ($resetText)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = progressColor
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = progressColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

package com.devson.vedinsta.ui.screen.setting

import android.content.Context
import android.widget.Toast
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
    onNavigateToSecurityLimits: () -> Unit,
    onNavigateToStorageSettings: () -> Unit,
    onThemeChanged: (Int) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var linkActionLabel by remember { mutableStateOf(settingsViewModel.getDefaultActionLabel()) }
    var showLinkActionDialog by remember { mutableStateOf(false) }

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
            title = "Storage & Filenames",
            subtitle = "Save locations, filename templates & tags",
            icon = Icons.Default.FolderOpen,
            iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
            iconColor = MaterialTheme.colorScheme.tertiary,
            onClick = onNavigateToStorageSettings
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
        SettingsClickableItem(
            title = "Security & Limits",
            subtitle = "Manage download limits & quota statistics",
            icon = Icons.Default.Security,
            iconContainerColor = MaterialTheme.colorScheme.errorContainer,
            iconColor = MaterialTheme.colorScheme.error,
            onClick = onNavigateToSecurityLimits
        )

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



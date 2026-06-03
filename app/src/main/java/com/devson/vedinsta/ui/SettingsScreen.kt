package com.devson.vedinsta.ui

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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.Coil
import com.devson.vedinsta.viewmodel.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    onNavigateToAbout: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit,
    onThemeChanged: (Int) -> Unit
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
            .navigationBarsPadding()
            .padding(16.dp)
    ) {

        // 1. Display Settings Category
        SettingsCategoryHeader("Display Settings")

        SettingsClickableItem(
            title = "App Theme",
            subtitle = "Theme selection, palette & navbar transparency",
            onClick = onNavigateToAppearance
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Storage Location Category
        SettingsCategoryHeader("Storage Settings")
        
        SettingsClickableItem(
            title = "Images Save Location",
            subtitle = imagePath,
            onClick = {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                imagePicker.launch(intent)
            }
        )

        SettingsClickableItem(
            title = "Videos Save Location",
            subtitle = videoPath,
            onClick = {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                videoPicker.launch(intent)
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Link Behavior Category
        SettingsCategoryHeader("Link Behaviors")

        SettingsClickableItem(
            title = "When sharing a link:",
            subtitle = linkActionLabel,
            onClick = { showLinkActionDialog = true }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 4. Cache Management Category
        SettingsCategoryHeader("Cache & History")

        SettingsClickableItem(
            title = "Clear Cache",
            subtitle = "Clears media preview thumbnails & temp files",
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
            onClick = onNavigateToPrivacyPolicy
        )

        SettingsClickableItem(
            title = "About Application",
            subtitle = "Version, developer info, license",
            onClick = onNavigateToAbout
        )
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
        text = title,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun SettingsClickableItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Arrow Right",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
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

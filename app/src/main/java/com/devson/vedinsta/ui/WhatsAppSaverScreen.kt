package com.devson.vedinsta.ui

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import coil.compose.AsyncImage
import androidx.compose.ui.draw.clip
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.devson.vedinsta.viewmodel.WhatsAppState
import com.devson.vedinsta.viewmodel.WhatsAppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsAppSaverScreen(
    viewModel: WhatsAppViewModel,
    onStatusClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val savedStatuses by viewModel.savedStatuses.collectAsState()
    val isSevenDaySaverEnabled by viewModel.isSevenDaySaverEnabled.collectAsState()
    val preservedStatuses by viewModel.preservedStatuses.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showSettingsBottomSheet by remember { mutableStateOf(false) }

    val selectedFiles = remember { mutableStateListOf<DocumentFile>() }
    var isSelectionMode by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        selectedFiles.clear()
        isSelectionMode = false
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) {
            viewModel.loadPreservedStatuses(context)
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                viewModel.loadStatuses(context, uri)
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to persist permission: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.checkPermission(context)
        viewModel.loadPreservedStatuses(context)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.background
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Recent", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Preserved", fontWeight = FontWeight.Bold) }
                )
            }
            IconButton(
                onClick = { showSettingsBottomSheet = true },
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Saver Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (selectedTab == 0) {
                when (val currentState = state) {
                    is WhatsAppState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    is WhatsAppState.PermissionRequired -> {
                        PermissionRequiredView(onGrantClick = {
                            val whatsappDir = File(
                                Environment.getExternalStorageDirectory(),
                                "Android/media/com.whatsapp/WhatsApp/Media/.Statuses"
                            )
                            val businessDir = File(
                                Environment.getExternalStorageDirectory(),
                                "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses"
                            )
                            val initialUri = when {
                                whatsappDir.exists() -> {
                                    Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AAndroid%2Fmedia%2Fcom.whatsapp%2FWhatsApp%2FMedia%2F.Statuses")
                                }
                                businessDir.exists() -> {
                                    Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AAndroid%2Fmedia%2Fcom.whatsapp.w4b%2FWhatsApp%20Business%2FMedia%2F.Statuses")
                                }
                                else -> {
                                    Toast.makeText(context, "WhatsApp status folder not found. Please select it manually.", Toast.LENGTH_LONG).show()
                                    null
                                }
                            }
                            launcher.launch(initialUri)
                        })
                    }
                    is WhatsAppState.Success -> {
                        if (currentState.statuses.isEmpty()) {
                            EmptyStatusesView(onRefreshClick = {
                                viewModel.checkPermission(context)
                            })
                        } else {
                            StatusesGrid(
                                statuses = currentState.statuses,
                                savedStatuses = savedStatuses,
                                selectedFiles = selectedFiles,
                                isSelectionMode = isSelectionMode,
                                onSaveClick = { file ->
                                    viewModel.saveStatus(context, file)
                                },
                                onSaveAudioClick = { file ->
                                    viewModel.saveAudioFromVideo(context, file)
                                },
                                onStatusClick = onStatusClick,
                                onToggleSelect = { file ->
                                    if (selectedFiles.contains(file)) {
                                        selectedFiles.remove(file)
                                        if (selectedFiles.isEmpty()) {
                                            isSelectionMode = false
                                        }
                                    } else {
                                        isSelectionMode = true
                                        selectedFiles.add(file)
                                    }
                                }
                            )
                        }
                    }
                    is WhatsAppState.Error -> {
                        ErrorView(
                            message = currentState.message,
                            onRetryClick = {
                                viewModel.checkPermission(context)
                            }
                        )
                    }
                }
            } else {
                PreservedStatusesGrid(
                    statuses = preservedStatuses,
                    onDeleteClick = { file ->
                        viewModel.deletePreservedStatus(context, file)
                    },
                    onStatusClick = { file ->
                        viewPreservedStatus(context, file)
                    }
                )
            }

            SelectionActionBar(
                visible = isSelectionMode && selectedTab == 0,
                selectedCount = selectedFiles.size,
                onClearSelection = {
                    selectedFiles.clear()
                    isSelectionMode = false
                },
                onSaveClick = {
                    viewModel.saveStatuses(context, selectedFiles.toList())
                    selectedFiles.clear()
                    isSelectionMode = false
                }
            )
        }
    }

    if (showSettingsBottomSheet) {
        WAPreserverSettingsBottomSheet(
            isSevenDaySaverEnabled = isSevenDaySaverEnabled,
            onToggleChange = { enabled ->
                viewModel.setSevenDaySaverEnabled(context, enabled)
            },
            onDismissRequest = { showSettingsBottomSheet = false }
        )
    }
}

@Composable
fun PermissionRequiredView(onGrantClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(100.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = "Folder Access",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "WhatsApp Status Saver",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "To view and save WhatsApp statuses, VedInsta needs access to your WhatsApp media folder.\n\nPlease click the button below and select 'Use this folder' in the system folder selection window.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onGrantClick,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Grant Folder Access",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
fun StatusesGrid(
    statuses: List<DocumentFile>,
    savedStatuses: Set<String>,
    selectedFiles: List<DocumentFile>,
    isSelectionMode: Boolean,
    onSaveClick: (DocumentFile) -> Unit,
    onSaveAudioClick: (DocumentFile) -> Unit,
    onStatusClick: (Int) -> Unit,
    onToggleSelect: (DocumentFile) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(
            start = 8.dp,
            top = 8.dp,
            end = 8.dp,
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 68.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(statuses, key = { _, file -> file.uri.toString() }) { index, file ->
            val isSaved = savedStatuses.contains(file.name)
            val isSelected = selectedFiles.contains(file)
            StatusItem(
                file = file,
                isSaved = isSaved,
                isSelected = isSelected,
                isSelectionMode = isSelectionMode,
                onSaveClick = { onSaveClick(file) },
                onSaveAudioClick = { onSaveAudioClick(file) },
                onItemClick = {
                    if (isSelectionMode) {
                        onToggleSelect(file)
                    } else {
                        onStatusClick(index)
                    }
                },
                onItemLongClick = {
                    onToggleSelect(file)
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StatusItem(
    file: DocumentFile,
    isSaved: Boolean,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onSaveClick: () -> Unit,
    onSaveAudioClick: () -> Unit,
    onItemClick: () -> Unit,
    onItemLongClick: () -> Unit
) {
    val context = LocalContext.current
    val isVideo = remember(file.name) {
        file.name?.endsWith(".mp4", ignoreCase = true) == true
    }

    val imageRequest = remember(file.uri) {
        ImageRequest.Builder(context)
            .data(file.uri)
            .size(300, 300)
            .diskCachePolicy(CachePolicy.DISABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .build()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.7f)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onItemClick,
                onLongClick = onItemLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = imageRequest,
                contentDescription = "WhatsApp Status",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            if (isVideo) {
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .size(24.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .align(Alignment.TopStart),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Video",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (isSaved) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Saved",
                            tint = Color.Green,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Saved",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            } else if (isSelectionMode) {
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF25D366).copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Selected",
                            tint = Color(0xFF25D366),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .padding(8.dp)
                            .size(20.dp)
                            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .align(Alignment.TopEnd),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .border(2.dp, Color.White, RoundedCornerShape(7.dp))
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PreservedStatusesGrid(
    statuses: List<File>,
    onDeleteClick: (File) -> Unit,
    onStatusClick: (File) -> Unit
) {
    if (statuses.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No Preserved Statuses",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Statuses will be automatically preserved here for 7 days when the '7-Day Saver' toggle is enabled.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(
                start = 8.dp,
                top = 8.dp,
                end = 8.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 68.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(statuses, key = { _, file -> file.absolutePath }) { _, file ->
                PreservedStatusItem(
                    file = file,
                    onDeleteClick = { onDeleteClick(file) },
                    onItemClick = { onStatusClick(file) }
                )
            }
        }
    }
}

@Composable
fun PreservedStatusItem(
    file: File,
    onDeleteClick: () -> Unit,
    onItemClick: () -> Unit
) {
    val context = LocalContext.current
    val isVideo = remember(file.name) {
        file.name.endsWith(".mp4", ignoreCase = true)
    }

    val imageRequest = remember(file.absolutePath) {
        ImageRequest.Builder(context)
            .data(file)
            .size(300, 300)
            .diskCachePolicy(CachePolicy.DISABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .build()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.7f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onItemClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = imageRequest,
                contentDescription = "Preserved Status",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            if (isVideo) {
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .size(24.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .align(Alignment.TopStart),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Video",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(28.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun BoxScope.SelectionActionBar(
    visible: Boolean,
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onSaveClick: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(16.dp)
            .navigationBarsPadding()
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClearSelection) {
                        Icon(Icons.Default.Close, contentDescription = "Clear Selection", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$selectedCount Selected",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Button(
                    onClick = onSaveClick,
                    enabled = selectedCount > 0,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save to Gallery", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun EmptyStatusesView(onRefreshClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No Statuses Found",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Make sure you have viewed statuses on WhatsApp first, then check back here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRefreshClick,
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Refresh")
        }
    }
}

@Composable
fun ErrorView(message: String, onRetryClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "An Error Occurred",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRetryClick,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Retry")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WAPreserverSettingsBottomSheet(
    isSevenDaySaverEnabled: Boolean,
    onToggleChange: (Boolean) -> Unit,
    onDismissRequest: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Status Preservation Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "7-Day Saver",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Automatically preserve newly viewed statuses locally. Files will be kept for exactly 7 days (168 hours) and then automatically cleaned up.",
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = isSevenDaySaverEnabled,
                    onCheckedChange = onToggleChange
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun viewPreservedStatus(context: android.content.Context, file: File) {
    try {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, if (file.name.endsWith(".mp4", ignoreCase = true)) "video/*" else "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
    }
}

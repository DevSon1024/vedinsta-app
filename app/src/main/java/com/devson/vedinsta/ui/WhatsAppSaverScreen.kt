package com.devson.vedinsta.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.documentfile.provider.DocumentFile
import coil.compose.AsyncImage
import com.devson.vedinsta.viewmodel.WhatsAppState
import com.devson.vedinsta.viewmodel.WhatsAppViewModel

@Composable
fun WhatsAppSaverScreen(
    viewModel: WhatsAppViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val savedStatuses by viewModel.savedStatuses.collectAsState()

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
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
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
                    val initialUri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AAndroid%2Fmedia%2Fcom.whatsapp%2FWhatsApp%2FMedia%2F.Statuses")
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
                        onSaveClick = { file ->
                            viewModel.saveStatus(context, file)
                        },
                        onSaveAudioClick = { file ->
                            viewModel.saveAudioFromVideo(context, file)
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
    onSaveClick: (DocumentFile) -> Unit,
    onSaveAudioClick: (DocumentFile) -> Unit
) {
    var previewFile by remember { mutableStateOf<DocumentFile?>(null) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(
            start = 8.dp,
            top = 8.dp,
            end = 8.dp,
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 88.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(statuses, key = { it.uri.toString() }) { file ->
            val isSaved = savedStatuses.contains(file.name)
            StatusItem(
                file = file,
                isSaved = isSaved,
                onSaveClick = { onSaveClick(file) },
                onSaveAudioClick = { onSaveAudioClick(file) },
                onItemClick = { previewFile = file }
            )
        }
    }

    if (previewFile != null) {
        val isSaved = savedStatuses.contains(previewFile!!.name)
        StatusPreviewDialog(
            file = previewFile!!,
            isSaved = isSaved,
            onDismiss = { previewFile = null },
            onSaveClick = {
                onSaveClick(previewFile!!)
                previewFile = null
            },
            onSaveAudioClick = {
                onSaveAudioClick(previewFile!!)
                previewFile = null
            }
        )
    }
}

@Composable
fun StatusItem(
    file: DocumentFile,
    isSaved: Boolean,
    onSaveClick: () -> Unit,
    onSaveAudioClick: () -> Unit,
    onItemClick: () -> Unit
) {
    val isVideo = remember(file.name) {
        file.name?.endsWith(".mp4", ignoreCase = true) == true
    }
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.7f)
            .clickable { onItemClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = file.uri,
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
            } else {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (isVideo) {
                                showMenu = true
                            } else {
                                onSaveClick()
                            }
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                                androidx.compose.foundation.shape.CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download Status",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    if (isVideo) {
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Save Video") },
                                onClick = {
                                    showMenu = false
                                    onSaveClick()
                                },
                                leadingIcon = { Icon(Icons.Default.Movie, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Save Audio Only") },
                                onClick = {
                                    showMenu = false
                                    onSaveAudioClick()
                                },
                                leadingIcon = { Icon(Icons.Default.MusicNote, contentDescription = null) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusPreviewDialog(
    file: DocumentFile,
    isSaved: Boolean,
    onDismiss: () -> Unit,
    onSaveClick: () -> Unit,
    onSaveAudioClick: () -> Unit
) {
    val isVideo = remember(file.name) {
        file.name?.endsWith(".mp4", ignoreCase = true) == true
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (isVideo) {
                    AndroidView(
                        factory = { ctx ->
                            android.widget.VideoView(ctx).apply {
                                setVideoURI(file.uri)
                                val mediaController = android.widget.MediaController(ctx)
                                mediaController.setAnchorView(this)
                                setMediaController(mediaController)
                                setOnPreparedListener { mp ->
                                    mp.isLooping = true
                                    start()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    AsyncImage(
                        model = file.uri,
                        contentDescription = "Status Preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isSaved) {
                        Button(
                            onClick = { /* Dismiss */ },
                            enabled = false,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.DarkGray,
                                contentColor = Color.LightGray
                            )
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Saved to Gallery", fontWeight = FontWeight.Bold)
                        }
                    } else if (isVideo) {
                        Button(
                            onClick = onSaveClick,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Movie, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save Video", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onSaveAudioClick,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.MusicNote, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save Audio Only", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = onSaveClick,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save to Gallery", fontWeight = FontWeight.Bold)
                        }
                    }
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

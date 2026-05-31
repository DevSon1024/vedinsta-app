package com.devson.vedinsta.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import com.devson.vedinsta.model.MediaResult
import com.devson.vedinsta.viewmodel.ExtractionState
import com.devson.vedinsta.viewmodel.InstagramAuthState
import com.devson.vedinsta.viewmodel.InstagramAuthViewModel
import com.devson.vedinsta.viewmodel.MediaExtractionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaSelectionScreen(
    authViewModel: InstagramAuthViewModel,
    extractionViewModel: MediaExtractionViewModel,
    onNavigateToLogin: () -> Unit
) {
    val context = LocalContext.current
    val authState by authViewModel.authState.collectAsState()
    val extractionState by extractionViewModel.extractionState.collectAsState()
    val selectedIndexes by extractionViewModel.selectedIndexes.collectAsState()
    val chosenQualities by extractionViewModel.chosenQualities.collectAsState()

    var instagramUrl by remember { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(extractionState) {
        if (extractionState is ExtractionState.Success) {
            snackbarHostState.showSnackbar("Media extracted")
        }
    }

    // Instagram gradient brush
    val instagramGradient = remember {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF833AB4), // Purple
                Color(0xFFFD1D1D), // Red
                Color(0xFFF77737)  // Orange
            )
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "VedInsta Downloader",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                ),
                modifier = Modifier.background(instagramGradient)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            if (extractionState is ExtractionState.Success && (extractionState as ExtractionState.Success).extractedPost.mediaList.isNotEmpty()) {
                val successState = extractionState as ExtractionState.Success
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LinkInputSection(
                            url = instagramUrl,
                            onUrlChange = { instagramUrl = it },
                            onPasteClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                                instagramUrl = text
                            },
                            onExtractClick = {
                                extractionViewModel.extractMedia(instagramUrl, authViewModel)
                            },
                            isEnabled = authState is InstagramAuthState.LoggedIn
                        )
                    }

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${successState.extractedPost.mediaList.size} media items found",
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Bold
                            )
                            Row {
                                TextButton(onClick = { extractionViewModel.selectAll(successState.extractedPost.mediaList) }) {
                                    Text("Select All", color = MaterialTheme.colorScheme.primary)
                                }
                                TextButton(onClick = { extractionViewModel.selectNone() }) {
                                    Text("Select None", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                                }
                            }
                        }
                    }

                    items(successState.extractedPost.mediaList, key = { it.index ?: 0 }) { item ->
                        val idx = item.index ?: 1
                        MediaItemCard(
                            item = item,
                            isSelected = selectedIndexes.contains(idx),
                            chosenUrl = chosenQualities[idx],
                            onToggleSelect = { extractionViewModel.toggleSelection(idx) },
                            onQualityChange = { newUrl -> extractionViewModel.changeQuality(idx, newUrl) }
                        )
                    }

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Button(
                            onClick = {
                                val selectedItems = successState.extractedPost.mediaList.filter { selectedIndexes.contains(it.index ?: -1) }
                                val containsVideo = selectedItems.any { it.type == "video" }
                                val isReel = instagramUrl.contains("/reel/", ignoreCase = true) || instagramUrl.contains("/reels/", ignoreCase = true) || containsVideo
                                
                                val snackbarMessage = if (isReel) {
                                    "Started Reels Downloading"
                                } else {
                                    val count = selectedItems.size
                                    "$count ${if (count == 1) "Image" else "Images"} Download Started"
                                }
                                
                                scope.launch {
                                    snackbarHostState.showSnackbar(snackbarMessage)
                                }
                                
                                extractionViewModel.downloadSelected(successState.extractedPost, instagramUrl)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = selectedIndexes.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "Download Selected (${selectedIndexes.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    LinkInputSection(
                        url = instagramUrl,
                        onUrlChange = { instagramUrl = it },
                        onPasteClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                            instagramUrl = text
                        },
                        onExtractClick = {
                            extractionViewModel.extractMedia(instagramUrl, authViewModel)
                        },
                        isEnabled = authState is InstagramAuthState.LoggedIn
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when (val state = extractionState) {
                            is ExtractionState.Idle -> {
                                Text(
                                    "Paste an Instagram link above and click Extract to fetch posts/reels.",
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(24.dp)
                                )
                            }
                            is ExtractionState.Loading -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("Running python extractor...", color = MaterialTheme.colorScheme.onBackground)
                                }
                            }
                            is ExtractionState.Error -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Error",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = state.message,
                                        color = MaterialTheme.colorScheme.error,
                                        textAlign = TextAlign.Center,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun LinkInputSection(
    url: String,
    onUrlChange: (String) -> Unit,
    onPasteClick: () -> Unit,
    onExtractClick: () -> Unit,
    isEnabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                label = { Text("Instagram Post/Reel URL") },
                placeholder = { Text("https://www.instagram.com/p/...") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    if (url.isNotEmpty()) {
                        IconButton(onClick = { onUrlChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.outline,
                    focusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                singleLine = true,
                enabled = isEnabled
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onPasteClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    enabled = isEnabled
                ) {
                    Text("Paste URL")
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = onExtractClick,
                    modifier = Modifier.weight(1.5f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    ),
                    enabled = isEnabled && url.isNotBlank()
                ) {
                    Text("Extract Media", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun MediaItemCard(
    item: MediaResult,
    isSelected: Boolean,
    chosenUrl: String?,
    onToggleSelect: () -> Unit,
    onQualityChange: (String) -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    var showQualityMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.85f)
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onToggleSelect() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Media Preview Image (Load from chosen quality URL or fallback)
            AsyncImage(
                model = chosenUrl ?: item.url,
                contentDescription = "Media Preview",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )

            // Dark overlay for text readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                            startY = 100f
                        )
                    )
            )

            // Checkbox overlay at Top-Right (always styled with white check/border over image overlay)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.6f))
                    .border(1.5.dp, Color.White, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Media Info at Bottom (rendered over dark gradient, so text is white for legibility)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                // Type Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (item.type == "video") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    if (item.type == "video") {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Video",
                            tint = Color.White,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                    }
                    Text(
                        text = item.type?.uppercase() ?: "IMAGE",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Resolution Selection Pill
                val qualities = item.qualities ?: emptyList()
                val currentQuality = qualities.find { it.url == chosenUrl }
                val width = currentQuality?.width ?: item.width ?: 0
                val height = currentQuality?.height ?: item.height ?: 0
                val resolutionText = if (width > 0 && height > 0) "${width}x${height}" else "Default Quality"

                Box {
                    Surface(
                        modifier = Modifier.clickable { showQualityMenu = true },
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Black.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = "$resolutionText ▾",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showQualityMenu,
                        onDismissRequest = { showQualityMenu = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        if (qualities.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Original (${item.width}x${item.height})", color = MaterialTheme.colorScheme.onSurface) },
                                onClick = { showQualityMenu = false }
                            )
                        } else {
                            qualities.forEachIndexed { i, q ->
                                val label = if (i == 0) "${q.width}x${q.height} (Original/High)" else "${q.width}x${q.height}"
                                DropdownMenuItem(
                                    text = { Text(label, color = MaterialTheme.colorScheme.onSurface) },
                                    onClick = {
                                        q.url?.let(onQualityChange)
                                        showQualityMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Item index info
                Text(
                    text = "Part ${item.index ?: 1}",
                    color = Color.LightGray,
                    fontSize = 10.sp
                )
            }
        }
    }
}

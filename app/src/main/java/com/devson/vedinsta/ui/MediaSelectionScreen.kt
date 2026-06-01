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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import com.devson.vedinsta.model.MediaResult
import com.devson.vedinsta.viewmodel.ExtractionState
import com.devson.vedinsta.viewmodel.InstagramAuthState
import com.devson.vedinsta.viewmodel.InstagramAuthViewModel
import com.devson.vedinsta.viewmodel.MediaExtractionViewModel
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaSelectionScreen(
    authViewModel: InstagramAuthViewModel,
    extractionViewModel: MediaExtractionViewModel,
    onNavigateToLogin: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val authState by authViewModel.authState.collectAsState()
    val extractionState by extractionViewModel.extractionState.collectAsState()
    val selectedIndexes by extractionViewModel.selectedIndexes.collectAsState()
    val chosenQualities by extractionViewModel.chosenQualities.collectAsState()

    var instagramUrl by remember { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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
            if (extractionState is ExtractionState.Success && (extractionState as ExtractionState.Success).extractedPost.mediaList.isNotEmpty()) {
                val successState = extractionState as ExtractionState.Success
                val allSelected = selectedIndexes.size == successState.extractedPost.mediaList.size

                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = successState.extractedPost.username?.ifEmpty { "Unknown" } ?: "Unknown",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { extractionViewModel.reset() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    actions = {
                        Box(
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(24.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .border(
                                    width = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .background(
                                    if (allSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                )
                                .clickable {
                                    if (allSelected) {
                                        extractionViewModel.selectNone()
                                    } else {
                                        extractionViewModel.selectAll(successState.extractedPost.mediaList)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (allSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Checked",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            } else {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "VedInsta Downloader",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    ),
                    modifier = Modifier.background(instagramGradient)
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (extractionState is ExtractionState.Success && (extractionState as ExtractionState.Success).extractedPost.mediaList.isNotEmpty()) {
                val successState = extractionState as ExtractionState.Success
                val pagerState = rememberPagerState(pageCount = { successState.extractedPost.mediaList.size })

                HorizontalPager(
                    state = pagerState,
                    contentPadding = PaddingValues(horizontal = 48.dp),
                    pageSpacing = 16.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) { page ->
                    val item = successState.extractedPost.mediaList[page]
                    val idx = item.index ?: 1
                    val isSelected = selectedIndexes.contains(idx)
                    val chosenUrl = chosenQualities[idx]

                    val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
                    val scale = lerp(
                        start = 0.85f,
                        stop = 1.0f,
                        fraction = 1f - pageOffset.coerceIn(0f, 1f)
                    )
                    val alpha = lerp(
                        start = 0.6f,
                        stop = 1.0f,
                        fraction = 1f - pageOffset.coerceIn(0f, 1f)
                    )

                    Card(
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                this.alpha = alpha
                            }
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(24.dp))
                            .clickable { extractionViewModel.toggleSelection(idx) },
                        shape = RoundedCornerShape(24.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            val previewUrl = remember(item) {
                                val qualities = item.qualities?.filter { !it.url.isNullOrBlank() } ?: emptyList()
                                if (qualities.isEmpty()) {
                                    item.url
                                } else {
                                    val sorted = qualities.sortedBy { (it.width ?: 0) * (it.height ?: 0) }
                                    val mediumIndex = sorted.size / 2
                                    sorted[mediumIndex].url ?: item.url
                                }
                            }

                            AsyncImage(
                                model = previewUrl,
                                contentDescription = "Media Preview",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )

                            if (item.type == "video") {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(16.dp)
                                        .size(36.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Video",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(16.dp)
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(
                                        width = 2.dp,
                                        color = Color.White,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.4f)
                                    )
                                    .clickable { extractionViewModel.toggleSelection(idx) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            val qualities = item.qualities ?: emptyList()
                            val currentQuality = qualities.find { it.url == chosenUrl }
                            val width = currentQuality?.width ?: item.width ?: 0
                            val height = currentQuality?.height ?: item.height ?: 0
                            
                            val shorterSide = minOf(width, height)
                            val resolutionText = if (shorterSide > 0) "${shorterSide} px" else "1080 px"

                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 20.dp)
                            ) {
                                var showQualityMenu by remember { mutableStateOf(false) }

                                Surface(
                                    modifier = Modifier.clickable { showQualityMenu = true },
                                    shape = RoundedCornerShape(20.dp),
                                    color = Color.Black.copy(alpha = 0.65f),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = resolutionText,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Select Quality",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = showQualityMenu,
                                    onDismissRequest = { showQualityMenu = false },
                                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                                ) {
                                    if (qualities.isEmpty()) {
                                        val labelText = if (item.width != null && item.height != null) {
                                            "${item.width}x${item.height}"
                                        } else {
                                            "Original Quality"
                                        }
                                        DropdownMenuItem(
                                            text = { Text(labelText, color = MaterialTheme.colorScheme.onSurface) },
                                            onClick = { showQualityMenu = false }
                                        )
                                    } else {
                                        qualities.forEachIndexed { i, q ->
                                            val qWidth = q.width ?: 0
                                            val qHeight = q.height ?: 0
                                            val label = if (qWidth > 0 && qHeight > 0) {
                                                if (i == 0) "${qWidth}x${qHeight} (Original)" else "${qWidth}x${qHeight}"
                                            } else {
                                                if (i == 0) "Original Quality" else "Option ${i + 1}"
                                            }
                                            DropdownMenuItem(
                                                text = { Text(label, color = MaterialTheme.colorScheme.onSurface) },
                                                onClick = {
                                                    q.url?.let { extractionViewModel.changeQuality(idx, it) }
                                                    showQualityMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(successState.extractedPost.mediaList.size) { iteration ->
                        val dotOffset = ((pagerState.currentPage - iteration) + pagerState.currentPageOffsetFraction).absoluteValue
                        val fraction = (1f - dotOffset.coerceIn(0f, 1f))
                        
                        val color = androidx.compose.ui.graphics.lerp(
                            start = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            stop = MaterialTheme.colorScheme.primary,
                            fraction = fraction
                        )
                        val size = lerp(
                            start = 8f,
                            stop = 12f,
                            fraction = fraction
                        ).dp
                        
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(size)
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
                }

                 Button(
                    onClick = {
                        val selectedItems = if (selectedIndexes.isEmpty()) {
                            val currentItem = successState.extractedPost.mediaList[pagerState.currentPage]
                            val currentIdx = currentItem.index ?: 1
                            extractionViewModel.toggleSelection(currentIdx)
                            listOf(currentItem)
                        } else {
                            successState.extractedPost.mediaList.filter { selectedIndexes.contains(it.index ?: -1) }
                        }
                        
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 16.dp),
                    enabled = true,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Text(
                        "DOWNLOAD (${selectedIndexes.size}/${successState.extractedPost.mediaList.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
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
            // Media Preview Image (Load from lowest quality URL to save bandwidth)
            val previewUrl = remember(item) {
                val qualities = item.qualities ?: emptyList()
                if (qualities.isEmpty()) {
                    item.url
                } else {
                    qualities.filter { !it.url.isNullOrBlank() }
                        .minByOrNull { (it.width ?: 9999) * (it.height ?: 9999) }?.url ?: item.url
                }
            }

            AsyncImage(
                model = previewUrl,
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

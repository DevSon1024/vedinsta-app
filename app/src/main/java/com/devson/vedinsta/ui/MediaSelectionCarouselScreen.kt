package com.devson.vedinsta.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import coil.compose.AsyncImage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.devson.vedinsta.viewmodel.ExtractionState
import com.devson.vedinsta.viewmodel.InstagramAuthViewModel
import com.devson.vedinsta.viewmodel.MediaExtractionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaSelectionCarouselScreen(
    authViewModel: InstagramAuthViewModel,
    extractionViewModel: MediaExtractionViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToNotifications: () -> Unit
) {
    val context = LocalContext.current
    val extractionState by extractionViewModel.extractionState.collectAsStateWithLifecycle()
    val selectedIndexes by extractionViewModel.selectedIndexes.collectAsStateWithLifecycle()
    val chosenQualities by extractionViewModel.chosenQualities.collectAsStateWithLifecycle()

    val localPaths by produceState<List<String>>(initialValue = emptyList(), extractionState) {
        value = if (extractionState is ExtractionState.Success) {
            val postId = (extractionState as ExtractionState.Success).extractedPost.postId
            withContext(Dispatchers.IO) {
                try {
                    val db = com.devson.vedinsta.database.AppDatabase.getDatabase(context.applicationContext)
                    db.downloadedPostDao().getPostById(postId)?.mediaPaths ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
            }
        } else {
            emptyList()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            if (extractionState is ExtractionState.Success && (extractionState as ExtractionState.Success).extractedPost.mediaList.isNotEmpty()) {
                val successState = extractionState as ExtractionState.Success
                val allSelected = selectedIndexes.size == successState.extractedPost.mediaList.size

                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = successState.extractedPost.username.ifEmpty { "Unknown" },
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
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
                    title = { Text("Details", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
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
                                if (item.type == "video") {
                                    val tQuals = item.thumbnailQualities?.filter { !it.url.isNullOrBlank() } ?: emptyList()
                                    if (tQuals.isEmpty()) {
                                        item.thumbnailUrl ?: item.url
                                    } else {
                                        val sorted = tQuals.sortedBy { (it.width ?: 0) * (it.height ?: 0) }
                                        val target = sorted.find { (it.width ?: 0) >= 360 } ?: sorted.first()
                                        target.url ?: item.thumbnailUrl ?: item.url
                                    }
                                } else {
                                    val qualities = item.qualities?.filter { !it.url.isNullOrBlank() } ?: emptyList()
                                    if (qualities.isEmpty()) {
                                        item.url
                                    } else {
                                        val sorted = qualities.sortedBy { (it.width ?: 0) * (it.height ?: 0) }
                                        val target = sorted.find { (it.width ?: 0) >= 360 } ?: sorted.first()
                                        target.url ?: item.url
                                    }
                                }
                            }

                            val localFile = remember(localPaths, idx) {
                                val path = localPaths.getOrNull(idx - 1)
                                if (!path.isNullOrBlank()) {
                                    val f = File(path)
                                    if (f.exists() && f.canRead()) f else null
                                } else {
                                    null
                                }
                            }

                            val context = LocalContext.current
                            val imageRequest = remember(localFile, previewUrl) {
                                ImageRequest.Builder(context)
                                    .data(localFile ?: previewUrl)
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .build()
                            }
                            AsyncImage(
                                model = imageRequest,
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
                                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { showQualityMenu = true },
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
                        val instagramUrl = extractionViewModel.lastExtractedUrl
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

                        extractionViewModel.downloadSelected(successState.extractedPost, instagramUrl)
                        onNavigateToNotifications()
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
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No media loaded or invalid state.",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

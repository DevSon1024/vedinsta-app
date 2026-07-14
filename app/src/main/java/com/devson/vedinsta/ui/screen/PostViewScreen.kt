package com.devson.vedinsta.ui.screen

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.devson.vedinsta.database.DownloadedPost
import com.devson.vedinsta.ui.components.bounceClick
import com.devson.vedinsta.viewmodel.MainViewModel
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostViewScreen(
    post: DownloadedPost,
    isFavorite: (String) -> Boolean,
    onToggleFavorite: (String) -> Unit,
    onBackClick: () -> Unit,
    onDeletePost: (DownloadedPost) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val insetsController = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousLight = insetsController?.isAppearanceLightStatusBars ?: true
        
        insetsController?.isAppearanceLightStatusBars = false
        insetsController?.hide(WindowInsetsCompat.Type.statusBars())
        
        onDispose {
            insetsController?.show(WindowInsetsCompat.Type.statusBars())
            insetsController?.isAppearanceLightStatusBars = previousLight
        }
    }

    val mediaFiles = remember(post.mediaPaths) {
        post.mediaPaths.map { File(it) }.filter { it.exists() && it.canRead() && it.length() > 0 }
    }

    val pagerState = rememberPagerState(pageCount = { if (mediaFiles.isEmpty()) 1 else mediaFiles.size })
    val fav = isFavorite(post.postId)

    var isZoomActive by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showCaptionSheet by remember { mutableStateOf(false) }
    var showShareMenu by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showMoreOptionsSheet by remember { mutableStateOf(false) }
    val moreOptionsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val dateString = remember(post.downloadDate) {
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        dateFormat.format(Date(post.downloadDate))
    }

    val hashtags = remember(post.caption) {
        post.caption?.split(Regex("\\s+"))?.filter { it.startsWith("#") } ?: emptyList()
    }

    var isUiVisible by remember { mutableStateOf(true) }
    var isCaptionExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (mediaFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Media not found",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "Media file was deleted from your device.",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                isUiVisible = !isUiVisible
                            }
                        )
                    },
                userScrollEnabled = !isZoomActive
            ) { page ->
                val file = mediaFiles[page]
                val isVideo = file.extension.lowercase() in listOf("mp4", "mov", "avi")

                val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
                val fraction = 1f - pageOffset.coerceIn(0f, 1f)
                val pageScale = 0.95f + 0.05f * fraction

                val coroutineScope = rememberCoroutineScope()
                val zoomScale = remember { Animatable(1f) }
                val zoomOffsetX = remember { Animatable(0f) }
                val zoomOffsetY = remember { Animatable(0f) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown()
                                do {
                                    val event = awaitPointerEvent()
                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    val isMultiTouch = event.changes.size > 1

                                    if (isMultiTouch || zoomScale.value > 1f) {
                                        coroutineScope.launch {
                                            val newScale = (zoomScale.value * zoom).coerceIn(1f, 5f)
                                            zoomScale.snapTo(newScale)
                                            isZoomActive = newScale > 1f
                                            if (newScale > 1f) {
                                                zoomOffsetX.snapTo(zoomOffsetX.value + pan.x)
                                                zoomOffsetY.snapTo(zoomOffsetY.value + pan.y)
                                            } else {
                                                zoomOffsetX.snapTo(0f)
                                                zoomOffsetY.snapTo(0f)
                                            }
                                        }
                                        event.changes.forEach { it.consume() }
                                    }
                                } while (event.changes.any { it.pressed })

                                coroutineScope.launch {
                                    zoomScale.animateTo(1f)
                                    zoomOffsetX.animateTo(0f)
                                    zoomOffsetY.animateTo(0f)
                                    isZoomActive = false
                                }
                            }
                        }
                        .graphicsLayer {
                            scaleX = zoomScale.value * pageScale
                            scaleY = zoomScale.value * pageScale
                            translationX = zoomOffsetX.value
                            translationY = zoomOffsetY.value
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isVideo) {
                        VideoPlayer(file = file, isCurrentPage = pagerState.currentPage == page)
                    } else {
                        val mediaPath = file.absolutePath
                        val imageRequest = remember(mediaPath) {
                            ImageRequest.Builder(context)
                                .data(mediaPath)
                                .diskCachePolicy(CachePolicy.DISABLED)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .memoryCacheKey(mediaPath)
                                .diskCacheKey(mediaPath)
                                .crossfade(true)
                                .build()
                        }
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = "Post Media",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }

        // Bottom Gradient Overlay for caption readability
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.45f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.5f),
                            Color.Black.copy(alpha = 0.85f),
                            Color.Black
                        )
                    )
                )
        )

        // Floating Action Buttons (Vertical Column on the right)
        AnimatedVisibility(
            visible = isUiVisible,
            enter = fadeIn() + slideInHorizontally { it },
            exit = fadeOut() + slideOutHorizontally { it },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 48.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Favorite Button
                FloatingActionButtonItem(
                    icon = if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    tint = if (fav) Color(0xFFFF5252) else Color.White,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleFavorite(post.postId)
                    }
                )

                // Share Button
                Box {
                    FloatingActionButtonItem(
                        icon = Icons.Default.Share,
                        tint = Color.White,
                        onClick = { showShareMenu = true }
                    )

                    DropdownMenu(
                        expanded = showShareMenu,
                        onDismissRequest = { showShareMenu = false },
                        modifier = Modifier.background(Color(0xFF0F1115))
                    ) {
                        if (mediaFiles.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Share Current Media", color = Color.White) },
                                onClick = {
                                    showShareMenu = false
                                    val currentFile = mediaFiles[pagerState.currentPage]
                                    val fileUri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        currentFile
                                    )
                                    val shareIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        type = if (currentFile.extension.lowercase() in listOf("mp4", "mov", "avi")) "video/*" else "image/*"
                                        putExtra(Intent.EXTRA_STREAM, fileUri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Current Media"))
                                }
                            )
                            if (mediaFiles.size > 1) {
                                DropdownMenuItem(
                                    text = { Text("Share All Media", color = Color.White) },
                                    onClick = {
                                        showShareMenu = false
                                        val fileUris = ArrayList<Uri>()
                                        mediaFiles.forEach { file ->
                                            val fileUri = FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                file
                                            )
                                            fileUris.add(fileUri)
                                        }
                                        val shareIntent = Intent().apply {
                                            action = Intent.ACTION_SEND_MULTIPLE
                                            type = "*/*"
                                            putParcelableArrayListExtra(Intent.EXTRA_STREAM, fileUris)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share All Media"))
                                    }
                                )
                            }
                        }
                    }
                }

                // Options/More Menu
                FloatingActionButtonItem(
                    icon = Icons.Default.MoreVert,
                    tint = Color.White,
                    onClick = { showMoreOptionsSheet = true }
                )
            }
        }

        // Bottom Details and Metadata Overlay
        AnimatedVisibility(
            visible = isUiVisible,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 92.dp, bottom = 32.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Avatar",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text(
                        text = "@${post.username}",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                val annotatedCaption = buildAnnotatedString {
                    if (!post.caption.isNullOrEmpty()) {
                        withStyle(SpanStyle(color = Color.White)) {
                            append(post.caption)
                        }
                    }
                }

                if (annotatedCaption.isNotEmpty()) {
                    Text(
                        text = annotatedCaption,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        maxLines = if (isCaptionExpanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                isCaptionExpanded = !isCaptionExpanded
                            }
                    )
                }

                if (hashtags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(hashtags) { tag ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .clickable {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("hashtag", tag)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Copied tag: $tag", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = tag,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Saved $dateString",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 11.sp
                )
            }
        }

        // Floating Back Button (Top Left)
        AnimatedVisibility(
            visible = isUiVisible,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .border(0.5.dp, Color.White.copy(alpha = 0.1f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Pager Indicator overlay (Top Center)
        if (mediaFiles.size > 1) {
            AnimatedVisibility(
                visible = isUiVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1}/${mediaFiles.size}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showMoreOptionsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMoreOptionsSheet = false },
            sheetState = moreOptionsSheetState,
            containerColor = Color(0xFF0F1115),
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Media Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            scope.launch {
                                moreOptionsSheetState.hide()
                                showMoreOptionsSheet = false
                                onToggleFavorite(post.postId)
                            }
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Toggle Favorite",
                        tint = if (fav) Color(0xFFFF5252) else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = if (fav) "Remove from Favorites" else "Add to Favorites",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            scope.launch {
                                moreOptionsSheetState.hide()
                                showMoreOptionsSheet = false
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("post_link", "https://www.instagram.com/p/${post.postId}/")
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Copied post URL", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Link",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Copy Post Link",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            scope.launch {
                                moreOptionsSheetState.hide()
                                showMoreOptionsSheet = false
                                val instagramUri = Uri.parse("https://www.instagram.com/p/${post.postId}/")
                                val intent = Intent(Intent.ACTION_VIEW, instagramUri).apply {
                                    setPackage("com.instagram.android")
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    val webIntent = Intent(Intent.ACTION_VIEW, instagramUri)
                                    context.startActivity(webIntent)
                                }
                            }
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = "Open in Instagram",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Open in Instagram",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            scope.launch {
                                moreOptionsSheetState.hide()
                                showMoreOptionsSheet = false
                                showDeleteConfirmDialog = true
                            }
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Delete Post",
                        color = Color(0xFFFF5252),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Post", color = Color.White) },
            text = { Text("Are you sure you want to delete this post? This action cannot be undone.", color = Color.White.copy(alpha = 0.8f)) },
            containerColor = Color(0xFF0F1115),
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeletePost(post)
                    }
                ) {
                    Text("Delete", color = Color(0xFFFF5252))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            }
        )
    }
}

@Composable
fun FloatingActionButtonItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            .border(0.5.dp, Color.White.copy(alpha = 0.12f), CircleShape)
            .bounceClick(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
fun VideoPlayer(file: File, isCurrentPage: Boolean) {
    var isPlaying by remember { mutableStateOf(false) }
    val mediaPlayerRef = remember { mutableStateOf<MediaPlayer?>(null) }
    var isPrepared by remember { mutableStateOf(false) }

    LaunchedEffect(isCurrentPage) {
        isPlaying = isCurrentPage
    }

    var videoAspectRatio by remember(file) { mutableStateOf(16f / 9f) }
    LaunchedEffect(file) {
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            val ratio = try {
                retriever.setDataSource(file.absolutePath)
                val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toFloatOrNull() ?: 16f
                val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toFloatOrNull() ?: 9f
                val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                
                val actualWidth = if (rotation == 90 || rotation == 270) height else width
                val actualHeight = if (rotation == 90 || rotation == 270) width else height
                
                if (actualWidth > 0 && actualHeight > 0) {
                    actualWidth / actualHeight
                } else 16f / 9f
            } catch (_: Exception) {
                16f / 9f
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {}
            }
            videoAspectRatio = ratio
        }
    }

    val releasePlayer = remember {
        { player: MediaPlayer? ->
            if (player != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    try { player.stop() } catch (_: Exception) {}
                    try { player.release() } catch (_: Exception) {}
                }
            }
        }
    }

    DisposableEffect(file) {
        onDispose {
            val player = mediaPlayerRef.value
            mediaPlayerRef.value = null
            isPrepared = false
            releasePlayer(player)
        }
    }

    var currentPosition by remember { mutableStateOf(0) }
    var duration by remember { mutableStateOf(1) }

    LaunchedEffect(isPlaying, isPrepared) {
        if (isPlaying && isPrepared) {
            while (true) {
                mediaPlayerRef.value?.let { mp ->
                    try {
                        currentPosition = mp.currentPosition
                        duration = mp.duration.coerceAtLeast(1)
                    } catch (e: Exception) {}
                }
                delay(250L)
            }
        }
    }

    var showControlsOverlay by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                            val surface = Surface(surfaceTexture)
                            val oldPlayer = mediaPlayerRef.value
                            mediaPlayerRef.value = null
                            isPrepared = false
                            releasePlayer(oldPlayer)

                            try {
                                val mp = MediaPlayer().apply {
                                    setDataSource(ctx, Uri.fromFile(file))
                                    setSurface(surface)
                                    isLooping = true
                                    setOnPreparedListener {
                                        isPrepared = true
                                        if (isPlaying) {
                                            start()
                                        } else {
                                            seekTo(1)
                                        }
                                    }
                                    prepareAsync()
                                }
                                mediaPlayerRef.value = mp
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

                        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                            val player = mediaPlayerRef.value
                            mediaPlayerRef.value = null
                            isPrepared = false
                            releasePlayer(player)
                            return true
                        }

                        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                    }
                }
            },
            update = { view ->
                mediaPlayerRef.value?.let { mp ->
                    try {
                        if (isPrepared) {
                            if (isPlaying) {
                                if (!mp.isPlaying) mp.start()
                            } else {
                                if (mp.isPlaying) mp.pause()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            },
            modifier = Modifier.aspectRatio(videoAspectRatio)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable {
                    isPlaying = !isPlaying
                    showControlsOverlay = true
                }
        )

        // Transient Central play/pause overlay indication
        LaunchedEffect(isPlaying) {
            showControlsOverlay = true
            delay(800)
            showControlsOverlay = false
        }

        AnimatedVisibility(
            visible = showControlsOverlay,
            enter = scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
            exit = scaleOut(animationSpec = tween(300)) + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        // Minimalist progress bar at the bottom
        val progress = (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .align(Alignment.BottomCenter)
                .background(Color.White.copy(alpha = 0.25f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

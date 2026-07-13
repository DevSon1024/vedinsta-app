package com.devson.vedinsta.ui.screen

import android.content.Intent
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.devson.vedinsta.database.DownloadedPost
import com.devson.vedinsta.database.containsVideo
import com.devson.vedinsta.ui.showPostOptions
import com.devson.vedinsta.viewmodel.MainViewModel
import java.io.File
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class PlayPauseState { PLAY, PAUSE }

@Composable
fun FeedPlayScreen(
    mainViewModel: MainViewModel,
    initialPostId: String?,
    initialIndex: Int?,
    isFavorite: (String) -> Boolean,
    onToggleFavorite: (String) -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val window = remember(view) { (view.context as? android.app.Activity)?.window }

    // Edge-to-edge layout optimization: Hide device bottom navigation bar, keep status bar
    if (window != null) {
        val windowInsetsController = remember(window, view) {
            WindowCompat.getInsetsController(window, view)
        }
        DisposableEffect(windowInsetsController) {
            windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())
            windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            onDispose {
                windowInsetsController.show(WindowInsetsCompat.Type.navigationBars())
            }
        }
    }

    val posts by mainViewModel.allDownloadedPosts.observeAsState(emptyList())

    val videoPosts = remember(posts) {
        posts.filter { it.containsVideo }
    }

    val startIndex = remember(videoPosts, initialPostId, initialIndex) {
        val indexById = initialPostId?.let { id -> videoPosts.indexOfFirst { it.postId == id } } ?: -1
        if (indexById != -1) {
            indexById
        } else {
            initialIndex?.coerceIn(0, (videoPosts.size - 1).coerceAtLeast(0)) ?: 0
        }
    }

    if (videoPosts.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "No videos downloaded yet", color = Color.White, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onBackClick) {
                    Text("Go Back")
                }
            }
        }
    } else {
        val pagerState = rememberPagerState(
            initialPage = startIndex,
            pageCount = { videoPosts.size }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1
            ) { pageIndex ->
                val post = videoPosts[pageIndex]
                val isCurrentPage = pagerState.currentPage == pageIndex

                FeedItemView(
                    post = post,
                    isCurrentPage = isCurrentPage,
                    isFavorite = isFavorite,
                    onToggleFavorite = onToggleFavorite,
                    mainViewModel = mainViewModel,
                    onBackClick = onBackClick
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun FeedItemView(
    post: DownloadedPost,
    isCurrentPage: Boolean,
    isFavorite: (String) -> Boolean,
    onToggleFavorite: (String) -> Unit,
    mainViewModel: MainViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val videoPath = remember(post.mediaPaths) {
        post.mediaPaths.firstOrNull { it.endsWith(".mp4", ignoreCase = true) } ?: post.mediaPaths.firstOrNull()
    }
    val videoFile = remember(videoPath) {
        videoPath?.let { File(it) }
    }

    val imageRequest = remember(post.thumbnailPath) {
        ImageRequest.Builder(context)
            .data(if (post.thumbnailPath.isNotEmpty()) File(post.thumbnailPath) else null)
            .crossfade(true)
            .build()
    }

    // Performance Optimization: Delay ExoPlayer load to prevent enter/scroll navigation lag.
    var playerInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage) {
            delay(150L) // Wait 150ms for transition animation to end smoothly
            playerInitialized = true
        } else {
            delay(600L) // Preload adjacent items only after navigation settles
            playerInitialized = true
        }
    }

    // Unconditional ExoPlayer setup (returns null if not ready or not a video)
    val exoPlayer = remember(videoFile, playerInitialized) {
        if (videoFile != null && videoFile.exists() && playerInitialized) {
            ExoPlayer.Builder(context).build().apply {
                val mediaItem = MediaItem.fromUri(android.net.Uri.fromFile(videoFile))
                setMediaItem(mediaItem)
                repeatMode = Player.REPEAT_MODE_ONE
                prepare()
            }
        } else {
            null
        }
    }

    LaunchedEffect(exoPlayer, isCurrentPage) {
        if (exoPlayer != null) {
            if (isCurrentPage) {
                exoPlayer.playWhenReady = true
                exoPlayer.play()
            } else {
                exoPlayer.playWhenReady = false
                exoPlayer.pause()
            }
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer?.release()
        }
    }

    var isLongPressActive by remember { mutableStateOf(false) }
    var playPauseFeedbackState by remember { mutableStateOf<PlayPauseState?>(null) }
    var currentPosition by remember { mutableStateOf(0L) }
    var totalDuration by remember { mutableStateOf(0L) }

    LaunchedEffect(exoPlayer, isCurrentPage) {
        if (exoPlayer != null && isCurrentPage) {
            while (true) {
                currentPosition = exoPlayer.currentPosition
                totalDuration = exoPlayer.duration
                delay(100L)
            }
        }
    }

    LaunchedEffect(playPauseFeedbackState) {
        if (playPauseFeedbackState != null) {
            delay(600L)
            playPauseFeedbackState = null
        }
    }

    val progress = if (totalDuration > 0L) currentPosition.toFloat() / totalDuration.toFloat() else 0f
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        if (exoPlayer != null && videoFile != null && videoFile.exists()) {
            // Video Display Container with Tap & LongPress speed controls
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(post.postId) {
                        coroutineScope {
                            awaitPointerEventScope {
                                while (true) {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    
                                    val timerJob = launch {
                                        delay(400L)
                                        isLongPressActive = true
                                        exoPlayer.setPlaybackSpeed(2.0f)
                                    }

                                    var isReleased = false
                                    while (!isReleased) {
                                        val event = awaitPointerEvent()
                                        val dragEvent = event.changes.firstOrNull { it.id == down.id }
                                        if (dragEvent == null || !dragEvent.pressed) {
                                            isReleased = true
                                        }
                                    }

                                    if (timerJob.isActive) {
                                        timerJob.cancel()
                                        if (exoPlayer.isPlaying) {
                                            exoPlayer.pause()
                                            playPauseFeedbackState = PlayPauseState.PAUSE
                                        } else {
                                            exoPlayer.play()
                                            playPauseFeedbackState = PlayPauseState.PLAY
                                        }
                                    } else {
                                        isLongPressActive = false
                                        exoPlayer.setPlaybackSpeed(1.0f)
                                    }
                                }
                            }
                        }
                    }
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    update = { playerView ->
                        playerView.player = exoPlayer
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Bottom dark gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.35f)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.8f)
                            )
                        )
                    )
            )

            // UI Actions & Captures (Not blocking gestures)
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 16.dp, bottom = 16.dp)
                ) {
                    Text(
                        text = "@${post.username}",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    var isExpanded by remember { mutableStateOf(false) }
                    val captionText = post.caption.orEmpty()
                    if (captionText.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .animateContentSize()
                                .clickable { isExpanded = !isExpanded }
                        ) {
                            if (isExpanded) {
                                Text(
                                    text = captionText,
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                            } else {
                                Row {
                                    Text(
                                        text = captionText,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (captionText.length > 50) {
                                        Text(
                                            text = " more...",
                                            color = Color.LightGray,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    val isFav = isFavorite(post.postId)

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = { onToggleFavorite(post.postId) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Like",
                                tint = if (isFav) Color.Red else Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Text(
                            text = if (isFav) "Liked" else "Like",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = {
                                try {
                                    val fileUri = androidx.core.content.FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        videoFile
                                    )
                                    val shareIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        type = "video/*"
                                        putExtra(Intent.EXTRA_STREAM, fileUri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Video"))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error sharing video: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Text(
                            text = "Share",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = {
                                showPostOptions(
                                    context = context,
                                    post = post,
                                    viewModel = mainViewModel,
                                    onToggleFavorite = onToggleFavorite,
                                    isFavorite = isFav
                                )
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Text(
                            text = "More",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Top-left float back arrow
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // 2X Speed pill overlay at top center
            if (isLongPressActive) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 16.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "2X Speed",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Play/Pause pop overlay
            if (playPauseFeedbackState != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .size(72.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (playPauseFeedbackState == PlayPauseState.PLAY) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Interactive Bottom Seekbar (Touch/Drag seeking controls)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .align(Alignment.BottomCenter)
                    .pointerInput(totalDuration) {
                        if (totalDuration > 0L) {
                            awaitPointerEventScope {
                                while (true) {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val initialFraction = down.position.x / size.width.toFloat()
                                    exoPlayer.seekTo((initialFraction * totalDuration).toLong().coerceIn(0L, totalDuration))
                                    
                                    var isReleased = false
                                    while (!isReleased) {
                                        val event = awaitPointerEvent()
                                        val dragEvent = event.changes.firstOrNull { it.id == down.id }
                                        if (dragEvent == null || !dragEvent.pressed) {
                                            isReleased = true
                                        } else {
                                            dragEvent.consume()
                                            val fraction = dragEvent.position.x / size.width.toFloat()
                                            exoPlayer.seekTo((fraction * totalDuration).toLong().coerceIn(0L, totalDuration))
                                        }
                                    }
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.2f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(Color.White)
                    )
                }
            }
        } else {
            // Render Fallback Thumbnail
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = "Fallback Thumbnail",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

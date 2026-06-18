package com.devson.vedinsta.ui

import android.os.Build
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.devson.vedinsta.VedInstaApplication
import com.devson.vedinsta.database.CachedStoryEntity
import com.devson.vedinsta.viewmodel.FavoriteStoriesViewModel
import com.devson.vedinsta.viewmodel.StoryState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun InstagramStoryViewerScreen(
    viewModel: FavoriteStoriesViewModel,
    username: String,
    initialIndex: Int,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val storyState by viewModel.storyState.collectAsStateWithLifecycle()

    LaunchedEffect(username) {
        viewModel.loadStories(username)
    }

    DisposableEffect(username) {
        onDispose {
            viewModel.resetStoryState()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        when (val state = storyState) {
            is StoryState.Loading -> {
                CircularProgressIndicator(color = Color(0xFFE1306C))
            }
            is StoryState.Error -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = state.message,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onNavigateBack,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE1306C))
                    ) {
                        Text("Go Back", color = Color.White)
                    }
                }
            }
            is StoryState.Success -> {
                StoryViewerContent(
                    stories = state.stories,
                    initialIndex = initialIndex,
                    username = username,
                    onNavigateBack = onNavigateBack,
                    onStoryViewed = { viewModel.markStoryAsViewed(it) }
                )
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun StoryViewerContent(
    stories: List<CachedStoryEntity>,
    initialIndex: Int,
    username: String,
    onNavigateBack: () -> Unit,
    onStoryViewed: (Long) -> Unit
) {
    val context = LocalContext.current

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, stories.lastIndex),
        pageCount = { stories.size }
    )
    val currentIndex = pagerState.currentPage
    val currentStory = stories.getOrNull(currentIndex)

    if (currentStory == null) {
        LaunchedEffect(Unit) {
            onNavigateBack()
        }
        return
    }

    val scope = rememberCoroutineScope()
    var progress by remember { mutableFloatStateOf(0f) }
    var isPaused by remember { mutableStateOf(false) }

    fun goToNext() {
        if (pagerState.currentPage < stories.lastIndex) {
            scope.launch {
                pagerState.animateScrollToPage(pagerState.currentPage + 1)
            }
        } else {
            onNavigateBack()
        }
    }

    fun goToPrevious() {
        if (pagerState.currentPage > 0) {
            scope.launch {
                pagerState.animateScrollToPage(pagerState.currentPage - 1)
            }
        } else {
            onNavigateBack()
        }
    }

    // Reset progress and mark story as viewed when current page changes
    LaunchedEffect(currentIndex) {
        progress = 0f
        val story = stories.getOrNull(currentIndex)
        if (story != null && !story.isViewed) {
            onStoryViewed(story.id)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Blurred Background
        AnimatedContent(
            targetState = currentStory,
            transitionSpec = {
                fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
            },
            label = "StoryBgBlur"
        ) { targetStory ->
            val bgImageRequest = remember(targetStory.localFilePath) {
                ImageRequest.Builder(context)
                    .data(Uri.fromFile(java.io.File(targetStory.localFilePath)))
                    .size(80, 80)
                    .crossfade(true)
                    .build()
            }
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = bgImageRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                Modifier.blur(12.dp)
                            } else {
                                Modifier
                            }
                        )
                        .alpha(0.55f)
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                )
            }
        }

        // Foreground Content inside Pager
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = true
            ) { page ->
                val story = stories.getOrNull(page)
                if (story != null) {
                    StoryPageContent(
                        story = story,
                        isCurrentPage = page == currentIndex,
                        isPaused = isPaused,
                        onProgressChanged = { newProgress ->
                            if (page == currentIndex) {
                                progress = newProgress
                            }
                        },
                        onStoryCompleted = {
                            goToNext()
                        }
                    )
                }
            }

            // Foreground click/press overlay targets
            Row(modifier = Modifier.fillMaxSize()) {
                // Left 30% to go back
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(3f)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { goToPrevious() }
                            )
                        }
                )
                // Right 70% to go forward / press to pause
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(7f)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    isPaused = true
                                    tryAwaitRelease()
                                    isPaused = false
                                },
                                onTap = { goToNext() }
                            )
                        }
                )
            }
        }

        // Top-to-bottom shadow gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.7f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Overlay UI
        AnimatedVisibility(
            visible = !isPaused,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top control bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 8.dp)
                ) {
                    // Instagram pink/red hue Seekbars
                    StoryProgressRow(
                        storiesSize = stories.size,
                        currentIndex = currentIndex,
                        progressProvider = { progress }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Header Details
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "@$username",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Story ${currentIndex + 1} of ${stories.size}",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        }

                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Direct Download Floating Action Button
                FloatingActionButton(
                    onClick = {
                        try {
                            val srcFile = java.io.File(currentStory.localFilePath)
                            if (srcFile.exists()) {
                                val extension = if (currentStory.isVideo) "mp4" else "jpg"
                                val fileName = "${username}_story_${System.currentTimeMillis()}.$extension"
                                val targetDir = if (currentStory.isVideo) {
                                    com.devson.vedinsta.database.PostMediaManager.getVideoDirectory()
                                } else {
                                    com.devson.vedinsta.database.PostMediaManager.getImageDirectory()
                                }
                                targetDir.mkdirs()
                                val destFile = java.io.File(targetDir, fileName)
                                srcFile.copyTo(destFile, overwrite = true)

                                // Index it to MediaStore
                                android.media.MediaScannerConnection.scanFile(
                                    context,
                                    arrayOf(destFile.absolutePath),
                                    arrayOf(if (currentStory.isVideo) "video/mp4" else "image/jpeg"),
                                    null
                                )
                                Toast.makeText(context, "Story saved to gallery!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Cached file not found", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Failed to save story: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    containerColor = Color(0xFFE1306C),
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp, end = 24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download Story"
                    )
                }
            }
        }
    }
}

@Composable
fun StoryPageContent(
    story: CachedStoryEntity,
    isCurrentPage: Boolean,
    isPaused: Boolean,
    onProgressChanged: (Float) -> Unit,
    onStoryCompleted: () -> Unit
) {
    val context = LocalContext.current

    if (story.isVideo) {
        val player = remember {
            ExoPlayer.Builder(context).build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
            }
        }

        // Manage player resource release
        DisposableEffect(player) {
            onDispose {
                player.release()
            }
        }

        // Bind media items and handle prepare/play/pause state keyed to page focus and local file
        LaunchedEffect(isCurrentPage, story.localFilePath) {
            if (isCurrentPage) {
                val mediaItem = MediaItem.fromUri(Uri.fromFile(java.io.File(story.localFilePath)))
                player.setMediaItem(mediaItem)
                player.prepare()
                player.playWhenReady = !isPaused
            } else {
                player.pause()
            }
        }

        LaunchedEffect(isPaused) {
            if (isCurrentPage) {
                player.playWhenReady = !isPaused
            }
        }

        // Timer/Progress updates
        LaunchedEffect(isCurrentPage, isPaused) {
            if (isCurrentPage && !isPaused) {
                while (true) {
                    val pos = player.currentPosition
                    val dur = player.duration
                    if (dur > 0) {
                        onProgressChanged(pos.toFloat() / dur.toFloat())
                    }
                    delay(50)
                }
            }
        }

        // Autoadvance when player state reaches ENDED
        DisposableEffect(player) {
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED && isCurrentPage) {
                        onStoryCompleted()
                    }
                }
            }
            player.addListener(listener)
            onDispose {
                player.removeListener(listener)
            }
        }

        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    this.player = player
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                if (playerView.player != player) {
                    playerView.player = player
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    } else {
        var progress by remember { mutableFloatStateOf(0f) }

        LaunchedEffect(isCurrentPage, isPaused) {
            if (isCurrentPage && !isPaused) {
                val interval = 16L
                val totalSteps = 5000L / interval
                val step = 1f / totalSteps
                while (progress < 1.0f) {
                    delay(interval)
                    progress = (progress + step).coerceAtMost(1.0f)
                    onProgressChanged(progress)
                }
                onStoryCompleted()
            } else if (!isCurrentPage) {
                progress = 0f
            }
        }

        val imageRequest = remember(story.localFilePath) {
            ImageRequest.Builder(context)
                .data(Uri.fromFile(java.io.File(story.localFilePath)))
                .crossfade(true)
                .build()
        }

        AsyncImage(
            model = imageRequest,
            contentDescription = "Story Media",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
fun StoryProgressRow(
    storiesSize: Int,
    currentIndex: Int,
    progressProvider: () -> Float,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (i in 0 until storiesSize) {
            val segmentProgressProvider = {
                when {
                    i < currentIndex -> 1.0f
                    i > currentIndex -> 0.0f
                    else -> progressProvider()
                }
            }
            LinearProgressIndicator(
                progress = segmentProgressProvider,
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp),
                color = Color(0xFFE1306C), // Pink-Red Instagram-vibe progress accent
                trackColor = Color.White.copy(alpha = 0.3f),
                strokeCap = StrokeCap.Round
            )
        }
    }
}

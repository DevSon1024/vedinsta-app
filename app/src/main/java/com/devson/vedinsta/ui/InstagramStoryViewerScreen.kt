package com.devson.vedinsta.ui

import android.os.Build
import android.net.Uri
import android.widget.VideoView
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.devson.vedinsta.VedInstaApplication
import com.devson.vedinsta.database.CachedStoryEntity
import com.devson.vedinsta.viewmodel.FavoriteStoriesViewModel
import com.devson.vedinsta.viewmodel.StoryState
import kotlinx.coroutines.delay

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
    val app = context.applicationContext as? VedInstaApplication
    
    var currentIndex by remember { mutableIntStateOf(initialIndex.coerceIn(0, stories.lastIndex)) }
    val currentStory = stories.getOrNull(currentIndex)

    if (currentStory == null) {
        LaunchedEffect(Unit) {
            onNavigateBack()
        }
        return
    }

    var transitionCompleted by remember { mutableStateOf(false) }
    LaunchedEffect(currentIndex) {
        transitionCompleted = false
        delay(350)
        transitionCompleted = true
    }

    var progress by remember { mutableFloatStateOf(0f) }
    var isPaused by remember { mutableStateOf(false) }
    var videoDuration by remember { mutableLongStateOf(5000L) }
    var videoPosition by remember { mutableLongStateOf(0L) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }

    fun goToNext() {
        if (currentIndex < stories.lastIndex) {
            currentIndex++
        } else {
            onNavigateBack()
        }
    }

    fun goToPrevious() {
        if (currentIndex > 0) {
            currentIndex--
        } else {
            onNavigateBack()
        }
    }

    // Reset progress on story change and mark as viewed
    LaunchedEffect(currentIndex) {
        progress = 0f
        videoDuration = 5000L
        videoPosition = 0L
        videoViewRef = null

        val story = stories.getOrNull(currentIndex)
        if (story != null && !story.isViewed) {
            onStoryViewed(story.id)
        }
    }

    // Progress timer controller
    LaunchedEffect(currentIndex, isPaused) {
        if (!isPaused) {
            val story = stories.getOrNull(currentIndex) ?: return@LaunchedEffect
            if (story.isVideo) {
                while (true) {
                    videoViewRef?.let { view ->
                        val pos = view.currentPosition
                        val dur = view.duration
                        if (dur > 0) {
                            videoPosition = pos.toLong()
                            videoDuration = dur.toLong()
                            progress = pos.toFloat() / dur.toFloat()
                        }
                    }
                    delay(50)
                }
            } else {
                val interval = 16L
                val totalSteps = 5000L / interval
                val step = 1f / totalSteps
                while (progress < 1.0f) {
                    delay(interval)
                    progress = (progress + step).coerceAtMost(1.0f)
                }
                goToNext()
            }
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
                    .data(java.io.File(targetStory.localFilePath))
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

        // Foreground Content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(currentIndex) {
                    detectTapGestures(
                        onPress = {
                            isPaused = true
                            tryAwaitRelease()
                            isPaused = false
                        },
                        onTap = { offset ->
                            val width = size.width
                            if (offset.x < width * 0.3f) {
                                goToPrevious()
                            } else {
                                goToNext()
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (currentStory.isVideo && transitionCompleted) {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setOnPreparedListener { mp ->
                                videoDuration = mp.duration.toLong().coerceAtLeast(1000L)
                                if (!isPaused) {
                                    start()
                                }
                            }
                            setOnCompletionListener {
                                goToNext()
                            }
                            videoViewRef = this
                        }
                    },
                    update = { videoView ->
                        val localFile = java.io.File(currentStory.localFilePath)
                        val currentUri = Uri.fromFile(localFile)
                        if (videoView.tag != currentStory.localFilePath) {
                            videoView.tag = currentStory.localFilePath
                            videoView.setVideoURI(currentUri)
                        }
                        if (isPaused) {
                            videoView.pause()
                        } else {
                            videoView.start()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                val imageRequest = remember(currentStory.localFilePath) {
                    ImageRequest.Builder(context)
                        .data(java.io.File(currentStory.localFilePath))
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

package com.devson.vedinsta.ui

import android.net.Uri
import android.os.Build
import android.widget.VideoView
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
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.devson.vedinsta.VedInstaApplication
import com.devson.vedinsta.model.MediaResult
import com.devson.vedinsta.viewmodel.MainViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun InstagramStoryViewerScreen(
    mainViewModel: MainViewModel,
    initialIndex: Int,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as VedInstaApplication

    val username by mainViewModel.currentStoryUser.collectAsState()
    val profilePicUrl by mainViewModel.currentStoryProfilePic.collectAsState()
    val mediaList by mainViewModel.currentStoryMedia.collectAsState()

    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? android.app.Activity)?.window
        var originalLightStatus = false
        var originalLightNav = false
        var insetsController: androidx.core.view.WindowInsetsControllerCompat? = null
        if (window != null) {
            insetsController = WindowCompat.getInsetsController(window, view)
            originalLightStatus = insetsController.isAppearanceLightStatusBars
            originalLightNav = insetsController.isAppearanceLightNavigationBars
            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = false
        }
        onDispose {
            insetsController?.isAppearanceLightStatusBars = originalLightStatus
            insetsController?.isAppearanceLightNavigationBars = originalLightNav
        }
    }

    if (mediaList.isEmpty()) {
        LaunchedEffect(Unit) {
            onNavigateBack()
        }
        return
    }

    var currentIndex by remember { mutableIntStateOf(initialIndex.coerceIn(0, mediaList.lastIndex)) }
    val currentMedia = mediaList.getOrNull(currentIndex)

    if (currentMedia == null) {
        LaunchedEffect(Unit) {
            onNavigateBack()
        }
        return
    }

    val isVideo = remember(currentMedia.url) {
        currentMedia.type == "video"
    }

    var transitionCompleted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(350)
        transitionCompleted = true
    }

    var isVideoPlaying by remember(currentIndex) { mutableStateOf(false) }

    var progress by remember { mutableFloatStateOf(0f) }
    var isPaused by remember { mutableStateOf(false) }
    var videoDuration by remember { mutableLongStateOf(5000L) }
    var videoPosition by remember { mutableLongStateOf(0L) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }

    fun goToNext() {
        if (currentIndex < mediaList.lastIndex) {
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

    // Reset progress timers on index change
    LaunchedEffect(currentIndex) {
        progress = 0f
        videoDuration = 5000L
        videoPosition = 0L
        videoViewRef = null
    }

    // Progress timer loop controller
    LaunchedEffect(currentIndex, isPaused, isVideoPlaying) {
        if (!isPaused) {
            val media = mediaList.getOrNull(currentIndex)
            val isCurrentVideo = media?.type == "video"
            if (isCurrentVideo) {
                if (isVideoPlaying) {
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
                    progress = 0f
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
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        // Foreground Content Container with touch gestures
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(currentIndex, isVideo, isVideoPlaying) {
                    detectTapGestures(
                        onPress = {
                            if (!isVideo || isVideoPlaying) {
                                isPaused = true
                                tryAwaitRelease()
                                isPaused = false
                            }
                        },
                        onTap = { offset ->
                            val width = size.width
                            if (offset.x < width * 0.3f) {
                                goToPrevious()
                            } else {
                                if (isVideo && !isVideoPlaying) {
                                    isVideoPlaying = true
                                } else {
                                    goToNext()
                                }
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (isVideo && transitionCompleted && isVideoPlaying) {
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
                        val currentUrl = currentMedia.url ?: ""
                        if (videoView.tag != currentUrl && currentUrl.isNotEmpty()) {
                            videoView.tag = currentUrl
                            videoView.setVideoURI(Uri.parse(currentUrl))
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
                // Show static image preview (either the thumbnail or the image story itself)
                AsyncImage(
                    model = currentMedia.thumbnailUrl.takeIf { !it.isNullOrEmpty() } ?: currentMedia.url,
                    contentDescription = "Story Preview",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

                // Show Play button overlay for video if not playing yet
                if (isVideo && !isVideoPlaying) {
                    IconButton(
                        onClick = { isVideoPlaying = true },
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play Video",
                            tint = Color.White,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }
            }
        }

        // Shadow gradient overlay for header
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

        // UI Controls Layer
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
                    // Segmented progress indicators
                    StoryProgressRow(
                        storiesSize = mediaList.size,
                        currentIndex = currentIndex,
                        progressProvider = { progress }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Header profile row
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

                        Spacer(modifier = Modifier.width(4.dp))

                        // User profile picture
                        AsyncImage(
                            model = profilePicUrl,
                            contentDescription = "Avatar",
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.Gray),
                            contentScale = ContentScale.Crop
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = username,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Story ${currentIndex + 1} of ${mediaList.size}",
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

                // Bottom Action Bar (centered pill button mimicking WhatsApp status screen styling but themed for IG)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp)
                ) {
                    Button(
                        onClick = {
                            app.downloadSingleMedia(
                                url = currentMedia.url ?: "",
                                type = currentMedia.type ?: "image",
                                username = username,
                                index = currentIndex
                            )
                            android.widget.Toast.makeText(context, "Downloading story...", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE1306C)),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Download Story",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
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
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp)),
                color = Color(0xFFE1306C), // Instagram themed pink-red color
                trackColor = Color.White.copy(alpha = 0.35f),
                strokeCap = StrokeCap.Round
            )
        }
    }
}

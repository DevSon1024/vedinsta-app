package com.devson.vedinsta.ui

import android.os.Build
import android.net.Uri
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
import androidx.compose.material.icons.filled.MusicNote
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
import androidx.documentfile.provider.DocumentFile
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.devson.vedinsta.viewmodel.WhatsAppState
import com.devson.vedinsta.viewmodel.WhatsAppViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun WhatsAppStatusViewScreen(
    viewModel: WhatsAppViewModel,
    initialIndex: Int,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val savedStatuses by viewModel.savedStatuses.collectAsState()

    val statuses = remember {
        (viewModel.state.value as? WhatsAppState.Success)?.statuses ?: emptyList()
    }

    if (statuses.isEmpty()) {
        LaunchedEffect(Unit) {
            onNavigateBack()
        }
        return
    }

    var currentIndex by remember { mutableIntStateOf(initialIndex.coerceIn(0, statuses.lastIndex)) }
    val currentFile = statuses.getOrNull(currentIndex)

    if (currentFile == null) {
        LaunchedEffect(Unit) {
            onNavigateBack()
        }
        return
    }

    val isVideo = remember(currentFile.name) {
        currentFile.name?.endsWith(".mp4", ignoreCase = true) == true
    }

    var transitionCompleted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(350) // Delay slightly longer than the 250ms screen transition to ensure it completes smoothly
        transitionCompleted = true
    }

    var progress by remember { mutableFloatStateOf(0f) }
    var isPaused by remember { mutableStateOf(false) }
    var videoDuration by remember { mutableLongStateOf(5000L) }
    var videoPosition by remember { mutableLongStateOf(0L) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }

    fun goToNext() {
        if (currentIndex < statuses.lastIndex) {
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

    // Reset parameters on status change
    LaunchedEffect(currentIndex) {
        progress = 0f
        videoDuration = 5000L
        videoPosition = 0L
        videoViewRef = null
    }

    // Progress timer controller
    LaunchedEffect(currentIndex, isPaused) {
        if (!isPaused) {
            val file = statuses.getOrNull(currentIndex)
            val isCurrentVideo = file?.name?.endsWith(".mp4", ignoreCase = true) == true
            if (isCurrentVideo) {
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
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Blurred Background Layer with transition animation
        AnimatedContent(
            targetState = currentFile,
            transitionSpec = {
                fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
            },
            label = "BgBlurAnim"
        ) { targetFile ->
            val bgImageRequest = remember(targetFile.uri) {
                ImageRequest.Builder(context)
                    .data(targetFile.uri)
                    .size(80, 80) // Downsample aggressively for cheap and fast blur rendering
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
                                Modifier.blur(12.dp) // Lighter blur radius is highly performant on downsampled images
                            } else {
                                Modifier // Scale stretching 80x80 image to fill screen automatically blurs it on older SDKs
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

        // Foreground Content container with gesture detection
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
            if (isVideo && transitionCompleted) {
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
                        val currentUri = currentFile.uri
                        if (videoView.tag != currentUri) {
                            videoView.tag = currentUri
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
                // Show static image preview during transition or for images
                AsyncImage(
                    model = currentFile.uri,
                    contentDescription = "Status Preview",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }

        // Top-to-bottom shadow gradient for readability
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
                    // Segmented progress bars
                    StatusProgressRow(
                        statusesSize = statuses.size,
                        currentIndex = currentIndex,
                        progressProvider = { progress }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Header Row
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
                                text = "WhatsApp Status",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${formatLastModified(currentFile.lastModified())} - ${currentIndex + 1} of ${statuses.size}",
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

                // Bottom actions bar
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp)
                ) {
                    val isSaved = savedStatuses.contains(currentFile.name)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSaved) {
                            Button(
                                onClick = {},
                                enabled = false,
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.DarkGray,
                                    contentColor = Color.LightGray
                                ),
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                            ) {
                                Text("Saved to Gallery", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = { viewModel.saveStatus(context, currentFile) },
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Save Status", fontWeight = FontWeight.Bold, color = Color.White)
                            }

                            if (isVideo) {
                                Button(
                                    onClick = { viewModel.saveAudioFromVideo(context, currentFile) },
                                    shape = RoundedCornerShape(24.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF075E54)),
                                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                                ) {
                                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Save Audio", fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatLastModified(timestamp: Long): String {
    if (timestamp == 0L) return ""
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    if (diff < 0) return "Just now"

    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60

    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "$minutes m ago"
        hours < 24 -> "$hours h ago"
        else -> {
            val date = Date(timestamp)
            val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
            sdf.format(date)
        }
    }
}

@Composable
fun StatusProgressRow(
    statusesSize: Int,
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
        for (i in 0 until statusesSize) {
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
                color = Color(0xFF25D366),
                trackColor = Color.White.copy(alpha = 0.3f),
                strokeCap = StrokeCap.Round
            )
        }
    }
}

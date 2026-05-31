package com.devson.vedinsta.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.VideoView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.devson.vedinsta.database.DownloadedPost
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostViewScreen(
    post: DownloadedPost,
    onBackClick: () -> Unit,
    onDeletePost: (DownloadedPost) -> Unit
) {
    val context = LocalContext.current
    
    // Filter and collect valid files
    val mediaFiles = remember(post.mediaPaths) {
        post.mediaPaths.map { File(it) }.filter { it.exists() && it.canRead() && it.length() > 0 }
    }

    if (mediaFiles.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text("No valid media files found.", color = Color.White)
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { mediaFiles.size })
    var isCaptionExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "@${post.username}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val dateString = remember(post.downloadDate) {
                            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                            dateFormat.format(Date(post.downloadDate))
                        }
                        Text(
                            text = "Downloaded $dateString",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            fontSize = 11.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // Copy Link
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("post_link", "https://www.instagram.com/p/${post.postId}/")
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Link",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Share File
                    IconButton(onClick = {
                        val currentFile = mediaFiles[pagerState.currentPage]
                        val fileUri = androidx.core.content.FileProvider.getUriForFile(
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
                        context.startActivity(Intent.createChooser(shareIntent, "Share media"))
                    }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Delete Post
                    IconButton(onClick = {
                        onDeletePost(post)
                    }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Horizontal Carousel
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().background(Color.Black)
            ) { page ->
                val file = mediaFiles[page]
                val isVideo = file.extension.lowercase() in listOf("mp4", "mov", "avi")

                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (isVideo) {
                        VideoPlayer(file = file)
                    } else {
                        AsyncImage(
                            model = file,
                            contentDescription = "Post Media",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }

            // Carousel indicator / index counter
            if (mediaFiles.size > 1) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${mediaFiles.size}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Caption overlay at bottom
            if (!post.caption.isNullOrEmpty()) {
                val fullCaption = post.caption
                val isLong = fullCaption.length > 80

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                        .padding(24.dp)
                ) {
                    Text(
                        text = if (isCaptionExpanded || !isLong) fullCaption else fullCaption.take(80) + "...",
                        color = Color.White,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        maxLines = if (isCaptionExpanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    if (isLong) {
                        Text(
                            text = if (isCaptionExpanded) "Show Less" else "Show More",
                            color = MaterialTheme.colorScheme.tertiary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { isCaptionExpanded = !isCaptionExpanded }
                                .padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VideoPlayer(file: File) {
    var isPlaying by remember { mutableStateOf(true) }
    
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    setVideoURI(Uri.fromFile(file))
                    setOnPreparedListener { mp ->
                        mp.isLooping = true
                        if (isPlaying) start()
                    }
                    setOnCompletionListener {
                        // Restart or stop
                    }
                }
            },
            update = { view ->
                if (isPlaying) {
                    if (!view.isPlaying) view.start()
                } else {
                    if (view.isPlaying) view.pause()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        )

        // Overlay Tap to Play/Pause
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { isPlaying = !isPlaying }
        )

        if (!isPlaying) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Paused",
                tint = Color.White,
                modifier = Modifier
                    .size(56.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(28.dp))
                    .padding(12.dp)
            )
        }
    }
}

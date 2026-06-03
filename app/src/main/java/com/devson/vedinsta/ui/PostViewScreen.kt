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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.absoluteValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.CachePolicy
import com.devson.vedinsta.database.DownloadedPost
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

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
    val fav = isFavorite(post.postId)
    
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

    Scaffold(
        topBar = {
            VedInstaTopAppBar(
                title = "@${post.username}",
                titleContent = null,
                showBackButton = true,
                onBackClick = onBackClick,
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
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding()) // Pad only for the TopAppBar
                .background(MaterialTheme.colorScheme.background)
                .blur(if (showMoreOptionsSheet) 12.dp else 0.dp)
        ) {
            // Media Viewport
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black), // Keeps the media viewport region black for immersion
                contentAlignment = Alignment.Center
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val file = mediaFiles[page]
                    val isVideo = file.extension.lowercase() in listOf("mp4", "mov", "avi")

                    val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
                    val fraction = 1f - pageOffset.coerceIn(0f, 1f)
                    val scale = 0.95f + 0.05f * fraction

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                this.alpha = fraction
                                scaleX = scale
                                scaleY = scale
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isVideo) {
                            VideoPlayer(file = file)
                        } else {
                            // Blurred Background Image to cover black letterbox/pillarbox bars
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(file)
                                    .diskCachePolicy(CachePolicy.DISABLED)
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .blur(24.dp),
                                contentScale = ContentScale.Crop
                            )
                            // Dimming overlay to ensure the primary image remains legible
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.45f))
                            )
                            // Main Foreground Image
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(file)
                                    .diskCachePolicy(CachePolicy.DISABLED)
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Post Media",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }

                // Page indicator bubble
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
            }

            // Bottom Section (Instagram style, theme aware and with zero white padding)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(), // Ensures proper edge-to-edge drawing without bottom white padding
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Row of Action Buttons (Like, Share, Description)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Favorite/Like Icon
                            IconButton(onClick = { onToggleFavorite(post.postId) }) {
                                Icon(
                                    imageVector = if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Favorite",
                                    tint = if (fav) Color.Red else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // Share Icon with anchor for dropdown menu
                            Box {
                                IconButton(onClick = { showShareMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Share Options",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = showShareMenu,
                                    onDismissRequest = { showShareMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Share Current Media File") },
                                        onClick = {
                                            showShareMenu = false
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
                                            context.startActivity(Intent.createChooser(shareIntent, "Share Current Media"))
                                        }
                                    )
                                    if (mediaFiles.size > 1) {
                                        DropdownMenuItem(
                                            text = { Text("Share All Media Files") },
                                            onClick = {
                                                showShareMenu = false
                                                val fileUris = ArrayList<Uri>()
                                                mediaFiles.forEach { file ->
                                                    val fileUri = androidx.core.content.FileProvider.getUriForFile(
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

                            // More Options Icon
                            IconButton(onClick = { showMoreOptionsSheet = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreHoriz,
                                    contentDescription = "More Options",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        // Description/Caption indicator button
                        if (!post.caption.isNullOrEmpty()) {
                            TextButton(
                                onClick = { showCaptionSheet = true }
                            ) {
                                Text(
                                    text = "View Description",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // Download Date displayed below buttons
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Downloaded on $dateString",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    // Instagram-like caption summary
                    if (!post.caption.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showCaptionSheet = true }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            val shortCaption = if (post.caption.length > 70) post.caption.take(70) + "... more" else post.caption
                            Text(
                                text = shortCaption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet for Post Description
    if (showCaptionSheet && !post.caption.isNullOrEmpty()) {
        ModalBottomSheet(
            onDismissRequest = { showCaptionSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                // Header with Copy button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Description",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("post_caption", post.caption)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Description copied to clipboard", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Description",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable description block with selection support
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        SelectionContainer {
                            Text(
                                text = post.caption,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Dedicated Hashtags Section
                if (hashtags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Hashtags (${hashtags.size})",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Horizontal scroll of Hashtag chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(hashtags) { tag ->
                            SuggestionChip(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("hashtag", tag)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Copied hashtag: $tag", Toast.LENGTH_SHORT).show()
                                },
                                label = { Text(text = tag, fontWeight = FontWeight.SemiBold) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Button to copy all hashtags
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("hashtags", hashtags.joinToString(" "))
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "All hashtags copied", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Copy All Hashtags")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Modal Bottom Sheet for More Options
    if (showMoreOptionsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMoreOptionsSheet = false },
            sheetState = moreOptionsSheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Post Options",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Option: Open in Instagram
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            showMoreOptionsSheet = false
                            val instagramUri = Uri.parse("https://www.instagram.com/p/${post.postId}/")
                            val intent = Intent(Intent.ACTION_VIEW, instagramUri).apply {
                                setPackage("com.instagram.android")
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // Fallback to web browser
                                val webIntent = Intent(Intent.ACTION_VIEW, instagramUri)
                                context.startActivity(webIntent)
                            }
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Launch,
                        contentDescription = "Open in Instagram",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Open in Instagram",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun VideoPlayer(file: File) {
    var isPlaying by remember { mutableStateOf(false) }
    
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
            modifier = Modifier.fillMaxSize()
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

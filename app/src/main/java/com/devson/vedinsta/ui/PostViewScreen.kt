package com.devson.vedinsta.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.app.Activity
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import androidx.core.view.WindowInsetsCompat
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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

    // Disable device top status bar completely when viewing post
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

    // Filter and collect valid files
    val mediaFiles = remember(post.mediaPaths) {
        post.mediaPaths.map { File(it) }.filter { it.exists() && it.canRead() && it.length() > 0 }
    }

    // Compute aspect ratio from the first media file asynchronously off the main thread
    var mediaAspectRatio by remember(mediaFiles) { mutableStateOf(1f) }
    LaunchedEffect(mediaFiles) {
        withContext(Dispatchers.IO) {
            val firstFile = mediaFiles.firstOrNull()
            val ratio = if (firstFile != null) {
                val ext = firstFile.extension.lowercase()
                if (ext in listOf("mp4", "mov", "avi")) {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(firstFile.absolutePath)
                        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toFloatOrNull() ?: 1f
                        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toFloatOrNull() ?: 1f
                        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                        
                        val actualWidth = if (rotation == 90 || rotation == 270) height else width
                        val actualHeight = if (rotation == 90 || rotation == 270) width else height
                        
                        if (actualWidth > 0 && actualHeight > 0) {
                            (actualWidth / actualHeight).coerceIn(0.5f, 2f)
                        } else 9f / 16f
                    } catch (_: Exception) {
                        9f / 16f
                    } finally {
                        try {
                            retriever.release()
                        } catch (_: Exception) {}
                    }
                } else {
                    try {
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(firstFile.absolutePath, options)
                        if (options.outWidth > 0 && options.outHeight > 0) {
                            (options.outWidth.toFloat() / options.outHeight.toFloat()).coerceIn(0.5f, 2f)
                        } else 1f
                    } catch (_: Exception) { 1f }
                }
            } else {
                1f
            }
            mediaAspectRatio = ratio
        }
    }

    if (mediaFiles.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text("No valid media files found.", color = Color.White)
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { mediaFiles.size })
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

    val blurRadius by animateDpAsState(
        targetValue = if (showMoreOptionsSheet) 12.dp else 0.dp,
        animationSpec = if (showMoreOptionsSheet) tween(durationMillis = 300, easing = FastOutSlowInEasing) else snap(),
        label = "backdropBlur"
    )
    val overlayAlpha by animateFloatAsState(
        targetValue = if (showMoreOptionsSheet) 0.4f else 0f,
        animationSpec = if (showMoreOptionsSheet) tween(durationMillis = 300) else snap(),
        label = "backdropOverlay"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Modifier.blur(blurRadius)
                    } else {
                        Modifier
                    }
                )
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(mediaAspectRatio)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
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
                                            launch { zoomScale.animateTo(1f, tween(200)) }
                                            launch { zoomOffsetX.animateTo(0f, tween(200)) }
                                            launch { zoomOffsetY.animateTo(0f, tween(200)) }
                                            isZoomActive = false
                                        }
                                    }
                                }
                                .graphicsLayer {
                                    alpha = fraction
                                    scaleX = pageScale * zoomScale.value
                                    scaleY = pageScale * zoomScale.value
                                    translationX = zoomOffsetX.value
                                    translationY = zoomOffsetY.value
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isVideo) {
                                VideoPlayer(
                                    file = file,
                                    isCurrentPage = pagerState.currentPage == page
                                )
                            } else {
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

                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(top = 16.dp, start = 16.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                if (mediaFiles.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(mediaFiles.size) { index ->
                            val isActive = pagerState.currentPage == index
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .size(if (isActive) 8.dp else 6.dp)
                                    .background(
                                        color = if (isActive)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        shape = CircleShape
                                    )
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${pagerState.currentPage + 1}/${mediaFiles.size}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .then(
                            if (mediaFiles.size <= 1) Modifier.padding(top = 12.dp) else Modifier
                        )
                ) {
                    val annotatedCaption = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)) {
                            append("@${post.username}")
                        }
                        if (!post.caption.isNullOrEmpty()) {
                            append(" ")
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                                append(post.caption)
                            }
                        }
                    }

                    Text(
                        text = annotatedCaption,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !post.caption.isNullOrEmpty()) {
                                showCaptionSheet = true
                            }
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Downloaded on $dateString",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showDeleteConfirmDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Post",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(
                        onClick = { showMoreOptionsSheet = true },
                        modifier = Modifier
                            .size(48.dp)
                            .border(
                                width = 1.5.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "More Options",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Box {
                        IconButton(onClick = { showShareMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
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
                }
            }
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && overlayAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = overlayAlpha))
            )
        }
    }

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

                if (hashtags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Hashtags (${hashtags.size})",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))

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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            showMoreOptionsSheet = false
                            onToggleFavorite(post.postId)
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Toggle Favorite",
                        tint = if (fav) Color.Red else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = if (fav) "Remove from Favorites" else "Add to Favorites",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            showMoreOptionsSheet = false
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("post_link", "https://www.instagram.com/p/${post.postId}/")
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Link",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Copy Post Link",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

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

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Post") },
            text = { Text("Are you sure you want to delete this post? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeletePost(post)
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun VideoPlayer(file: File, isCurrentPage: Boolean) {
    var isPlaying by remember { mutableStateOf(false) }
    val mediaPlayerRef = remember { mutableStateOf<MediaPlayer?>(null) }
    var isPrepared by remember { mutableStateOf(false) }
 
    // Synchronize playback state with the active page
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

    // Helper to release player asynchronously to avoid main thread blocking
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

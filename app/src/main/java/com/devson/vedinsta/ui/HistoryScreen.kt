package com.devson.vedinsta.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import coil.size.Size
import com.devson.vedinsta.database.DownloadedPost
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    posts: List<DownloadedPost>,
    gridColumnCount: Int,
    onGridColumnsChanged: (Int) -> Unit,
    isListView: Boolean,
    onListViewChanged: (Boolean) -> Unit,
    isFavorite: (String) -> Boolean,
    onToggleFavorite: (String) -> Unit,
    onPostClick: (DownloadedPost) -> Unit,
    onPostLongClick: (DownloadedPost) -> Unit
) {
    if (posts.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "No Downloads Yet",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Your downloaded posts will appear here.",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    fontSize = 14.sp
                )
            }
        }
    } else {
        if (isListView) {
            // LIST VIEW LAYOUT
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(posts, key = { it.postId }) { post ->
                    val fav = isFavorite(post.postId)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .combinedClickable(
                                onClick = { onPostClick(post) },
                                onLongClick = { onPostLongClick(post) }
                            ),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Thumbnail Container
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                        .data(if (post.thumbnailPath.isNotEmpty()) File(post.thumbnailPath) else null)
                                        .size(Size.ORIGINAL)
                                        .videoFrameMillis(0L)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Post Thumbnail",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )

                                // Badge Overlay - Type at top-left corner
                                if (post.hasVideo) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(4.dp)
                                            .size(20.dp)
                                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Video",
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                } else if (post.totalImages > 1) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(4.dp)
                                            .size(20.dp)
                                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Collections,
                                            contentDescription = "Carousel",
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Text Content Column
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "@${post.username}",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                Text(
                                    text = post.caption?.ifEmpty { "No description" } ?: "No description",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Action heart icon button
                            IconButton(
                                onClick = { onToggleFavorite(post.postId) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Favorite",
                                    tint = if (fav) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // GRID VIEW LAYOUT
            var accumulatedZoom by remember { mutableStateOf(1f) }
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumnCount),
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(10.dp)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var hasChangedInThisGesture = false
                            do {
                                val event = awaitPointerEvent()
                                if (event.changes.size >= 2) {
                                    val zoom = event.calculateZoom()
                                    accumulatedZoom *= zoom
                                    if (!hasChangedInThisGesture) {
                                        if (accumulatedZoom > 1.25f) {
                                            val newCols = (gridColumnCount - 1).coerceIn(2, 4)
                                            if (newCols != gridColumnCount) {
                                                onGridColumnsChanged(newCols)
                                            }
                                            hasChangedInThisGesture = true
                                        } else if (accumulatedZoom < 0.75f) {
                                            val newCols = (gridColumnCount + 1).coerceIn(2, 4)
                                            if (newCols != gridColumnCount) {
                                                onGridColumnsChanged(newCols)
                                            }
                                            hasChangedInThisGesture = true
                                        }
                                    }
                                    event.changes.forEach { if (it.pressed) it.consume() }
                                } else {
                                    accumulatedZoom = 1f
                                    hasChangedInThisGesture = false
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    },
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(posts, key = { it.postId }) { post ->
                    val fav = isFavorite(post.postId)
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .combinedClickable(
                                onClick = { onPostClick(post) },
                                onLongClick = { onPostLongClick(post) }
                            ),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Thumbnail
                            AsyncImage(
                                model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                    .data(if (post.thumbnailPath.isNotEmpty()) File(post.thumbnailPath) else null)
                                    .size(Size.ORIGINAL)
                                    .videoFrameMillis(0L)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Post Thumbnail",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )

                            // Hide all overlays & favorite heart when grid column count is 4
                            if (gridColumnCount < 4) {
                                if (post.hasVideo) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(8.dp)
                                            .size(28.dp)
                                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Video",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else if (post.totalImages > 1) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(8.dp)
                                            .size(28.dp)
                                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Collections,
                                            contentDescription = "Carousel",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                // Favorite Button
                                IconButton(
                                    onClick = { onToggleFavorite(post.postId) },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .size(28.dp)
                                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                                ) {
                                    Icon(
                                        imageVector = if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = "Favorite",
                                        tint = if (fav) Color.Red else Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// VIEW SETTING BOTTOM SHEET COMPONENT
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewSettingBottomSheet(
    sheetState: SheetState,
    isListView: Boolean,
    onListViewChanged: (Boolean) -> Unit,
    gridColumnCount: Int,
    onGridColumnsChanged: (Int) -> Unit,
    onDismissRequest: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Layout & View Settings",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Layout Toggles
            Text(
                text = "Layout Mode",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { onListViewChanged(false) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!isListView) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (!isListView) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(imageVector = Icons.Default.GridView, contentDescription = "Grid View", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Grid View", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { onListViewChanged(true) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isListView) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isListView) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ViewList, contentDescription = "List View", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("List View", fontWeight = FontWeight.Bold)
                }
            }

            if (!isListView) {
                Spacer(modifier = Modifier.height(24.dp))

                // Column Count Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Grid Column Count",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "$gridColumnCount Columns",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = gridColumnCount.toFloat(),
                    onValueChange = { onGridColumnsChanged(it.toInt()) },
                    valueRange = 2f..4f,
                    steps = 1,
                    colors = SliderDefaults.colors(
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        thumbColor = MaterialTheme.colorScheme.primary
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "💡 Tip: You can also pinch-to-zoom directly on the grid to change layout columns dynamically.",
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

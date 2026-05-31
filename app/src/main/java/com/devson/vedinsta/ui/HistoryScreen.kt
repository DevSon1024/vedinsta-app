package com.devson.vedinsta.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.devson.vedinsta.database.DownloadedPost
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    posts: List<DownloadedPost>,
    gridColumnCount: Int,
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
        LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumnCount),
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(posts, key = { it.postId }) { post ->
                val fav = remember(post.postId) { isFavorite(post.postId) }
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .combinedClickable(
                            onClick = { onPostClick(post) },
                            onLongClick = { onPostLongClick(post) }
                        ),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Thumbnail
                        AsyncImage(
                            model = if (post.thumbnailPath.isNotEmpty()) File(post.thumbnailPath) else null,
                            contentDescription = "Post Thumbnail",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )

                        // Dark bottom overlay
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.4f)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                                    )
                                )
                        )

                        // Play indicator for videos
                        if (post.hasVideo) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Video",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(28.dp)
                                    .align(Alignment.Center)
                                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                                    .padding(4.dp)
                            )
                        }

                        // Username & Detail indicators at bottom (over dark gradient, so text is white)
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "@${post.username}",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (post.totalImages > 1) {
                                Text(
                                    text = "${post.totalImages} items",
                                    color = Color.LightGray,
                                    fontSize = 9.sp
                                )
                            }
                        }

                        // Favorite heart toggle button
                        IconButton(
                            onClick = { onToggleFavorite(post.postId) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(32.dp)
                                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        ) {
                            Icon(
                                imageVector = if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (fav) Color.Red else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

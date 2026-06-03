package com.devson.vedinsta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.devson.vedinsta.database.NotificationEntity
import java.io.File

@Composable
fun NotificationsScreen(
    notifications: List<NotificationEntity>,
    onNotificationClick: (NotificationEntity) -> Unit,
    onDeleteClick: (Long) -> Unit
) {
    if (notifications.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Text("No Notifications", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = 8.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 80.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(notifications, key = { it.id }) { item ->
                val isProgress = item.type == com.devson.vedinsta.database.NotificationType.DOWNLOAD_PROGRESS
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { if (!isProgress) onNotificationClick(item) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Thumbnail Preview
                        if (!item.thumbnailPath.isNullOrBlank()) {
                            val f = File(item.thumbnailPath)
                            if (f.exists() && f.canRead()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(f)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Preview",
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surface),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            if (isProgress) {
                                val message = item.message
                                val progressPercent: Float
                                val progressText: String

                                if (message.contains("/")) {
                                    val parts = message.split("/")
                                    val completed = parts.getOrNull(0)?.toFloatOrNull() ?: 0f
                                    val total = parts.getOrNull(1)?.toFloatOrNull() ?: 1f
                                    progressPercent = if (total > 0f) completed / total else 0f
                                    progressText = message
                                } else {
                                    val percentVal = message.removeSuffix("%").toIntOrNull() ?: 0
                                    progressPercent = percentVal / 100f
                                    progressText = "$percentVal%"
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    LinearProgressIndicator(
                                        progress = { progressPercent },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surface
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = progressText,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Text(
                                    text = item.message,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                        if (!isProgress) {
                            IconButton(onClick = { onDeleteClick(item.id) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

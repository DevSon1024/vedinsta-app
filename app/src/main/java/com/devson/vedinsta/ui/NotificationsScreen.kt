package com.devson.vedinsta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
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
import com.devson.vedinsta.viewmodel.NotificationViewModel
import com.devson.vedinsta.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    notifications: List<NotificationEntity>,
    onNotificationClick: (NotificationEntity) -> Unit,
    onDeleteClick: (Long) -> Unit,
    settingsViewModel: SettingsViewModel,
    notificationViewModel: NotificationViewModel
) {
    var showSettingsSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    // Read the current keep limit value from settings
    var maxNotificationsLimit by remember { mutableStateOf(settingsViewModel.maxNotificationsLimit) }

    // Temporary states to hold user selections in sheet before dismissal
    var tempIsUnrestricted by remember { mutableStateOf(maxNotificationsLimit == 0) }
    var tempSliderValue by remember { mutableStateOf(if (maxNotificationsLimit > 0) maxNotificationsLimit.toFloat() else 50f) }

    LaunchedEffect(showSettingsSheet) {
        if (showSettingsSheet) {
            val limit = settingsViewModel.maxNotificationsLimit
            tempIsUnrestricted = (limit == 0)
            tempSliderValue = if (limit > 0) limit.toFloat() else 50f
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Accessibility Header Row for settings configuration
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Keep Limit & Cleanup Settings",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            IconButton(onClick = { showSettingsSheet = true }) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Notification Settings",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (notifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("No Notifications", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = 4.dp,
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 60.dp
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
                            // ANR FIX: Pass the path as a String - NOT a File - so Coil skips
                            // the FileKeyer disk-stat call that happens on the main thread with File objects.
                            // The entire ImageRequest is hoisted into remember() to prevent
                            // re-allocation on every recomposition.
                            if (!item.thumbnailPath.isNullOrBlank()) {
                                val context = LocalContext.current
                                val imageRequest = remember(item.thumbnailPath) {
                                    ImageRequest.Builder(context)
                                        .data(item.thumbnailPath)
                                        .size(100, 100)
                                        .crossfade(true)
                                        .memoryCacheKey(item.thumbnailPath)
                                        .diskCacheKey(item.thumbnailPath)
                                        .error(com.devson.vedinsta.R.drawable.ic_error)
                                        .fallback(com.devson.vedinsta.R.drawable.ic_error)
                                        .build()
                                }
                                AsyncImage(
                                    model = imageRequest,
                                    contentDescription = "Preview",
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surface),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(12.dp))
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

    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                val finalLimit = if (tempIsUnrestricted) 0 else tempSliderValue.toInt()
                settingsViewModel.maxNotificationsLimit = finalLimit
                maxNotificationsLimit = finalLimit
                notificationViewModel.pruneNotifications(finalLimit)
                showSettingsSheet = false
            },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Notification Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Unrestricted Keep Limit",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Keep all notifications history indefinitely",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = tempIsUnrestricted,
                        onCheckedChange = { checked ->
                            tempIsUnrestricted = checked
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Maximum Keep Count: ${tempSliderValue.toInt()}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (tempIsUnrestricted) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = tempSliderValue,
                        onValueChange = { valRounded ->
                            tempSliderValue = valRounded
                        },
                        valueRange = 10f..200f,
                        steps = 18,
                        enabled = !tempIsUnrestricted,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }

                // Show Warning Card when user restricts limit below current notifications count
                val currentCount = notifications.size
                val limitValue = tempSliderValue.toInt()
                if (!tempIsUnrestricted && limitValue < currentCount) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Warning",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Warning: Choosing a limit of $limitValue (which is less than the current $currentCount notifications) will immediately delete all older notifications except for the latest $limitValue entries. Downloaded media files are safe and will not be deleted.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

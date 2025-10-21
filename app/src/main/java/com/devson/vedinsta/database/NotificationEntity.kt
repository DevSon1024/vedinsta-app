package com.devson.vedinsta.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val message: String,
    val type: NotificationType,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val postId: String? = null,
    val postUrl: String? = null,
    val filePaths: String? = null, // JSON string of file paths
    val thumbnailPath: String? = null,
    val priority: NotificationPriority = NotificationPriority.NORMAL
)

enum class NotificationType {
    DOWNLOAD_STARTED,
    DOWNLOAD_COMPLETED,
    DOWNLOAD_FAILED,
    DOWNLOAD_PROGRESS,
    SYSTEM_INFO
}

enum class NotificationPriority {
    LOW,
    NORMAL,
    HIGH
}
package com.devson.vedinsta.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloaded_posts")
data class DownloadedPost(
    @PrimaryKey
    val postId: String,           // Instagram post ID
    val postUrl: String,          // Original Instagram URL
    val thumbnailPath: String,    // Path to first downloaded image (thumbnail)
    val totalImages: Int,         // Total number of images in the post
    val downloadDate: Long,       // Timestamp when downloaded
    val hasVideo: Boolean = false // Whether post contains video
)

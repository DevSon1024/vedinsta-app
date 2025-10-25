package com.devson.vedinsta

import com.devson.vedinsta.database.DownloadedPost
import java.util.UUID

// Wrapper for grid items to support both real posts and downloading placeholders
data class GridPostItem(
    val post: DownloadedPost?,
    val isDownloading: Boolean,
    val tempId: String? = null, // used to reconcile replacement after DB save
    val downloadProgress: Int = 0, // Progress (0-100, -1 for indeterminate)
    val workId: UUID? = null // Associated WorkManager ID
) {
    // Determine the unique key for DiffUtil comparison
    val uniqueKey: String
        get() = post?.postId ?: tempId ?: "invalid_${hashCode()}" // Fallback if both are null

    // Override equals and hashCode based on the unique key for stable DiffUtil behavior
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as GridPostItem
        return uniqueKey == other.uniqueKey
    }

    override fun hashCode(): Int {
        return uniqueKey.hashCode()
    }
}
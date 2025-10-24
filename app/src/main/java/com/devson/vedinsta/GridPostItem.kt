package com.devson.vedinsta

import com.devson.vedinsta.database.DownloadedPost

// Wrapper for grid items to support both real posts and downloading placeholders
data class GridPostItem(
    val post: DownloadedPost?,
    val isDownloading: Boolean,
    val tempId: String? = null // used to reconcile replacement after DB save
)

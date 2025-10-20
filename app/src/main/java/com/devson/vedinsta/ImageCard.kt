package com.devson.vedinsta

data class ImageCard(
    val url: String,
    val type: String, // "image" or "video"
    var isSelected: Boolean = false
)
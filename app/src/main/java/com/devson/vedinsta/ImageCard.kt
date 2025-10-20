// src/main/java/com/devson/vedinsta/ImageCard.kt
package com.devson.vedinsta

data class ImageCard(
    val url: String,
    val type: String, // "image" or "video"
    val username: String, // <-- ADD THIS
    var isSelected: Boolean = false
)
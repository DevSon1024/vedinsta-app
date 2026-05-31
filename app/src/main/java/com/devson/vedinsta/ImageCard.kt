package com.devson.vedinsta

data class ImageCard(
    val url: String,
    val type: String,
    val username: String,
    var isSelected: Boolean = false
)
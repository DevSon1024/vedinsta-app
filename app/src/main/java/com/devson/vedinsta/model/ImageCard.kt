package com.devson.vedinsta.model

import androidx.compose.runtime.Stable

@Stable
data class ImageCard(
    val url: String,
    val type: String,
    val username: String,
    var isSelected: Boolean = false
)
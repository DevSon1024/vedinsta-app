package com.devson.vedinsta.model

data class ImageCard(
    val url: String,
    val type: String,
    val username: String,
    var isSelected: Boolean = false
)
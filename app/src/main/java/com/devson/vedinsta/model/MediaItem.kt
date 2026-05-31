package com.devson.vedinsta.model

import java.io.Serializable

data class MediaItem(
    val url: String,
    val type: String,
    val index: Int,
    var isSelected: Boolean = false
) : Serializable

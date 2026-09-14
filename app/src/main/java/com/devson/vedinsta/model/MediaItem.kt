package com.devson.vedinsta.model

import androidx.compose.runtime.Stable
import java.io.Serializable

@Stable
data class MediaItem(
    val url: String,
    val type: String,
    val index: Int,
    var isSelected: Boolean = false
) : Serializable

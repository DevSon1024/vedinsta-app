package com.devson.vedinsta.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class QualityOption(
    @SerializedName("url") val url: String? = null,
    @SerializedName("width") val width: Int? = null,
    @SerializedName("height") val height: Int? = null
) : Serializable

data class MediaResult(
    @SerializedName("url") val url: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("width") val width: Int? = null,
    @SerializedName("height") val height: Int? = null,
    @SerializedName("index") val index: Int? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerializedName("thumbnail_qualities") val thumbnailQualities: List<QualityOption>? = null,
    @SerializedName("qualities") val qualities: List<QualityOption>? = null
) : Serializable

data class ExtractedPost(
    val mediaList: List<MediaResult>,
    val username: String,
    val caption: String?,
    val postId: String
) : Serializable


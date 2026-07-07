package com.devson.vedinsta.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class QualityOption(
    @SerializedName("url") val url: String? = null,
    @SerializedName("width") val width: Int? = null,
    @SerializedName("height") val height: Int? = null
) : Serializable

data class MediaVariant(
    @SerializedName("url") val url: String,
    @SerializedName("resolution_label") val resolutionLabel: String
) : Serializable

data class ExtractedMediaNode(
    @SerializedName("thumbnail_url") val thumbnailUrl: String,
    @SerializedName("download_variants") val downloadVariants: List<MediaVariant> = emptyList(),
    @SerializedName("download_urls") val downloadUrls: List<String> = emptyList(),
    @SerializedName("url") val url: String? = null, // for backward compatibility
    @SerializedName("type") val type: String? = null,
    @SerializedName("width") val width: Int? = null,
    @SerializedName("height") val height: Int? = null,
    @SerializedName("index") val index: Int? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("thumbnail_qualities") val thumbnailQualities: List<QualityOption>? = null,
    @SerializedName("qualities") val qualities: List<QualityOption>? = null
) : Serializable

data class ExtractedPost(
    val mediaList: List<ExtractedMediaNode>,
    val username: String,
    val caption: String?,
    val postId: String
) : Serializable

data class InstagramResponse(
    @SerializedName("status") val status: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("caption") val caption: String? = null,
    @SerializedName("shortcode") val shortcode: String? = null,
    @SerializedName("media") val media: List<ExtractedMediaNode>? = null
) : Serializable

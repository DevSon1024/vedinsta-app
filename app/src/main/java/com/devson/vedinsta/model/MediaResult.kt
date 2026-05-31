package com.devson.vedinsta.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class MediaResult(
    @SerializedName("url") val url: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("width") val width: Int? = null,
    @SerializedName("height") val height: Int? = null,
    @SerializedName("index") val index: Int? = null,
    @SerializedName("error") val error: String? = null
) : Serializable

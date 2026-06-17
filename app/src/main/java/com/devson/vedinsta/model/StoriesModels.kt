package com.devson.vedinsta.model

import java.io.Serializable

data class StoryTrayItem(
    val userId: String,
    val username: String,
    val profilePicUrl: String,
    val isSeen: Boolean,
    val latestReelMedia: Long,
    val seen: Long
) : Serializable

data class StoryTrayResponse(
    val status: String,
    val tray: List<StoryTrayItem>? = null,
    val message: String? = null
) : Serializable

data class UserStoryResponse(
    val status: String,
    val username: String? = null,
    val media: List<MediaResult>? = null,
    val message: String? = null
) : Serializable

data class UserInfoResponse(
    val status: String,
    val userId: String? = null,
    val username: String? = null,
    val profilePicUrl: String? = null,
    val message: String? = null
) : Serializable

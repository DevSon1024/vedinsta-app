package com.devson.vedinsta.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "cached_story_trays")
data class CachedStoryTray(
    @PrimaryKey
    val userId: String,
    val username: String,
    val profilePicUrl: String,
    val isSeen: Boolean,
    val latestReelMedia: Long,
    val seen: Long,
    val expiryTimestamp: Long
) : Serializable

package com.devson.vedinsta.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_accounts")
data class FavoriteAccountEntity(
    @PrimaryKey
    val username: String,
    val profilePicUrl: String,
    val displayName: String,
    val addedAt: Long
)

@Entity(tableName = "cached_stories")
data class CachedStoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val usernameFk: String,
    val mediaUrl: String,
    val isVideo: Boolean,
    val expiresAt: Long,
    val isViewed: Boolean = false
)

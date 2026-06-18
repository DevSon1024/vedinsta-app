package com.devson.vedinsta.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_accounts")
data class FavoriteAccountEntity(
    @PrimaryKey
    val username: String,
    val profilePicUrl: String,
    val displayName: String,
    val addedAt: Long,
    val hasActiveStory: Boolean? = null,
    val lastStatusCheck: Long? = null
)

@Entity(tableName = "cached_stories")
data class CachedStoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @androidx.room.ColumnInfo(name = "username_fk")
    val usernameFk: String,
    @androidx.room.ColumnInfo(name = "local_file_path")
    val localFilePath: String,
    val isVideo: Boolean,
    val expiresAt: Long,
    @androidx.room.ColumnInfo(name = "is_viewed")
    val isViewed: Boolean = false
)

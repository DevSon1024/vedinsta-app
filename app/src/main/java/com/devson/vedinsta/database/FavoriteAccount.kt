package com.devson.vedinsta.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.compose.runtime.Immutable
import java.io.Serializable

@Immutable
@Entity(tableName = "favorite_accounts")
data class FavoriteAccount(
    @PrimaryKey
    val username: String,
    val userId: String,
    val profilePicUrl: String
) : Serializable

package com.devson.vedinsta.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity(tableName = "downloaded_posts")
@TypeConverters(Converters::class)
data class DownloadedPost(
    @PrimaryKey
    val postId: String,
    val postUrl: String,
    val thumbnailPath: String,
    val totalImages: Int,
    val downloadDate: Long,
    val hasVideo: Boolean = false,
    val username: String = "unknown",
    val caption: String? = null,
    val mediaPaths: List<String> = emptyList() // Store all file paths for this post
)

class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return Gson().toJson(value)
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        val listType = object : TypeToken<List<String>>() {}.type
        return Gson().fromJson(value, listType)
    }
}

package com.devson.vedinsta.database

import android.content.Context
import android.os.Environment
import java.io.File
import java.util.UUID

class PostMediaManager {

    companion object {
        private const val VEDINSTA_FOLDER = "VedInsta"
        private const val IMAGES_FOLDER = "Images"
        private const val VIDEOS_FOLDER = "Videos"

        fun getImageDirectory(): File {
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            return File(picturesDir, "$VEDINSTA_FOLDER/$IMAGES_FOLDER").apply {
                if (!exists()) mkdirs()
            }
        }

        fun getVideoDirectory(): File {
            val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            return File(moviesDir, "$VEDINSTA_FOLDER/$VIDEOS_FOLDER").apply {
                if (!exists()) mkdirs()
            }
        }

        fun generateUniqueFileName(username: String, mediaType: String, timestamp: Long = System.currentTimeMillis()): String {
            val extension = if (mediaType.lowercase() == "video") "mp4" else "jpg"
            val uniqueId = UUID.randomUUID().toString().substring(0, 8)
            return "${username}_${timestamp}_${uniqueId}.$extension"
        }

        fun deleteMediaFiles(mediaPaths: List<String>) {
            mediaPaths.forEach { path ->
                try {
                    File(path).delete()
                } catch (e: Exception) {
                    // Handle deletion errors silently
                }
            }
        }

        fun getPostMediaPaths(postId: String, allPosts: List<DownloadedPost>): List<String> {
            return allPosts.find { it.postId == postId }?.mediaPaths ?: emptyList()
        }
    }
}

package com.devson.vedinsta.ui

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object ThumbnailHelper {
    private const val TAG = "ThumbnailHelper"

    /**
     * Checks if the file path points to a video. If so, extracts the first frame
     * and saves it as a static JPEG image in the cache directory, returning the path of the JPEG.
     * Otherwise, returns the original path.
     */
    fun getSafeThumbnailPath(context: Context, filePath: String): String {
        if (filePath.isBlank()) return filePath

        val file = File(filePath)
        if (!file.exists()) return filePath

        val ext = file.extension.lowercase()
        val isVideo = ext in listOf("mp4", "mov", "avi", "mkv", "webm")

        if (!isVideo) {
            return filePath
        }

        try {
            val thumbsDir = File(context.cacheDir, "post_thumbnails")
            if (!thumbsDir.exists()) {
                thumbsDir.mkdirs()
            }

            // Create a unique name for this video's thumbnail
            val thumbFile = File(thumbsDir, "${file.nameWithoutExtension}_thumb.jpg")
            if (thumbFile.exists() && thumbFile.length() > 0) {
                return thumbFile.absolutePath
            }

            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(filePath)
                // Retrieve the frame at 0 microseconds (first frame)
                val bitmap = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (bitmap != null) {
                    FileOutputStream(thumbFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    }
                    Log.d(TAG, "Successfully generated video thumbnail: ${thumbFile.absolutePath}")
                    return thumbFile.absolutePath
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to extract video frame from: $filePath", e)
            } finally {
                try {
                    retriever.release()
                } catch (e: Exception) {
                    // Ignored
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in getSafeThumbnailPath for $filePath", e)
        }

        return filePath
    }
}

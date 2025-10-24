package com.devson.vedinsta

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.media.MediaScannerConnection
import com.devson.vedinsta.notification.VedInstaNotificationManager
import com.devson.vedinsta.database.PostMediaManager
import com.devson.vedinsta.database.AppDatabase
import com.devson.vedinsta.database.DownloadedPost

class VedInstaApplication : Application() {

    lateinit var settingsManager: SettingsManager

    companion object {
        private const val TAG = "VedInstaApplication"
    }

    override fun onCreate() {
        super.onCreate()
        settingsManager = SettingsManager(this)
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
    }

    /**
     * Downloads files to centralized folders and returns list of successfully downloaded file paths
     */
    suspend fun downloadFiles(context: Context, filesToDownload: List<ImageCard>, postId: String?): List<String> {
        Log.d(TAG, "=== downloadFiles called ===")
        Log.d(TAG, "Files to download: ${filesToDownload.size}")
        Log.d(TAG, "PostId: $postId")

        val downloadedFiles = mutableListOf<String>()
        var downloadedCount = 0

        for ((index, media) in filesToDownload.withIndex()) {
            try {
                Log.d(TAG, "Processing media $index: ${media.url}, type: ${media.type}")

                val directoryUriString = if (media.type == "video") {
                    settingsManager.videoDirectoryUri
                } else {
                    settingsManager.imageDirectoryUri
                }

                val filePath = if (directoryUriString != null) {
                    Log.d(TAG, "Using SAF download for media $index")
                    downloadWithSAFCentralized(context, media, Uri.parse(directoryUriString))
                } else {
                    Log.d(TAG, "Using default download for media $index")
                    downloadWithDownloadManagerCentralized(context, media)
                }

                Log.d(TAG, "Download result for media $index: $filePath")

                if (filePath != null) {
                    downloadedFiles.add(filePath)
                    downloadedCount++
                    Log.d(TAG, "Successfully downloaded file $index: $filePath")
                } else {
                    Log.w(TAG, "Failed to download file $index")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading media $index", e)
            }
        }

        withContext(Dispatchers.Main) {
            Toast.makeText(
                context,
                "Downloaded $downloadedCount / ${filesToDownload.size} files.",
                Toast.LENGTH_LONG
            ).show()
        }

        Log.d(TAG, "=== downloadFiles completed ===")
        Log.d(TAG, "Total downloaded files: ${downloadedFiles.size}")
        downloadedFiles.forEachIndexed { index, path ->
            Log.d(TAG, "Final file $index: $path")
        }

        return downloadedFiles
    }

    /**
     * Single-file download used for auto-download of single posts
     * FIXED VERSION with proper database saving and upsert logic
     */
    suspend fun downloadSingleFile(
        context: Context,
        mediaUrl: String,
        mediaType: String,
        username: String,
        postId: String?
    ): List<String> {
        Log.d(TAG, "=== downloadSingleFile called ===")
        Log.d(TAG, "Media URL: $mediaUrl")
        Log.d(TAG, "Media Type: $mediaType")
        Log.d(TAG, "Username: $username")
        Log.d(TAG, "PostId: $postId")

        return withContext(Dispatchers.IO) {
            val downloadedFiles = mutableListOf<String>()
            val notificationManager = VedInstaNotificationManager.getInstance(context)

            try {
                val fileName = PostMediaManager.generateUniqueFileName(username, mediaType)
                Log.d(TAG, "Generated filename: $fileName")

                // Show initial notification
                val notificationId = notificationManager.showDownloadStarted(fileName)

                // Determine save directory
                val directoryUriString = if (mediaType.lowercase() == "video") {
                    settingsManager.videoDirectoryUri
                } else {
                    settingsManager.imageDirectoryUri
                }

                // Download with progress tracking
                val savedPath = if (directoryUriString != null) {
                    Log.d(TAG, "Using SAF for single file")
                    downloadSingleFileWithSAFCentralized(context, mediaUrl, fileName, Uri.parse(directoryUriString), notificationManager, notificationId)
                } else {
                    Log.d(TAG, "Using default for single file")
                    downloadSingleFileWithDefaultCentralized(context, mediaUrl, fileName, mediaType, notificationManager, notificationId)
                }

                Log.d(TAG, "Single file download result: $savedPath")

                if (savedPath != null) {
                    downloadedFiles.add(savedPath)

                    // Show completion notification
                    notificationManager.cancelDownloadNotification(notificationId)
                    notificationManager.showDownloadCompleted(fileName, 1)

                    // Add to MediaStore for gallery visibility
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(savedPath),
                        arrayOf(if (mediaType.lowercase() == "video") "video/mp4" else "image/jpeg"),
                        null
                    )

                    // *** CRITICAL: Save to database with UPSERT logic ***
                    // *** CRITICAL: Save to database with ENHANCED UPSERT logic ***
                    if (postId != null) {
                        Log.d(TAG, "=== SAVING TO DATABASE ===")
                        Log.d(TAG, "PostId for DB: $postId")
                        Log.d(TAG, "Downloaded file for DB: $savedPath")

                        try {
                            val db = AppDatabase.getDatabase(context.applicationContext)

                            // *** FIX: Check if post already exists ***
                            val existingPost = db.downloadedPostDao().getPostById(postId)
                            Log.d(TAG, "Existing post in DB: $existingPost")

                            if (existingPost != null) {
                                if (existingPost.mediaPaths.isEmpty()) {
                                    // If existing post has empty mediaPaths, update with our file
                                    Log.d(TAG, "Updating existing post (empty mediaPaths) with: [$savedPath]")
                                    db.downloadedPostDao().updateMediaPaths(postId, listOf(savedPath))
                                } else {
                                    // Merge with existing paths
                                    val updatedPaths = (existingPost.mediaPaths + savedPath).distinct()
                                    Log.d(TAG, "Merging with existing paths: $updatedPaths")
                                    db.downloadedPostDao().updateMediaPaths(postId, updatedPaths)
                                }
                            } else {
                                // Create new post with IGNORE strategy (won't overwrite if another component creates it)
                                val downloadedPost = DownloadedPost(
                                    postId = postId,
                                    postUrl = mediaUrl,
                                    thumbnailPath = savedPath,
                                    totalImages = 1,
                                    downloadDate = System.currentTimeMillis(),
                                    hasVideo = mediaType.lowercase() == "video",
                                    username = username,
                                    caption = null,
                                    mediaPaths = listOf(savedPath)
                                )

                                Log.d(TAG, "Inserting new post (with IGNORE): $downloadedPost")
                                db.downloadedPostDao().insert(downloadedPost) // Uses IGNORE strategy

                                // If insert was ignored, update the mediaPaths
                                val insertedPost = db.downloadedPostDao().getPostById(postId)
                                if (insertedPost != null && insertedPost.mediaPaths.isEmpty()) {
                                    Log.d(TAG, "Insert was ignored, updating mediaPaths manually")
                                    db.downloadedPostDao().updateMediaPaths(postId, listOf(savedPath))
                                }
                            }

                            Log.d(TAG, "Database operation completed")

                            // Verify the save
                            val savedPost = db.downloadedPostDao().getPostById(postId)
                            Log.d(TAG, "Final verification - Retrieved post: $savedPost")
                            if (savedPost != null) {
                                Log.d(TAG, "Final verification SUCCESS - MediaPaths: ${savedPost.mediaPaths}")
                                Log.d(TAG, "Final verification SUCCESS - MediaPaths count: ${savedPost.mediaPaths.size}")
                            } else {
                                Log.e(TAG, "Final verification FAILED - Post not found after operation!")
                            }

                        } catch (e: Exception) {
                            Log.e(TAG, "DATABASE SAVE ERROR", e)
                        }
                    } else {
                        Log.w(TAG, "PostId is null - cannot save to database")
                    }

                    Log.d(TAG, "Single file download completed successfully")
                } else {
                    notificationManager.showDownloadError(fileName, "Download failed")
                    Log.w(TAG, "Single file download failed")
                }

            } catch (e: Exception) {
                notificationManager.showDownloadError("Download", e.message ?: "Unknown error")
                Log.e(TAG, "Single file download error", e)
            }

            Log.d(TAG, "=== downloadSingleFile completed ===")
            Log.d(TAG, "Downloaded files: $downloadedFiles")
            downloadedFiles
        }
    }

    /**
     * Download using Storage Access Framework (SAF) to centralized folders
     */
    private suspend fun downloadWithSAFCentralized(context: Context, media: ImageCard, directoryUri: Uri): String? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "SAF download starting for: ${media.url}")

                val directory = DocumentFile.fromTreeUri(context, directoryUri) ?: return@withContext null

                val mimeType = if (media.type == "video") "video/mp4" else "image/jpeg"
                val fileName = PostMediaManager.generateUniqueFileName(media.username, media.type)

                Log.d(TAG, "SAF creating file: $fileName")

                val newFile = directory.createFile(mimeType, fileName)
                newFile?.uri?.let { fileUri ->
                    context.contentResolver.openOutputStream(fileUri)?.use { outputStream ->
                        URL(media.url).openStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }

                    val resultPath = fileUri.toString()
                    Log.d(TAG, "SAF download completed: $resultPath")
                    return@withContext resultPath
                }

                Log.w(TAG, "SAF download failed: Could not create file")
                null
            } catch (e: Exception) {
                Log.e(TAG, "SAF download error", e)
                null
            }
        }
    }

    /**
     * Download using DownloadManager to centralized folders
     */
    private suspend fun downloadWithDownloadManagerCentralized(context: Context, media: ImageCard): String? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Default download starting for: ${media.url}")

                val fileName = PostMediaManager.generateUniqueFileName(media.username, media.type)
                Log.d(TAG, "Generated filename: $fileName")

                val targetDirectory = if (media.type == "video") {
                    PostMediaManager.getVideoDirectory()
                } else {
                    PostMediaManager.getImageDirectory()
                }

                Log.d(TAG, "Target directory: ${targetDirectory.absolutePath}")
                Log.d(TAG, "Directory exists: ${targetDirectory.exists()}")

                val file = File(targetDirectory, fileName)
                Log.d(TAG, "Target file: ${file.absolutePath}")

                // Download file directly
                URL(media.url).openStream().use { inputStream ->
                    FileOutputStream(file).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                Log.d(TAG, "File downloaded successfully")
                Log.d(TAG, "File exists: ${file.exists()}")
                Log.d(TAG, "File size: ${file.length()}")

                // Add to MediaStore for gallery visibility
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(file.absolutePath),
                    arrayOf(if (media.type == "video") "video/mp4" else "image/jpeg"),
                    null
                )

                Log.d(TAG, "MediaScan completed for: ${file.absolutePath}")

                return@withContext file.absolutePath
            } catch (e: Exception) {
                Log.e(TAG, "Default download error", e)
                null
            }
        }
    }

    private suspend fun downloadSingleFileWithSAFCentralized(
        context: Context,
        mediaUrl: String,
        fileName: String,
        directoryUri: Uri,
        notificationManager: VedInstaNotificationManager,
        notificationId: Int
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                val directory = DocumentFile.fromTreeUri(context, directoryUri) ?: return@withContext null
                val mimeType = if (fileName.endsWith(".mp4")) "video/mp4" else "image/jpeg"

                val newFile = directory.createFile(mimeType, fileName)
                newFile?.uri?.let { fileUri ->
                    context.contentResolver.openOutputStream(fileUri)?.use { outputStream ->
                        downloadWithProgress(mediaUrl, outputStream, notificationManager, notificationId, fileName)
                    }
                    return@withContext fileUri.toString()
                }
                null
            } catch (e: Exception) {
                Log.e(TAG, "SAF single file error", e)
                null
            }
        }
    }

    private suspend fun downloadSingleFileWithDefaultCentralized(
        context: Context,
        mediaUrl: String,
        fileName: String,
        mediaType: String,
        notificationManager: VedInstaNotificationManager,
        notificationId: Int
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                val targetDirectory = if (mediaType.lowercase() == "video") {
                    PostMediaManager.getVideoDirectory()
                } else {
                    PostMediaManager.getImageDirectory()
                }

                val file = File(targetDirectory, fileName)

                // Download with progress tracking
                FileOutputStream(file).use { outputStream ->
                    downloadWithProgress(mediaUrl, outputStream, notificationManager, notificationId, fileName)
                }

                return@withContext file.absolutePath
            } catch (e: Exception) {
                Log.e(TAG, "Default single file error", e)
                null
            }
        }
    }

    private suspend fun downloadWithProgress(
        url: String,
        outputStream: java.io.OutputStream,
        notificationManager: VedInstaNotificationManager,
        notificationId: Int,
        fileName: String
    ) {
        withContext(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection()
                connection.connectTimeout = 15000
                connection.readTimeout = 30000

                val contentLength = connection.contentLength
                val inputStream = connection.getInputStream()

                val buffer = ByteArray(8192)
                var totalBytesRead = 0L
                var bytesRead: Int
                var lastProgressUpdate = 0

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead

                    val progress = if (contentLength > 0) {
                        ((totalBytesRead * 100) / contentLength).toInt()
                    } else {
                        -1
                    }

                    if (progress != lastProgressUpdate && (progress - lastProgressUpdate >= 5 || totalBytesRead % 102400 == 0L)) {
                        notificationManager.updateDownloadProgress(notificationId, fileName, progress)
                        lastProgressUpdate = progress
                    }
                }

                inputStream.close()
            } catch (e: Exception) {
                throw e
            }
        }
    }

    /**
     * Get app's private directory for caching thumbnails
     */
    fun getThumbnailCacheDir(context: Context): File {
        val thumbnailDir = File(context.cacheDir, "thumbnails")
        if (!thumbnailDir.exists()) {
            thumbnailDir.mkdirs()
        }
        return thumbnailDir
    }

    /**
     * Save thumbnail for quick access in the home screen
     */
    suspend fun saveThumbnail(context: Context, imageUrl: String, postId: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val thumbnailDir = getThumbnailCacheDir(context)
                val thumbnailFile = File(thumbnailDir, "$postId.jpg")

                URL(imageUrl).openStream().use { inputStream ->
                    FileOutputStream(thumbnailFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                thumbnailFile.absolutePath
            } catch (e: Exception) {
                Log.e(TAG, "Thumbnail save error", e)
                null
            }
        }
    }

    /**
     * Clean up old thumbnail cache files
     */
    fun cleanThumbnailCache(context: Context, maxAgeMillis: Long = 7 * 24 * 60 * 60 * 1000L) {
        try {
            val thumbnailDir = getThumbnailCacheDir(context)
            val currentTime = System.currentTimeMillis()

            thumbnailDir.listFiles()?.forEach { file ->
                if (currentTime - file.lastModified() > maxAgeMillis) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Thumbnail cleanup error", e)
        }
    }
}
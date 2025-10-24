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
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import okhttp3.*
import java.util.concurrent.TimeUnit
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
                        // Use the enhanced download method
                        downloadWithSimpleHeaders(mediaUrl, outputStream, notificationManager, notificationId, fileName)
                    }
                    return@withContext fileUri.toString()
                }
                null
            } catch (e: Exception) {
                Log.e(TAG, "SAF download error", e)
                null
            }
        }
    }
    private suspend fun downloadWithSimpleHeaders(
        url: String,
        outputStream: java.io.OutputStream,
        notificationManager: VedInstaNotificationManager,
        notificationId: Int,
        fileName: String
    ) {
        withContext(Dispatchers.IO) {
            val urlConnection = URL(url).openConnection() as HttpURLConnection

            try {
                urlConnection.apply {
                    requestMethod = "GET"
                    connectTimeout = 30000
                    readTimeout = 120000

                    // Essential headers
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36")
                    setRequestProperty("Referer", "https://www.instagram.com/")
                    setRequestProperty("Accept", "*/*")
                }

                urlConnection.connect()

                if (urlConnection.responseCode == HttpURLConnection.HTTP_OK) {
                    val contentLength = urlConnection.contentLength.toLong()

                    urlConnection.inputStream.use { inputStream ->
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
                    }
                } else {
                    throw IOException("HTTP ${urlConnection.responseCode}: ${urlConnection.responseMessage}")
                }

            } finally {
                urlConnection.disconnect()
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
                Log.d(TAG, "Enhanced Instagram download starting")
                Log.d(TAG, "URL: $mediaUrl")
                Log.d(TAG, "File: $fileName")

                val targetDirectory = if (mediaType.lowercase() == "video") {
                    PostMediaManager.getVideoDirectory()
                } else {
                    PostMediaManager.getImageDirectory()
                }

                val file = File(targetDirectory, fileName)
                Log.d(TAG, "Target file path: ${file.absolutePath}")

                // Try multiple download strategies
                var success = false

                // Strategy 1: Enhanced headers with referrer context
                if (!success) {
                    success = downloadWithInstagramContext(mediaUrl, file, notificationManager, notificationId, fileName)
                }

                // Strategy 2: Range request (partial content)
                if (!success) {
                    Log.d(TAG, "Trying range request strategy")
                    success = downloadWithRangeRequest(mediaUrl, file, notificationManager, notificationId, fileName)
                }

                // Strategy 3: Simple request with minimal headers
                if (!success) {
                    Log.d(TAG, "Trying minimal headers strategy")
                    success = downloadWithMinimalHeaders(mediaUrl, file, notificationManager, notificationId, fileName)
                }

                if (success && file.exists() && file.length() > 0) {
                    Log.d(TAG, "File downloaded successfully. Size: ${file.length()} bytes")
                    return@withContext file.absolutePath
                } else {
                    Log.e(TAG, "All download strategies failed")
                    return@withContext null
                }

            } catch (e: Exception) {
                Log.e(TAG, "Enhanced Instagram download error", e)
                return@withContext null
            }
        }
    }

    private suspend fun downloadWithInstagramContext(
        url: String,
        file: File,
        notificationManager: VedInstaNotificationManager,
        notificationId: Int,
        fileName: String
    ): Boolean {
        return try {
            val urlConnection = URL(url).openConnection() as HttpURLConnection

            urlConnection.apply {
                requestMethod = "GET"
                connectTimeout = 30000
                readTimeout = 120000
                doInput = true

                // Instagram-specific headers with mobile context
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36 Instagram 308.0.0.34.113 Android")
                setRequestProperty("Accept", "video/webm,video/ogg,video/*;q=0.9,application/ogg;q=0.7,audio/*;q=0.6,*/*;q=0.5")
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Connection", "keep-alive")
                setRequestProperty("Referer", "https://www.instagram.com/")
                setRequestProperty("Origin", "https://www.instagram.com")
                setRequestProperty("sec-ch-ua", "\"Google Chrome\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"")
                setRequestProperty("sec-ch-ua-mobile", "?1")
                setRequestProperty("sec-ch-ua-platform", "\"Android\"")
                setRequestProperty("Sec-Fetch-Dest", "video")
                setRequestProperty("Sec-Fetch-Mode", "cors")
                setRequestProperty("Sec-Fetch-Site", "cross-site")
            }

            urlConnection.connect()
            val responseCode = urlConnection.responseCode
            Log.d(TAG, "Instagram context download - Response: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                downloadFileContent(urlConnection, file, notificationManager, notificationId, fileName)
                true
            } else {
                Log.w(TAG, "Instagram context download failed: $responseCode")
                urlConnection.disconnect()
                false
            }

        } catch (e: Exception) {
            Log.w(TAG, "Instagram context download error", e)
            false
        }
    }

    /**
     * Download with Range request (partial content strategy)
     */
    private suspend fun downloadWithRangeRequest(
        url: String,
        file: File,
        notificationManager: VedInstaNotificationManager,
        notificationId: Int,
        fileName: String
    ): Boolean {
        return try {
            val urlConnection = URL(url).openConnection() as HttpURLConnection

            urlConnection.apply {
                requestMethod = "GET"
                connectTimeout = 30000
                readTimeout = 120000
                doInput = true

                // Range request headers
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36")
                setRequestProperty("Accept", "*/*")
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("Referer", "https://www.instagram.com/")
                setRequestProperty("Origin", "https://www.instagram.com")
                setRequestProperty("Range", "bytes=0-") // Request all content starting from byte 0
                setRequestProperty("Connection", "keep-alive")
            }

            urlConnection.connect()
            val responseCode = urlConnection.responseCode
            Log.d(TAG, "Range request download - Response: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_PARTIAL) {
                downloadFileContent(urlConnection, file, notificationManager, notificationId, fileName)
                true
            } else {
                Log.w(TAG, "Range request download failed: $responseCode")
                urlConnection.disconnect()
                false
            }

        } catch (e: Exception) {
            Log.w(TAG, "Range request download error", e)
            false
        }
    }

    /**
     * Download with minimal headers as fallback
     */
    private suspend fun downloadWithMinimalHeaders(
        url: String,
        file: File,
        notificationManager: VedInstaNotificationManager,
        notificationId: Int,
        fileName: String
    ): Boolean {
        return try {
            val urlConnection = URL(url).openConnection() as HttpURLConnection

            urlConnection.apply {
                requestMethod = "GET"
                connectTimeout = 45000  // Longer timeout as fallback
                readTimeout = 180000    // 3 minutes
                doInput = true

                // Minimal headers
                setRequestProperty("User-Agent", "Instagram 308.0.0.34.113 Android (31/12; 450dpi; 1080x2392; samsung; SM-G991B; o1s; exynos2100; en_US; 458229237)")
                setRequestProperty("Accept", "*/*")
            }

            urlConnection.connect()
            val responseCode = urlConnection.responseCode
            Log.d(TAG, "Minimal headers download - Response: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                downloadFileContent(urlConnection, file, notificationManager, notificationId, fileName)
                true
            } else {
                Log.w(TAG, "Minimal headers download failed: $responseCode")
                urlConnection.disconnect()
                false
            }

        } catch (e: Exception) {
            Log.w(TAG, "Minimal headers download error", e)
            false
        }
    }

    /**
     * Common method to download file content from connection
     */
    private suspend fun downloadFileContent(
        connection: HttpURLConnection,
        file: File,
        notificationManager: VedInstaNotificationManager,
        notificationId: Int,
        fileName: String
    ) {
        try {
            val contentLength = connection.contentLength.toLong()
            Log.d(TAG, "Content length: $contentLength bytes")

            FileOutputStream(file).use { outputStream ->
                connection.inputStream.use { inputStream ->
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
                            (totalBytesRead / 1024).toInt()
                        }

                        if (progress != lastProgressUpdate && (progress - lastProgressUpdate >= 5 || totalBytesRead % 102400 == 0L)) {
                            notificationManager.updateDownloadProgress(notificationId, fileName, progress)
                            lastProgressUpdate = progress
                        }
                    }
                }
            }

            Log.d(TAG, "File content downloaded successfully")
        } finally {
            connection.disconnect()
        }
    }
}
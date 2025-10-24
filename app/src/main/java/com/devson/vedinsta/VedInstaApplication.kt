package com.devson.vedinsta

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.media.MediaScannerConnection
import com.devson.vedinsta.notification.VedInstaNotificationManager
import com.devson.vedinsta.database.PostMediaManager

class VedInstaApplication : Application() {

    lateinit var settingsManager: SettingsManager

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
        val downloadedFiles = mutableListOf<String>()
        var downloadedCount = 0

        for (media in filesToDownload) {
            try {
                val directoryUriString = if (media.type == "video") {
                    settingsManager.videoDirectoryUri
                } else {
                    settingsManager.imageDirectoryUri
                }

                val filePath = if (directoryUriString != null) {
                    downloadWithSAFCentralized(context, media, Uri.parse(directoryUriString))
                } else {
                    downloadWithDownloadManagerCentralized(context, media)
                }

                if (filePath != null) {
                    downloadedFiles.add(filePath)
                    downloadedCount++
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        withContext(Dispatchers.Main) {
            Toast.makeText(
                context,
                "Downloaded $downloadedCount / ${filesToDownload.size} files.",
                Toast.LENGTH_LONG
            ).show()
        }

        return downloadedFiles
    }

    /**
     * Download using Storage Access Framework (SAF) to centralized folders
     */
    private suspend fun downloadWithSAFCentralized(context: Context, media: ImageCard, directoryUri: Uri): String? {
        return withContext(Dispatchers.IO) {
            try {
                val directory = DocumentFile.fromTreeUri(context, directoryUri) ?: return@withContext null

                val mimeType = if (media.type == "video") "video/mp4" else "image/jpeg"
                val fileName = PostMediaManager.generateUniqueFileName(media.username, media.type)

                val newFile = directory.createFile(mimeType, fileName)
                newFile?.uri?.let { fileUri ->
                    context.contentResolver.openOutputStream(fileUri)?.use { outputStream ->
                        URL(media.url).openStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    return@withContext fileUri.toString()
                }
                null
            } catch (e: Exception) {
                e.printStackTrace()
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
                val fileName = PostMediaManager.generateUniqueFileName(media.username, media.type)

                val targetDirectory = if (media.type == "video") {
                    PostMediaManager.getVideoDirectory()
                } else {
                    PostMediaManager.getImageDirectory()
                }

                val file = File(targetDirectory, fileName)

                // Download file directly
                URL(media.url).openStream().use { inputStream ->
                    FileOutputStream(file).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                // Add to MediaStore for gallery visibility
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(file.absolutePath),
                    arrayOf(if (media.type == "video") "video/mp4" else "image/jpeg"),
                    null
                )

                return@withContext file.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * Single-file download used for auto-download of single posts
     */
    suspend fun downloadSingleFile(
        context: Context,
        mediaUrl: String,
        mediaType: String,
        username: String,
        postId: String?
    ): List<String> {
        return withContext(Dispatchers.IO) {
            val downloadedFiles = mutableListOf<String>()
            val notificationManager = VedInstaNotificationManager.getInstance(context)

            try {
                val fileName = PostMediaManager.generateUniqueFileName(username, mediaType)

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
                    downloadSingleFileWithSAFCentralized(context, mediaUrl, fileName, Uri.parse(directoryUriString), notificationManager, notificationId)
                } else {
                    downloadSingleFileWithDefaultCentralized(context, mediaUrl, fileName, mediaType, notificationManager, notificationId)
                }

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
                } else {
                    notificationManager.showDownloadError(fileName, "Download failed")
                }

            } catch (e: Exception) {
                notificationManager.showDownloadError("Download", e.message ?: "Unknown error")
                e.printStackTrace()
            }

            downloadedFiles
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
                e.printStackTrace()
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
                e.printStackTrace()
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
                e.printStackTrace()
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
            e.printStackTrace()
        }
    }
}

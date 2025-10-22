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
     * Downloads files and returns list of successfully downloaded file paths
     * This is used to save completed downloads to the database
     */
    suspend fun downloadFiles(context: Context, filesToDownload: List<ImageCard>, postId: String?): List<String> {
        val downloadedFiles = mutableListOf<String>()
        var downloadedCount = 0
        val dirName = postId ?: System.currentTimeMillis().toString() // Use postId as directory name

        for (media in filesToDownload) {
            try {
                val directoryUriString = if (media.type == "video") {
                    settingsManager.videoDirectoryUri
                } else {
                    settingsManager.imageDirectoryUri
                }

                val filePath = if (directoryUriString != null) {
                    downloadWithSAF(context, media, Uri.parse(directoryUriString), dirName)
                } else {
                    downloadWithDownloadManager(context, media, dirName)
                }

                if (filePath != null) {
                    downloadedFiles.add(filePath)
                    downloadedCount++
                }
            } catch (e: Exception) {
                // Log error but continue with other downloads
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
     * Download using Storage Access Framework (SAF) and return file path
     */
    private suspend fun downloadWithSAF(context: Context, media: ImageCard, directoryUri: Uri, postDirName: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val directory = DocumentFile.fromTreeUri(context, directoryUri) ?: return@withContext null
                // Create post-specific subdirectory
                val postDir = directory.findFile(postDirName) ?: directory.createDirectory(postDirName) ?: return@withContext null

                val mimeType = if (media.type == "video") "video/mp4" else "image/jpeg"
                val fileExtension = if (media.type == "video") ".mp4" else ".jpg"
                val timestamp = SimpleDateFormat("ddMMyyyyHHmmssSSS", Locale.US).format(Date())
                val fileName = "${media.username}_175$timestamp$fileExtension"

                val newFile = postDir.createFile(mimeType, fileName)
                newFile?.uri?.let { fileUri ->
                    context.contentResolver.openOutputStream(fileUri)?.use { outputStream ->
                        URL(media.url).openStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    // Return the file URI as string path for database storage
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
     * Download using DownloadManager and return file path
     */
    private suspend fun downloadWithDownloadManager(context: Context, media: ImageCard, postDirName: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val fileExtension = if (media.type == "video") ".mp4" else ".jpg"
                val timestamp = SimpleDateFormat("ddMMyyyyHHmmssSSS", Locale.US).format(Date())
                val fileName = "${media.username}_${media.type}_$timestamp$fileExtension"

                // Create VedInsta directory if it doesn't exist
                val vedInstaDir = File(
                    Environment.getExternalStoragePublicDirectory(
                        if (media.type == "video") Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
                    ),
                    "VedInsta"
                )
                // Create post-specific subdirectory
                val postDir = File(vedInstaDir, postDirName)
                if (!postDir.exists()) {
                    postDir.mkdirs()
                }

                val file = File(postDir, fileName)

                // Download file directly instead of using DownloadManager for better control
                URL(media.url).openStream().use { inputStream ->
                    FileOutputStream(file).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                // Notify DownloadManager about the downloaded file (optional, for gallery visibility)
                try {
                    val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    // You can add the file to DownloadManager's database if needed
                } catch (e: Exception) {
                    // Ignore DownloadManager errors, file is already downloaded
                }

                return@withContext file.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * Alternative method using DownloadManager for background downloads
     * Returns estimated file path (may not be immediately available)
     */
    private fun downloadWithDownloadManagerBackground(context: Context, media: ImageCard): String? {
        return try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val uri = Uri.parse(media.url)
            val fileExtension = if (media.type == "video") ".mp4" else ".jpg"
            val timestamp = SimpleDateFormat("ddMMyyyyHHmmssSSS", Locale.US).format(Date())
            val fileName = "${media.username}_175$timestamp$fileExtension"
            val subDirectory = "VedInsta/"

            val request = DownloadManager.Request(uri)
                .setTitle(fileName)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(
                    if (media.type == "video") Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES,
                    subDirectory + fileName
                )

            val downloadId = downloadManager.enqueue(request)

            // Return estimated file path
            val baseDir = Environment.getExternalStoragePublicDirectory(
                if (media.type == "video") Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
            )
            File(baseDir, subDirectory + fileName).absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
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
    // Single-file download used for auto-download of single posts
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
            val dirName = postId ?: System.currentTimeMillis().toString() // Use postId as directory name

            try {
                // Create filename
                val timestamp = SimpleDateFormat("ddMMyyyyHHmmssSSS", Locale.US).format(Date())
                val fileExtension = if (mediaType.lowercase() == "video") ".mp4" else ".jpg"
                val fileName = "${username}_$timestamp$fileExtension"

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
                    downloadSingleFileWithSAF(context, mediaUrl, fileName, Uri.parse(directoryUriString), notificationManager, notificationId, dirName)
                } else {
                    downloadSingleFileWithDefault(context, mediaUrl, fileName, mediaType, notificationManager, notificationId, dirName)
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
                    // Show error notification
                    notificationManager.showDownloadError(fileName, "Download failed")
                }

            } catch (e: Exception) {
                // Show error notification
                notificationManager.showDownloadError("Download", e.message ?: "Unknown error")
                e.printStackTrace()
            }

            downloadedFiles
        }
    }
    private suspend fun downloadSingleFileWithSAF(
        context: Context,
        mediaUrl: String,
        fileName: String,
        directoryUri: Uri,
        notificationManager: VedInstaNotificationManager,
        notificationId: Int,
        postDirName: String
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                val directory = DocumentFile.fromTreeUri(context, directoryUri) ?: return@withContext null
                // Create post-specific subdirectory
                val postDir = directory.findFile(postDirName) ?: directory.createDirectory(postDirName) ?: return@withContext null

                val mimeType = if (fileName.endsWith(".mp4")) "video/mp4" else "image/jpeg"

                val newFile = postDir.createFile(mimeType, fileName)
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

    private suspend fun downloadSingleFileWithDefault(
        context: Context,
        mediaUrl: String,
        fileName: String,
        mediaType: String,
        notificationManager: VedInstaNotificationManager,
        notificationId: Int,
        postDirName: String
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                // Create VedInsta directory if it doesn't exist
                val vedInstaDir = File(
                    Environment.getExternalStoragePublicDirectory(
                        if (mediaType.lowercase() == "video") Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
                    ),
                    "VedInsta"
                )
                // Create post-specific subdirectory
                val postDir = File(vedInstaDir, postDirName)
                if (!postDir.exists()) {
                    postDir.mkdirs()
                }

                val file = File(postDir, fileName)

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

                    // Update progress every 5% or every 100KB to avoid too frequent updates
                    val progress = if (contentLength > 0) {
                        ((totalBytesRead * 100) / contentLength).toInt()
                    } else {
                        -1 // Indeterminate progress
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

    private fun downloadFileToDefault(
        context: Context,
        url: String,
        fileName: String,
        mediaType: String
    ): String? {
        return try {
            val directory = if (mediaType == "video") "Movies/VedInsta" else "Pictures/VedInsta"
            val file = File(Environment.getExternalStorageDirectory(), "$directory/$fileName")
            file.parentFile?.mkdirs()

            val connection = URL(url).openConnection()
            connection.connectTimeout = 10000
            connection.readTimeout = 30000

            val inputStream = connection.getInputStream()
            val outputStream = FileOutputStream(file)

            inputStream.copyTo(outputStream)

            inputStream.close()
            outputStream.close()

            // Add to MediaStore
            MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                arrayOf(if (mediaType == "video") "video/mp4" else "image/jpeg"),
                null
            )

            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Clean up old thumbnail cache files (optional - call periodically)
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
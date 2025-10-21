// src/main/java/com/devson/vedinsta/VedInstaApplication.kt
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
    suspend fun downloadFiles(context: Context, filesToDownload: List<ImageCard>): List<String> {
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
                    downloadWithSAF(context, media, Uri.parse(directoryUriString))
                } else {
                    downloadWithDownloadManager(context, media)
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
    private suspend fun downloadWithSAF(context: Context, media: ImageCard, directoryUri: Uri): String? {
        return withContext(Dispatchers.IO) {
            try {
                val directory = DocumentFile.fromTreeUri(context, directoryUri) ?: return@withContext null
                val mimeType = if (media.type == "video") "video/mp4" else "image/jpeg"
                val fileExtension = if (media.type == "video") ".mp4" else ".jpg"
                val timestamp = SimpleDateFormat("ddMMyyyyHHmmssSSS", Locale.US).format(Date())
                val fileName = "${media.username}_175$timestamp$fileExtension"

                val newFile = directory.createFile(mimeType, fileName)
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
    private suspend fun downloadWithDownloadManager(context: Context, media: ImageCard): String? {
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
                if (!vedInstaDir.exists()) {
                    vedInstaDir.mkdirs()
                }

                val file = File(vedInstaDir, fileName)

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
            val fileName = "${media.username}_${media.type}_$timestamp$fileExtension"
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
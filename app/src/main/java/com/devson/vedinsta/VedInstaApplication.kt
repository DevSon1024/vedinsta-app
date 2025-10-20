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

    // --- MOVE THE DOWNLOAD LOGIC HERE ---
    suspend fun downloadFiles(context: Context, filesToDownload: List<ImageCard>) {
        var downloadedCount = 0
        for (media in filesToDownload) {
            val directoryUriString = if (media.type == "video") {
                settingsManager.videoDirectoryUri
            } else {
                settingsManager.imageDirectoryUri
            }
            if (directoryUriString != null) {
                if (downloadWithSAF(context, media, Uri.parse(directoryUriString))) {
                    downloadedCount++
                }
            } else {
                if (downloadWithDownloadManager(context, media)) {
                    downloadedCount++
                }
            }
        }
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Downloaded $downloadedCount / ${filesToDownload.size} files.", Toast.LENGTH_LONG).show()
        }
    }

    private fun downloadWithSAF(context: Context, media: ImageCard, directoryUri: Uri): Boolean {
        return try {
            val directory = DocumentFile.fromTreeUri(context, directoryUri) ?: return false
            val mimeType = if (media.type == "video") "video/mp4" else "image/jpeg"
            val fileExtension = if (media.type == "video") ".mp4" else ".jpg"
            val timestamp = SimpleDateFormat("ddMMyyyyHHmmssSSS", Locale.US).format(Date())
            val fileName = "${media.username}_175${timestamp}$fileExtension"
            val newFile = directory.createFile(mimeType, fileName)
            newFile?.uri?.let { fileUri ->
                context.contentResolver.openOutputStream(fileUri)?.use { outputStream ->
                    URL(media.url).openStream().use { it.copyTo(outputStream) }
                }
            }
            true
        } catch (e: Exception) { false }
    }

    private fun downloadWithDownloadManager(context: Context, media: ImageCard): Boolean {
        return try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val uri = Uri.parse(media.url)
            val fileExtension = if (media.type == "video") ".mp4" else ".jpg"
            val timestamp = SimpleDateFormat("ddMMyyyyHHmmssSSS", Locale.US).format(Date())
            val fileName = "${media.username}_175${timestamp}$fileExtension"
            val subDirectory = if (media.type == "video") "VedInsta/" else "VedInsta/"
            val request = DownloadManager.Request(uri)
                .setTitle(fileName)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(
                    if (media.type == "video") Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES,
                    subDirectory + fileName
                )
            downloadManager.enqueue(request)
            true
        } catch (e: Exception) { false }
    }
}
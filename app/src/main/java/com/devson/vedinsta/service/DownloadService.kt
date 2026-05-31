package com.devson.vedinsta.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.app.NotificationCompat
import com.devson.vedinsta.notification.VedInstaNotificationManager
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class DownloadService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var notificationManager: VedInstaNotificationManager
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    companion object {
        const val EXTRA_DOWNLOAD_URL = "download_url"
        const val EXTRA_FILE_PATH = "file_path"
        const val EXTRA_FILE_NAME = "file_name"
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.193 Mobile Safari/537.36 Instagram 314.0.0.19.113 Android (34/14; 450dpi; 1440x3088; samsung; SM-S918B; dm3q; qcom; en_US; 557876543)"
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = VedInstaNotificationManager.getInstance(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            val url = it.getStringExtra(EXTRA_DOWNLOAD_URL)
            val filePath = it.getStringExtra(EXTRA_FILE_PATH)
            val fileName = it.getStringExtra(EXTRA_FILE_NAME)

            if (url != null && filePath != null && fileName != null) {
                val notificationId = System.currentTimeMillis().toInt()
                startForeground(notificationId, createForegroundNotification(fileName))

                serviceScope.launch {
                    downloadFile(url, filePath, fileName, notificationId)
                    stopSelfResult(startId)
                }
            } else {
                stopSelfResult(startId)
            }
        }

        return START_NOT_STICKY
    }

    private fun createForegroundNotification(fileName: String) =
        // Fixed: Use CHANNEL_ID_SILENT instead of the missing CHANNEL_ID
        NotificationCompat.Builder(this, VedInstaNotificationManager.CHANNEL_ID_SILENT)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("VedInsta Download")
            .setContentText("Downloading: $fileName")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

    private suspend fun downloadFile(url: String, filePath: String, fileName: String, notificationId: Int) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    notificationManager.showDownloadError(fileName, "HTTP ${response.code}")
                    return
                }

                val body = response.body ?: throw IOException("Empty response body")
                val contentLength = body.contentLength()

                val file = File(filePath)
                file.parentFile?.mkdirs()

                body.byteStream().use { inputStream ->
                    FileOutputStream(file).use { outputStream ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalBytesRead = 0L

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                        }
                    }
                }
            }

            notificationManager.cancelDownloadNotification(notificationId)
            notificationManager.showDownloadCompleted(fileName, 1)

        } catch (e: Exception) {
            notificationManager.showDownloadError(fileName, e.message ?: "Unknown error")
        } finally {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
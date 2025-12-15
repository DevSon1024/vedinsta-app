package com.devson.vedinsta

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.devson.vedinsta.notification.VedInstaNotificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay // Import delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class EnhancedDownloadManager(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val notificationManagerCompat = NotificationManagerCompat.from(applicationContext)

    companion object {
        private const val TAG = "EnhancedDownloadManager"
        const val KEY_MEDIA_URL = "media_url"
        const val KEY_FILE_PATH = "file_path"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_POST_ID = "post_id"
        const val KEY_MEDIA_TYPE = "media_type"
        const val PROGRESS = "Progress"
        private const val NOTIFICATION_ID_OFFSET = 1000
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 1500L
    }

    private val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    } else {
        PendingIntent.FLAG_UPDATE_CURRENT
    }


    override suspend fun doWork(): Result {
        val mediaUrl = inputData.getString(KEY_MEDIA_URL) ?: return Result.failure()
        val filePath = inputData.getString(KEY_FILE_PATH) ?: return Result.failure()
        val fileName = inputData.getString(KEY_FILE_NAME) ?: "downloading_file"
        val notificationId = id.hashCode() + NOTIFICATION_ID_OFFSET

        val initialNotification = createProgressNotification(fileName, 0)
        try {
            setForeground(ForegroundInfo(notificationId, initialNotification))
            Log.d(TAG, "Foreground service started for worker $id with notification ID $notificationId")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting foreground info (permission or other issue)", e)
        }

        setProgress(workDataOf(PROGRESS to 0))

        return try {
            Log.d(TAG, "Starting download for: $mediaUrl")
            val success = downloadWithProgress(mediaUrl, filePath, fileName, notificationId)
            if (success) {
                Log.d(TAG, "Download finished successfully: $filePath")
                val outputData = workDataOf(
                    PROGRESS to 100,
                    KEY_FILE_PATH to filePath
                )
                Result.success(outputData)
            } else {
                Log.w(TAG, "Download failed or cancelled for: $filePath")
                Result.failure()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download failed with exception for $filePath", e)
            Result.failure()
        }
    }

    private suspend fun downloadWithProgress(
        url: String,
        filePath: String,
        fileName: String,
        notificationId: Int
    ): Boolean = withContext(Dispatchers.IO) {
        val client = createEnhancedOkHttpClient()
        val request = createEnhancedRequest(url)
        var file: File? = null
        var currentTotalBytesRead = 0L
        var contentLength = -1L
        var lastNotificationUpdateTime = 0L

        try {
            file = File(filePath)
            file.parentFile?.mkdirs()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Download failed - Code: ${response.code} for $url")
                    return@withContext false
                }
                val body = response.body ?: run {
                    Log.e(TAG, "Empty response body for $url"); return@withContext false
                }
                contentLength = body.contentLength()
                Log.d(TAG, "File size: ${if (contentLength > 0) "${contentLength / 1024} KB" else "Unknown"}")

                body.byteStream().use { inputStream ->
                    FileOutputStream(file).use { outputStream ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead: Int
                        currentTotalBytesRead = 0L
                        var lastProgress = -1

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            if (isStopped) {
                                Log.d(TAG, "Download cancelled for $filePath")
                                runCatching { outputStream.close(); inputStream.close(); file.delete() }
                                return@withContext false
                            }
                            outputStream.write(buffer, 0, bytesRead)
                            currentTotalBytesRead += bytesRead

                            val now = System.currentTimeMillis()
                            val progress = if (contentLength > 0) ((currentTotalBytesRead * 100) / contentLength).toInt() else -1

                            if (progress != lastProgress && progress >= -1) {
                                setProgress(workDataOf(PROGRESS to progress))
                                lastProgress = progress

                                if (now - lastNotificationUpdateTime >= NOTIFICATION_UPDATE_INTERVAL_MS || progress == 100 || progress == -1 && lastNotificationUpdateTime == 0L) {
                                    val notification = createProgressNotification(fileName, progress)
                                    try {
                                        notificationManagerCompat.notify(notificationId, notification)
                                        lastNotificationUpdateTime = now
                                    } catch (e: SecurityException) {
                                        Log.w(TAG, "Permission denied updating notification")
                                        lastNotificationUpdateTime = Long.MAX_VALUE
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (contentLength <= 0 && currentTotalBytesRead == 0L && file.exists() && file.length() == 0L) {
                Log.w(TAG, "Downloaded zero-byte file (unknown or zero size): $filePath")
            } else if (contentLength > 0 && currentTotalBytesRead != contentLength) {
                Log.e(TAG, "Size mismatch: Expected=$contentLength, Got=$currentTotalBytesRead for $filePath")
                runCatching { file.delete() }
                return@withContext false
            }

            return@withContext true
        } catch (e: IOException) {
            Log.e(TAG, "IOException during download for $filePath", e)
            runCatching { file?.delete() }
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "Generic Exception during download for $filePath", e)
            runCatching { file?.delete() }
            return@withContext false
        }
    }


    private fun createProgressNotification(fileName: String, progress: Int): Notification {
        val title = "Downloading Media"
        val contentText = if (progress >= 0) "$fileName ($progress%)" else "$fileName (Downloading...)"
        val indeterminate = progress < 0

        val mainActivityIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val contentPendingIntent: PendingIntent = PendingIntent.getActivity(
            applicationContext,
            id.hashCode(),
            mainActivityIntent,
            pendingIntentFlags
        )

        val cancelPendingIntent: PendingIntent = WorkManager.getInstance(applicationContext)
            .createCancelPendingIntent(id)

        // USE SILENT CHANNEL FOR PROGRESS
        return NotificationCompat.Builder(applicationContext, VedInstaNotificationManager.CHANNEL_ID_SILENT)
            .setContentTitle(title)
            .setTicker(title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW) // Silent
            .setProgress(100, if (indeterminate) 0 else progress, indeterminate)
            .setContentIntent(contentPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            .build()
    }

    private fun createEnhancedOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val newRequest = originalRequest.newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 12; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36 Instagram 308.0.0.34.113 Android")
                    .header("Accept", "*/*")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .header("Connection", "keep-alive")
                    .header("Referer", "https://www.instagram.com/")
                    .header("Origin", "https://www.instagram.com")
                    .header("Sec-Fetch-Dest", "empty")
                    .header("Sec-Fetch-Mode", "cors")
                    .header("Sec-Fetch-Site", "cross-site")
                    .header("sec-ch-ua", "\"Google Chrome\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"")
                    .header("sec-ch-ua-mobile", "?1")
                    .header("sec-ch-ua-platform", "\"Android\"")
                    .removeHeader("DNT")
                    .build()
                chain.proceed(newRequest)
            }
            .build()
    }

    private fun createEnhancedRequest(url: String): Request {
        return Request.Builder()
            .url(url)
            .get()
            .build()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val fileName = inputData.getString(KEY_FILE_NAME) ?: "downloading_file"
        val notificationId = id.hashCode() + NOTIFICATION_ID_OFFSET
        return ForegroundInfo(notificationId, createProgressNotification(fileName, 0))
    }
}
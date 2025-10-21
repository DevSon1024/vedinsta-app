package com.devson.vedinsta.notification

import android.app.NotificationChannel
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.devson.vedinsta.MainActivity
import com.devson.vedinsta.R
import com.devson.vedinsta.database.AppDatabase
import com.devson.vedinsta.database.NotificationEntity
import com.devson.vedinsta.database.NotificationType
import com.devson.vedinsta.database.NotificationPriority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class VedInstaNotificationManager private constructor(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "download_channel"
        const val CHANNEL_NAME = "Download Progress"
        const val CHANNEL_DESCRIPTION = "Shows download progress for Instagram media"
        const val NOTIFICATION_ID_DOWNLOAD = 1001

        @Volatile
        private var INSTANCE: VedInstaNotificationManager? = null

        fun getInstance(context: Context): VedInstaNotificationManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VedInstaNotificationManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val notificationManager = NotificationManagerCompat.from(context)
    private val database = AppDatabase.getDatabase(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = CHANNEL_DESCRIPTION
                setShowBadge(false)
            }

            val systemNotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            systemNotificationManager.createNotificationChannel(channel)
        }
    }

    fun showDownloadStarted(fileName: String, postUrl: String? = null): Int {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("VedInsta Download")
            .setContentText("Starting download: $fileName")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setProgress(100, 0, true)
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID_DOWNLOAD, notification)
        } catch (e: SecurityException) {
            // Handle permission denied silently
        }

        // Save to database
        scope.launch {
            database.notificationDao().insertNotification(
                NotificationEntity(
                    title = "Download Started",
                    message = "Starting download: $fileName",
                    type = NotificationType.DOWNLOAD_STARTED,
                    postUrl = postUrl,
                    priority = NotificationPriority.NORMAL
                )
            )
        }

        return NOTIFICATION_ID_DOWNLOAD
    }

    fun updateDownloadProgress(notificationId: Int, fileName: String, progress: Int, max: Int = 100) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("VedInsta Download")
            .setContentText("Downloading: $fileName ($progress%)")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(max, progress, false)
            .setOngoing(true)
            .build()

        try {
            notificationManager.notify(notificationId, notification)
        } catch (e: SecurityException) {
            // Handle permission denied silently
        }
    }

    fun showDownloadCompleted(fileName: String, totalFiles: Int, postUrl: String? = null, filePaths: List<String> = emptyList()) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download Completed")
            .setContentText("Successfully downloaded $totalFiles file(s)")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            notificationManager.notify(System.currentTimeMillis().toInt(), notification)
        } catch (e: SecurityException) {
            // Handle permission denied silently
        }

        // Save to database
        scope.launch {
            database.notificationDao().insertNotification(
                NotificationEntity(
                    title = "Download Completed",
                    message = "Successfully downloaded $totalFiles file(s): $fileName",
                    type = NotificationType.DOWNLOAD_COMPLETED,
                    postUrl = postUrl,
                    filePaths = filePaths.joinToString(","),
                    thumbnailPath = filePaths.firstOrNull(),
                    priority = NotificationPriority.HIGH
                )
            )
        }
    }

    fun showDownloadError(fileName: String, error: String, postUrl: String? = null) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Download Failed")
            .setContentText("Error downloading $fileName: $error")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        try {
            notificationManager.notify(System.currentTimeMillis().toInt(), notification)
        } catch (e: SecurityException) {
            // Handle permission denied silently
        }

        // Save to database
        scope.launch {
            database.notificationDao().insertNotification(
                NotificationEntity(
                    title = "Download Failed",
                    message = "Error downloading $fileName: $error",
                    type = NotificationType.DOWNLOAD_FAILED,
                    postUrl = postUrl,
                    priority = NotificationPriority.HIGH
                )
            )
        }
    }

    fun cancelDownloadNotification(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }

    // Helper method to add custom notifications
    suspend fun addCustomNotification(title: String, message: String, type: NotificationType, priority: NotificationPriority = NotificationPriority.NORMAL) {
        database.notificationDao().insertNotification(
            NotificationEntity(
                title = title,
                message = message,
                type = type,
                priority = priority
            )
        )
    }
}
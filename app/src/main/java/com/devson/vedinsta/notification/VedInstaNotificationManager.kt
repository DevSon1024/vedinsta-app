package com.devson.vedinsta.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.devson.vedinsta.MainActivity
import com.devson.vedinsta.R
import com.devson.vedinsta.database.AppDatabase
import com.devson.vedinsta.database.NotificationEntity
import com.devson.vedinsta.database.NotificationType
import com.devson.vedinsta.database.NotificationPriority
import com.devson.vedinsta.service.SharedLinkProcessingService
import kotlin.random.Random

class VedInstaNotificationManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "VedInstaNotificationMgr"

        // Channel for Background tasks (Loading, Progress, Completion) - No Sound/Popup
        const val CHANNEL_ID_SILENT = "download_channel_silent_v4"
        const val CHANNEL_NAME_SILENT = "Download Progress (Silent)"

        // Channel for User Actions (Selection, Errors) - Sound + Popup
        const val CHANNEL_ID_ALERT = "download_channel_alert_v4"
        const val CHANNEL_NAME_ALERT = "Download Actions"

        const val NOTIFICATION_ID_LINK_PROCESSING = 1001
        const val NOTIFICATION_ID_MULTIPLE_CONTENT = 1002
        const val NOTIFICATION_ID_BATCH_DOWNLOAD = 1003

        @Volatile
        private var INSTANCE: VedInstaNotificationManager? = null

        fun getInstance(context: Context): VedInstaNotificationManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VedInstaNotificationManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val notificationManagerCompat = NotificationManagerCompat.from(context)
    private val database = AppDatabase.getDatabase(context)

    private val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    } else {
        PendingIntent.FLAG_UPDATE_CURRENT
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val systemNotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 1. Silent Channel (Low Importance)
            val silentChannel = NotificationChannel(
                CHANNEL_ID_SILENT,
                CHANNEL_NAME_SILENT,
                NotificationManager.IMPORTANCE_LOW // No sound, no popup
            ).apply {
                description = "Shows active downloads and completion silently"
                setShowBadge(false)
            }

            // 2. Alert Channel (High Importance)
            val alertChannel = NotificationChannel(
                CHANNEL_ID_ALERT,
                CHANNEL_NAME_ALERT,
                NotificationManager.IMPORTANCE_HIGH // Sound + Popup
            ).apply {
                description = "Shows actions required and errors"
                setShowBadge(true)
                enableVibration(true)
                enableLights(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            systemNotificationManager.createNotificationChannels(listOf(silentChannel, alertChannel))
        }
    }

    // --- Silent Notifications ---

    fun showLinkProcessing() {
        // SILENT CHANNEL
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_SILENT)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("VedInsta")
            .setContentText("Processing link...")
            .setProgress(0, 0, true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        notify(NOTIFICATION_ID_LINK_PROCESSING, notification)
    }

    fun showDownloadProgress(notificationId: Int, completedFiles: Int, totalFiles: Int) {
        val remaining = totalFiles - completedFiles
        val contentText = "Downloading... ($completedFiles/$totalFiles files)"
        val subText = when {
            remaining <= 0 -> "All files downloaded"
            remaining == 1 -> "$remaining file remaining"
            else -> "$remaining files remaining"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_SILENT)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("VedInsta · Downloading")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (totalFiles > 1) {
            builder.setSubText(subText)
            builder.setProgress(totalFiles, completedFiles, false)
        } else {
            builder.setProgress(0, 0, true)
        }

        notify(notificationId, builder.build())
    }

    fun showBatchDownloadProgress(current: Int, total: Int) {
        showDownloadProgress(NOTIFICATION_ID_BATCH_DOWNLOAD, current, total)
    }

    fun showBatchDownloadProgress(notificationId: Int, current: Int, total: Int, title: String) {
        showDownloadProgress(notificationId, current, total)
    }

    fun showSingleDownloadProgress(notificationId: Int, fileName: String, progress: Int) {
        if (progress >= 100) {
            showDownloadProgress(notificationId, 1, 1)
        } else {
            showDownloadProgress(notificationId, 0, 1)
        }
    }

    fun showBatchDownloadComplete(totalFiles: Int) {
        cancelBatchDownloadNotification()
        showDownloadCompleted(NOTIFICATION_ID_BATCH_DOWNLOAD, "Download Complete", "Saved $totalFiles file(s)")
    }

    fun showDownloadCompleted(fileName: String, totalFiles: Int, postUrl: String? = null, filePaths: List<String> = emptyList()) {
        val countText = if (totalFiles > 1) "$totalFiles files saved" else "File saved to gallery"
        showDownloadCompleted(System.currentTimeMillis().toInt(), "Download Complete", countText)
    }

    fun showDownloadCompleted(notificationId: Int, fileName: String, totalFiles: Int) {
        val countText = if (totalFiles > 1) "$totalFiles files saved" else "File saved to gallery"
        showDownloadCompleted(notificationId, "Download Complete", countText)
    }

    fun showDownloadCompleted(title: String, message: String) {
        showDownloadCompleted(System.currentTimeMillis().toInt(), title, message)
    }

    fun showDownloadCompleted(notificationId: Int, title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            pendingIntentFlags
        )

        // SILENT CHANNEL
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_SILENT)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("VedInsta · $title")
            .setContentText(message)
            .setSubText("Tap to open")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setDefaults(0)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notify(notificationId, notification)
    }

    // --- Alert/Popup Notifications ---

    fun showDownloadStartedPopup(title: String, message: String) {
        val notificationId = System.currentTimeMillis().toInt()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            pendingIntentFlags
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALERT)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setTimeoutAfter(3000) // Auto-vanish after 3 seconds
            .build()

        notify(notificationId, notification)
    }

    fun showMultipleContentOptions(url: String, itemCount: Int, autoOpenSelection: Boolean = false) {
        val downloadAllIntent = Intent(context, SharedLinkProcessingService::class.java).apply {
            action = SharedLinkProcessingService.ACTION_DOWNLOAD_ALL
            putExtra(SharedLinkProcessingService.EXTRA_INSTAGRAM_URL, url)
        }
        val downloadAllPendingIntent = PendingIntent.getService(
            context,
            NOTIFICATION_ID_MULTIPLE_CONTENT,
            downloadAllIntent,
            pendingIntentFlags
        )

        val selectIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("POST_URL", url)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        if (autoOpenSelection) {
            context.startActivity(selectIntent)
            cancelMultipleContentNotification()
            return
        }

        val selectPendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_MULTIPLE_CONTENT + 1,
            selectIntent,
            pendingIntentFlags
        )

        // ALERT CHANNEL (Heads-up)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALERT)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Found $itemCount items")
            .setContentText("Tap to Select or Download All")
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Popup
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setDefaults(NotificationCompat.DEFAULT_ALL) // Sound + Vibrate
            .setContentIntent(selectPendingIntent)
            .addAction(
                android.R.drawable.stat_sys_download_done,
                "Download All",
                downloadAllPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_view,
                "Select",
                selectPendingIntent
            )
            .build()

        notify(NOTIFICATION_ID_MULTIPLE_CONTENT, notification)
    }

    fun showRestrictedPostError() {
        cancelLinkProcessingNotification()

        val notificationId = System.currentTimeMillis().toInt()

        // ALERT CHANNEL (Heads-up for Restricted Post)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALERT)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Restricted Content")
            .setContentText("This post is private or restricted")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText("This Instagram post is private, restricted, or requires a login to view."))
            .build()

        notify(notificationId, notification)
    }

    fun showLinkError(message: String) {
        cancelLinkProcessingNotification()

        val notificationId = System.currentTimeMillis().toInt()

        // ALERT CHANNEL (Heads-up for Errors)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALERT)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Download Failed")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .build()

        notify(notificationId, notification)
    }

    fun showDownloadError(fileName: String, error: String, postUrl: String? = null) {
        showDownloadError(System.currentTimeMillis().toInt(), fileName, error, postUrl)
    }

    fun showDownloadError(notificationId: Int, fileName: String, error: String, postUrl: String? = null) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, notificationId, intent, pendingIntentFlags
        )

        // ALERT CHANNEL (Heads-up for Download Errors)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALERT)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Download Failed")
            .setContentText("Error downloading $fileName")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText("Error downloading $fileName: $error"))
            .build()
        notify(notificationId, notification)
    }

    // --- Helpers ---

    fun cancelLinkProcessingNotification() {
        try {
            notificationManagerCompat.cancel(NOTIFICATION_ID_LINK_PROCESSING)
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling link processing notification", e)
        }
    }

    fun cancelMultipleContentNotification() {
        try {
            notificationManagerCompat.cancel(NOTIFICATION_ID_MULTIPLE_CONTENT)
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling multiple content notification", e)
        }
    }

    fun cancelBatchDownloadNotification() {
        try {
            notificationManagerCompat.cancel(NOTIFICATION_ID_BATCH_DOWNLOAD)
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling batch download notification", e)
        }
    }

    fun cancelDownloadNotification(notificationId: Int) {
        try {
            notificationManagerCompat.cancel(notificationId)
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling notification $notificationId", e)
        }
    }

    suspend fun addCustomNotification(
        title: String,
        message: String,
        type: NotificationType,
        priority: NotificationPriority = NotificationPriority.NORMAL,
        postId: String? = null,
        postUrl: String? = null,
        thumbnailPath: String? = null
    ) {
        try {
            database.notificationDao().insertNotification(
                NotificationEntity(
                    title = title,
                    message = message,
                    type = type,
                    priority = priority,
                    postId = postId,
                    postUrl = postUrl,
                    thumbnailPath = thumbnailPath
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error adding custom notification to DB", e)
        }
    }

    suspend fun updateProgressInDb(postId: String?, username: String?, progressText: String) {
        if (postId == null) return
        try {
            val dao = database.notificationDao()
            val existing = dao.getNotificationByPostIdAndType(postId, NotificationType.DOWNLOAD_PROGRESS)
            val title = if (username != null) "Downloading from @$username" else "Downloading Media"
            val message = progressText
            
            if (existing != null) {
                dao.updateNotification(existing.copy(
                    message = message,
                    timestamp = System.currentTimeMillis()
                ))
            } else {
                dao.insertNotification(NotificationEntity(
                    title = title,
                    message = message,
                    type = NotificationType.DOWNLOAD_PROGRESS,
                    postId = postId,
                    timestamp = System.currentTimeMillis()
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating progress in DB", e)
        }
    }

    suspend fun removeProgressFromDb(postId: String?) {
        if (postId == null) return
        try {
            val dao = database.notificationDao()
            dao.deleteNotificationByPostIdAndType(postId, NotificationType.DOWNLOAD_PROGRESS)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing progress from DB", e)
        }
    }

    private fun notify(id: Int, notification: Notification) {
        try {
            if (notificationManagerCompat.areNotificationsEnabled()) {
                notificationManagerCompat.notify(id, notification)
            } else {
                Log.w(TAG, "Notifications disabled")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException", e)
        }
    }
}
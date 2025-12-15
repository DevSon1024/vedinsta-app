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
import com.devson.vedinsta.DownloadActivity
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
        // CHANGE: Updated ID to force Android to apply new Importance settings (Popup)
        const val CHANNEL_ID = "download_channel_v3"
        const val CHANNEL_NAME = "Download Progress"
        const val CHANNEL_DESCRIPTION = "Shows download progress for Instagram media"

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
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH // CRITICAL: Enables Heads-up Popup
            ).apply {
                description = CHANNEL_DESCRIPTION
                setShowBadge(true)
                enableVibration(true)
                enableLights(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val systemNotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            systemNotificationManager.createNotificationChannel(channel)
        }
    }

    fun showLinkProcessing() {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
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

    fun cancelLinkProcessingNotification() {
        try {
            notificationManagerCompat.cancel(NOTIFICATION_ID_LINK_PROCESSING)
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling link processing notification", e)
        }
    }

    fun showMultipleContentOptions(url: String, itemCount: Int, autoOpenSelection: Boolean = false) {
        // Option 1: Download All (Handled by Service)
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

        // Option 2: Select Items (Opens Activity)
        val selectIntent = Intent(context, DownloadActivity::class.java).apply {
            putExtra("POST_URL", url)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        // If auto-open requested, start activity immediately
        if (autoOpenSelection) {
            context.startActivity(selectIntent)
            // Even if auto-opening, we might want to clear any existing notification
            cancelMultipleContentNotification()
            return
        }

        val selectPendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_MULTIPLE_CONTENT + 1,
            selectIntent,
            pendingIntentFlags
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_multiple_images) // Ensure icon exists
            .setContentTitle("Found $itemCount items")
            .setContentText("Tap to Select or Download All")
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Popup
            .setCategory(NotificationCompat.CATEGORY_MESSAGE) // Heads-up behavior
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setDefaults(NotificationCompat.DEFAULT_ALL) // Sound + Vibrate
            .setContentIntent(selectPendingIntent) // Tapping notification body opens selection
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

    fun cancelMultipleContentNotification() {
        try {
            notificationManagerCompat.cancel(NOTIFICATION_ID_MULTIPLE_CONTENT)
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling multiple content notification", e)
        }
    }

    fun showBatchDownloadProgress(current: Int, total: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading")
            .setContentText("$current of $total")
            .setProgress(total, current, false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .build()

        notify(NOTIFICATION_ID_BATCH_DOWNLOAD, notification)
    }

    fun showBatchDownloadComplete(totalFiles: Int) {
        cancelBatchDownloadNotification()

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            intent,
            pendingIntentFlags
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download complete")
            .setContentText("$totalFiles item(s) downloaded")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .build()

        notify(System.currentTimeMillis().toInt(), notification)
    }

    fun cancelBatchDownloadNotification() {
        try {
            notificationManagerCompat.cancel(NOTIFICATION_ID_BATCH_DOWNLOAD)
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling batch download notification", e)
        }
    }

    fun showDownloadError(fileName: String, error: String, postUrl: String? = null) {
        val notificationId = System.currentTimeMillis().toInt()
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, notificationId, intent, pendingIntentFlags
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Download Failed")
            .setContentText("Error downloading $fileName")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText("Error downloading $fileName: $error"))
            .build()
        notify(notificationId, notification)
    }

    fun showDownloadCompleted(fileName: String, totalFiles: Int, postUrl: String? = null, filePaths: List<String> = emptyList()) {
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

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download Completed")
            .setContentText("Saved $totalFiles file(s) for $fileName")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notify(notificationId, notification)
    }

    fun showRestrictedPostError() {
        cancelLinkProcessingNotification()
        showLinkError("This post is private or restricted")
    }

    fun showLinkError(message: String) {
        cancelLinkProcessingNotification()

        val notificationId = System.currentTimeMillis().toInt()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Link Processing Failed")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .build()

        notify(notificationId, notification)
    }

    suspend fun addCustomNotification(title: String, message: String, type: NotificationType, priority: NotificationPriority = NotificationPriority.NORMAL) {
        try {
            database.notificationDao().insertNotification(
                NotificationEntity(
                    title = title, message = message, type = type, priority = priority
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error adding custom notification to DB", e)
        }
    }

    fun cancelDownloadNotification(notificationId: Int) {
        try {
            notificationManagerCompat.cancel(notificationId)
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling notification $notificationId", e)
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
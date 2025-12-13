package com.devson.vedinsta.notification

import android.app.NotificationChannel
import android.app.NotificationManager // Import NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build // Added Build import
import android.os.Bundle // Added Bundle import for PendingIntent
import android.util.Log // <-- Added Log import
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import com.devson.vedinsta.MainActivity
import com.devson.vedinsta.R
import com.devson.vedinsta.database.AppDatabase
import com.devson.vedinsta.database.NotificationEntity
import com.devson.vedinsta.database.NotificationType
import com.devson.vedinsta.database.NotificationPriority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.random.Random
import com.devson.vedinsta.MediaSelectionActivity
import com.devson.vedinsta.service.SharedLinkProcessingService

class VedInstaNotificationManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "VedInstaNotificationMgr"
        const val CHANNEL_ID = "download_channel"
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
    private val scope = CoroutineScope(Dispatchers.IO)

    // Determine the correct PendingIntent flag based on Android version
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
                NotificationManager.IMPORTANCE_HIGH  // Changed to HIGH for heads-up
            ).apply {
                description = CHANNEL_DESCRIPTION
                setShowBadge(true)
                enableVibration(true)
                enableLights(true)
            }
            val systemNotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            systemNotificationManager.createNotificationChannel(channel)
        }
    }


    fun showDownloadEnqueued(fileName: String, postUrl: String? = null): Int {
        val notificationId = Random.nextInt()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        // Corrected PendingIntent.getActivity call
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            notificationId, // Use unique request code
            intent,
            pendingIntentFlags // Use determined flags
            // Removed Bundle argument as it's optional and not needed here
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pending)
            .setContentTitle("Download Queued")
            .setContentText("Waiting to download: $fileName")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(false)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        try {
            if (notificationManagerCompat.areNotificationsEnabled()) {
                notificationManagerCompat.notify(notificationId, notification)
            } else { Log.w(TAG, "Notifications disabled") }
        } catch (e: SecurityException) { Log.e(TAG, "SecurityException", e) }
        return notificationId
    }

    // Worker handles progress updates via setForegroundInfoAsync

    fun showDownloadCompleted(fileName: String, totalFiles: Int, postUrl: String? = null, filePaths: List<String> = emptyList()) {
        val notificationId = System.currentTimeMillis().toInt()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            notificationId, // Unique request code
            intent,
            pendingIntentFlags // Use determined flags
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download Completed")
            .setContentText("Successfully downloaded $totalFiles file(s) for $fileName")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        try {
            if (notificationManagerCompat.areNotificationsEnabled()) {
                notificationManagerCompat.notify(notificationId, notification)
            } else { Log.w(TAG, "Notifications disabled") }
        } catch (e: SecurityException) { Log.e(TAG, "SecurityException", e) }
        // DB save moved to Application
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
            .setContentText("Error downloading $fileName: ${error.take(50)}")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText("Error downloading $fileName: $error"))
            .build()
        try {
            if (notificationManagerCompat.areNotificationsEnabled()) {
                notificationManagerCompat.notify(notificationId, notification)
            } else { Log.w(TAG, "Notifications disabled") }
        } catch (e: SecurityException) { Log.e(TAG, "SecurityException", e) }
        // DB save moved to Application
    }

    fun cancelDownloadNotification(notificationId: Int) {
        try {
            notificationManagerCompat.cancel(notificationId)
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling notification $notificationId", e)
        }
    }
    fun showLinkProcessing() {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Processing link...")
            .setProgress(0, 0, true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .build()

        try {
            if (notificationManagerCompat.areNotificationsEnabled()) {
                notificationManagerCompat.notify(NOTIFICATION_ID_LINK_PROCESSING, notification)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException", e)
        }
    }

    fun cancelLinkProcessingNotification() {
        try {
            notificationManagerCompat.cancel(NOTIFICATION_ID_LINK_PROCESSING)
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling link processing notification", e)
        }
    }

    fun showMultipleContentOptions(url: String, itemCount: Int) {
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

        val selectIntent = Intent(context, SharedLinkProcessingService::class.java).apply {
            action = SharedLinkProcessingService.ACTION_SELECT_ITEMS
            putExtra(SharedLinkProcessingService.EXTRA_INSTAGRAM_URL, url)
        }
        val selectPendingIntent = PendingIntent.getService(
            context,
            NOTIFICATION_ID_MULTIPLE_CONTENT + 1,
            selectIntent,
            pendingIntentFlags
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("$itemCount items found")
            .setContentText("Choose an option")
            .setPriority(NotificationCompat.PRIORITY_HIGH)  // HIGH for heads-up
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)  // Message category
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)  // Allow alert
            .setDefaults(NotificationCompat.DEFAULT_ALL)  // Sound, vibration, lights
            .addAction(
                android.R.drawable.stat_sys_download_done,
                "Download All",
                downloadAllPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_view,
                "Select Items",
                selectPendingIntent
            )
            .build()

        try {
            if (notificationManagerCompat.areNotificationsEnabled()) {
                notificationManagerCompat.notify(NOTIFICATION_ID_MULTIPLE_CONTENT, notification)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException", e)
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

        try {
            if (notificationManagerCompat.areNotificationsEnabled()) {
                notificationManagerCompat.notify(NOTIFICATION_ID_BATCH_DOWNLOAD, notification)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException", e)
        }
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

        try {
            if (notificationManagerCompat.areNotificationsEnabled()) {
                notificationManagerCompat.notify(System.currentTimeMillis().toInt(), notification)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException", e)
        }
    }

    fun cancelBatchDownloadNotification() {
        try {
            notificationManagerCompat.cancel(NOTIFICATION_ID_BATCH_DOWNLOAD)
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling batch download notification", e)
        }
    }


    fun cancelMultipleContentNotification() {
        try {
            notificationManagerCompat.cancel(NOTIFICATION_ID_MULTIPLE_CONTENT)
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling multiple content notification", e)
        }
    }

    fun showRestrictedPostError() {
        cancelLinkProcessingNotification()

        val notificationId = System.currentTimeMillis().toInt()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Restricted Content")
            .setContentText("This post is private or restricted")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("This Instagram post is private or restricted.\n\nYou may need to:\n• Follow the account\n• Log in to Instagram\n• Check if the post still exists")
            )
            .build()

        try {
            if (notificationManagerCompat.areNotificationsEnabled()) {
                notificationManagerCompat.notify(notificationId, notification)
            } else {
                Log.w(TAG, "Notifications disabled")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException showing restricted post error", e)
        }
    }

    fun showLinkError(message: String) {
        cancelLinkProcessingNotification()

        val notificationId = System.currentTimeMillis().toInt()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Link Processing Failed")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .build()

        try {
            if (notificationManagerCompat.areNotificationsEnabled()) {
                notificationManagerCompat.notify(notificationId, notification)
            } else {
                Log.w(TAG, "Notifications disabled")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException showing link error", e)
        }
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
}
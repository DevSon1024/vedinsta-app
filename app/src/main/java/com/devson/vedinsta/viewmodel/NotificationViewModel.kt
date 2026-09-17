package com.devson.vedinsta.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.devson.vedinsta.VedInstaApplication
import com.devson.vedinsta.database.AppDatabase
import com.devson.vedinsta.database.NotificationDao
import com.devson.vedinsta.database.NotificationEntity
import com.devson.vedinsta.database.NotificationType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotificationViewModel(application: Application) : AndroidViewModel(application) {
    private val notificationDao: NotificationDao = AppDatabase.getDatabase(application).notificationDao()

    val allNotifications: LiveData<List<NotificationEntity>> = notificationDao.getAllNotifications()
    val unreadNotifications: LiveData<List<NotificationEntity>> = notificationDao.getUnreadNotifications()
    val unreadCount: LiveData<Int> = notificationDao.getUnreadCount(NotificationType.DOWNLOAD_PROGRESS)

    init {
        cleanStaleDownloads()
    }

    fun cleanStaleDownloads() {
        viewModelScope.launch {
            try {
                val cutoffTime = System.currentTimeMillis() - (60L * 60L * 1000L) // 1 hour ago
                notificationDao.markStaleDownloadsAsFailed(cutoffTime)
            } catch (e: Exception) {
                Log.e("NotificationViewModel", "Failed to clean stale downloads", e)
            }
        }
    }

    fun cancelDownload(notification: NotificationEntity) {
        viewModelScope.launch {
            val postId = notification.postId
            if (postId != null) {
                try {
                    // Cancel WorkManager tasks by tag
                    androidx.work.WorkManager.getInstance(getApplication()).cancelAllWorkByTag(postId)
                    // Cancel DownloadService active jobs
                    com.devson.vedinsta.service.DownloadService.cancelDownload(postId)
                } catch (e: Exception) {
                    Log.e("NotificationViewModel", "Failed to cancel running downloads for $postId", e)
                }
            }
            // Delete notification from DB
            notificationDao.deleteNotificationById(notification.id)
        }
    }

    suspend fun resolvePostUrl(notification: NotificationEntity): String? = withContext(Dispatchers.IO) {
        if (!notification.postUrl.isNullOrBlank()) {
            return@withContext notification.postUrl
        }
        val postId = notification.postId
        if (!postId.isNullOrBlank()) {
            if (postId.startsWith("http://") || postId.startsWith("https://")) {
                return@withContext postId
            }
            if (!postId.startsWith("batch_") && !postId.startsWith("single_") && !postId.contains(" ") && !postId.contains("/")) {
                return@withContext "https://www.instagram.com/p/$postId/"
            }
            try {
                val db = AppDatabase.getDatabase(getApplication())
                val post = db.downloadedPostDao().getPostById(postId)
                if (!post?.postUrl.isNullOrBlank()) {
                    return@withContext post?.postUrl
                }
            } catch (e: Exception) {
                Log.e("NotificationViewModel", "Error resolving postUrl from DB for $postId", e)
            }
        }
        null
    }

    fun retryDownload(notification: NotificationEntity) {
        viewModelScope.launch {
            val postUrl = resolvePostUrl(notification)
            if (postUrl.isNullOrBlank()) {
                Log.w("NotificationViewModel", "Cannot retry download: postUrl could not be resolved for notification ${notification.id}")
                return@launch
            }
            // First, delete the failed notification from DB so it's replaced
            notificationDao.deleteNotificationById(notification.id)
            
            // Trigger the download again
            try {
                val app = getApplication<VedInstaApplication>()
                app.downloadPostFromUrl(postUrl)
            } catch (e: Exception) {
                Log.e("NotificationViewModel", "Failed to retry download for $postUrl", e)
            }
        }
    }

    fun markAsRead(id: Long) {
        viewModelScope.launch {
            notificationDao.markAsRead(id)
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            notificationDao.markAllAsRead(NotificationType.DOWNLOAD_PROGRESS)
        }
    }

    fun deleteNotification(id: Long) {
        viewModelScope.launch {
            notificationDao.deleteNotificationById(id)
        }
    }

    fun getNotificationsByType(type: NotificationType): LiveData<List<NotificationEntity>> {
        return notificationDao.getNotificationsByType(type)
    }

    fun cleanOldNotifications() {
        viewModelScope.launch {
            val cutoffTime = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L) // 30 days
            notificationDao.deleteOldNotifications(cutoffTime)
        }
    }

    fun pruneNotifications(limit: Int) {
        if (limit <= 0) return
        viewModelScope.launch {
            notificationDao.pruneNotifications(limit)
        }
    }
}
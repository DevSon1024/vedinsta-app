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
import kotlinx.coroutines.launch

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

    fun retryDownload(notification: NotificationEntity) {
        val postUrl = notification.postUrl ?: return
        viewModelScope.launch {
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
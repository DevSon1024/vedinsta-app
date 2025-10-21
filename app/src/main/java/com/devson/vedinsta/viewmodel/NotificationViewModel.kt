package com.devson.vedinsta.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.devson.vedinsta.database.AppDatabase
import com.devson.vedinsta.database.NotificationDao
import com.devson.vedinsta.database.NotificationEntity
import com.devson.vedinsta.database.NotificationType
import kotlinx.coroutines.launch

class NotificationViewModel(application: Application) : AndroidViewModel(application) {
    private val notificationDao: NotificationDao = AppDatabase.getDatabase(application).notificationDao()

    val allNotifications: LiveData<List<NotificationEntity>> = notificationDao.getAllNotifications()
    val unreadNotifications: LiveData<List<NotificationEntity>> = notificationDao.getUnreadNotifications()
    val unreadCount: LiveData<Int> = notificationDao.getUnreadCount()

    fun markAsRead(id: Long) {
        viewModelScope.launch {
            notificationDao.markAsRead(id)
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            notificationDao.markAllAsRead()
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
}
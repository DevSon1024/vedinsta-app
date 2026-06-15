package com.devson.vedinsta.database

import androidx.lifecycle.LiveData
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY type = 'DOWNLOAD_PROGRESS' DESC, timestamp DESC")
    fun getAllNotifications(): LiveData<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE isRead = 0 ORDER BY timestamp DESC")
    fun getUnreadNotifications(): LiveData<List<NotificationEntity>>

    @Query("SELECT COUNT(*) FROM notifications WHERE isRead = 0 AND type != :excludeType")
    fun getUnreadCount(excludeType: NotificationType): LiveData<Int>

    @Query("SELECT COUNT(*) FROM notifications WHERE isRead = 0 AND type != :excludeType")
    suspend fun getUnreadCountSync(excludeType: NotificationType): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity)

    @Update
    suspend fun updateNotification(notification: NotificationEntity)

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: Long)

    @Query("UPDATE notifications SET isRead = 1 WHERE type != :excludeType")
    suspend fun markAllAsRead(excludeType: NotificationType)

    @Query("SELECT * FROM notifications WHERE postId = :postId AND type = :type LIMIT 1")
    suspend fun getNotificationByPostIdAndType(postId: String, type: NotificationType): NotificationEntity?

    @Query("DELETE FROM notifications WHERE postId = :postId AND type = :type")
    suspend fun deleteNotificationByPostIdAndType(postId: String, type: NotificationType)

    @Delete
    suspend fun deleteNotification(notification: NotificationEntity)

    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun deleteNotificationById(id: Long)

    @Query("DELETE FROM notifications WHERE timestamp < :cutoffTime")
    suspend fun deleteOldNotifications(cutoffTime: Long)

    @Query("DELETE FROM notifications WHERE type != 'DOWNLOAD_PROGRESS' AND id NOT IN (SELECT id FROM (SELECT id FROM notifications WHERE type != 'DOWNLOAD_PROGRESS' ORDER BY timestamp DESC LIMIT :limit))")
    suspend fun pruneNotifications(limit: Int)

    @Query("SELECT * FROM notifications WHERE type = :type ORDER BY timestamp DESC")
    fun getNotificationsByType(type: NotificationType): LiveData<List<NotificationEntity>>

    @Query("UPDATE notifications SET type = 'DOWNLOAD_FAILED', title = 'Download Failed', message = 'The download was interrupted or timed out.' WHERE type = 'DOWNLOAD_PROGRESS' AND timestamp < :cutoffTime")
    suspend fun markStaleDownloadsAsFailed(cutoffTime: Long)
}
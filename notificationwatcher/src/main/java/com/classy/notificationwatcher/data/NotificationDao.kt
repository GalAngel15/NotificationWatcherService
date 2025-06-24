package com.classy.notificationwatcher.data
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    fun getAllNotifications(): Flow<List<NotificationData>>

    @Query("SELECT * FROM notifications WHERE packageName = :packageName ORDER BY timestamp DESC")
    fun getNotificationsByPackage(packageName: String): Flow<List<NotificationData>>

    @Query("SELECT * FROM notifications WHERE isDeleted = 1 ORDER BY deletedTimestamp DESC")
    fun getDeletedNotifications(): Flow<List<NotificationData>>

    @Query("SELECT * FROM notifications WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    fun getNotificationsByTimeRange(startTime: Long, endTime: Long): Flow<List<NotificationData>>

    @Insert
    suspend fun insertNotification(notification: NotificationData): Long

    @Update
    suspend fun updateNotification(notification: NotificationData)

    @Query("UPDATE notifications SET isDeleted = 1, deletedTimestamp = :deletedTime WHERE notificationKey = :key")
    suspend fun markAsDeleted(key: String, deletedTime: Long)

    @Query("DELETE FROM notifications WHERE timestamp < :cutoffTime")
    suspend fun deleteOldNotifications(cutoffTime: Long)

    // Tracking methods
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateTracking(tracking: NotificationTracking)

    @Query("SELECT * FROM notification_tracking WHERE notificationKey = :key")
    suspend fun getTracking(key: String): NotificationTracking?

    @Query("UPDATE notification_tracking SET isActive = 0 WHERE notificationKey = :key")
    suspend fun deactivateTracking(key: String)

    @Query("SELECT * FROM notification_tracking WHERE isActive = 1 AND packageName = :packageName")
    suspend fun getActiveTrackingForPackage(packageName: String): List<NotificationTracking>
}
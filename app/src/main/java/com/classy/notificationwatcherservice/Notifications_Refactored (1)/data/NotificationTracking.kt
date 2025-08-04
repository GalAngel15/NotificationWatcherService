package com.classy.notificationwatcher.data
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notification_tracking")
data class NotificationTracking(
    @PrimaryKey
    val notificationKey: String,
    val packageName: String,
    val originalTimestamp: Long,
    val lastSeenTimestamp: Long,
    val isActive: Boolean = true
)
package com.classy.notificationwatcher.data
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notifications")
data class NotificationData(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val title: String?,
    val text: String?,
    val subText: String?,
    val bigText: String?,
    val timestamp: Long,
    val isDeleted: Boolean = false,
    val deletedTimestamp: Long? = null,
    val notificationKey: String?,
    val category: String?,
    val priority: Int,
    val isOngoing: Boolean = false,
    val groupKey: String?
)
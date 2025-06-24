package com.classy.notificationwatcher.core

import com.classy.notificationwatcher.data.CategoryStat

data class NotificationStats(
    val totalNotifications: Int,
    val deletedNotifications: Int,
    val topApps: List<AppStat>,
    val notificationsToday: Int,
    val notificationsThisWeek: Int,
    val averagePerDay: Double,
    val peakHour: Int,
    val oldestNotification: Long?,
    val newestNotification: Long?,
    val categoryStats: List<CategoryStat> = emptyList()
)

data class AppStat(
    val packageName: String,
    val appName: String,
    val notificationCount: Int,
    val deletedCount: Int,
    val percentage: Double
)

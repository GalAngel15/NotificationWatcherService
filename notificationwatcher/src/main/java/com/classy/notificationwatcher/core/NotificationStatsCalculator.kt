package com.classy.notificationwatcher.core

import com.classy.notificationwatcher.data.CategoryStat
import com.classy.notificationwatcher.data.NotificationDao
import kotlinx.coroutines.flow.first
import java.util.*

object NotificationStatsCalculator {

    suspend fun calculate(dao: NotificationDao): NotificationStats {
        val all = dao.getAllNotifications().first()
        val deleted = dao.getDeletedNotifications().first()
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()

        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis
        val today = dao.getNotificationsByTimeRange(startOfDay, now).first()

        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        val startOfWeek = calendar.timeInMillis
        val week = dao.getNotificationsByTimeRange(startOfWeek, now).first()

        val groupedByApp = all.groupBy { it.packageName }
        val topApps = groupedByApp.map { (pkg, list) ->
            val deletedCount = list.count { it.isDeleted }
            AppStat(
                packageName = pkg,
                appName = list.first().appName,
                notificationCount = list.size,
                deletedCount = deletedCount,
                percentage = list.size.toDouble() / all.size * 100
            )
        }.sortedByDescending { it.notificationCount }.take(10)

        val groupedByHour = all.groupBy {
            Calendar.getInstance().apply { timeInMillis = it.timestamp }
                .get(Calendar.HOUR_OF_DAY)
        }
        val peakHour = groupedByHour.maxByOrNull { it.value.size }?.key ?: 0

        val oldest = all.minOfOrNull { it.timestamp }
        val days = if (oldest != null) maxOf(1, ((now - oldest) / (24 * 60 * 60 * 1000)).toInt()) else 1
        val avgPerDay = all.size.toDouble() / days
        val groupedByCategory = all.groupBy { it.category ?: "Unknown" }
        val categoryStats = groupedByCategory.map { (category, list) ->
            CategoryStat(
                category = category,
                count = list.size,
                percentage = list.size.toDouble() / all.size * 100
            )
        }.sortedByDescending { it.count }


        return NotificationStats(
            totalNotifications = all.size,
            deletedNotifications = deleted.size,
            topApps = topApps,
            notificationsToday = today.size,
            notificationsThisWeek = week.size,
            averagePerDay = avgPerDay,
            peakHour = peakHour,
            oldestNotification = oldest,
            newestNotification = all.maxOfOrNull { it.timestamp },
            categoryStats = categoryStats
        )
    }
}

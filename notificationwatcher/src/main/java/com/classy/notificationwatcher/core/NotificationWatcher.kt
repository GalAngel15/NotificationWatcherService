package com.classy.notificationwatcher.core

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.classy.notificationwatcher.data.NotificationData
import com.classy.notificationwatcher.data.NotificationDatabase
import com.classy.notificationwatcher.service.NotificationListener
import com.classy.notificationwatcher.service.NotificationWatcherService
import com.classy.notificationwatcher.core.utils.NotificationExporter
import kotlinx.coroutines.flow.Flow
import java.io.File

class NotificationWatcher private constructor(private val context: Context) {

    private val database = NotificationDatabase.getDatabase(context)
    private val exporter = NotificationExporter(context)

    companion object {
        @Volatile
        private var INSTANCE: NotificationWatcher? = null

        fun getInstance(context: Context): NotificationWatcher {
            return INSTANCE ?: synchronized(this) {
                val instance = NotificationWatcher(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    fun isNotificationAccessGranted(): Boolean {
        val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(context)
        return enabledListeners.contains(context.packageName)
    }

    fun requestNotificationAccess() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun startWatching(): Boolean {
        if (!isNotificationAccessGranted()) return false
        val intent = Intent(context, NotificationWatcherService::class.java)
        context.startService(intent)
        return true
    }

    fun stopWatching() {
        val intent = Intent(context, NotificationWatcherService::class.java)
        context.stopService(intent)
    }

    fun addListener(listener: NotificationListener) {
        NotificationWatcherService.getInstance()?.addNotificationListener(listener)
    }

    fun removeListener(listener: NotificationListener) {
        NotificationWatcherService.getInstance()?.removeNotificationListener(listener)
    }

    fun getAllNotifications(): Flow<List<NotificationData>> =
        database.notificationDao().getAllNotifications()

    fun getNotificationsByApp(packageName: String): Flow<List<NotificationData>> =
        database.notificationDao().getNotificationsByPackage(packageName)

    fun getDeletedNotifications(): Flow<List<NotificationData>> =
        database.notificationDao().getDeletedNotifications()

    fun getNotificationsByTimeRange(startTime: Long, endTime: Long): Flow<List<NotificationData>> =
        database.notificationDao().getNotificationsByTimeRange(startTime, endTime)

    fun getNotificationsFromLastHours(hours: Int): Flow<List<NotificationData>> {
        val endTime = System.currentTimeMillis()
        val startTime = endTime - (hours * 60 * 60 * 1000L)
        return getNotificationsByTimeRange(startTime, endTime)
    }

    fun getTodaysNotifications(): Flow<List<NotificationData>> {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis
        val endOfDay = System.currentTimeMillis()
        return getNotificationsByTimeRange(startOfDay, endOfDay)
    }

    suspend fun exportToCsv(outputFile: File): Boolean =
        exporter.exportToCsv(database.notificationDao(), outputFile)

    suspend fun exportToJson(outputFile: File): Boolean =
        exporter.exportToJson(database.notificationDao(), outputFile)

    suspend fun exportAppToCsv(packageName: String, outputFile: File): Boolean =
        exporter.exportAppToCsv(database.notificationDao(), packageName, outputFile)

    suspend fun exportDeletedToCsv(outputFile: File): Boolean =
        exporter.exportDeletedToCsv(database.notificationDao(), outputFile)

    suspend fun cleanOldNotifications(daysToKeep: Int = 30) {
        val cutoff = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L)
        database.notificationDao().deleteOldNotifications(cutoff)
    }

    suspend fun getNotificationStats(): NotificationStats =
        NotificationStatsCalculator.calculate(database.notificationDao())
}

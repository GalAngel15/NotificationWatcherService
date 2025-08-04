package com.classy.notificationwatcher.core

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import com.classy.notificationwatcher.data.NotificationData
import com.classy.notificationwatcher.data.NotificationDatabase
import com.classy.notificationwatcher.data.NotificationRepository
import com.classy.notificationwatcher.data.RoomNotificationRepository
import com.classy.notificationwatcher.service.NotificationListener
import com.classy.notificationwatcher.service.NotificationWatcherService
import com.classy.notificationwatcher.core.utils.NotificationExporter
import com.classy.notificationwatcher.core.NotificationPermissionManager
import kotlinx.coroutines.flow.Flow
import java.io.File

class NotificationWatcher private constructor(private val context: Context) {

    // Repository abstracts access to notification data. By using an interface
    // here, the watcher is decoupled from Room and can easily be tested.
    private val repository: NotificationRepository =
        RoomNotificationRepository(NotificationDatabase.getDatabase(context).notificationDao())

    private val exporter = NotificationExporter(context)

    // Responsible solely for managing notification listener & battery
    // optimisation permissions.
    private val permissionManager = NotificationPermissionManager(context)

    private val listeners = mutableSetOf<NotificationListener>()
    private var currentLaunchIntent: Intent? = null

    private val PREFS_NAME = "notification_watcher_prefs"
    private val KEY_IS_WATCHING = "is_watching"

    companion object {
        @Volatile
        private var INSTANCE: NotificationWatcher? = null
        /**
         * Singleton instance of NotificationWatcher.
         * Use this method to get the instance in your application.
         */
        fun getInstance(context: Context): NotificationWatcher {
            return INSTANCE ?: synchronized(this) {
                val instance = NotificationWatcher(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    fun addListener(listener: NotificationListener) {
        listeners.add(listener)
        NotificationWatcherService.getInstance()?.addNotificationListener(listener)
    }

    /**
     * Prompts the user to disable battery optimisation via the
     * [NotificationPermissionManager]. This delegation keeps permission logic
     * contained within the manager class.
     */
    fun requestBatteryOptimizationDialog(activity: Activity) {
        permissionManager.requestDisableBatteryOptimizations(activity)
    }


    fun removeListener(listener: NotificationListener) {
        listeners.remove(listener)
        NotificationWatcherService.getInstance()?.removeNotificationListener(listener)
    }

    fun getListeners(): Set<NotificationListener> = listeners // כדי שהשירות יוכל לבקש את הליסנרים

    fun setLaunchIntent(intent: Intent) {
        currentLaunchIntent = intent
        NotificationWatcherService.getInstance()?.setLaunchIntent(intent)
    }

    fun isWatching(): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_IS_WATCHING, false)
    }

    /**
     * Returns true if the user has granted notification listener access. This
     * simply delegates to the [NotificationPermissionManager].
     */
    fun isNotificationAccessGranted(): Boolean = permissionManager.isNotificationAccessGranted()

    /**
     * Opens the system settings screen where the user can grant notification
     * listener access. Delegates to the [NotificationPermissionManager].
     */
    fun requestNotificationAccess() {
        permissionManager.requestNotificationAccess()
    }

    fun startWatching(): Boolean {
        if (!isNotificationAccessGranted()) return false

        val intent = Intent(context, NotificationWatcherService::class.java)
        currentLaunchIntent?.let {
            intent.putExtra("launch_intent", it) // Passing the launch intent to the service
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)  // Use startForegroundService
        } else {
            context.startService(intent)
        }
        // Save the state that we are watching notifications in shared preferences
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_IS_WATCHING, true).apply()

        return true
    }

    fun stopWatching() {
        val intent = Intent(context, NotificationWatcherService::class.java)
        context.stopService(intent)

        NotificationWatcherService.getInstance()?.stopSelf()

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_IS_WATCHING, false).apply()
    }

    fun getAllNotifications(): Flow<List<NotificationData>> =
        repository.getAllNotifications()

    fun getNotificationsByApp(packageName: String): Flow<List<NotificationData>> =
        repository.getNotificationsByPackage(packageName)

    fun getDeletedNotifications(): Flow<List<NotificationData>> =
        repository.getDeletedNotifications()

    fun getNotificationsByTimeRange(startTime: Long, endTime: Long): Flow<List<NotificationData>> =
        repository.getNotificationsByTimeRange(startTime, endTime)

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
        exporter.exportToCsv(repository, outputFile)

    suspend fun exportToJson(outputFile: File): Boolean =
        exporter.exportToJson(repository, outputFile)

    suspend fun exportAppToCsv(packageName: String, outputFile: File): Boolean =
        exporter.exportAppToCsv(repository, packageName, outputFile)

    suspend fun exportDeletedToCsv(outputFile: File): Boolean =
        exporter.exportDeletedToCsv(repository, outputFile)

    suspend fun cleanOldNotifications(daysToKeep: Int = 30) {
        val cutoff = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L)
        repository.deleteOldNotifications(cutoff)
    }

    suspend fun getNotificationStats(): NotificationStats =
        NotificationStatsCalculator.calculate(repository)
}

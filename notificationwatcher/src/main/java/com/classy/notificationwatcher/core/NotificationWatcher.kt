package com.classy.notificationwatcher.core

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
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
     * Requests the user to disable battery optimization for the app.
     * This is necessary to ensure that the notification service runs continuously without being killed by the system.
     * @param activity The activity from which the dialog is shown.
     */
    fun requestBatteryOptimizationDialog(activity: Activity) {
        val powerManager = activity.getSystemService(PowerManager::class.java)
        val packageName = activity.packageName

        if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(packageName)) {
            AlertDialog.Builder(activity)
                .setTitle("⚠️ Battery Optimization")
                .setMessage("To ensure notifications are always received, please disable battery optimization for this app.\n\nWithout this, Android might stop the service unexpectedly.")
                .setPositiveButton("Allow") { _, _ ->
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    activity.startActivity(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
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

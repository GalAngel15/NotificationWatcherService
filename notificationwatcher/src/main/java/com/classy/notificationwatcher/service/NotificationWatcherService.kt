package com.classy.notificationwatcher.service


import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.classy.notificationwatcher.data.NotificationData
import com.classy.notificationwatcher.data.NotificationDatabase
import com.classy.notificationwatcher.data.NotificationTracking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NotificationWatcherService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var database: NotificationDatabase
    private val notificationListeners = mutableListOf<NotificationListener>()
    private var launchIntent: Intent? = null

    companion object {
        private const val TAG = "NotificationWatcher"
        private var instance: NotificationWatcherService? = null

        fun getInstance() = instance
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = NotificationDatabase.getDatabase(this)

        createNotificationChannel()
        startForeground(1, buildForegroundNotification())

        Log.d(TAG, "NotificationWatcherService created")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d(TAG, "NotificationWatcherService destroyed")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let { notification ->
            serviceScope.launch {
                handleNewNotification(notification)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.let { notification ->
            serviceScope.launch {
                handleRemovedNotification(notification)
            }
        }
    }

    private suspend fun handleNewNotification(sbn: StatusBarNotification) {
        try {
            val notification = sbn.notification
            val extras = notification.extras

            val appName = getAppName(sbn.packageName)
            val notificationData = NotificationData(
                packageName = sbn.packageName,
                appName = appName,
                title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
                text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
                subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
                bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
                timestamp = sbn.postTime,
                notificationKey = sbn.key,
                category = notification.category,
                priority = notification.priority,
                isOngoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0,
                groupKey = sbn.groupKey
            )

            // Save to database
            val notificationId = database.notificationDao().insertNotification(notificationData)

            // Track for deleted message detection
            val tracking = NotificationTracking(
                notificationKey = sbn.key,
                packageName = sbn.packageName,
                originalTimestamp = sbn.postTime,
                lastSeenTimestamp = System.currentTimeMillis()
            )
            database.notificationDao().insertOrUpdateTracking(tracking)

            // Notify listeners
            notificationListeners.forEach { listener ->
                try {
                    listener.onNotificationReceived(notificationData.copy(id = notificationId))
                } catch (e: Exception) {
                    Log.e(TAG, "Error in notification listener", e)
                }
            }

            Log.d(TAG, "New notification: ${notificationData.appName} - ${notificationData.title}")

        } catch (e: Exception) {
            Log.e(TAG, "Error handling new notification", e)
        }
    }

    private suspend fun handleRemovedNotification(sbn: StatusBarNotification) {
        try {
            val currentTime = System.currentTimeMillis()
            val timeSincePosted = currentTime - sbn.postTime

            // If notification was removed quickly (within 10 seconds), might be deleted by user
            if (timeSincePosted < 10000 && isMessagingApp(sbn.packageName)) {
                database.notificationDao().markAsDeleted(sbn.key, currentTime)

                // Notify listeners about potential deleted message
                notificationListeners.forEach { listener ->
                    try {
                        listener.onPossibleDeletedMessage(sbn.packageName, sbn.key, currentTime)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in deleted message listener", e)
                    }
                }

                Log.d(TAG, "Possible deleted message detected: ${getAppName(sbn.packageName)}")
            }

            // Deactivate tracking
            database.notificationDao().deactivateTracking(sbn.key)

        } catch (e: Exception) {
            Log.e(TAG, "Error handling removed notification", e)
        }
    }

    private fun getAppName(packageName: String): String {
        return try {
            val packageManager = packageManager
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    private fun isMessagingApp(packageName: String): Boolean {
        val messagingApps = setOf(
            "com.whatsapp",
            "org.telegram.messenger",
            "com.facebook.orca", // Messenger
            "com.viber.voip",
            "com.instagram.android",
            "com.snapchat.android",
            "com.discord",
            "com.google.android.apps.messaging" // Google Messages
        )
        return messagingApps.contains(packageName)
    }

    // Public API methods
    fun addNotificationListener(listener: NotificationListener) {
        notificationListeners.add(listener)
    }

    fun removeNotificationListener(listener: NotificationListener) {
        notificationListeners.remove(listener)
    }

    fun setLaunchIntent(intent: Intent) {
        launchIntent = intent
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channelId = "notification_watcher_channel"
            val channelName = "Notification Watcher"
            val importance = android.app.NotificationManager.IMPORTANCE_LOW
            val channel = android.app.NotificationChannel(channelId, channelName, importance)
            channel.description = "Used to keep the notification watcher running in background"

            val notificationManager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        return NotificationCompat.Builder(this, "notification_watcher_channel")
            .setContentTitle("🔔 Watching notifications")
            .setContentText("Tap to open Notification Watcher")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()
    }


}
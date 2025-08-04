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

/**
 * Encapsulates logic related to notification listener and battery optimization
 * permissions. By centralising these checks and requests in a single class we
 * achieve a clear separation of concerns and make it easier to test or
 * substitute the implementation.
 */
class NotificationPermissionManager(private val context: Context) {

    /**
     * Checks whether the app has been granted notification listener access.
     */
    fun isNotificationAccessGranted(): Boolean {
        val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
        return enabledPackages.contains(context.packageName)
    }

    /**
     * Launches the system settings screen where the user can grant the
     * notification listener permission. Should be called from a UI context.
     */
    fun requestNotificationAccess() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        // To ensure the activity is started outside of an activity context we need
        // the NEW_TASK flag. See documentation: https://developer.android.com/reference/android/content/Intent#FLAG_ACTIVITY_NEW_TASK
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * Checks if the app is ignoring battery optimisations. On Android 6+ the
     * system may kill background services unless battery optimisation is
     * disabled for the app.
     */
    fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = context.getSystemService(PowerManager::class.java)
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    /**
     * Shows a dialog prompting the user to disable battery optimisation. If
     * the user accepts, the system settings screen will be opened. This
     * function should only be called from an [Activity].
     */
    fun requestDisableBatteryOptimizations(activity: Activity) {
        val packageName = context.packageName
        val powerManager = activity.getSystemService(PowerManager::class.java)

        // Only show the dialog if we're not already ignoring battery optimisations
        if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(packageName)) {
            AlertDialog.Builder(activity)
                .setTitle("⚠️ Battery Optimization")
                .setMessage("To ensure notifications are always received, please disable battery optimisation for this app.\n\nWithout this, Android might stop the service unexpectedly.")
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
}
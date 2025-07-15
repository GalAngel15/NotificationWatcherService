# NotificationWatcherService

[](https://jitpack.io/#GalAngel15/NotificationWatcherService)

An Android library to effortlessly listen, store, and manage device notifications. It runs as a persistent foreground service to reliably capture all incoming notifications, detect potentially deleted messages from messaging apps, and provides a rich API for querying, analyzing, and exporting notification data.

-----

## Features

  - **Persistent Service**: Runs as a foreground service to ensure continuous operation.
  - **Notification Logging**: Automatically saves all incoming notifications to a local Room database for persistence.
  - **Deleted Message Detection**: Uses heuristics to identify and flag notifications that are removed shortly after being posted, commonly seen in messaging apps like WhatsApp ("This message was deleted").
  - **Rich Data Querying**: Offers a comprehensive API to retrieve notifications using various criteria:
      - All notifications
      - By specific application
      - Only deleted notifications
      - Within a specific time range
  - **In-depth Statistics**: Calculate and retrieve powerful insights from your notification history, including:
      - Total notifications received
      - Notifications received today and this week
      - Top 10 most active apps
      - Average daily notification count
      - Peak notification hour
  - **Data Export**: Easily export notification data to **CSV** or **JSON** formats for backup or external analysis.
  - **Real-time Listening**: Attach listeners to react to notifications as they are received or deleted in real-time.
  - **Clean & Simple API**: Designed with a singleton pattern for easy integration and management.

-----

## Setup

### 1\. Add JitPack Repository

Add the JitPack repository to your root `build.gradle` file (or `settings.gradle` for newer projects):

```groovy
// settings.gradle
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' } // Add this line
    }
}
```

### 2\. Add the Dependency

Add the library dependency to your app-level `build.gradle` (or `build.gradle.kts`) file. **Remember to replace `VERSION` with the latest release number from JitPack.**

```groovy
// build.gradle
dependencies {
    implementation 'com.github.GalAngel15:NotificationWatcherService:VERSION'
}
```

### 3\. Update AndroidManifest.xml

Declare the service and required permissions in your `AndroidManifest.xml`:

```xml
<manifest ...>

    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
        tools:ignore="ProtectedPermissions" />

    <application ...>

        <service
            android:name="com.classy.notificationwatcher.service.NotificationWatcherService"
            android:label="NotificationWatcherService"
            android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
            android:exported="true">
            <intent-filter>
                <action android:name="android.service.notification.NotificationListenerService" />
            </intent-filter>
        </service>

        </application>

</manifest>
```

-----

## Usage

### 1\. Initialization and Permissions

The library requires the "Notification Access" permission to function. You should check for this permission and guide the user to grant it if necessary.

```kotlin
// Get the singleton instance
val watcher = NotificationWatcher.getInstance(this)

// Check for permission first
if (watcher.isNotificationAccessGranted()) {
    // Permission is granted, you can start the service
    watcher.startWatching()
} else {
    // Redirect user to the system settings to grant access
    watcher.requestNotificationAccess()
}
```

### 2\. Starting and Stopping the Service

To ensure the foreground service notification can bring the user back to your app, set a launch intent before starting.

```kotlin
val watcher = NotificationWatcher.getInstance(this)

// Create an intent to launch your main activity (or any other activity)
val launchIntent = Intent(this, MainActivity::class.java)
watcher.setLaunchIntent(launchIntent)

// Start the service
watcher.startWatching()

// To stop the service later
watcher.stopWatching()
```

### 3\. Retrieving Notification Data

All data retrieval functions return a `kotlinx.coroutines.flow.Flow`, which is ideal for observing data changes in a modern Android app.

```kotlin
// Make sure you are in a CoroutineScope
lifecycleScope.launch {
    val watcher = NotificationWatcher.getInstance(this@YourActivity)

    // Get all notifications as a Flow
    watcher.getAllNotifications().collect { notifications ->
        // Update your UI or process the list of NotificationData
        Log.d("MyApp", "Total notifications: ${notifications.size}")
    }

    // Get notifications for a specific app (e.g., WhatsApp)
    watcher.getNotificationsByApp("com.whatsapp").collect { whatsappMessages ->
        // ...
    }

    // Get only the notifications that were marked as deleted
    watcher.getDeletedNotifications().collect { deletedNotifications ->
        // ...
    }
}
```

### 4\. Calculating Statistics

You can get a comprehensive statistical summary with a single suspend function call.

```kotlin
lifecycleScope.launch {
    // This is a suspend function
    val stats: NotificationStats = NotificationWatcher.getInstance(this@YourActivity).getNotificationStats()

    Log.d("MyApp", "Notifications today: ${stats.notificationsToday}")
    Log.d("MyApp", "Average per day: ${stats.averagePerDay}")
    stats.topApps.firstOrNull()?.let { topApp ->
        Log.d("MyApp", "Top App: ${topApp.appName} with ${topApp.notificationCount} notifications.")
    }
}
```

### 5\. Exporting Data

Export all notifications to a CSV or JSON file. This is a suspend function and should be called from a coroutine.

```kotlin
lifecycleScope.launch(Dispatchers.IO) {
    val watcher = NotificationWatcher.getInstance(this@YourActivity)

    // Export to CSV
    val csvFile = File(filesDir, "all_notifications.csv")
    val csvSuccess = watcher.exportToCsv(csvFile)
    if (csvSuccess) Log.d("MyApp", "Successfully exported to ${csvFile.absolutePath}")

    // Export to JSON
    val jsonFile = File(filesDir, "all_notifications.json")
    val jsonSuccess = watcher.exportToJson(jsonFile)
    if (jsonSuccess) Log.d("MyApp", "Successfully exported to ${jsonFile.absolutePath}")
}
```

### 6\. Using a Real-time Listener

If you need to react to notifications instantly, you can add a `NotificationListener`.

```kotlin
val myListener = object : NotificationListener {
    override fun onNotificationReceived(notification: NotificationData) {
        // A new notification just arrived!
        Log.d("Realtime", "New notification from ${notification.appName}: ${notification.title}")
    }

    override fun onPossibleDeletedMessage(packageName: String, notificationKey: String, deletedTime: Long) {
        // A notification was removed quickly, possibly a deleted message
        Log.w("Realtime", "A message from $packageName might have been deleted.")
    }
}

// Add the listener
val watcher = NotificationWatcher.getInstance(this)
watcher.addListener(myListener)

// Don't forget to remove it when your component is destroyed to avoid memory leaks
// For example, in onDestroy() or onClear() of a ViewModel
// watcher.removeListener(myListener)
```

-----

## API Overview

  - **`NotificationWatcher`**: The main singleton class for interacting with the library.
      - `getInstance(context)`: Gets the singleton instance.
      - `startWatching()` / `stopWatching()`: Manages the lifecycle of the service.
      - `isNotificationAccessGranted()` / `requestNotificationAccess()`: Handles permissions.
      - `getAllNotifications()`: Returns a `Flow<List<NotificationData>>`.
      - `getNotificationStats()`: Suspend function that returns `NotificationStats`.
      - `exportToCsv(file)` / `exportToJson(file)`: Suspend functions for data export.
      - `addListener(listener)` / `removeListener(listener)`: Manages real-time listeners.
  - **`NotificationData`**: A data class representing a single captured notification.
  - **`NotificationStats`**: A data class holding all calculated statistics.

-----

## License

This project is licensed under the MIT License - see the `LICENSE.md` file for details.

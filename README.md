# NotificationWatcherService

A powerful Android library for effortlessly listening, storing, analyzing, and exporting device notifications.

It runs as a persistent foreground service, reliably capturing all incoming notifications — even detecting potentially deleted messages (like in WhatsApp). All data is saved in a local Room database and can be accessed via a clean, Kotlin-first API using `Flow`. The library also offers rich statistical insights and built-in export to CSV/JSON formats.

---

## 📽️ Demo Video

[![Watch the video](https://img.youtube.com/vi/1BElGQRSCg0/0.jpg)](https://youtu.be/1BElGQRSCg0)

---

## Features

- **Persistent Foreground Service**: Ensures the service runs continuously and reliably.
- **Notification Logging**: Saves all notifications locally using Room.
- **Deleted Message Detection**: Identifies messages that are quickly removed (e.g., "This message was deleted").
- **Flexible Querying**: Retrieve notifications by:
  - Application package name
  - Time range
  - Deleted-only
  - All notifications
- **Insightful Statistics**:
  - Total, daily, weekly counts
  - Most active apps
  - Average per day
  - Peak notification hour
- **Export to CSV/JSON**: Backup or analyze your data externally.
- **Real-time Listeners**: React instantly to new/deleted notifications.
- **Kotlin-first API**: Uses Flow, suspend functions, and modern patterns.

---

## Setup

### 1. Add the JitPack repository

In your root `settings.gradle` or `build.gradle`:

```groovy
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' } // Add this line
    }
}
````

### 2. Add the dependency

Replace `VERSION` with the latest release from JitPack:

```groovy
dependencies {
    implementation 'com.github.GalAngel15:NotificationWatcherService:VERSION'
}
```

---

## Permissions

Make sure to declare the following in your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
    tools:ignore="ProtectedPermissions" />
```

Declare the service inside your `<application>` tag:

```xml
<service
    android:name="com.classy.notificationwatcher.service.NotificationWatcherService"
    android:label="NotificationWatcherService"
    android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
    android:exported="true">
    <intent-filter>
        <action android:name="android.service.notification.NotificationListenerService" />
    </intent-filter>
</service>
```

---

## Usage

### 1. Initialize the Library

```kotlin
val watcher = NotificationWatcher.getInstance(context)
if (watcher.isNotificationAccessGranted()) {
    watcher.startWatching()
} else {
    watcher.requestNotificationAccess()
}
```

You can also pass a launch intent to control what happens when the foreground notification is clicked:

```kotlin
watcher.setLaunchIntent(Intent(context, MainActivity::class.java))
```

---

### 2. Observe Notifications via Flow

```kotlin
lifecycleScope.launch {
    watcher.getAllNotifications().collect { list ->
        // Handle list of NotificationData
    }

    watcher.getNotificationsByApp("com.whatsapp").collect { messages ->
        // ...
    }

    watcher.getDeletedNotifications().collect { deleted ->
        // ...
    }
}
```

---

### 3. Get Statistics

```kotlin
val stats = watcher.getNotificationStats()
Log.d("Stats", "Today: ${stats.notificationsToday}, Peak hour: ${stats.peakHour}")
```

---

### 4. Export Data

```kotlin
lifecycleScope.launch(Dispatchers.IO) {
    val csvFile = File(filesDir, "notifications.csv")
    watcher.exportToCsv(csvFile)

    val jsonFile = File(filesDir, "notifications.json")
    watcher.exportToJson(jsonFile)
}
```

---

### 5. Real-Time Listener

```kotlin
val listener = object : NotificationListener {
    override fun onNotificationReceived(notification: NotificationData) {
        Log.d("Realtime", "New from ${notification.appName}")
    }

    override fun onPossibleDeletedMessage(packageName: String, notificationKey: String, deletedTime: Long) {
        Log.w("Realtime", "$packageName may have deleted a message.")
    }
}
watcher.addListener(listener)
```

Don’t forget to remove the listener when appropriate.

---

## Architecture Overview

| Layer       | Class                             | Responsibility                                     |
| ----------- | --------------------------------- | -------------------------------------------------- |
| **UI**      | `MainActivity`                    | Displays notifications and stats                   |
| **Adapter** | `NotificationAdapter`             | Binds notification list to RecyclerView            |
| **Core**    | `NotificationWatcher` (singleton) | Main controller – handles service, data, and logic |
|             | `NotificationStatsCalculator`     | Computes statistics from database                  |
|             | `NotificationPermissionManager`   | Manages permission logic and battery optimization  |
|             | `NotificationStats`               | Data class for analytics summary                   |
| **Data**    | `NotificationData`                | Room entity – single notification                  |
|             | `NotificationTracking`            | Tracks possible deleted messages                   |
|             | `RoomNotificationRepository`      | Repository implementation using Room               |
|             | `NotificationDao`                 | DAO interface for Room                             |
|             | `NotificationDatabase`            | Room database setup                                |
| **Service** | `NotificationWatcherService`      | Listens to system notifications and persists them  |
| **Utils**   | `NotificationExporter`            | Exports data to CSV or JSON                        |
| **Events**  | `NotificationListener`            | Interface for receiving notification events        |

---

## License

This project is licensed under the MIT License – see the [LICENSE.md](LICENSE.md) file for details.

```

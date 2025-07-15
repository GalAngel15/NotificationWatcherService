package com.classy.notificationwatcherservice
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.classy.notificationwatcher.core.NotificationWatcher
import com.classy.notificationwatcher.data.NotificationData
import com.classy.notificationwatcher.service.NotificationListener
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity(), NotificationListener {

    private lateinit var notificationWatcher: NotificationWatcher
    private lateinit var statusText: TextView
    private lateinit var permissionButton: Button
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var exportButton: Button
    private lateinit var statsButton: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var filterSpinner: Spinner
    private lateinit var notificationAdapter: NotificationAdapter
    private lateinit var appFilterSpinner: Spinner
    private var appFilterMap: Map<String, String> = emptyMap() // appName -> packageName
    private val notifications = mutableListOf<NotificationData>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupNotificationWatcher()
        setupRecyclerView()
        setupSpinner()

        NotificationWatcher.getInstance(this).requestBatteryOptimizationDialog(this)

        // If the service is already running and has notification access, start watching notifications
        if (notificationWatcher.isWatching() && notificationWatcher.isNotificationAccessGranted()) {
            notificationWatcher.startWatching() // הפעל מחדש את השירות אם הוא אמור לפעול
        }
        updateUI()
        loadAppFilter()

    }

    private fun initViews() {
        statusText = findViewById(R.id.statusText)
        permissionButton = findViewById(R.id.permissionButton)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        exportButton = findViewById(R.id.exportButton)
        statsButton = findViewById(R.id.statsButton)
        recyclerView = findViewById(R.id.recyclerView)
        filterSpinner = findViewById(R.id.filterSpinner)

        permissionButton.setOnClickListener { requestPermission() }
        startButton.setOnClickListener { startWatching() }
        stopButton.setOnClickListener { stopWatching() }
        exportButton.setOnClickListener { exportData() }
        statsButton.setOnClickListener { showStats() }
        appFilterSpinner = findViewById(R.id.appFilterSpinner)
    }

    private fun setupNotificationWatcher() {
        notificationWatcher = NotificationWatcher.getInstance(this)

        // Define the launch intent for the notification watcher service
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        notificationWatcher.setLaunchIntent(launchIntent) // <--- שנה את השורה הזו (קורא ל-setLaunchIntent של NotificationWatcher)
        notificationWatcher.addListener(this) // <--- הוסף את השורה הזו
    }

    private fun setupRecyclerView() {
        notificationAdapter = NotificationAdapter(notifications)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = notificationAdapter
    }

    private fun setupSpinner() {
        val filterOptions = arrayOf("All Notifications", "Deleted Only", "Today Only", "Last Hour")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, filterOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        filterSpinner.adapter = adapter

        filterSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                loadFilteredNotifications()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun updateUI() {
        val hasPermission = notificationWatcher.isNotificationAccessGranted()
        val isWatching = notificationWatcher.isWatching() // השג את מצב "צופה" מ-NotificationWatcher

        statusText.text = if (hasPermission) {
            "✅ Notification access granted"
        } else {
            "❌ Notification access required"
        }

        permissionButton.isEnabled = !hasPermission
        startButton.isEnabled = hasPermission && !isWatching
        stopButton.isEnabled = hasPermission && isWatching
        exportButton.isEnabled = hasPermission
        statsButton.isEnabled = hasPermission
    }

    private fun requestPermission() {
        notificationWatcher.requestNotificationAccess()
    }

    private fun startWatching() {
        if (notificationWatcher.startWatching()) {
            notificationWatcher.addListener(this)
            Toast.makeText(this, "Started watching notifications", Toast.LENGTH_SHORT).show()
            startButton.isEnabled = false
            stopButton.isEnabled = true
        } else {
            Toast.makeText(this, "Failed to start - check permissions", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopWatching() {
        notificationWatcher.stopWatching()
        notificationWatcher.removeListener(this)
        Toast.makeText(this, "Stopped watching notifications", Toast.LENGTH_SHORT).show()
        startButton.isEnabled = true
        stopButton.isEnabled = false
    }

    private fun exportData() {
        lifecycleScope.launch {
            try {
                val externalDir = getExternalFilesDir(null)
                val timestamp = System.currentTimeMillis()
                val csvFile = File(externalDir, "notifications_${System.currentTimeMillis()}.csv")
                val jsonFile = File(externalDir, "notifications_${System.currentTimeMillis()}.json")

                val csvSuccess = notificationWatcher.exportToCsv(csvFile)
                val jsonSuccess = notificationWatcher.exportToJson(jsonFile)

                val fileToShare = when {
                    csvSuccess -> csvFile
                    jsonSuccess -> jsonFile
                    else -> null
                }
                if (fileToShare != null) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Export successful!", Toast.LENGTH_SHORT).show()
                        shareFile(fileToShare)
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Export failed", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Export error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun shareFile(file: File) {
        val uri = FileProvider.getUriForFile(
            this,
            "${packageName}.provider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (file.name.endsWith(".json")) "application/json" else "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(intent, "Open or share exported file"))
    }


    /**
     * Load the app filter spinner with unique app names from notifications.
     * This method collects all notifications and populates the spinner with distinct app names.
     */
    private fun loadAppFilter() {
        lifecycleScope.launch {
            notificationWatcher.getAllNotifications().collectLatest { allNotifications ->
                val uniqueApps = allNotifications
                    .map { it.appName to it.packageName }
                    .distinctBy { it.second }
                    .sortedBy { it.first }

                appFilterMap = uniqueApps.toMap()

                val appNames = listOf("All Apps") + uniqueApps.map { it.first }

                val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, appNames)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

                appFilterSpinner.adapter = adapter

                appFilterSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                        loadFilteredNotifications()
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
            }
        }
    }

    /**
     * Show notification statistics in a dialog.
     * This method collects stats from the NotificationWatcher and displays them in a formatted dialog.
     */
    private fun showStats() {
        lifecycleScope.launch {
            try {
                val stats = notificationWatcher.getNotificationStats()
                val message = buildString {
                    appendLine("📊 Notification Statistics")
                    appendLine("━━━━━━━━━━━━━━━━━━━━━━")
                    appendLine("Total: ${stats.totalNotifications}")
                    appendLine("Deleted: ${stats.deletedNotifications}")
                    appendLine("Today: ${stats.notificationsToday}")
                    appendLine("This Week: ${stats.notificationsThisWeek}")
                    appendLine("Average/Day: ${"%.1f".format(stats.averagePerDay)}")
                    appendLine("Peak Hour: ${stats.peakHour}:00")
                    appendLine()
                    appendLine("🔥 Top Apps:")
                    stats.topApps.take(3).forEach { app ->
                        appendLine("${app.appName}: ${app.notificationCount} (${app.deletedCount} deleted)")
                    }
                }

                runOnUiThread {
                    androidx.appcompat.app.AlertDialog.Builder(this@MainActivity)
                        .setTitle("Notification Statistics")
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Stats error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
        // Load initial data
        loadFilteredNotifications()

    }

    // NotificationListener implementation
    override fun onNotificationReceived(notification: NotificationData) {
        runOnUiThread {
            Toast.makeText(this, "📱 ${notification.appName}: ${notification.title}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPossibleDeletedMessage(packageName: String, notificationKey: String, deletedTime: Long) {
        runOnUiThread {
            Toast.makeText(this, "🗑️ Possible deleted message detected!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadFilteredNotifications() {
        val selectedFilter = filterSpinner.selectedItemPosition
        val selectedAppName = appFilterSpinner.selectedItem?.toString() ?: "All Apps"
        val selectedPackageName = appFilterMap[selectedAppName]

        lifecycleScope.launch {
            val baseFlow = when (selectedFilter) {
                0 -> notificationWatcher.getAllNotifications()
                1 -> notificationWatcher.getDeletedNotifications()
                2 -> notificationWatcher.getTodaysNotifications()
                3 -> notificationWatcher.getNotificationsFromLastHours(1)
                else -> notificationWatcher.getAllNotifications()
            }

            baseFlow.collectLatest { list ->
                val filteredList = if (selectedPackageName != null) {
                    list.filter { it.packageName == selectedPackageName }
                } else list

                notifications.clear()
                notifications.addAll(filteredList)
                notificationAdapter.notifyDataSetChanged()
            }
        }
    }

}
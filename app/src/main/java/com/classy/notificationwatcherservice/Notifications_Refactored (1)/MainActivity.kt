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
import com.classy.notificationwatcherservice.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity(), NotificationListener {
    private lateinit var notificationWatcher: NotificationWatcher
    private lateinit var binding: ActivityMainBinding
    private lateinit var notificationAdapter: NotificationAdapter
    private lateinit var appFilterSpinner: Spinner
    private var appFilterMap: Map<String, String> = emptyMap() // appName -> packageName
    private val notifications = mutableListOf<NotificationData>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Inflate view binding and set content view
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initViews()
        setupNotificationWatcher()
        setupRecyclerView()
        setupSpinner()

        NotificationWatcher.getInstance(this).requestBatteryOptimizationDialog(this)

        // If the service is already running and has notification access, start watching notifications
        if (notificationWatcher.isWatching() && notificationWatcher.isNotificationAccessGranted()) {
            notificationWatcher.startWatching()
        }
        updateUI()
        loadAppFilter()

    }

    private fun initViews() {
        // Assign view references from binding
        binding.permissionButton.setOnClickListener { requestPermission() }
        binding.startButton.setOnClickListener { startWatching() }
        binding.stopButton.setOnClickListener { stopWatching() }
        binding.exportButton.setOnClickListener { exportData() }
        binding.statsButton.setOnClickListener { showStats() }
        appFilterSpinner = binding.appFilterSpinner
    }

    private fun setupNotificationWatcher() {
        notificationWatcher = NotificationWatcher.getInstance(this)

        // Define the launch intent for the notification watcher service
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        notificationWatcher.setLaunchIntent(launchIntent)
        notificationWatcher.addListener(this)
    }

    private fun setupRecyclerView() {
        notificationAdapter = NotificationAdapter(notifications)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = notificationAdapter
    }

    private fun setupSpinner() {
        val filterOptions = arrayOf("All Notifications", "Deleted Only", "Today Only", "Last Hour")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, filterOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.filterSpinner.adapter = adapter

        binding.filterSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                loadFilteredNotifications()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun updateUI() {
        val hasPermission = notificationWatcher.isNotificationAccessGranted()
        val isWatching = notificationWatcher.isWatching() // השג את מצב "צופה" מ-NotificationWatcher
        binding.statusText.text = if (hasPermission) {
            "✅ Notification access granted"
        } else {
            "❌ Notification access required"
        }
        binding.permissionButton.isEnabled = !hasPermission
        binding.startButton.isEnabled = hasPermission && !isWatching
        binding.stopButton.isEnabled = hasPermission && isWatching
        binding.exportButton.isEnabled = hasPermission
        binding.statsButton.isEnabled = hasPermission
    }

    private fun requestPermission() {
        notificationWatcher.requestNotificationAccess()
    }

    private fun startWatching() {
        if (notificationWatcher.startWatching()) {
            notificationWatcher.addListener(this)
            Toast.makeText(this, "Started watching notifications", Toast.LENGTH_SHORT).show()
            binding.startButton.isEnabled = false
            binding.stopButton.isEnabled = true
        } else {
            Toast.makeText(this, "Failed to start - check permissions", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopWatching() {
        notificationWatcher.stopWatching()
        notificationWatcher.removeListener(this)
        Toast.makeText(this, "Stopped watching notifications", Toast.LENGTH_SHORT).show()
        binding.startButton.isEnabled = true
        binding.stopButton.isEnabled = false
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
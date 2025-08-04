package com.classy.notificationwatcher.core.utils

import android.content.Context
import com.google.gson.GsonBuilder
import com.classy.notificationwatcher.data.NotificationRepository
import com.classy.notificationwatcher.data.NotificationData
import kotlinx.coroutines.flow.first
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class NotificationExporter(private val context: Context) {

    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .create()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    /**
     * Exports all notifications from the repository to a CSV file. The
     * repository abstraction allows the caller to supply any implementation
     * (e.g. Room, in‑memory, remote) without tying the exporter to a specific
     * storage mechanism.
     */
    suspend fun exportToCsv(repository: NotificationRepository, outputFile: File): Boolean {
        return try {
            val notifications = repository.getAllNotifications().first()
            val csvContent = buildCsvContent(notifications)
            outputFile.writeText(csvContent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Exports all notifications from the repository to a JSON file.
     */
    suspend fun exportToJson(repository: NotificationRepository, outputFile: File): Boolean {
        return try {
            val notifications = repository.getAllNotifications().first()
            val jsonContent = gson.toJson(notifications)
            outputFile.writeText(jsonContent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Exports notifications for a specific package to a CSV file.
     */
    suspend fun exportAppToCsv(repository: NotificationRepository, packageName: String, outputFile: File): Boolean {
        return try {
            val notifications = repository.getNotificationsByPackage(packageName).first()
            val csvContent = buildCsvContent(notifications)
            outputFile.writeText(csvContent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Exports only the deleted notifications to a CSV file.
     */
    suspend fun exportDeletedToCsv(repository: NotificationRepository, outputFile: File): Boolean {
        return try {
            val notifications = repository.getDeletedNotifications().first()
            val csvContent = buildCsvContent(notifications)
            outputFile.writeText(csvContent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun buildCsvContent(notifications: List<NotificationData>): String {
        val header = "ID,App Name,Package Name,Title,Text,Timestamp,Is Deleted,Deleted Timestamp,Category,Priority\n"

        val rows = notifications.joinToString("\n") { notification ->
            listOf(
                notification.id.toString(),
                escapeCsv(notification.appName),
                escapeCsv(notification.packageName),
                escapeCsv(notification.title ?: ""),
                escapeCsv(notification.text ?: ""),
                dateFormat.format(Date(notification.timestamp)),
                notification.isDeleted.toString(),
                if (notification.deletedTimestamp != null) dateFormat.format(Date(notification.deletedTimestamp)) else "",
                escapeCsv(notification.category ?: ""),
                notification.priority.toString()
            ).joinToString(",")
        }

        return header + rows
    }

    private fun escapeCsv(text: String): String {
        return "\"${text.replace("\"", "\"\"")}\""
    }
}
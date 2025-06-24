package com.classy.notificationwatcher.core.utils

import android.content.Context
import com.google.gson.GsonBuilder
import com.classy.notificationwatcher.data.NotificationDao
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

    suspend fun exportToCsv(dao: NotificationDao, outputFile: File): Boolean {
        return try {
            val notifications = dao.getAllNotifications().first()
            val csvContent = buildCsvContent(notifications)
            outputFile.writeText(csvContent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun exportToJson(dao: NotificationDao, outputFile: File): Boolean {
        return try {
            val notifications = dao.getAllNotifications().first()
            val jsonContent = gson.toJson(notifications)
            outputFile.writeText(jsonContent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun exportAppToCsv(dao: NotificationDao, packageName: String, outputFile: File): Boolean {
        return try {
            val notifications = dao.getNotificationsByPackage(packageName).first()
            val csvContent = buildCsvContent(notifications)
            outputFile.writeText(csvContent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun exportDeletedToCsv(dao: NotificationDao, outputFile: File): Boolean {
        return try {
            val notifications = dao.getDeletedNotifications().first()
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
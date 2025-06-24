package com.classy.notificationwatcherservice

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.classy.notificationwatcher.data.NotificationData
import java.text.SimpleDateFormat
import java.util.*

class NotificationAdapter(
    private val notifications: List<NotificationData>
) : RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())

    class NotificationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val appName: TextView = itemView.findViewById(R.id.appName)
        val title: TextView = itemView.findViewById(R.id.title)
        val text: TextView = itemView.findViewById(R.id.text)
        val timestamp: TextView = itemView.findViewById(R.id.timestamp)
        val statusIndicator: TextView = itemView.findViewById(R.id.statusIndicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return NotificationViewHolder(view)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val notification = notifications[position]

        holder.appName.text = notification.appName
        holder.title.text = notification.title ?: "No title"
        holder.text.text = notification.text ?: "No content"
        holder.timestamp.text = dateFormat.format(Date(notification.timestamp))

        // Status indicator
        holder.statusIndicator.text = when {
            notification.isDeleted -> "🗑️"
            notification.isOngoing -> "📌"
            else -> "📱"
        }

        // Visual styling for deleted messages
        val alpha = if (notification.isDeleted) 0.6f else 1.0f
        holder.itemView.alpha = alpha
    }

    override fun getItemCount() = notifications.size
}
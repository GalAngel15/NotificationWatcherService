package com.classy.notificationwatcherservice

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.classy.notificationwatcher.data.NotificationData
import com.classy.notificationwatcherservice.databinding.ItemNotificationBinding
import java.text.SimpleDateFormat
import java.util.*

class NotificationAdapter(
    private val notifications: List<NotificationData>
) : RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())

    class NotificationViewHolder(val binding: ItemNotificationBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = ItemNotificationBinding.inflate(layoutInflater, parent, false)
        return NotificationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val notification = notifications[position]
        val binding = holder.binding

        binding.appName.text = notification.appName
        binding.title.text = notification.title ?: "No title"
        binding.text.text = notification.text ?: "No content"
        binding.timestamp.text = dateFormat.format(Date(notification.timestamp))

        // Status indicator
        binding.statusIndicator.text = when {
            notification.isDeleted -> "🗑️"
            notification.isOngoing -> "📌"
            else -> "📱"
        }

        // Visual styling for deleted messages
        val alpha = if (notification.isDeleted) 0.6f else 1.0f
        binding.root.alpha = alpha
    }

    override fun getItemCount() = notifications.size
}
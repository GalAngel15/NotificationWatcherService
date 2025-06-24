package com.classy.notificationwatcher.service

import com.classy.notificationwatcher.data.NotificationData

interface NotificationListener {
    fun onNotificationReceived(notification: NotificationData)
    fun onPossibleDeletedMessage(packageName: String, notificationKey: String, deletedTime: Long)
}
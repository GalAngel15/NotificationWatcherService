package com.classy.notificationwatcher.data

import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over the notification data source. This interface decouples the
 * rest of the code from a concrete database implementation (e.g. Room).
 *
 * Having an interface allows you to swap out the underlying storage (for
 * example, an in‑memory cache or a remote source) without changing the
 * business logic. It also makes the code more testable because a fake
 * repository can be injected into classes that depend on it.
 */
interface NotificationRepository {

    /**
     * Returns a flow of all notifications ordered by newest first.
     */
    fun getAllNotifications(): Flow<List<NotificationData>>

    /**
     * Returns a flow of notifications for a specific package, ordered by
     * newest first.
     */
    fun getNotificationsByPackage(packageName: String): Flow<List<NotificationData>>

    /**
     * Returns a flow of all notifications that were marked as deleted.
     */
    fun getDeletedNotifications(): Flow<List<NotificationData>>

    /**
     * Returns a flow of notifications posted between [startTime] and
     * [endTime], ordered by newest first.
     */
    fun getNotificationsByTimeRange(startTime: Long, endTime: Long): Flow<List<NotificationData>>

    /**
     * Persists a new notification and returns its generated ID. You should
     * provide the [NotificationData] without an ID (it will be auto‑generated).
     */
    suspend fun insertNotification(notification: NotificationData): Long

    /**
     * Updates an existing notification. Use this when you need to change
     * fields on a previously saved notification.
     */
    suspend fun updateNotification(notification: NotificationData)

    /**
     * Marks a notification as deleted by its unique key. The deleted time
     * allows downstream consumers to know when the deletion occurred.
     */
    suspend fun markAsDeleted(notificationKey: String, deletedTime: Long)

    /**
     * Deletes all notifications older than the cutoff time. Useful for
     * housekeeping to prevent unbounded growth of the database.
     */
    suspend fun deleteOldNotifications(cutoffTime: Long)

    /**
     * Creates or updates a tracking entry for a notification. Tracking
     * information is used to detect messages that might have been deleted
     * shortly after posting.
     */
    suspend fun insertOrUpdateTracking(tracking: NotificationTracking)

    /**
     * Retrieves a tracking entry by its notification key, or null if none
     * exists.
     */
    suspend fun getTracking(notificationKey: String): NotificationTracking?

    /**
     * Deactivates a tracking entry. After deactivation the entry is no longer
     * considered when looking for possible deleted messages.
     */
    suspend fun deactivateTracking(notificationKey: String)

    /**
     * Returns all active tracking entries for a given package. These entries
     * can be used to detect when a notification disappears without a
     * corresponding removal callback.
     */
    suspend fun getActiveTrackingForPackage(packageName: String): List<NotificationTracking>


    /**
     *
     */
    fun resetSession()

}
package com.classy.notificationwatcher.data

/**
 * Concrete implementation of [NotificationRepository] backed by a Room DAO.
 *
 * This class delegates all calls to a [NotificationDao] instance. In
 * production code it can be created via dependency injection by providing
 * the DAO from [NotificationDatabase].
 */
class RoomNotificationRepository(private val dao: NotificationDao) : NotificationRepository {
    private val lastNotifications = mutableListOf<NotificationData>()

    override fun getAllNotifications() = dao.getAllNotifications()

    override fun getNotificationsByPackage(packageName: String) =
        dao.getNotificationsByPackage(packageName)

    override fun getDeletedNotifications() = dao.getDeletedNotifications()

    override fun getNotificationsByTimeRange(startTime: Long, endTime: Long) =
        dao.getNotificationsByTimeRange(startTime, endTime)

    override suspend fun insertNotification(notification: NotificationData): Long {
        if (isDuplicate(notification)) return -1

        lastNotifications.add(notification)
        if (lastNotifications.size > 20) lastNotifications.removeAt(0)

        return dao.insertNotification(notification)
    }

    private fun isDuplicate(newNotification: NotificationData): Boolean {
        return lastNotifications.any {
            it.title == newNotification.title &&
            it.packageName == newNotification.packageName &&
            it.text == newNotification.text && kotlin.math.abs(it.timestamp - newNotification.timestamp) <= 700
        }
    }

    override suspend fun updateNotification(notification: NotificationData) {
        dao.updateNotification(notification)
    }

    override suspend fun markAsDeleted(notificationKey: String, deletedTime: Long) {
        dao.markAsDeleted(notificationKey, deletedTime)
    }

    override suspend fun deleteOldNotifications(cutoffTime: Long) {
        dao.deleteOldNotifications(cutoffTime)
    }

    override suspend fun insertOrUpdateTracking(tracking: NotificationTracking) {
        dao.insertOrUpdateTracking(tracking)
    }

    override suspend fun getTracking(notificationKey: String): NotificationTracking? =
        dao.getTracking(notificationKey)

    override suspend fun deactivateTracking(notificationKey: String) {
        dao.deactivateTracking(notificationKey)
    }

    override suspend fun getActiveTrackingForPackage(packageName: String): List<NotificationTracking> =
        dao.getActiveTrackingForPackage(packageName)

    override fun resetSession() {
        lastNotifications.clear()
    }

}
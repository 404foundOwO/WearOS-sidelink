package com.found404.sidelink.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    fun getAllNotifications(): Flow<List<NotificationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity)

    @Transaction
    suspend fun syncNotifications(notifications: List<NotificationEntity>) {
        deleteAllNotifications()
        notifications.forEach { insertNotification(it) }
        deleteOldNotifications(50)
    }

    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun deleteNotification(id: String)

    @Query("DELETE FROM notifications")
    suspend fun deleteAllNotifications()

    @Query("DELETE FROM notifications WHERE id NOT IN (SELECT id FROM notifications ORDER BY timestamp DESC LIMIT :limit)")
    suspend fun deleteOldNotifications(limit: Int)
}

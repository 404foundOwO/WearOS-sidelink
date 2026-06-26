package com.found404.sidelink.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    fun getAllNotifications(): Flow<List<NotificationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: NotificationEntity)

    @Delete
    suspend fun delete(notification: NotificationEntity)

    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM notifications")
    suspend fun deleteAll()

    @Transaction
    suspend fun syncNotifications(notifications: List<NotificationEntity>) {
        deleteAll()
        notifications.forEach { insert(it) }
        deleteOldNotifications(50)
    }

    @Query("DELETE FROM notifications WHERE id NOT IN (SELECT id FROM notifications ORDER BY timestamp DESC LIMIT :limit)")
    suspend fun deleteOldNotifications(limit: Int)
}

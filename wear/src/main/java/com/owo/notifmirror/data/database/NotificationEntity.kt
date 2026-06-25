package com.found404.sidelink.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey val id: String,
    val packageName: String,
    val title: String?,
    val text: String?,
    val timestamp: Long,
    val iconBase64: String?
)

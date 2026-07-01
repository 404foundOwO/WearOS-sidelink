package com.found404.sidelink.data.model

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.found404.sidelink.data.local.NotificationEntity
import java.io.ByteArrayOutputStream

data class NotifMessage(
    val sender: String,
    val text: String,
    val timestamp: Long
)

data class NotifAction(
    val index: Int,
    val label: String
)

data class NotificationData(
    val id: String,
    val packageName: String,
    val appName: String? = null,
    val title: String?,
    val text: String?,
    val icon: Bitmap?,
    val timestamp: Long = System.currentTimeMillis(),
    val hasReply: Boolean = false,
    val key: String = id,
    val messages: List<NotifMessage> = emptyList(),
    val actions: List<NotifAction> = emptyList(),
    val progressMax: Int = 0,
    val progressCurrent: Int = 0,
    val progressIndeterminate: Boolean = false
) {
    fun toEntity(): NotificationEntity {
        val stream = ByteArrayOutputStream()
        icon?.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val byteArray = if (icon != null) stream.toByteArray() else null
        return NotificationEntity(
            id = id,
            packageName = packageName,
            title = title,
            text = text,
            icon = byteArray,
            timestamp = timestamp
        )
    }

    companion object {
        fun fromEntity(entity: NotificationEntity): NotificationData {
            val bitmap = entity.icon?.let {
                BitmapFactory.decodeByteArray(it, 0, it.size)
            }
            return NotificationData(
                id = entity.id,
                packageName = entity.packageName,
                title = entity.title,
                text = entity.text,
                icon = bitmap,
                timestamp = entity.timestamp
            )
        }
    }
}

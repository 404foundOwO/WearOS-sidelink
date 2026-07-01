package com.found404.sidelink.data.model

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable

@Immutable
data class NotificationData(
    val id: String,
    val packageName: String,
    val appName: String? = null,
    val title: String?,
    val text: String?,
    val icon: Bitmap?,
    val timestamp: Long = System.currentTimeMillis(),
    val iconBase64: String? = null,
    val hasReply: Boolean = false,
    val progressMax: Int = 0,
    val progressCurrent: Int = 0,
    val progressIndeterminate: Boolean = false
)

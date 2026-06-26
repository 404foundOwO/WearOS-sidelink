package com.found404.sidelink.data.model

import android.graphics.Bitmap

data class MediaData(
    val title: String?,
    val artist: String?,
    val album: String?,
    val artwork: Bitmap?,
    val isPlaying: Boolean,
    val packageName: String?
)

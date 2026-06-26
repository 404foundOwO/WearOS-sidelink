package com.found404.sidelink.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.MediaBrowserServiceCompat
import com.found404.sidelink.communication.BluetoothCommunicationManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MirrorMediaBrowserService : MediaBrowserServiceCompat() {

    @Inject lateinit var commManager: BluetoothCommunicationManager

    companion object {
        private const val TAG = "MirrorMediaBrowser"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "media_service_channel"
    }

    override fun onCreate() {
        super.onCreate()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID, "Media Playback", NotificationManager.IMPORTANCE_LOW
                )
                (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .createNotificationChannel(channel)
            }

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Media Mirror")
                .setContentText("Controlling remote media")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .build()

            startForeground(NOTIFICATION_ID, notification)

            // Safely set session token — wrapped so a failure here doesn't crash the app
            try {
                val token = commManager.getSessionToken()
                if (token != null) {
                    sessionToken = token
                } else {
                    Log.w(TAG, "Session token is null, skipping")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set session token", e)
            }

        } catch (e: Exception) {
            Log.e(TAG, "onCreate failed", e)
            stopSelf()
        }
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot = BrowserRoot("root", null)

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        result.sendResult(mutableListOf())
    }
}

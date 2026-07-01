package com.found404.sidelink.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.found404.sidelink.communication.BluetoothCommunicationManager
import com.found404.sidelink.shared.CommunicationConstants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import org.json.JSONObject
import javax.inject.Inject

@AndroidEntryPoint
class WearMirrorService : Service() {

    @Inject lateinit var commManager: BluetoothCommunicationManager

    companion object {
        private const val TAG = "WearService"
        private const val NOTIFICATION_ID = 101
        private const val CHANNEL_ID = "wear_mirror_channel"
        private const val LOW_BATTERY_THRESHOLD = 20
        private const val FULL_BATTERY_THRESHOLD = 100
    }

    private var lastBatteryLevel = -1
    private var lowBatteryNotified = false
    private var fullBatteryNotified = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val percent = if (scale > 0) (level * 100 / scale) else level
            if (percent == lastBatteryLevel) return
            lastBatteryLevel = percent

            // Send battery level to phone over BT
            val json = JSONObject().apply {
                put(CommunicationConstants.KEY_TYPE, CommunicationConstants.TYPE_BATTERY_STATUS)
                put(CommunicationConstants.KEY_BATTERY_LEVEL, percent)
            }
            commManager.sendBatteryStatus(json.toString())

            // Reset flags when battery changes significantly
            if (percent > LOW_BATTERY_THRESHOLD + 5) lowBatteryNotified = false
            if (percent < FULL_BATTERY_THRESHOLD - 5) fullBatteryNotified = false
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        try {
            createNotificationChannel()
            startForegroundServiceSafe()
            commManager.startServer()
            registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

            // Update foreground notification with live connection status
            serviceScope.launch {
                commManager.connectionStatus.collect { status ->
                    val text = when (status) {
                        com.found404.sidelink.communication.ConnectionStatus.CONNECTED -> "Phone connected"
                        com.found404.sidelink.communication.ConnectionStatus.WAITING -> "Waiting for phone · Keep-alive"
                        com.found404.sidelink.communication.ConnectionStatus.DISCONNECTED -> "Disconnected"
                    }
                    getSystemService(NotificationManager::class.java)
                        ?.notify(NOTIFICATION_ID, createNotification("Service active", text))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fatal Error in Service", e)
            stopSelf()
        }
    }

    private fun startForegroundServiceSafe() {
        try {
            val notification = createNotification("Service active", "Waiting for phone · Keep-alive")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Foreground failed", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sidelink Keep-Alive",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Keep-alive service to maintain notification mirroring. Safe to ignore."
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(title: String, content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try { unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
        serviceScope.cancel()
        commManager.stop()
        super.onDestroy()
    }
}

package com.found404.sidelink.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.found404.sidelink.communication.BluetoothCommunicationManager
import com.found404.sidelink.data.model.MediaData
import com.found404.sidelink.data.model.NotificationData
import com.found404.sidelink.data.model.NotifMessage
import com.found404.sidelink.data.model.NotifAction
import com.found404.sidelink.data.repository.MirrorRepository
import com.found404.sidelink.data.repository.SettingsRepository
import com.found404.sidelink.shared.CommunicationConstants
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class SidelinkService : NotificationListenerService(), BluetoothCommunicationManager.BluetoothMessageListener {

    private var mediaSessionManager: MediaSessionManager? = null
    private val mediaControllers = mutableMapOf<MediaSession.Token, MediaController>()
    private val callbacks = mutableMapOf<MediaSession.Token, MediaController.Callback>()
    
    private lateinit var communicationManager: BluetoothCommunicationManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var settingsRepository: SettingsRepository
    private val audioManager: AudioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    // Cache of live StatusBarNotifications so action PendingIntents can be fired from the watch
    private val activeSbnCache = mutableMapOf<String, android.service.notification.StatusBarNotification>()
    private var currentArtQuality = 240
    private var mirrorMediaEnabled = true
    private var maxMirroredNotifications = 50
    private var showWatchBattery = true
    private var watchBatteryLevel: Int? = null

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        updateMediaControllers(controllers)
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        try {
            MirrorRepository.initialize(this)
            settingsRepository = SettingsRepository(this)
            communicationManager = BluetoothCommunicationManager.getInstance(this)
            communicationManager.listener = this
            
            createNotificationChannel()
            startForegroundService()

            mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as? MediaSessionManager
            
            communicationManager.ensureStarted(settingsRepository)

            serviceScope.launch {
                settingsRepository.albumArtQuality.collect { quality ->
                    currentArtQuality = quality
                }
            }

            serviceScope.launch {
                settingsRepository.mirrorMediaEnabled.collect { mirrorMediaEnabled = it }
            }

            serviceScope.launch {
                settingsRepository.maxMirroredNotifications.collect { maxMirroredNotifications = it }
            }

            serviceScope.launch {
                settingsRepository.showWatchBattery.collect { showWatchBattery = it }
            }
            
            MirrorRepository.activeMedia.onEach { mediaData ->
                if (mirrorMediaEnabled) {
                    communicationManager.updateMediaStatus(mediaData)
                } else {
                    communicationManager.updateMediaStatus(null)
                }
            }.launchIn(serviceScope)

            communicationManager.connectionState.onEach { connected ->
                if (connected) syncNotifications()
            }.launchIn(serviceScope)

        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("mirror_service", "Mirror Service", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun startForegroundService() {
        try {
            val notification = buildForegroundNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            } else {
                startForeground(1, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground", e)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val batteryText = if (showWatchBattery && watchBatteryLevel != null) {
            " · Watch ${watchBatteryLevel}%"
        } else ""
        return NotificationCompat.Builder(this, "mirror_service")
            .setContentTitle("Service active")
            .setContentText("Syncing with watch$batteryText")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun updateForegroundNotification() {
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager?.notify(1, buildForegroundNotification())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update foreground notification", e)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        syncNotifications()
        
        try {
            mediaSessionManager?.addOnActiveSessionsChangedListener(
                sessionListener,
                ComponentName(this, SidelinkService::class.java)
            )
            updateMediaControllers(mediaSessionManager?.getActiveSessions(ComponentName(this, SidelinkService::class.java)))
        } catch (e: Exception) {
            Log.e(TAG, "Media sessions failed", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            communicationManager.listener = null
            mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionListener)
            serviceScope.cancel()
        } catch (e: Exception) {}
    }

    override fun onConnectionStateChanged(connected: Boolean) {
        if (connected) syncNotifications()
    }

    override fun onDismissReceived(notificationId: String) {
        try {
            cancelNotification(notificationId)
        } catch (e: Exception) {}
    }

    override fun onReplyReceived(notificationId: String, message: String) {
        sendReply(notificationId, message)
    }

    override fun onMediaActionReceived(action: String) {
        val controllers = mediaControllers.values
        val controller = controllers.find { it.playbackState?.state == PlaybackState.STATE_PLAYING } 
                        ?: controllers.firstOrNull()
                        ?: mediaSessionManager?.getActiveSessions(ComponentName(this, SidelinkService::class.java))?.firstOrNull()

        if (controller == null) return

        controller.transportControls?.let { controls ->
            when (action) {
                CommunicationConstants.ACTION_PLAY -> controls.play()
                CommunicationConstants.ACTION_PAUSE -> controls.pause()
                CommunicationConstants.ACTION_NEXT -> controls.skipToNext()
                CommunicationConstants.ACTION_PREVIOUS -> controls.skipToPrevious()
            }
        }
    }

    override fun onActionTriggerReceived(notificationId: String, actionIndex: Int) {
        try {
            val sbn = activeSbnCache[notificationId] ?: return
            val action = sbn.notification.actions?.getOrNull(actionIndex) ?: return
            // Only fire actions without RemoteInput (reply is handled separately)
            if (action.remoteInputs.isNullOrEmpty()) {
                action.actionIntent.send()
                Log.d(TAG, "Fired action '${ action.title}' on $notificationId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "onActionTriggerReceived failed", e)
        }
    }

    override fun onWatchBatteryReceived(level: Int) {
        if (level >= 0) {
            watchBatteryLevel = level
            if (showWatchBattery) updateForegroundNotification()
        }
    }

    override fun onVolumeDeltaReceived(delta: Float) {
        val direction = if (delta > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val percentage = (current.toFloat() / max * 100).toInt()
        communicationManager.sendVolumeState(percentage)
    }

    private fun sendReply(notificationId: String, replyText: String) {
        val active = try { activeNotifications } catch (e: Exception) { null }
        val sbn = active?.find { it.key == notificationId } ?: return
        val action = sbn.notification.actions?.find { it.remoteInputs?.isNotEmpty() == true } ?: return
        val results = Bundle().apply { putCharSequence(action.remoteInputs[0].resultKey, replyText) }
        val intent = Intent()
        RemoteInput.addResultsToIntent(action.remoteInputs, intent, results)
        try { action.actionIntent.send(this, 0, intent) } catch (e: Exception) { Log.e(TAG, "Reply failed", e) }
    }

    private fun shouldMirror(sbn: StatusBarNotification, blacklisted: Set<String>): Boolean {
        val pkg = sbn.packageName
        if (blacklisted.contains(pkg)) return false
        if (pkg == packageName) return false
        if (pkg == "android" || pkg == "com.android.systemui") return false

        val flags = sbn.notification.flags
        val isOngoing = (flags and Notification.FLAG_ONGOING_EVENT) != 0
        val isNoClear = (flags and Notification.FLAG_NO_CLEAR) != 0
        val isGroupSummary = (flags and Notification.FLAG_GROUP_SUMMARY) != 0
        // Allow ongoing notifications through if they have progress (downloads, uploads, etc.)
        val hasProgress = sbn.notification.extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0
                || sbn.notification.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)

        return !isGroupSummary && (!isOngoing && !isNoClear || hasProgress)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn == null) return
        activeSbnCache[sbn.key] = sbn
        serviceScope.launch {
            try {
                val blacklisted = settingsRepository.blacklistedPackages.first()
                
                if (!shouldMirror(sbn, blacklisted)) {
                    MirrorRepository.removeNotification(sbn.key)
                    communicationManager.dismissNotification(sbn.key)
                    return@launch
                }

                val data = sbn.toNotificationData()
                if (data != null) {
                    MirrorRepository.addNotification(data, maxMirroredNotifications)
                    communicationManager.sendNotification(data)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error onNotificationPosted", e)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn == null) return
        activeSbnCache.remove(sbn.key)
        MirrorRepository.removeNotification(sbn.key)
        communicationManager.dismissNotification(sbn.key)
    }

    private fun syncNotifications() {
        serviceScope.launch {
            try {
                val active = try { activeNotifications } catch (e: Exception) { null } ?: emptyArray()
                val blacklisted = settingsRepository.blacklistedPackages.first()
                val dataList = active.filter { shouldMirror(it, blacklisted) }
                                     .mapNotNull { it.toNotificationData() }
                MirrorRepository.updateNotifications(dataList, maxMirroredNotifications)
                communicationManager.sendSyncAll(dataList.map { it.id })
                dataList.forEach { communicationManager.sendNotification(it) }
            } catch (e: Exception) {
                Log.e(TAG, "Sync failed", e)
            }
        }
    }

    private fun updateMediaControllers(controllers: List<MediaController>?) {
        clearMediaControllers()
        controllers?.forEach { controller ->
            val callback = object : MediaController.Callback() {
                private var lastEncodedTrack: String? = null
                
                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    val trackId = "${metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)}${metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)}"
                    val shouldReencode = trackId != lastEncodedTrack
                    if (shouldReencode) lastEncodedTrack = trackId
                    updateActiveMedia(controller, shouldReencode)
                }
                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    updateActiveMedia(controller, false)
                }
            }
            controller.registerCallback(callback)
            mediaControllers[controller.sessionToken] = controller
            callbacks[controller.sessionToken] = callback
        }
        updateActiveMedia(controllers?.find { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: controllers?.firstOrNull(), true)
    }

    private fun clearMediaControllers() {
        mediaControllers.forEach { (token, controller) -> 
            try { callbacks[token]?.let { controller.unregisterCallback(it) } } catch (e: Exception) {}
        }
        mediaControllers.clear()
        callbacks.clear()
    }

    private fun updateActiveMedia(controller: MediaController?, reencodeArt: Boolean) {
        if (controller == null) { MirrorRepository.updateMedia(null); return }
        val metadata = controller.metadata
        
        serviceScope.launch(Dispatchers.Default) {
            val art = try { 
                val raw = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                raw?.let { scaleBitmap(it, currentArtQuality) }
            } catch (e: Exception) { null }

            val mediaData = MediaData(
                title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),
                artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST),
                album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM),
                artwork = art,
                isPlaying = controller.playbackState?.state == PlaybackState.STATE_PLAYING,
                packageName = controller.packageName
            )
            withContext(Dispatchers.Main) {
                MirrorRepository.updateMedia(mediaData)
            }
        }
    }

    private suspend fun scaleBitmap(bm: Bitmap, maxSize: Int): Bitmap = withContext(Dispatchers.Default) {
        var width = bm.width
        var height = bm.height
        val bitmapRatio = width.toFloat() / height.toFloat()
        if (bitmapRatio > 1) {
            width = maxSize
            height = (width / bitmapRatio).toInt()
        } else {
            height = maxSize
            width = (height * bitmapRatio).toInt()
        }
        Bitmap.createScaledBitmap(bm, width, height, true)
    }

    private suspend fun StatusBarNotification.toNotificationData(): NotificationData? = withContext(Dispatchers.Default) {
        try {
            val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            if (title == null && text == null) return@withContext null

            // Extract MessagingStyle if available (WhatsApp, Telegram, Messages, etc.)
            val messages: List<NotifMessage> = try {
                val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
                style?.messages?.map<NotificationCompat.MessagingStyle.Message, NotifMessage> { msg ->
                    NotifMessage(
                        sender = msg.person?.name?.toString() ?: msg.sender?.toString() ?: title ?: "",
                        text = msg.text?.toString() ?: "",
                        timestamp = msg.timestamp
                    )
                } ?: emptyList()
            } catch (e: Exception) { emptyList() }

            val icon = try {
                val userContext = try { createPackageContext(packageName, 0) } catch (e: Exception) { this@SidelinkService }
                val iconObj = notification.getLargeIcon() ?: notification.smallIcon
                iconObj?.loadDrawable(userContext)?.let { drawableToBitmap(it) }
            } catch (e: Exception) {
                try { packageManager.getApplicationIcon(packageName).let { drawableToBitmap(it) } } catch (ignore: Exception) { null }
            }

            val hasReply = notification.actions?.any { it.remoteInputs?.isNotEmpty() == true } == true
            // Extract non-reply actions (reply is handled via RemoteInput separately)
            val actions = notification.actions?.mapIndexedNotNull { index, action ->
                if (action.remoteInputs.isNullOrEmpty() && !action.title.isNullOrBlank()) {
                    NotifAction(index = index, label = action.title.toString())
                } else null
            } ?: emptyList()
            val resolvedAppName = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString() } catch (e: Exception) { packageName }

            // Extract progress info if present
            val extras = notification.extras
            val progressMax = extras.getInt(android.app.Notification.EXTRA_PROGRESS_MAX, 0)
            val progressCurrent = extras.getInt(android.app.Notification.EXTRA_PROGRESS, 0)
            val progressIndeterminate = extras.getBoolean(android.app.Notification.EXTRA_PROGRESS_INDETERMINATE, false)

            NotificationData(id = key, packageName = packageName, appName = resolvedAppName, title = title, text = text, icon = icon, hasReply = hasReply, key = key, messages = messages, actions = actions, progressMax = progressMax, progressCurrent = progressCurrent, progressIndeterminate = progressIndeterminate)
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun drawableToBitmap(drawable: Drawable): Bitmap = withContext(Dispatchers.Default) {
        if (drawable is BitmapDrawable) {
            val bmp = drawable.bitmap
            if (bmp.config == Bitmap.Config.ARGB_8888) return@withContext bmp
            return@withContext bmp.copy(Bitmap.Config.ARGB_8888, false)
        }
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 48
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 48
        val bitmap = Bitmap.createBitmap(width.coerceAtMost(96), height.coerceAtMost(96), Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        bitmap
    }

    companion object {
        private const val TAG = "SidelinkPhoneService"
    }
}

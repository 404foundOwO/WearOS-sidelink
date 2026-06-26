package com.found404.sidelink.communication

import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.found404.sidelink.data.model.NotificationData
import com.found404.sidelink.data.repository.WearMirrorRepository
import com.found404.sidelink.shared.CommunicationConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import org.json.JSONObject
import java.io.*
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SidelinkWearBT"
private const val NOTIFICATION_ID_MEDIA = 999
private const val READ_TIMEOUT_MS = 120_000L // 2× default keep-alive window

enum class ConnectionStatus {
    DISCONNECTED,
    WAITING,
    CONNECTED
}

@Singleton
class BluetoothCommunicationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: WearMirrorRepository
) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    // Use a dedicated scope that survives service restarts (singleton lives as long as process)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var serverJob: Job? = null
    @Volatile private var watchdogJob: Job? = null
    @Volatile private var serverSocket: BluetoothServerSocket? = null
    @Volatile private var clientSocket: BluetoothSocket? = null
    @Volatile private var writer: PrintWriter? = null
    @Volatile private var reader: BufferedReader? = null

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    val connectionState: StateFlow<Boolean> = _connectionStatus
        .map { it == ConnectionStatus.CONNECTED }
        .stateIn(scope, SharingStarted.Eagerly, false)

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _volumePercentage = MutableStateFlow<Int?>(null)
    val volumePercentage: StateFlow<Int?> = _volumePercentage.asStateFlow()

    internal var mediaSession: MediaSessionCompat? = null

    companion object {
        @Volatile private var INSTANCE: BluetoothCommunicationManager? = null
        fun getInstance(context: Context): BluetoothCommunicationManager {
            return INSTANCE ?: throw IllegalStateException("Not initialized via Hilt")
        }
    }

    init {
        INSTANCE = this
        createNotificationChannel()
    }

    fun getSessionToken(): MediaSessionCompat.Token? {
        if (mediaSession == null) initMediaSession()
        return mediaSession?.sessionToken
    }

    private fun initMediaSession() {
        try {
            mediaSession = MediaSessionCompat(context, "SidelinkMedia").apply {
                setFlags(
                    MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
                )
                setCallback(object : MediaSessionCompat.Callback() {
                    override fun onPlay()           { sendMediaAction(CommunicationConstants.ACTION_PLAY) }
                    override fun onPause()          { sendMediaAction(CommunicationConstants.ACTION_PAUSE) }
                    override fun onSkipToNext()     { sendMediaAction(CommunicationConstants.ACTION_NEXT) }
                    override fun onSkipToPrevious() { sendMediaAction(CommunicationConstants.ACTION_PREVIOUS) }
                })
                isActive = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaSession init failed", e)
        }
    }

    // ── Server lifecycle ──────────────────────────────────────────────────────

    /**
     * Safe to call multiple times — always cancels any previous server job
     * and starts fresh. This fixes the "needs multiple restarts" bug where
     * the singleton's serverJob was still running (or stuck) from a previous
     * service instance.
     */
    fun startServer() {
        Log.d(TAG, "SERVER: startServer — cancelling any previous job")
        serverJob?.cancel()
        closeServerSocket()
        closeClientSocket()

        serverJob = scope.launch {
            acceptLoop()
        }
    }

    fun stop() {
        Log.d(TAG, "SERVER: stop()")
        serverJob?.cancel()
        serverJob = null
        closeServerSocket()
        closeClientSocket()
        mediaSession?.release()
        mediaSession = null
    }

    // ── Accept loop ───────────────────────────────────────────────────────────

    private suspend fun acceptLoop() = coroutineScope {
        while (isActive) {
            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
                _lastError.value = "Bluetooth disabled"
                delay(5000)
                continue
            }

            try {
                if (serverSocket == null) {
                    openServerSocket()
                }

                if (serverSocket == null) {
                    Log.e(TAG, "SERVER: Could not open server socket, retrying in 3s")
                    delay(3000)
                    continue
                }

                _connectionStatus.value = ConnectionStatus.WAITING
                Log.d(TAG, "SERVER: waiting for accept()...")
                
                val socket = withContext(Dispatchers.IO) {
                    serverSocket?.accept()
                }

                if (socket != null) {
                    Log.d(TAG, "SERVER: connection accepted from ${socket.remoteDevice?.address}")
                    serveClient(socket) // Strictly sequential handling
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "SERVER: loop error: ${e.message}")
                closeServerSocket()
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
                delay(2000)
            }
        }
    }

    private fun openServerSocket() {
        try {
            // Priority: Insecure RFCOMM on Channel 1 (Most compatible with Galaxy Watch & Wear OS)
            val m = bluetoothAdapter!!.javaClass.getMethod("listenUsingRfcommOn", Int::class.java)
            serverSocket = m.invoke(bluetoothAdapter, 1) as BluetoothServerSocket
            Log.d(TAG, "SERVER: socket opened via reflection (ch 1)")
        } catch (e: Exception) {
            Log.w(TAG, "SERVER: reflection failed, trying insecure UUID")
            try {
                serverSocket = bluetoothAdapter!!.listenUsingInsecureRfcommWithServiceRecord(
                    CommunicationConstants.DEFAULT_BLUETOOTH_NAME,
                    CommunicationConstants.UUID_NOTIF_MIRROR
                )
                Log.d(TAG, "SERVER: socket opened via insecure UUID")
            } catch (e2: Exception) {
                Log.e(TAG, "SERVER: all socket open methods failed: ${e2.message}")
            }
        }
    }

    // ── Client session ────────────────────────────────────────────────────────

    private suspend fun serveClient(socket: BluetoothSocket) {
        clientSocket = socket
        try {
            writer = PrintWriter(BufferedWriter(OutputStreamWriter(socket.outputStream)), true)
            reader = BufferedReader(InputStreamReader(socket.inputStream))

            _connectionStatus.value = ConnectionStatus.CONNECTED
            _lastError.value = null
            Log.d(TAG, "SERVER: client session started")

            sendPong() // Immediate handshake response
            startWatchdog()

            withContext(Dispatchers.IO) {
                while (isActive && _connectionStatus.value == ConnectionStatus.CONNECTED) {
                    val line = reader?.readLine() ?: break
                    resetWatchdog()
                    if (line.isNotBlank()) {
                        scope.launch { handleIncomingData(line) }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "SERVER: session error: ${e.message}")
        } finally {
            Log.d(TAG, "SERVER: cleaning up session")
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
            closeClientSocket()
        }
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (isActive && _connectionStatus.value == ConnectionStatus.CONNECTED) {
                delay(READ_TIMEOUT_MS)
                Log.w(TAG, "SERVER: Watchdog timeout - no data from phone. Closing.")
                closeClientSocket()
            }
        }
    }

    private fun resetWatchdog() {
        startWatchdog()
    }

    // ── Socket close helpers ──────────────────────────────────────────────────

    private fun closeServerSocket() {
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
    }

    private fun closeClientSocket() {
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        watchdogJob?.cancel()
        try { clientSocket?.close() } catch (_: Exception) {}
        clientSocket = null
        writer = null
        reader = null
    }

    // ── Incoming message handling ─────────────────────────────────────────────

    private var lastMediaData: com.found404.sidelink.data.model.MediaData? = null
    private var ongoingTimeoutJob: Job? = null
    private var lastTrackId: String? = null

    private suspend fun handleIncomingData(jsonString: String) = withContext(Dispatchers.Default) {
        try {
            val json = JSONObject(jsonString)
            val type = json.optString(CommunicationConstants.KEY_TYPE)

            when (type) {
                CommunicationConstants.TYPE_PING -> sendPong()

                CommunicationConstants.TYPE_NOTIFICATION -> {
                    val data = NotificationData(
                        id          = json.getString(CommunicationConstants.KEY_ID),
                        packageName = json.getString(CommunicationConstants.KEY_PACKAGE),
                        appName     = json.optString(CommunicationConstants.KEY_APP_NAME).takeIf { it.isNotEmpty() },
                        title       = json.optString(CommunicationConstants.KEY_TITLE),
                        text        = json.optString(CommunicationConstants.KEY_TEXT),
                        icon        = null,
                        timestamp   = json.getLong(CommunicationConstants.KEY_TIMESTAMP),
                        iconBase64  = json.optString(CommunicationConstants.KEY_ICON),
                        hasReply    = json.optBoolean(CommunicationConstants.KEY_HAS_REPLY, false)
                    )
                    repository.addOrUpdateNotification(data)
                    withContext(Dispatchers.Main) {
                        showNativeNotification(data)
                        sendNotifAck(data.id)
                    }
                }

                CommunicationConstants.TYPE_MEDIA_STATUS -> {
                    val currentTrackId = json.optString("trackId")
                    val artworkBase64  = json.optString(CommunicationConstants.KEY_ARTWORK)
                    val mediaData = com.found404.sidelink.data.model.MediaData(
                        title       = json.optString(CommunicationConstants.KEY_TITLE),
                        artist      = json.optString(CommunicationConstants.KEY_ARTIST),
                        album       = json.optString(CommunicationConstants.KEY_ALBUM),
                        artwork     = null,
                        isPlaying   = json.optBoolean(CommunicationConstants.KEY_IS_PLAYING, false),
                        packageName = json.optString(CommunicationConstants.KEY_PACKAGE),
                        artworkBase64 = artworkBase64
                    )
                    if (mediaData != lastMediaData || currentTrackId != lastTrackId) {
                        lastMediaData = mediaData
                        lastTrackId   = currentTrackId
                        repository.updateMedia(mediaData)
                        withContext(Dispatchers.Main) {
                            updateMediaSession(mediaData)
                            showNativeMediaNotification(mediaData)
                            handleOngoingLifecycle(mediaData.isPlaying)
                        }
                    }
                }

                CommunicationConstants.TYPE_MEDIA_CLEARED -> {
                    lastMediaData = null
                    repository.updateMedia(null)
                    withContext(Dispatchers.Main) {
                        mediaSession?.isActive = false
                        cancelNativeMediaNotification()
                    }
                }

                CommunicationConstants.TYPE_DISMISS -> {
                    val id = json.getString(CommunicationConstants.KEY_ID)
                    repository.removeNotification(id)
                    withContext(Dispatchers.Main) { 
                        cancelNativeNotification(id)
                        sendNotifAck(id)
                    }
                }

                CommunicationConstants.TYPE_SYNC_ALL -> {
                    val ids = json.optJSONArray(CommunicationConstants.KEY_DATA)
                    if (ids != null) {
                        val idList = mutableSetOf<String>()
                        for (i in 0 until ids.length()) {
                            idList.add(ids.getString(i))
                        }
                        val currentNotifs = repository.notifications.first()
                        currentNotifs.forEach { notif ->
                            if (!idList.contains(notif.id)) {
                                repository.removeNotification(notif.id)
                                withContext(Dispatchers.Main) { cancelNativeNotification(notif.id) }
                            }
                        }
                    }
                }

                CommunicationConstants.TYPE_VOLUME_STATE -> {
                    _volumePercentage.value = json.optInt(CommunicationConstants.KEY_VALUE)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "SERVER: parse error: ${e.message}")
        }
    }

    // ── Send helpers ──────────────────────────────────────────────────────────

    fun sendReply(id: String, text: String) {
        send(JSONObject().apply {
            put(CommunicationConstants.KEY_TYPE, CommunicationConstants.TYPE_REPLY)
            put(CommunicationConstants.KEY_ID, id)
            put(CommunicationConstants.KEY_TEXT, text)
        }.toString())
    }

    fun sendDismiss(id: String) {
        send(JSONObject().apply {
            put(CommunicationConstants.KEY_TYPE, CommunicationConstants.TYPE_DISMISS)
            put(CommunicationConstants.KEY_ID, id)
        }.toString())
    }

    fun sendMediaAction(action: String) = send(JSONObject().apply {
        put(CommunicationConstants.KEY_TYPE, CommunicationConstants.TYPE_MEDIA_CONTROL)
        put(CommunicationConstants.KEY_ACTION, action)
    }.toString())

    fun sendBatteryStatus(json: String) = send(json)

    fun sendVolumeDelta(delta: Float) = send(JSONObject().apply {
        put(CommunicationConstants.KEY_TYPE, CommunicationConstants.TYPE_VOLUME_DELTA)
        put(CommunicationConstants.KEY_VALUE, delta)
    }.toString())

    private fun sendNotifAck(id: String) = send(JSONObject().apply {
        put(CommunicationConstants.KEY_TYPE, CommunicationConstants.TYPE_NOTIF_ACK)
        put(CommunicationConstants.KEY_DATA, id)
    }.toString())

    private fun sendPong() = send(JSONObject().apply {
        put(CommunicationConstants.KEY_TYPE, CommunicationConstants.TYPE_PONG)
    }.toString())

    private fun send(data: String) {
        val w = writer ?: return
        scope.launch(Dispatchers.IO) {
            try {
                synchronized(w) { w.println(data) }
            } catch (e: Exception) {
                Log.e(TAG, "SERVER: send failed: ${e.message}")
                closeClientSocket()
            }
        }
    }

    // ── Notification helpers ──────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = android.app.NotificationChannel(
            "mirrored_notifs", "Mirrored Notifications", NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Notifications mirrored from your phone" }
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun showNativeNotification(data: NotificationData) {
        try {
            val nm = NotificationManagerCompat.from(context)

            val replyIntent = Intent(context, WearNotificationReceiver::class.java).apply {
                action = "com.found404.sidelink.ACTION_REPLY"
                putExtra("notification_id", data.id)
            }
            val replyPendingIntent = PendingIntent.getBroadcast(
                context, data.id.hashCode(), replyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            val remoteInput = androidx.core.app.RemoteInput.Builder("extra_reply")
                .setLabel("Reply").build()
            val replyAction = NotificationCompat.Action.Builder(
                android.R.drawable.ic_menu_send, "Reply", replyPendingIntent
            ).addRemoteInput(remoteInput).build()

            val dismissIntent = Intent(context, WearNotificationReceiver::class.java).apply {
                action = "com.found404.sidelink.ACTION_DISMISS"
                putExtra("notification_id", data.id)
            }
            val dismissPendingIntent = PendingIntent.getBroadcast(
                context, data.id.hashCode() + 1, dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            scope.launch {
                val iconBitmap = data.iconBase64?.takeIf { it.isNotEmpty() }?.let { b64 ->
                    runCatching {
                        withContext(Dispatchers.Default) {
                            val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        }
                    }.getOrNull()
                }
                withContext(Dispatchers.Main) {
                    val builder = NotificationCompat.Builder(context, "mirrored_notifs")
                        .setSmallIcon(android.R.drawable.stat_notify_chat)
                        .setLargeIcon(iconBitmap)
                        .setContentTitle(data.title)
                        .setContentText(data.text)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setAutoCancel(true)
                        .setDeleteIntent(dismissPendingIntent)
                        .apply { if (data.hasReply) addAction(replyAction) }
                    nm.notify(data.id.hashCode(), builder.build())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "showNativeNotification failed", e)
        }
    }

    private suspend fun updateMediaSession(mediaData: com.found404.sidelink.data.model.MediaData) {
        try {
            if (mediaSession == null) initMediaSession()
            mediaSession?.isActive = true
            val art = mediaData.artworkBase64?.takeIf { it.isNotEmpty() }?.let { b64 ->
                runCatching {
                    withContext(Dispatchers.Default) {
                        val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                }.getOrNull()
            }
            mediaSession?.setMetadata(MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, mediaData.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, mediaData.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, mediaData.album)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, -1L)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art)
                .build())
            val state = if (mediaData.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
            mediaSession?.setPlaybackState(PlaybackStateCompat.Builder()
                .setState(state, 0L, 1f)
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                ).build())
        } catch (e: Exception) {
            Log.e(TAG, "updateMediaSession failed", e)
        }
    }

    private fun showNativeMediaNotification(mediaData: com.found404.sidelink.data.model.MediaData) {
        try {
            val nm = NotificationManagerCompat.from(context)
            val flag = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

            val prevPending    = PendingIntent.getBroadcast(context, 10,
                Intent(context, WearNotificationReceiver::class.java).apply { action = "com.found404.sidelink.CMD_MEDIA_PREV" }, flag)
            val playPausePending = PendingIntent.getBroadcast(context, 11,
                Intent(context, WearNotificationReceiver::class.java).apply {
                    action = if (mediaData.isPlaying) "com.found404.sidelink.CMD_MEDIA_PAUSE"
                             else "com.found404.sidelink.CMD_MEDIA_PLAY"
                }, flag)
            val nextPending    = PendingIntent.getBroadcast(context, 12,
                Intent(context, WearNotificationReceiver::class.java).apply { action = "com.found404.sidelink.CMD_MEDIA_NEXT" }, flag)
            val touchPending   = PendingIntent.getActivity(context, 0,
                Intent(context, com.found404.sidelink.MainActivity::class.java), flag)

            scope.launch {
                val art = mediaData.artworkBase64?.takeIf { it.isNotEmpty() }?.let { b64 ->
                    runCatching {
                        withContext(Dispatchers.Default) {
                            val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        }
                    }.getOrNull()
                }
                withContext(Dispatchers.Main) {
                    val builder = NotificationCompat.Builder(context, "mirrored_notifs")
                        .setSmallIcon(android.R.drawable.ic_media_play)
                        .setContentTitle(mediaData.title ?: "Unknown Track")
                        .setContentText(mediaData.artist ?: "Unknown Artist")
                        .setLargeIcon(art)
                        .setOngoing(true)
                        .setContentIntent(touchPending)
                        .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .addAction(android.R.drawable.ic_media_previous, "Previous", prevPending)
                        .addAction(if (mediaData.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, "Play/Pause", playPausePending)
                        .addAction(android.R.drawable.ic_media_next, "Next", nextPending)
                        .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                            .setMediaSession(mediaSession?.sessionToken)
                            .setShowActionsInCompactView(0, 1, 2))
                    try {
                        val ongoing = OngoingActivity.Builder(context, NOTIFICATION_ID_MEDIA, builder)
                            .setStaticIcon(android.R.drawable.ic_media_play)
                            .setTouchIntent(touchPending)
                            .setStatus(Status.Builder().addTemplate("${mediaData.title} - ${mediaData.artist}").build())
                            .build()
                        ongoing.apply(context)
                    } catch (_: Exception) {}
                    nm.notify(NOTIFICATION_ID_MEDIA, builder.build())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "showNativeMediaNotification failed", e)
        }
    }

    private fun handleOngoingLifecycle(isPlaying: Boolean) {
        ongoingTimeoutJob?.cancel()
        if (!isPlaying) {
            ongoingTimeoutJob = scope.launch {
                delay(5 * 60 * 1000L)
                cancelNativeMediaNotification()
                mediaSession?.isActive = false
            }
        }
    }

    private fun cancelNativeMediaNotification() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_MEDIA)
    }

    private fun cancelNativeNotification(id: String) {
        NotificationManagerCompat.from(context).cancel(id.hashCode())
    }
}

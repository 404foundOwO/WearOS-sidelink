package com.found404.sidelink.communication

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.PowerManager
import android.util.Log
import com.found404.sidelink.data.model.MediaData
import com.found404.sidelink.data.model.NotificationData
import com.found404.sidelink.data.repository.SettingsRepository
import com.found404.sidelink.shared.CommunicationConstants
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.*

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

class BluetoothCommunicationManager private constructor(private val context: Context) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val socketMutex = Mutex()

    private var clientSocket: BluetoothSocket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null
    private var readJob: Job? = null

    private var isRunning = false
    private var lastAddress: String? = null
    private var connectJob: Job? = null
    private var observeJob: Job? = null
    private var heartbeatJob: Job? = null
    private var pongDeferred: CompletableDeferred<Unit>? = null
    @Volatile private var skipNextDelay = false
    private var retryCount = 0
    @Volatile private var started = false
    @Volatile private var autoReconnectEnabled = true
    @Volatile private var keepAliveIntervalSec = 45
    @Volatile private var firstRetryDelayMs = 800L
    @Volatile private var includeNotificationIcons = true
    @Volatile private var connectHfpEnabled = false
    private var hfpProxy: BluetoothHeadset? = null

    private val _watchBatteryLevel = MutableStateFlow<Int?>(null)
    val watchBatteryLevel: StateFlow<Int?> = _watchBatteryLevel.asStateFlow()

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                if (state == BluetoothAdapter.STATE_ON && isRunning && _connectionStatus.value != ConnectionStatus.CONNECTED) {
                    Log.d(TAG, "Bluetooth turned ON, forcing reconnect")
                    connectNow()
                }
            }
        }
    }

    init {
        context.registerReceiver(bluetoothStateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
    }

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    val connectionState: StateFlow<Boolean> = _connectionStatus
        .map { it == ConnectionStatus.CONNECTED }
        .stateIn(scope, SharingStarted.Eagerly, false)

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    var listener: BluetoothMessageListener? = null
    private val unacknowledgedNotifications = mutableMapOf<String, NotificationData>()
    private val pendingDismissals = mutableSetOf<String>()
    private val wakeLock: PowerManager.WakeLock by lazy {
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Sidelink:BluetoothSend")
    }

    interface BluetoothMessageListener {
        fun onDismissReceived(notificationId: String)
        fun onReplyReceived(notificationId: String, message: String)
        fun onMediaActionReceived(action: String)
        fun onVolumeDeltaReceived(delta: Float)
        fun onWatchBatteryReceived(level: Int)
        fun onConnectionStateChanged(connected: Boolean)
    }

    /**
     * Single entry point for connection lifecycle. Observes saved watch address and
     * connects automatically; safe to call from Application, Service, and ViewModel.
     */
    fun ensureStarted(settingsRepository: SettingsRepository) {
        synchronized(this) {
            if (started) return
            started = true
        }
        observeJob?.cancel()
        observeJob = scope.launch {
            settingsRepository.targetDeviceAddress.collectLatest { address ->
                if (address == null) {
                    stopConnection()
                } else {
                    scheduleConnection(address, immediate = true)
                }
            }
        }
        scope.launch {
            settingsRepository.autoReconnectEnabled.collect { autoReconnectEnabled = it }
        }
        scope.launch {
            settingsRepository.keepAliveIntervalSec.collect { keepAliveIntervalSec = it }
        }
        scope.launch {
            settingsRepository.reconnectionTimeout.collect { firstRetryDelayMs = it }
        }
        scope.launch {
            settingsRepository.mirrorNotificationIcons.collect { includeNotificationIcons = it }
        }
        scope.launch {
            settingsRepository.connectHfpEnabled.collect { connectHfpEnabled = it }
        }
    }

    /** User tapped Connect — bypass backoff and retry immediately. */
    fun connectNow() {
        val address = lastAddress ?: return
        scheduleConnection(address, immediate = true)
    }

    /** @deprecated Use [ensureStarted] + saved address, or [connectNow]. Kept for compatibility. */
    fun connect(address: String) {
        lastAddress = address
        scheduleConnection(address, immediate = true)
    }

    fun forceReconnect() = connectNow()

    private fun scheduleConnection(address: String, immediate: Boolean) {
        Log.d(TAG, "CLIENT: scheduleConnection $address immediate=$immediate")
        val sameTarget = lastAddress == address
        isRunning = true
        lastAddress = address
        if (immediate) {
            skipNextDelay = true
            retryCount = 0
        }

        val alreadyConnecting = connectJob?.isActive == true &&
            _connectionStatus.value == ConnectionStatus.CONNECTING &&
            sameTarget && !immediate
        if (alreadyConnecting) {
            Log.d(TAG, "CLIENT: already connecting to $address, skipping duplicate")
            return
        }

        connectJob?.cancel()
        connectJob = scope.launch {
            while (isRunning) {
                _connectionStatus.value = ConnectionStatus.CONNECTING
                if (tryConnect(address)) {
                    retryCount = 0
                    _connectionStatus.value = ConnectionStatus.CONNECTED
                    while (isRunning && _connectionStatus.value == ConnectionStatus.CONNECTED) {
                        delay(500)
                    }
                } else if (isRunning) {
                    _connectionStatus.value = ConnectionStatus.DISCONNECTED
                }

                if (!isRunning || _connectionStatus.value == ConnectionStatus.CONNECTED) continue
                if (!autoReconnectEnabled && !skipNextDelay) {
                    Log.d(TAG, "CLIENT: auto-reconnect disabled, waiting for manual connect")
                    break
                }

                if (skipNextDelay) {
                    skipNextDelay = false
                    retryCount = 0
                    Log.d(TAG, "CLIENT: retrying immediately")
                } else {
                    val delayMs = when {
                        retryCount == 0 -> firstRetryDelayMs
                        else -> (1000L * (1 shl retryCount.coerceAtMost(5))).coerceAtMost(30000L)
                    }
                    Log.d(TAG, "CLIENT: retrying in ${delayMs}ms (attempt #$retryCount)")
                    try {
                        delay(delayMs)
                    } catch (_: CancellationException) {
                        if (!isRunning) break
                        continue
                    }
                    retryCount++
                }
            }
        }
    }

    fun stopConnection() {
        Log.d(TAG, "CLIENT: stopConnection")
        isRunning = false
        connectJob?.cancel()
        connectJob = null
        teardownSocket(notifyListener = true)
    }

    private suspend fun tryConnect(address: String): Boolean = socketMutex.withLock {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.w(TAG, "CLIENT: Bluetooth disabled")
            _lastError.value = "Bluetooth disabled"
            return false
        }

        Log.d(TAG, "CLIENT: attempting connection to $address")
        val device = try {
            bluetoothAdapter.getRemoteDevice(address)
        } catch (e: Exception) {
            Log.e(TAG, "CLIENT: invalid address $address")
            _lastError.value = "Invalid device address"
            return false
        }

        try {
            bluetoothAdapter.cancelDiscovery()
        } catch (_: Exception) {
        }

        val methods = listOf(
            "reflection-ch1" to suspend {
                val m = device.javaClass.getMethod("createRfcommSocket", Int::class.java)
                m.invoke(device, 1) as BluetoothSocket
            },
            "insecure-uuid" to suspend {
                device.createInsecureRfcommSocketToServiceRecord(CommunicationConstants.UUID_NOTIF_MIRROR)
            }
        )

        for ((label, factory) in methods) {
            try {
                teardownSocket(notifyListener = false)

                val socket = factory()
                clientSocket = socket

                Log.d(TAG, "CLIENT: connecting via $label...")
                withTimeout(CONNECT_TIMEOUT_MS) {
                    withContext(Dispatchers.IO) { socket.connect() }
                }

                writer = PrintWriter(BufferedWriter(OutputStreamWriter(socket.outputStream)), true)
                reader = BufferedReader(InputStreamReader(socket.inputStream))

                val handshake = CompletableDeferred<Unit>()
                pongDeferred = handshake

                readJob = scope.launch {
                    try {
                        while (isActive && isRunning) {
                            val line = reader?.readLine() ?: break
                            if (line.isNotBlank()) dispatchIncomingLine(line)
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "CLIENT: read loop ended: ${e.message}")
                    } finally {
                        teardownSocket(notifyListener = true)
                    }
                }

                delay(50)
                sendPing()

                try {
                    withTimeout(HANDSHAKE_TIMEOUT_MS) {
                        handshake.await()
                    }
                    Log.d(TAG, "CLIENT: handshake OK via $label")
                    _lastError.value = null
                    startHeartbeat(device)
                    if (connectHfpEnabled) {
                        connectHfpProfile(device)
                    }
                    withContext(Dispatchers.Main.immediate) {
                        listener?.onConnectionStateChanged(true)
                    }
                    return true
                } catch (e: Exception) {
                    Log.w(TAG, "CLIENT: handshake failed via $label: ${e.message}")
                    readJob?.cancel()
                    teardownSocket(notifyListener = false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "CLIENT: $label failed: ${e.message}")
            }
        }

        _lastError.value = "Could not reach watch — open Sidelink on the watch and try Connect"
        return false
    }

    /**
     * Attempts to bring up the Hands-Free Profile (HFP) connection to the watch using the
     * hidden BluetoothHeadset#connect(BluetoothDevice) method via reflection, since it is not
     * part of the public SDK. This only makes the watch appear "connected" for calls in its
     * system Bluetooth UI; Sidelink itself does not route call audio.
     *
     * Best-effort only: failures are logged and swallowed so they never affect the RFCOMM
     * notification channel, which is the app's core function.
     */
    private fun connectHfpProfile(device: BluetoothDevice) {
        try {
            bluetoothAdapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    if (profile != BluetoothProfile.HEADSET) return
                    hfpProxy = proxy as? BluetoothHeadset
                    try {
                        val connectMethod = BluetoothHeadset::class.java.getMethod("connect", BluetoothDevice::class.java)
                        connectMethod.invoke(hfpProxy, device)
                        Log.d(TAG, "CLIENT: HFP connect requested")
                    } catch (e: Exception) {
                        Log.w(TAG, "CLIENT: HFP connect failed: ${e.message}")
                    }
                }

                override fun onServiceDisconnected(profile: Int) {
                    hfpProxy = null
                }
            }, BluetoothProfile.HEADSET)
        } catch (e: Exception) {
            Log.w(TAG, "CLIENT: HFP proxy setup failed: ${e.message}")
        }
    }

    private fun disconnectHfpProfile(device: BluetoothDevice?) {
        val proxy = hfpProxy ?: return
        try {
            if (device != null) {
                val disconnectMethod = BluetoothHeadset::class.java.getMethod("disconnect", BluetoothDevice::class.java)
                disconnectMethod.invoke(proxy, device)
            }
        } catch (e: Exception) {
            Log.w(TAG, "CLIENT: HFP disconnect failed: ${e.message}")
        } finally {
            try {
                bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HEADSET, proxy)
            } catch (_: Exception) {
            }
            hfpProxy = null
        }
    }

    /**
     * Returns true if the watch is currently connected on the HFP profile, false otherwise.
     * Uses the public BluetoothProfile#getConnectionState(BluetoothDevice) API on whatever
     * proxy we currently hold; if we don't hold a proxy yet, treats it as disconnected so the
     * watchdog will attempt to (re)connect.
     */
    private fun isHfpConnected(device: BluetoothDevice): Boolean {
        val proxy = hfpProxy ?: return false
        return try {
            proxy.getConnectionState(device) == BluetoothProfile.STATE_CONNECTED
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Periodically checked from the heartbeat loop while RFCOMM stays connected. HFP can drop
     * independently of our RFCOMM socket (watch sleep, OS profile teardown, etc.), and nothing
     * else will bring it back up since Sidelink is the one that originally requested it.
     */
    private fun maintainHfpConnection(device: BluetoothDevice) {
        if (!connectHfpEnabled) return
        if (hfpProxy != null && isHfpConnected(device)) return
        Log.d(TAG, "CLIENT: HFP not connected, retrying")
        connectHfpProfile(device)
    }

    private fun startHeartbeat(device: BluetoothDevice) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            delay(5_000)
            while (isActive && _connectionStatus.value == ConnectionStatus.CONNECTED) {
                val handshake = CompletableDeferred<Unit>()
                pongDeferred = handshake
                sendPing()
                try {
                    withTimeout(HANDSHAKE_TIMEOUT_MS) {
                        handshake.await()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "CLIENT: keep-alive timeout, closing connection")
                    teardownSocket(notifyListener = true)
                    break
                }
                maintainHfpConnection(device)
                val intervalMs = keepAliveIntervalSec.coerceIn(15, 120) * 1000L
                delay(intervalMs)
            }
        }
    }

    /** PONG must be handled on the reader thread so keep-alive cannot time out in a race. */
    private fun dispatchIncomingLine(jsonString: String) {
        try {
            val type = JSONObject(jsonString).optString(CommunicationConstants.KEY_TYPE)
            if (type == CommunicationConstants.TYPE_PONG) {
                pongDeferred?.complete(Unit)
                return
            }
            if (type == CommunicationConstants.TYPE_BATTERY_STATUS) {
                val level = JSONObject(jsonString).optInt(CommunicationConstants.KEY_BATTERY_LEVEL, -1)
                if (level >= 0) _watchBatteryLevel.value = level
            }
        } catch (e: Exception) {
            Log.e(TAG, "CLIENT: line parse error: ${e.message}")
            return
        }
        handleIncomingData(jsonString)
    }

    private fun handleIncomingData(jsonString: String) {
        scope.launch(Dispatchers.Default) {
            try {
                val json = JSONObject(jsonString)
                val type = json.optString(CommunicationConstants.KEY_TYPE)

                withContext(Dispatchers.Main.immediate) {
                    when (type) {
                        CommunicationConstants.TYPE_PING -> sendPong()
                        CommunicationConstants.TYPE_NOTIF_ACK -> {
                            val id = json.optString(CommunicationConstants.KEY_DATA)
                            synchronized(unacknowledgedNotifications) {
                                unacknowledgedNotifications.remove(id)
                            }
                            synchronized(pendingDismissals) {
                                pendingDismissals.remove(id)
                            }
                        }
                        CommunicationConstants.TYPE_DISMISS -> listener?.onDismissReceived(json.getString(CommunicationConstants.KEY_ID))
                        CommunicationConstants.TYPE_REPLY -> listener?.onReplyReceived(
                            json.getString(CommunicationConstants.KEY_ID),
                            json.getString(CommunicationConstants.KEY_TEXT)
                        )
                        CommunicationConstants.TYPE_MEDIA_CONTROL -> listener?.onMediaActionReceived(json.getString(CommunicationConstants.KEY_ACTION))
                        CommunicationConstants.TYPE_VOLUME_DELTA -> listener?.onVolumeDeltaReceived(json.optDouble(CommunicationConstants.KEY_VALUE).toFloat())
                        CommunicationConstants.TYPE_BATTERY_STATUS -> listener?.onWatchBatteryReceived(json.optInt(CommunicationConstants.KEY_BATTERY_LEVEL, -1))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "CLIENT: parse error: ${e.message}")
            }
        }
    }

    private fun sendPing() = send(JSONObject().apply { put(CommunicationConstants.KEY_TYPE, CommunicationConstants.TYPE_PING) }.toString())
    private fun sendPong() = send(JSONObject().apply { put(CommunicationConstants.KEY_TYPE, CommunicationConstants.TYPE_PONG) }.toString())

    fun sendNotification(notification: NotificationData) = scope.launch {
        synchronized(unacknowledgedNotifications) {
            unacknowledgedNotifications[notification.id] = notification
            if (unacknowledgedNotifications.size > 50) {
                unacknowledgedNotifications.remove(unacknowledgedNotifications.keys.first())
            }
        }
        sendNotificationInternal(notification)

        delay(5000)
        val stillUnacked = synchronized(unacknowledgedNotifications) {
            unacknowledgedNotifications.containsKey(notification.id)
        }
        if (stillUnacked && connectionState.value) {
            sendNotificationInternal(notification)
        }
    }

    private fun sendNotificationInternal(notification: NotificationData) {
        if (!connectionState.value) return
        scope.launch {
            try {
                val iconBase64 = if (includeNotificationIcons) notification.icon?.toBase64Png() else null
                val json = JSONObject().apply {
                    put(CommunicationConstants.KEY_TYPE, CommunicationConstants.TYPE_NOTIFICATION)
                    put(CommunicationConstants.KEY_ID, notification.id)
                    put(CommunicationConstants.KEY_PACKAGE, notification.packageName)
                    put(CommunicationConstants.KEY_TITLE, notification.title ?: "")
                    put(CommunicationConstants.KEY_TEXT, notification.text ?: "")
                    put(CommunicationConstants.KEY_TIMESTAMP, notification.timestamp)
                    put(CommunicationConstants.KEY_HAS_REPLY, notification.hasReply)
                    notification.appName?.let { put(CommunicationConstants.KEY_APP_NAME, it) }
                    iconBase64?.let { put(CommunicationConstants.KEY_ICON, it) }
                }
                sendWithWakeLock(json.toString())
            } catch (e: Exception) {
                Log.e(TAG, "CLIENT: notify sync failed", e)
            }
        }
    }

    fun sendVolumeState(percentage: Int) = scope.launch {
        if (!connectionState.value) return@launch
        try {
            val json = JSONObject().apply {
                put(CommunicationConstants.KEY_TYPE, CommunicationConstants.TYPE_VOLUME_STATE)
                put(CommunicationConstants.KEY_VALUE, percentage)
            }
            send(json.toString())
        } catch (e: Exception) {
            Log.e(TAG, "CLIENT: volume state sync failed", e)
        }
    }

    private fun sendWithWakeLock(data: String) {
        try {
            wakeLock.acquire(3000)
            send(data)
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
        }
    }

    fun updateMediaStatus(mediaData: MediaData?) = scope.launch {
        if (!connectionState.value) return@launch
        try {
            if (mediaData == null) {
                send(JSONObject().apply { put(CommunicationConstants.KEY_TYPE, CommunicationConstants.TYPE_MEDIA_CLEARED) }.toString())
                return@launch
            }

            val artworkBase64 = mediaData.artwork?.toBase64()
            val json = JSONObject().apply {
                put(CommunicationConstants.KEY_TYPE, CommunicationConstants.TYPE_MEDIA_STATUS)
                put(CommunicationConstants.KEY_TITLE, mediaData.title ?: "")
                put(CommunicationConstants.KEY_ARTIST, mediaData.artist ?: "")
                put(CommunicationConstants.KEY_ALBUM, mediaData.album ?: "")
                put(CommunicationConstants.KEY_IS_PLAYING, mediaData.isPlaying)
                put(CommunicationConstants.KEY_PACKAGE, mediaData.packageName ?: "")
                put("trackId", "${mediaData.title}${mediaData.artist}${mediaData.packageName}")
                artworkBase64?.let { put(CommunicationConstants.KEY_ARTWORK, it) }
            }
            send(json.toString())
        } catch (e: Exception) {
            Log.e(TAG, "CLIENT: media sync failed", e)
        }
    }

    fun sendSyncAll(ids: List<String>) = scope.launch {
        if (!connectionState.value) return@launch
        try {
            val json = JSONObject().apply {
                put(CommunicationConstants.KEY_TYPE, CommunicationConstants.TYPE_SYNC_ALL)
                val array = JSONArray()
                ids.forEach { array.put(it) }
                put(CommunicationConstants.KEY_DATA, array)
            }
            send(json.toString())
        } catch (e: Exception) {
            Log.e(TAG, "CLIENT: sync all failed", e)
        }
    }

    fun dismissNotification(id: String) = scope.launch {
        synchronized(pendingDismissals) {
            pendingDismissals.add(id)
            if (pendingDismissals.size > 100) {
                pendingDismissals.remove(pendingDismissals.first())
            }
        }
        dismissNotificationInternal(id)
    }

    private fun dismissNotificationInternal(id: String) {
        if (!connectionState.value) return
        scope.launch {
            try {
                val json = JSONObject().apply {
                    put(CommunicationConstants.KEY_TYPE, CommunicationConstants.TYPE_DISMISS)
                    put(CommunicationConstants.KEY_ID, id)
                }
                send(json.toString())
            } catch (e: Exception) {
                Log.e(TAG, "CLIENT: dismiss sync failed", e)
            }
        }
    }

    private fun send(data: String) {
        synchronized(this) {
            try {
                writer?.println(data) ?: return
            } catch (e: Exception) {
                Log.e(TAG, "CLIENT: send failed: ${e.message}")
                scope.launch { teardownSocket(notifyListener = true) }
            }
        }
    }

    private fun teardownSocket(notifyListener: Boolean) {
        val wasConnected = _connectionStatus.value == ConnectionStatus.CONNECTED
        heartbeatJob?.cancel()
        readJob?.cancel()
        readJob = null
        if (connectHfpEnabled) {
            val deviceForHfp = clientSocket?.remoteDevice
            disconnectHfpProfile(deviceForHfp)
        }
        try {
            clientSocket?.close()
        } catch (_: Exception) {
        }
        clientSocket = null
        writer = null
        reader = null
        if (_connectionStatus.value == ConnectionStatus.CONNECTED) {
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
        }
        if (notifyListener && wasConnected) {
            scope.launch(Dispatchers.Main.immediate) {
                listener?.onConnectionStateChanged(false)
            }
        }
    }

    /** App/process shutdown only — do not call when a service stops. */
    fun release() {
        stopConnection()
        started = false
        observeJob?.cancel()
        observeJob = null
        try {
            context.unregisterReceiver(bluetoothStateReceiver)
        } catch (_: Exception) {
        }
        scope.cancel()
    }

    /** @deprecated Use [stopConnection] or [release]. */
    fun close() = release()

    private suspend fun Bitmap.toBase64(): String = withContext(Dispatchers.Default) {
        val os = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 75, os)
        android.util.Base64.encodeToString(os.toByteArray(), android.util.Base64.NO_WRAP)
    }

    private suspend fun Bitmap.toBase64Png(): String = withContext(Dispatchers.Default) {
        val os = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.PNG, 100, os)
        android.util.Base64.encodeToString(os.toByteArray(), android.util.Base64.NO_WRAP)
    }

    companion object {
        private const val TAG = "SidelinkPhoneBT"
        private const val CONNECT_TIMEOUT_MS = 8_000L
        private const val HANDSHAKE_TIMEOUT_MS = 6_000L

        @Volatile private var INSTANCE: BluetoothCommunicationManager? = null
        fun getInstance(context: Context) = INSTANCE ?: synchronized(this) {
            INSTANCE ?: BluetoothCommunicationManager(context.applicationContext).also { INSTANCE = it }
        }
    }
}

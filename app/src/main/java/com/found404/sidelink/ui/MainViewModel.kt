package com.found404.sidelink.ui

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.found404.sidelink.communication.BluetoothCommunicationManager
import com.found404.sidelink.communication.ConnectionStatus
import com.found404.sidelink.data.model.NotificationData
import com.found404.sidelink.data.repository.MirrorRepository
import com.found404.sidelink.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LogEntry(val timestamp: Long, val message: String, val isError: Boolean = false)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepository = SettingsRepository(application)
    private val bluetoothManager = application.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val commManager = BluetoothCommunicationManager.getInstance(application)

    val notifications: StateFlow<List<NotificationData>> = MirrorRepository.notifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeMedia = MirrorRepository.activeMedia
    val connectionStatus = commManager.connectionStatus
    val isConnected = commManager.connectionState
    val lastError = commManager.lastError

    val blacklistedPackages: StateFlow<Set<String>> = settingsRepository.blacklistedPackages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val targetDeviceAddress: StateFlow<String?> = settingsRepository.targetDeviceAddress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val batteryOptPromptShown: StateFlow<Boolean> = settingsRepository.batteryOptPromptShown
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val albumArtQuality: StateFlow<Int> = settingsRepository.albumArtQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 240)

    val mirrorMediaEnabled: StateFlow<Boolean> = settingsRepository.mirrorMediaEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val mirrorNotificationIcons: StateFlow<Boolean> = settingsRepository.mirrorNotificationIcons
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val showWatchBattery: StateFlow<Boolean> = settingsRepository.showWatchBattery
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val maxMirroredNotifications: StateFlow<Int> = settingsRepository.maxMirroredNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 50)

    val keepAliveIntervalSec: StateFlow<Int> = settingsRepository.keepAliveIntervalSec
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 45)

    val autoReconnectEnabled: StateFlow<Boolean> = settingsRepository.autoReconnectEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val reconnectionTimeout: StateFlow<Long> = settingsRepository.reconnectionTimeout
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 800L)

    val connectHfpEnabled: StateFlow<Boolean> = settingsRepository.connectHfpEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val watchBatteryLevel = commManager.watchBatteryLevel

    private val _pairedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val pairedDevices = _pairedDevices.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    init {
        commManager.ensureStarted(settingsRepository)
        updatePairedDevices()
        viewModelScope.launch {
            connectionStatus.collect { status ->
                when (status) {
                    ConnectionStatus.CONNECTED -> addLog("Connected")
                    ConnectionStatus.CONNECTING -> addLog("Connecting to device...")
                    ConnectionStatus.DISCONNECTED -> addLog("Disconnected")
                }
            }
        }
        viewModelScope.launch {
            lastError.collect { error ->
                if (error != null) addLog("Error: $error", isError = true)
            }
        }
    }

    fun addLog(message: String, isError: Boolean = false) {
        val entry = LogEntry(System.currentTimeMillis(), message, isError)
        _logs.value = (_logs.value + entry).takeLast(200)
    }

    fun updatePairedDevices() {
        try {
            _pairedDevices.value = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
        } catch (e: SecurityException) {
            _pairedDevices.value = emptyList()
        }
    }

    fun setTargetDeviceAddress(address: String?) {
        viewModelScope.launch {
            settingsRepository.setTargetDeviceAddress(address)
            if (address != null) {
                addLog("Selected $address")
            }
        }
    }

    fun connectManually() {
        if (targetDeviceAddress.value == null) return
        addLog("Request sent")
        commManager.connectNow()
    }

    fun openBluetoothSettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    }

    fun setReconnectionTimeout(timeout: Long) {
        viewModelScope.launch { settingsRepository.setReconnectionTimeout(timeout) }
    }

    fun togglePackageBlacklist(packageName: String) {
        viewModelScope.launch { settingsRepository.togglePackageBlacklist(packageName) }
    }

    fun blockApp(packageName: String) {
        viewModelScope.launch {
            settingsRepository.addToBlacklist(packageName)
            addLog("Blocked app: $packageName")
        }
    }

    fun markBatteryOptPromptShown() {
        viewModelScope.launch { settingsRepository.setBatteryOptPromptShown() }
    }

    fun setAlbumArtQuality(quality: Int) {
        viewModelScope.launch { settingsRepository.setAlbumArtQuality(quality) }
    }

    fun setMirrorMediaEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setMirrorMediaEnabled(enabled) }
    }

    fun setMirrorNotificationIcons(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setMirrorNotificationIcons(enabled) }
    }

    fun setShowWatchBattery(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setShowWatchBattery(enabled) }
    }

    fun setMaxMirroredNotifications(max: Int) {
        viewModelScope.launch { settingsRepository.setMaxMirroredNotifications(max) }
    }

    fun setKeepAliveIntervalSec(seconds: Int) {
        viewModelScope.launch { settingsRepository.setKeepAliveIntervalSec(seconds) }
    }

    fun setAutoReconnectEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoReconnectEnabled(enabled) }
    }

    fun setConnectHfpEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setConnectHfpEnabled(enabled) }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun sendNote(text: String) {
        val notification = NotificationData(
            id = "note_${System.currentTimeMillis()}",
            packageName = "com.found404.sidelink",
            appName = "Sidelink",
            title = "Note",
            text = text,
            icon = null,
            hasReply = false
        )
        commManager.sendNotification(notification)
        addLog("Sent text")
    }
}

package com.found404.sidelink.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.BluetoothSearching
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.found404.sidelink.communication.ConnectionStatus
import com.found404.sidelink.data.model.NotificationData
import com.found404.sidelink.ui.components.NotificationItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun SidelinkApp(viewModel: MainViewModel = viewModel()) {
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val isConnected by viewModel.isConnected.collectAsStateWithLifecycle()
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val isConnecting = connectionStatus == ConnectionStatus.CONNECTING
    val lastError by viewModel.lastError.collectAsStateWithLifecycle()
    val blacklisted by viewModel.blacklistedPackages.collectAsStateWithLifecycle()
    val targetDeviceAddress by viewModel.targetDeviceAddress.collectAsStateWithLifecycle()
    val pairedDevices by viewModel.pairedDevices.collectAsStateWithLifecycle()
    val batteryOptShown by viewModel.batteryOptPromptShown.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val mirrorMediaEnabled by viewModel.mirrorMediaEnabled.collectAsStateWithLifecycle()
    val mirrorNotificationIcons by viewModel.mirrorNotificationIcons.collectAsStateWithLifecycle()
    val showWatchBattery by viewModel.showWatchBattery.collectAsStateWithLifecycle()
    val maxMirroredNotifications by viewModel.maxMirroredNotifications.collectAsStateWithLifecycle()
    val keepAliveIntervalSec by viewModel.keepAliveIntervalSec.collectAsStateWithLifecycle()
    val autoReconnectEnabled by viewModel.autoReconnectEnabled.collectAsStateWithLifecycle()
    val reconnectionTimeout by viewModel.reconnectionTimeout.collectAsStateWithLifecycle()
    val connectHfpEnabled by viewModel.connectHfpEnabled.collectAsStateWithLifecycle()
    val watchBatteryLevel by viewModel.watchBatteryLevel.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val context = LocalContext.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "notifications"

    // Battery optimization prompt â€” one time only
    if (!batteryOptShown) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val isIgnoring = pm.isIgnoringBatteryOptimizations(context.packageName)
        if (!isIgnoring) {
            AlertDialog(
                onDismissRequest = { viewModel.markBatteryOptPromptShown() },
                title = { Text("Disable app optimization") },
                text = { Text("Disable battery optimization for the app,just to be sure it works.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.markBatteryOptPromptShown()
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }) { Text("Open Settings") }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.markBatteryOptPromptShown() }) { Text("Dismiss") }
                }
            )
        } else {
            LaunchedEffect(Unit) { viewModel.markBatteryOptPromptShown() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (currentRoute != "settings") {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isConnected -> Color(0xFF4CAF50)
                                            isConnecting -> Color(0xFFFFA726)
                                            else -> Color(0xFFE53935)
                                        }
                                    )
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Sidelink", fontWeight = FontWeight.SemiBold)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }) {
                        Icon(Icons.Outlined.NotificationsActive, contentDescription = "Notification Permission")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentRoute == "notifications",
                    onClick = { navController.navigate("notifications") },
                    icon = { Icon(Icons.Default.Notifications, null) },
                    label = { Text("Notifs") }
                )
                NavigationBarItem(
                    selected = currentRoute == "settings",
                    onClick = { navController.navigate("settings") },
                    icon = { Icon(Icons.Default.Settings, null) },
                    label = { Text("Settings") }
                )
                NavigationBarItem(
                    selected = currentRoute == "logs",
                    onClick = { navController.navigate("logs") },
                    icon = { Icon(Icons.Outlined.Article, null) },
                    label = { Text("Logs") }
                )
            }
        }
    ) { innerPadding ->
        NavHost(navController, "notifications", Modifier.padding(innerPadding)) {
            composable("notifications") {
                NotificationListAdaptive(
                    notifications = notifications,
                    blacklisted = blacklisted,
                    onBlockApp = { viewModel.blockApp(it) },
                    onSendNote = { viewModel.sendNote(it) }
                )
            }
            composable("settings") {
                LaunchedEffect(Unit) { viewModel.updatePairedDevices() }
                val albumArtQuality by viewModel.albumArtQuality.collectAsState()
                SettingsScreen(
                    isConnected = isConnected,
                    isConnecting = isConnecting,
                    lastError = lastError,
                    watchBatteryLevel = watchBatteryLevel,
                    blacklistedPackages = blacklisted,
                    targetDeviceAddress = targetDeviceAddress,
                    pairedDevices = pairedDevices,
                    albumArtQuality = albumArtQuality,
                    mirrorMediaEnabled = mirrorMediaEnabled,
                    mirrorNotificationIcons = mirrorNotificationIcons,
                    showWatchBattery = showWatchBattery,
                    maxMirroredNotifications = maxMirroredNotifications,
                    keepAliveIntervalSec = keepAliveIntervalSec,
                    autoReconnectEnabled = autoReconnectEnabled,
                    connectHfpEnabled = connectHfpEnabled,
                    reconnectionTimeout = reconnectionTimeout,
                    onToggleBlacklist = { viewModel.togglePackageBlacklist(it) },
                    onOpenBT = { viewModel.openBluetoothSettings(context) },
                    onDeviceSelect = { viewModel.setTargetDeviceAddress(it) },
                    onConnectManual = { viewModel.connectManually() },
                    onRefreshDevices = { viewModel.updatePairedDevices() },
                    onOpenAppFilter = { navController.navigate("appfilter") },
                    onQualityChange = { viewModel.setAlbumArtQuality(it) },
                    onMirrorMediaChange = { viewModel.setMirrorMediaEnabled(it) },
                    onMirrorIconsChange = { viewModel.setMirrorNotificationIcons(it) },
                    onShowWatchBatteryChange = { viewModel.setShowWatchBattery(it) },
                    onMaxNotificationsChange = { viewModel.setMaxMirroredNotifications(it) },
                    onKeepAliveChange = { viewModel.setKeepAliveIntervalSec(it) },
                    onAutoReconnectChange = { viewModel.setAutoReconnectEnabled(it) },
                    onConnectHfpChange = { viewModel.setConnectHfpEnabled(it) },
                    onReconnectionTimeoutChange = { viewModel.setReconnectionTimeout(it) }
                )
            }
            composable("logs") {
                LogScreen(logs = logs, onClear = { viewModel.clearLogs() })
            }
            composable("appfilter") {
                AppFilterScreen(
                    blacklistedPackages = blacklisted,
                    onToggleBlacklist = { viewModel.togglePackageBlacklist(it) },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

// -- Notification list --------------------------------------------------------

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun NotificationListAdaptive(
    notifications: List<NotificationData>,
    blacklisted: Set<String>,
    onBlockApp: (String) -> Unit,
    onSendNote: (String) -> Unit = {}
) {
    val navigator = rememberListDetailPaneScaffoldNavigator<NotificationData>()
    var selectedNotification by remember { mutableStateOf<NotificationData?>(null) }
    var noteText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Send a notification") },
                singleLine = true,
                trailingIcon = {
                    if (noteText.isNotBlank()) {
                        IconButton(onClick = {
                            onSendNote(noteText.trim())
                            noteText = ""
                        }) {
                            Icon(Icons.Default.Send, contentDescription = "Send")
                        }
                    }
                }
            )
        }
        HorizontalDivider()

        if (notifications.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Notifications, null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(12.dp))
                    Text("No notifications yet", color = MaterialTheme.colorScheme.outline)
                    Text("Current notification's appear here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            ListDetailPaneScaffold(
                modifier = Modifier.weight(1f),
                directive = navigator.scaffoldDirective,
                value = navigator.scaffoldValue,
                listPane = {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(notifications, key = { it.key }) { notification ->
                            NotificationItem(notification, Modifier.clickable {
                                selectedNotification = notification
                                scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail) }
                            })
                        }
                    }
                },
                detailPane = {
                    selectedNotification?.let { notif ->
                        val ctx = LocalContext.current
                        var showBlockConfirm by remember { mutableStateOf(false) }

                        if (showBlockConfirm) {
                            AlertDialog(
                                onDismissRequest = { showBlockConfirm = false },
                                title = { Text("Block App") },
                                text = { Text("Stop notifications from ${getAppName(ctx, notif.packageName)}?") },
                                confirmButton = {
                                    TextButton(onClick = {
                                        onBlockApp(notif.packageName)
                                        showBlockConfirm = false
                                        scope.launch { navigator.navigateBack() }
                                    }) { Text("Block", color = MaterialTheme.colorScheme.error) }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showBlockConfirm = false }) { Text("Cancel") }
                                }
                            )
                        }

                        Column(Modifier.fillMaxSize().padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val appIcon = remember(notif.packageName) { getAppIcon(ctx, notif.packageName) }
                                appIcon?.let {
                                    Image(
                                        bitmap = it.toBitmap(48, 48).asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp))
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        notif.appName ?: getAppName(ctx, notif.packageName),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        formatTimestamp(notif.timestamp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            Text(notif.title ?: "No Title",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(6.dp))
                            Text(notif.text ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(24.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { scope.launch { navigator.navigateBack() } }) {
                                    Text("Back")
                                }
                                Button(
                                    onClick = { showBlockConfirm = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                ) {
                                    Icon(Icons.Default.Block, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Block App")
                                }
                            }
                        }
                    } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Select a notification", color = MaterialTheme.colorScheme.outline)
                    }
                }
            )
        }
    }
}

// -- Settings -----------------------------------------------------------------

@Composable
fun SettingsScreen(
    isConnected: Boolean,
    isConnecting: Boolean,
    lastError: String?,
    watchBatteryLevel: Int?,
    blacklistedPackages: Set<String>,
    targetDeviceAddress: String?,
    pairedDevices: List<android.bluetooth.BluetoothDevice>,
    albumArtQuality: Int,
    mirrorMediaEnabled: Boolean,
    mirrorNotificationIcons: Boolean,
    showWatchBattery: Boolean,
    maxMirroredNotifications: Int,
    keepAliveIntervalSec: Int,
    autoReconnectEnabled: Boolean,
    connectHfpEnabled: Boolean,
    reconnectionTimeout: Long,
    onToggleBlacklist: (String) -> Unit,
    onOpenBT: () -> Unit,
    onDeviceSelect: (String?) -> Unit,
    onConnectManual: () -> Unit,
    onRefreshDevices: () -> Unit,
    onOpenAppFilter: () -> Unit = {},
    onQualityChange: (Int) -> Unit = {},
    onMirrorMediaChange: (Boolean) -> Unit = {},
    onMirrorIconsChange: (Boolean) -> Unit = {},
    onShowWatchBatteryChange: (Boolean) -> Unit = {},
    onMaxNotificationsChange: (Int) -> Unit = {},
    onKeepAliveChange: (Int) -> Unit = {},
    onAutoReconnectChange: (Boolean) -> Unit = {},
    onConnectHfpChange: (Boolean) -> Unit = {},
    onReconnectionTimeoutChange: (Long) -> Unit = {}
) {
    val context = LocalContext.current

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        isConnected -> MaterialTheme.colorScheme.primaryContainer
                        isConnecting -> MaterialTheme.colorScheme.tertiaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Column(Modifier.padding(16.dp).fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(10.dp).clip(CircleShape)
                                .background(
                                    when {
                                        isConnected -> Color(0xFF4CAF50)
                                        isConnecting -> Color(0xFFFFA726)
                                        else -> Color(0xFFE53935)
                                    }
                                )
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when {
                                isConnected -> "Device Connected"
                                isConnecting -> "Connecting"
                                else -> "Not Connected"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (targetDeviceAddress != null) {
                        Spacer(Modifier.height(4.dp))
                        Text("Target: $targetDeviceAddress",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (lastError != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(lastError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                    if (showWatchBattery && watchBatteryLevel != null && isConnected) {
                        Spacer(Modifier.height(4.dp))
                        Text("Battery: $watchBatteryLevel%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item {
            Text("Watch Device",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                var expanded by remember { mutableStateOf(false) }
                Box(Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.BluetoothSearching, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            targetDeviceAddress?.let { addr ->
                                pairedDevices.firstOrNull { it.address == addr }
                                    ?.let { try { it.name } catch (e: SecurityException) { null } } ?: addr
                            } ?: "Select Device...",
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(
                            text = { Text("None") },
                            onClick = { onDeviceSelect(null); expanded = false }
                        )
                        pairedDevices.forEach { device ->
                            val name = try { device.name } catch (e: SecurityException) { null } ?: device.address
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(name, style = MaterialTheme.typography.bodyMedium)
                                        Text(device.address,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline)
                                    }
                                },
                                onClick = { onDeviceSelect(device.address); expanded = false }
                            )
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onRefreshDevices) {
                    Icon(Icons.Default.Refresh, "Refresh")
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onConnectManual,
                    modifier = Modifier.weight(1f),
                    enabled = targetDeviceAddress != null && !isConnected
                ) {
                    if (isConnecting) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Text("Connecting...")
                        }
                    } else {
                        Text(if (isConnected) "Connected" else "Connect")
                    }
                }
                OutlinedButton(
                    onClick = onOpenBT,
                    modifier = Modifier.weight(1f)
                ) { Text("BT Settings") }
            }
        }

        item {
            OutlinedButton(
                onClick = onOpenAppFilter,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Manage App Filters")
                Spacer(Modifier.weight(1f))
                val blockedCount = blacklistedPackages.size
                if (blockedCount > 0) {
                    Badge { Text("$blockedCount") }
                }
            }
        }

        item {
            Text("Settings",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.outline)
        }

        item {
            SettingsToggleRow(
                title = "Mirror media",
                subtitle = "Send now-playing info and artwork, with controls.",
                checked = mirrorMediaEnabled,
                onCheckedChange = onMirrorMediaChange
            )
        }

        item {
            SettingsToggleRow(
                title = "Send app icons",
                subtitle = "Disable to reduce bandwidth, for some reason if you want that on bluetooth.",
                checked = mirrorNotificationIcons,
                onCheckedChange = onMirrorIconsChange
            )
        }

        item {
            SettingsToggleRow(
                title = "Show battery",
                subtitle = "Display battery level in the service notification and settings",
                checked = showWatchBattery,
                onCheckedChange = onShowWatchBatteryChange
            )
        }

        item {
            SettingsToggleRow(
                title = "Auto-reconnect",
                subtitle = "Keep trying to reconnect, don't think it gives up",
                checked = autoReconnectEnabled,
                onCheckedChange = onAutoReconnectChange
            )
        }

        item {
            SettingsToggleRow(
                title = "Call audio (HFP)",
                subtitle = "Experimental: connect the watch for calls alongside notifications.",
                checked = connectHfpEnabled,
                onCheckedChange = onConnectHfpChange
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Max notifications displayed", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text("Notifications are removed beyond this,if you somehow get that much.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = maxMirroredNotifications.toFloat(),
                            onValueChange = { onMaxNotificationsChange(it.toInt()) },
                            valueRange = 10f..100f,
                            steps = 8,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(16.dp))
                        Text("$maxMirroredNotifications",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.widthIn(min = 32.dp))
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Keep-alive interval", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text("How often the phone pings the watch to stay connected.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = keepAliveIntervalSec.toFloat(),
                            onValueChange = { onKeepAliveChange(it.toInt()) },
                            valueRange = 15f..120f,
                            steps = 6,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(16.dp))
                        Text("${keepAliveIntervalSec}s",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.widthIn(min = 40.dp))
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Reconnect delay", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text("Wait time before the first reconnect attempt after disconnect",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = reconnectionTimeout.toFloat(),
                            onValueChange = { onReconnectionTimeoutChange(it.toLong()) },
                            valueRange = 200f..5000f,
                            steps = 4,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(16.dp))
                        Text("${"%.1f".format(reconnectionTimeout / 1000f)}s",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.widthIn(min = 40.dp))
                    }
                }
            }
        }

        item {
            Text("Media",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.outline)
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Album Art Quality", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text("Will make the background higher quality but will increase the loading time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = albumArtQuality.toFloat(),
                            onValueChange = { onQualityChange(it.toInt()) },
                            valueRange = 120f..600f,
                            steps = 3,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(16.dp))
                        Text("${albumArtQuality}px", 
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.widthIn(min = 48.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// -- Log screen ---------------------------------------------------------------

@Composable
fun LogScreen(logs: List<LogEntry>, onClear: () -> Unit) {
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Connection Logs",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onClear) { Text("Clear") }
        }
        HorizontalDivider()
        if (logs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No logs yet..", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(logs) { entry ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            formatTimestamp(entry.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.width(48.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            entry.message,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = if (entry.isError) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

// -- App filter screen --------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppFilterScreen(
    blacklistedPackages: Set<String>,
    onToggleBlacklist: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showSystemApps by remember { mutableStateOf(false) }
    var allApps by remember { mutableStateOf<List<Triple<String, Boolean, String>>>(emptyList()) }

    LaunchedEffect(Unit) {
        allApps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val launcherPkgs = try {
                val li = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
                pm.queryIntentActivities(li, 0).map { it.activityInfo.packageName }.toSet()
            } catch (_: Exception) { emptySet() }
            try {
                pm.getInstalledApplications(0)
                    .map { it.packageName }
                    .distinct()
                    .sorted()
                    .map { pkg ->
                        val name = try {
                            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                        } catch (_: Exception) { pkg }
                        Triple(pkg, launcherPkgs.contains(pkg), name)
                    }
            } catch (_: Exception) {
                launcherPkgs.sorted().map { pkg ->
                    val name = try {
                        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                    } catch (_: Exception) { pkg }
                    Triple(pkg, true, name)
                }
            }
        }
    }

    val filtered = remember(allApps, showSystemApps) {
        if (showSystemApps) allApps else allApps.filter { it.second }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Filters") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("System Apps", style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.width(4.dp))
                        Switch(checked = showSystemApps, onCheckedChange = { showSystemApps = it })
                    }
                }
            )
        }
    ) { padding ->
        if (allApps.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filtered, key = { it.first }) { (pkg, _, _) ->
                    AppFilterRow(
                        context = context,
                        packageName = pkg,
                        isBlacklisted = blacklistedPackages.contains(pkg),
                        onToggle = { onToggleBlacklist(pkg) }
                    )
                }
            }
        }
    }
}

// -- App filter row -----------------------------------------------------------

@Composable
private fun AppFilterRow(
    context: Context,
    packageName: String,
    isBlacklisted: Boolean,
    onToggle: () -> Unit
) {
    val appName = remember(packageName) { getAppName(context, packageName) }
    val appIcon = remember(packageName) { getAppIcon(context, packageName) }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        appIcon?.let {
            Image(
                bitmap = it.toBitmap(48, 48).asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp))
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(appName, Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Switch(checked = !isBlacklisted, onCheckedChange = { onToggle() })
    }
}

// -- Helpers ------------------------------------------------------------------

private fun getAppName(context: Context, packageName: String): String = try {
    context.packageManager.getApplicationLabel(
        context.packageManager.getApplicationInfo(packageName, 0)
    ).toString()
} catch (_: PackageManager.NameNotFoundException) { packageName }

private fun getAppIcon(context: Context, packageName: String): Drawable? = try {
    context.packageManager.getApplicationIcon(packageName)
} catch (_: PackageManager.NameNotFoundException) { null }

private fun formatTimestamp(ts: Long?): String {
    if (ts == null || ts == 0L) return ""
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
}

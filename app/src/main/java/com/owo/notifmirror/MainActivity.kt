package com.found404.sidelink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import com.found404.sidelink.data.repository.MirrorRepository
import com.found404.sidelink.ui.NotifMirrorApp
import com.found404.sidelink.ui.theme.NotifMirrorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize repository first
        MirrorRepository.initialize(this)

        val permissions = mutableListOf<String>()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            permissions.add(android.Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(android.Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(android.Manifest.permission.BLUETOOTH_SCAN)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        permissions.add(android.Manifest.permission.ACCESS_FINE_LOCATION)

        requestPermissions(permissions.toTypedArray(), 1)

        enableEdgeToEdge()
        setContent {
            NotifMirrorTheme {
                NotifMirrorApp()
            }
        }
    }
}

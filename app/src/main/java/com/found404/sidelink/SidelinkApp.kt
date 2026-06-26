package com.found404.sidelink

import android.app.Application
import com.found404.sidelink.communication.BluetoothCommunicationManager
import com.found404.sidelink.data.repository.SettingsRepository
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SidelinkApp : Application() {
    override fun onCreate() {
        super.onCreate()
        BluetoothCommunicationManager.getInstance(this)
            .ensureStarted(SettingsRepository(this))
    }
}

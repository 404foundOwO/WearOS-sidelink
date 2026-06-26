package com.found404.sidelink.data.repository

import android.content.Context
import com.found404.sidelink.data.local.AppDatabase
import com.found404.sidelink.data.local.NotificationDao
import com.found404.sidelink.data.model.MediaData
import com.found404.sidelink.data.model.NotificationData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

object MirrorRepository {
    private var notificationDao: NotificationDao? = null
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _notifications = MutableStateFlow<List<NotificationData>>(emptyList())
    private val _databaseInitialized = MutableStateFlow(false)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val notifications: Flow<List<NotificationData>> = _databaseInitialized.flatMapLatest { initialized ->
        val dao = notificationDao
        if (initialized && dao != null) {
            dao.getAllNotifications().map { entities ->
                entities.map { NotificationData.fromEntity(it) }
            }
        } else {
            _notifications
        }
    }

    private val _activeMedia = MutableStateFlow<MediaData?>(null)
    val activeMedia: StateFlow<MediaData?> = _activeMedia.asStateFlow()

    fun initialize(context: Context) {
        if (notificationDao == null) {
            val database = AppDatabase.getDatabase(context)
            notificationDao = database.notificationDao()
            _databaseInitialized.value = true
        }
    }

    fun updateNotifications(newNotifications: List<NotificationData>, maxCount: Int = 50) {
        repositoryScope.launch {
            val capped = newNotifications.take(maxCount)
            val dao = notificationDao
            if (dao != null) {
                dao.syncNotifications(capped.map { it.toEntity() })
            } else {
                _notifications.value = capped
            }
        }
    }

    fun addNotification(notification: NotificationData, maxCount: Int = 50) {
        repositoryScope.launch {
            val dao = notificationDao
            if (dao != null) {
                dao.insertNotification(notification.toEntity())
                dao.deleteOldNotifications(maxCount)
            } else {
                _notifications.value = (listOf(notification) + _notifications.value.filter { it.id != notification.id }).take(maxCount)
            }
        }
    }

    fun removeNotification(id: String) {
        repositoryScope.launch {
            val dao = notificationDao
            if (dao != null) {
                dao.deleteNotification(id)
            } else {
                _notifications.value = _notifications.value.filter { it.id != id }
            }
        }
    }

    fun updateMedia(mediaData: MediaData?) {
        _activeMedia.value = mediaData
    }
}

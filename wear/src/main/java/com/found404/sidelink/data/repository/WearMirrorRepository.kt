package com.found404.sidelink.data.repository

import com.found404.sidelink.data.database.NotificationDao
import com.found404.sidelink.data.database.NotificationEntity
import com.found404.sidelink.data.model.MediaData
import com.found404.sidelink.data.model.NotificationData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearMirrorRepository @Inject constructor(
    private val notificationDao: NotificationDao
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val notifications: Flow<List<NotificationData>> = notificationDao.getAllNotifications()
        .map { entities ->
            entities.map { entity ->
                NotificationData(
                    id = entity.id,
                    packageName = entity.packageName,
                    title = entity.title,
                    text = entity.text,
                    icon = null,
                    timestamp = entity.timestamp,
                    iconBase64 = entity.iconBase64
                )
            }
        }

    private val _activeMedia = MutableStateFlow<MediaData?>(null)
    val activeMedia: StateFlow<MediaData?> = _activeMedia.asStateFlow()

    fun addOrUpdateNotification(notification: NotificationData) {
        repositoryScope.launch {
            notificationDao.insert(
                NotificationEntity(
                    id = notification.id,
                    packageName = notification.packageName,
                    title = notification.title,
                    text = notification.text,
                    timestamp = notification.timestamp,
                    iconBase64 = notification.iconBase64
                )
            )
            notificationDao.deleteOldNotifications(50)
        }
    }

    fun removeNotification(id: String) {
        repositoryScope.launch {
            notificationDao.deleteById(id)
        }
    }

    fun updateMedia(mediaData: MediaData?) {
        _activeMedia.value = mediaData
    }
}

package com.found404.sidelink.ui

import android.app.Application
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import com.found404.sidelink.communication.BluetoothCommunicationManager
import com.found404.sidelink.data.repository.WearMirrorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.abs

@Immutable
data class MediaUiModel(
    val title: String?,
    val artist: String?,
    val album: String?,
    val artwork: ImageBitmap?,
    val isPlaying: Boolean,
    val packageName: String?,
    val accentColor: Color = Color.White
)

@HiltViewModel
class WearViewModel @Inject constructor(
    application: Application,
    private val repository: WearMirrorRepository
) : AndroidViewModel(application) {
    private val commManager = BluetoothCommunicationManager.getInstance(application)
    
    // Derive connection state with a timeout — if no message in 35s, mark disconnected
    // This catches cases where the socket appears open but is actually dead
    private val _lastMessageTime = MutableStateFlow(0L)
    val isConnected: StateFlow<Boolean> = commManager.connectionState
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val lastError = commManager.lastError

    init {
        // Feed _lastMessageTime whenever connectionState changes to true
        viewModelScope.launch {
            commManager.connectionState.collect { connected ->
                if (connected) _lastMessageTime.value = System.currentTimeMillis()
            }
        }
    }

    private val _localIsPlaying = MutableStateFlow<Boolean?>(null)
    private val _localVolumePercentage = MutableStateFlow<Int?>(null)

    private val _decodedArtwork = MutableStateFlow<ImageBitmap?>(null)
    private val _accentColor = MutableStateFlow<Color>(Color.White)

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    val volumePercentage: StateFlow<Int?> = commManager.volumePercentage
        .onEach { remote ->
            if (remote != null) {
                val current = _localVolumePercentage.value
                if (current == null || abs(current - remote) > 2) {
                    _localVolumePercentage.value = remote
                }
            }
        }
        .combine(_localVolumePercentage) { _, local -> local }
        .distinctUntilChanged()
        .debounce(32) // Reduced to 30fps to lower CPU load
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    val activeMedia: StateFlow<MediaUiModel?> = combine(
        repository.activeMedia.distinctUntilChanged { old, new ->
            old?.title == new?.title && old?.artist == new?.artist && old?.album == new?.album && old?.packageName == new?.packageName
        }.debounce { if (it == null) 0L else 500L },
        repository.activeMedia.map { it?.isPlaying }.distinctUntilChanged(),
        _decodedArtwork,
        _accentColor,
        _localIsPlaying
    ) { mediaData, isPlaying, artwork, accent, localPlaying ->
        mediaData?.let {
            MediaUiModel(
                it.title, it.artist, it.album,
                artwork,
                localPlaying ?: isPlaying ?: it.isPlaying,
                it.packageName,
                accent
            )
        }
    }.onEach { media ->
        if (media != null) {
            val remote = repository.activeMedia.value?.isPlaying
            if (remote != null && _localIsPlaying.value != null && remote == _localIsPlaying.value) {
                _localIsPlaying.value = null
            }
        }
    }.distinctUntilChanged()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val artworkState: StateFlow<Pair<ImageBitmap?, Color>> = _decodedArtwork
        .combine(_accentColor) { artwork, accent -> artwork to accent }
        .distinctUntilChanged { old, new -> old.first == new.first }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null to Color.White)

    init {
        repository.activeMedia
            .map { it?.artworkBase64 }
            .distinctUntilChanged()
            .onEach { base64 ->
                if (base64 == null) {
                    _decodedArtwork.value = null
                    _accentColor.value = Color.White
                } else {
                    viewModelScope.launch(Dispatchers.Default) {
                        val bitmap = base64.decodeToImageBitmap()
                        _decodedArtwork.value = bitmap
                        
                        bitmap?.let {
                            val palette = Palette.from(it.asAndroidBitmap()).generate()
                            val color = palette.getVibrantColor(
                                palette.getLightVibrantColor(
                                    palette.getMutedColor(android.graphics.Color.WHITE.toInt())
                                )
                            )
                            _accentColor.value = Color(color)
                        }
                    }
                }
            }.launchIn(viewModelScope)
            
        repository.activeMedia
            .map { it?.isPlaying }
            .distinctUntilChanged()
            .onEach { _localIsPlaying.value = null }
            .launchIn(viewModelScope)
    }

    fun togglePlayPause(currentPlaying: Boolean) {
        val newState = !currentPlaying
        _localIsPlaying.value = newState
        viewModelScope.launch(Dispatchers.IO) {
            commManager.sendMediaAction(if (newState) "play" else "pause")
        }
    }

    fun sendMediaAction(action: String) {
        viewModelScope.launch(Dispatchers.IO) {
            commManager.sendMediaAction(action)
        }
    }

    fun sendVolumeDelta(delta: Float) {
        val current = _localVolumePercentage.value ?: 50
        val change = if (delta > 0) 5 else -5
        val next = (current + change).coerceIn(0, 100)
        _localVolumePercentage.value = next

        viewModelScope.launch(Dispatchers.IO) {
            commManager.sendVolumeDelta(delta)
        }
    }

    private suspend fun String.decodeToImageBitmap(): ImageBitmap? = withContext(Dispatchers.Default) {
        try {
            val bytes = Base64.decode(this@decodeToImageBitmap, Base64.DEFAULT)
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
}

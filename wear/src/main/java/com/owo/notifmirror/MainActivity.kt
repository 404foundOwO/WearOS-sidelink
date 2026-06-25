package com.found404.sidelink

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BluetoothConnected
import androidx.compose.material.icons.outlined.BluetoothDisabled
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.*
import androidx.wear.ambient.AmbientLifecycleObserver
import com.found404.sidelink.service.WearMirrorService
import com.found404.sidelink.shared.CommunicationConstants
import com.found404.sidelink.ui.WearViewModel
import com.found404.sidelink.ui.MediaUiModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val isAmbientMode = mutableStateOf(value = false)

    private val ambientCallback = object : AmbientLifecycleObserver.AmbientLifecycleCallback {
        override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
            isAmbientMode.value = true
        }

        override fun onExitAmbient() {
            isAmbientMode.value = false
        }

        override fun onUpdateAmbient() {
            // Optional: update UI every minute
        }
    }

    private lateinit var ambientObserver: AmbientLifecycleObserver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        ambientObserver = AmbientLifecycleObserver(this, ambientCallback)
        lifecycle.addObserver(ambientObserver)
        
        val requiredPermissions = mutableListOf<String>()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            requiredPermissions.add(android.Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            requestPermissions(missingPermissions.toTypedArray(), 1)
        }

        startService(Intent(this, WearMirrorService::class.java))

        setContent { 
            MaterialTheme {
                WearApp(isAmbient = isAmbientMode.value)
            }
        }
    }
}

@Composable
fun WearApp(viewModel: WearViewModel = viewModel(), isAmbient: Boolean = false) {
    val context = LocalContext.current
    val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator }
    val focusRequester = remember { FocusRequester() }
    val haptic = LocalHapticFeedback.current

    val volume by viewModel.volumePercentage.collectAsStateWithLifecycle()
    val prevVolume = remember { mutableStateOf(volume ?: 50) }

    SideEffect {
        val current = volume ?: 50
        val amplitude = (current / 100f * 180f + 20f).toInt().coerceIn(1, 255)
        // 0% vol = amplitude 20 (barely felt)
        // 50% vol = amplitude 110 (medium)  
        // 100% vol = amplitude 200 (strong)
        vibrator.vibrate(VibrationEffect.createOneShot(8, amplitude))
    }

    LaunchedEffect(volume) {
        val current = volume ?: return@LaunchedEffect
        val prev = prevVolume.value
        prevVolume.value = current

        when {
            // upper limit
            current == 100 && prev < 100 -> {
                repeat(3) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    delay(40)
                }
            }
            // lower limit
            current == 0 && prev > 0 -> {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                delay(80)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            // every 10% boundary crossed
            current % 10 == 0 -> {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }

    val onRotaryEvent = remember(viewModel, haptic) {
        { delta: Float ->
            viewModel.sendVolumeDelta(delta)
            true
        }
    }

    Scaffold(
        timeText = { TimeTextWrapper(viewModel) },
        modifier = Modifier
            .onRotaryScrollEvent { onRotaryEvent(it.verticalScrollPixels) }
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            if (!isAmbient) {
                FullScreenBackground(viewModel)
            }

            MainContent(viewModel, isAmbient)
        }
    }
    
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
private fun TimeTextWrapper(viewModel: WearViewModel) {
    val activeMedia by viewModel.activeMedia.collectAsStateWithLifecycle()
    if (activeMedia == null) {
        TimeText()
    }
}

@Composable
private fun FullScreenBackground(viewModel: WearViewModel) {
    val artworkPair by viewModel.artworkState.collectAsStateWithLifecycle()
    val artwork = artworkPair.first
    
    key(artwork) {
        if (artwork != null) {
            Image(
                bitmap = artwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f))
            )
        }
    }
}

@Composable
private fun MainContent(viewModel: WearViewModel, isAmbient: Boolean) {
    val activeMedia by viewModel.activeMedia.collectAsStateWithLifecycle()
    val accentColor by remember { 
        derivedStateOf { activeMedia?.accentColor ?: Color(0xFF1DB954) } 
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ConnectionStatus(viewModel, accentColor, isAmbient)

            Spacer(Modifier.height(8.dp))

            if (activeMedia != null) {
                MediaView(activeMedia!!, viewModel, isAmbient)
            } else {
                NoMediaPlaceholder()
            }
        }
    }
}

@Composable
private fun ConnectionStatus(viewModel: WearViewModel, accentColor: Color, isAmbient: Boolean) {
    val isConnected by viewModel.isConnected.collectAsStateWithLifecycle()
    
    val tint = if (isAmbient) {
        Color.White
    } else {
        if (isConnected) accentColor else Color.Red
    }
    
    val text = if (isConnected) "Linked" else "Disconnected"
    val textColor = if (isAmbient) {
        Color.White.copy(alpha = 0.6f)
    } else {
        (if (isConnected) accentColor else Color.Gray).copy(alpha = 0.7f)
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (isConnected) Icons.Outlined.BluetoothConnected else Icons.Outlined.BluetoothDisabled,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(10.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.caption3,
            color = textColor
        )
    }
}

@Composable
private fun NoMediaPlaceholder() {
    Text(
        "No media active",
        style = MaterialTheme.typography.caption2,
        color = Color.Gray
    )
}

@Composable
fun MediaView(media: MediaUiModel, viewModel: WearViewModel, isAmbient: Boolean) {
    val accentColor = if (isAmbient) Color.White else media.accentColor
    val haptic = LocalHapticFeedback.current

    val onPrevClick = remember(viewModel, haptic) {
        {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            viewModel.sendMediaAction(CommunicationConstants.ACTION_PREVIOUS)
        }
    }
    val onPlayPauseClick = remember(viewModel, haptic, media.isPlaying) {
        {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            viewModel.togglePlayPause(media.isPlaying)
        }
    }
    val onNextClick = remember(viewModel, haptic) {
        {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            viewModel.sendMediaAction(CommunicationConstants.ACTION_NEXT)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = media.title ?: "Unknown",
            style = MaterialTheme.typography.title3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = media.artist ?: "Unknown Artist",
            style = MaterialTheme.typography.caption1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = Color.LightGray
        )
        
        Spacer(Modifier.height(14.dp))
        
        MediaControls(
            isPlaying = media.isPlaying,
            accentColor = accentColor,
            isAmbient = isAmbient,
            onPrevClick = onPrevClick,
            onPlayPauseClick = onPlayPauseClick,
            onNextClick = onNextClick
        )
    }
}

@Composable
private fun MediaControls(
    isPlaying: Boolean,
    accentColor: Color,
    isAmbient: Boolean,
    onPrevClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onPrevClick,
            modifier = Modifier.size(ButtonDefaults.SmallButtonSize),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = if (isAmbient) Color.Transparent else accentColor.copy(alpha = 0.2f),
                contentColor = if (isAmbient) Color.White else accentColor
            ),
            border = if (isAmbient) {
                ButtonDefaults.outlinedButtonBorder(Color.White.copy(alpha = 0.5f))
            } else {
                ButtonDefaults.buttonBorder()
            }
        ) {
            Icon(Icons.Default.SkipPrevious, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        
        Button(
            onClick = onPlayPauseClick,
            modifier = Modifier.size(ButtonDefaults.DefaultButtonSize),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = if (isAmbient) Color.Transparent else accentColor,
                contentColor = if (isAmbient) Color.White else (if (accentColor == Color.White) Color.Black else Color.White)
            ),
            border = if (isAmbient) {
                ButtonDefaults.outlinedButtonBorder(Color.White)
            } else {
                ButtonDefaults.buttonBorder()
            }
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(26.dp)
            )
        }
        
        Button(
            onClick = onNextClick,
            modifier = Modifier.size(ButtonDefaults.SmallButtonSize),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = if (isAmbient) Color.Transparent else accentColor.copy(alpha = 0.2f),
                contentColor = if (isAmbient) Color.White else accentColor
            ),
            border = if (isAmbient) {
                ButtonDefaults.outlinedButtonBorder(Color.White.copy(alpha = 0.5f))
            } else {
                ButtonDefaults.buttonBorder()
            }
        ) {
            Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

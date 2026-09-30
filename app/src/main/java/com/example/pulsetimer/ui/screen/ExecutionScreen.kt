package com.pulsetimer.ui.screen

import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.widget.VideoView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulsetimer.data.AppSettingsStore
import com.pulsetimer.viewmodel.TimerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

@Composable
fun ExecutionScreen(
    viewModel: TimerViewModel = viewModel(),
    templateId: Long,
    templateName: String,
    onFinish: () -> Unit
) {
    val timerState by viewModel.timerState.collectAsState()
    val context = LocalContext.current
    val settings by AppSettingsStore.settings.collectAsState()
    val phaseColor = parseColor(timerState.currentIntervalColor)
    val backgroundImage = androidx.compose.runtime.produceState<android.graphics.Bitmap?>(
        initialValue = null,
        timerState.backgroundType,
        timerState.backgroundValue
    ) {
        value = if (timerState.backgroundType == "CUSTOM_IMAGE" && timerState.backgroundValue.isNotBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(Uri.parse(timerState.backgroundValue))
                        ?.use(BitmapFactory::decodeStream)
                } catch (error: IOException) {
                    Log.e("ExecutionScreen", "Unable to load selected training background", error)
                    null
                } catch (error: SecurityException) {
                    Log.e("ExecutionScreen", "Access to selected training background was lost", error)
                    null
                }
            }
        } else {
            null
        }
    }
    val backgroundColor by animateColorAsState(
        targetValue = phaseColor,
        label = "bg_color"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    if (dragAmount < -50) {
                        viewModel.skipInterval()
                    }
                }
            }
    ) {
        val image = backgroundImage.value
        if (
            settings.animatedBackgrounds &&
            timerState.backgroundType == "VIDEO" &&
            timerState.backgroundValue.isNotBlank()
        ) {
            VideoPhaseBackground(
                uri = timerState.backgroundValue,
                isPaused = timerState.isPaused,
                fallbackColor = backgroundColor
            )
        } else if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
        } else {
            if (settings.animatedBackgrounds) {
                AnimatedPhaseBackground(color = backgroundColor)
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(backgroundColor, Color(0xFF0B0B0B))
                            )
                        )
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    viewModel.stopTimer()
                    onFinish()
                }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Закрыть",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Text(
                    text = timerState.templateName.ifEmpty { templateName },
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge
                )
                Box(modifier = Modifier.size(48.dp))
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedContent(
                    targetState = IntervalLabel(
                        index = timerState.currentIntervalIndex,
                        name = timerState.currentIntervalName,
                        total = timerState.totalIntervals
                    ),
                    transitionSpec = {
                        val movingForward = targetState.index > initialState.index
                        val enterOffset = if (movingForward) 1 else -1
                        (
                            slideInHorizontally(
                                initialOffsetX = { width -> width * enterOffset },
                                animationSpec = tween(durationMillis = 350)
                            ) + fadeIn(animationSpec = tween(durationMillis = 250))
                        ).togetherWith(
                            slideOutHorizontally(
                                targetOffsetX = { width -> -width * enterOffset },
                                animationSpec = tween(durationMillis = 350)
                            ) + fadeOut(animationSpec = tween(durationMillis = 250))
                        )
                    },
                    label = "interval_page"
                ) { label ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = label.name,
                            color = Color.White,
                            style = MaterialTheme.typography.headlineLarge,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Интервал ${label.index + 1} из ${label.total}",
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = formatTime(timerState.timeRemainingSeconds),
                        color = Color.White,
                        fontSize = (maxWidth.value / 3.6f).coerceIn(56f, 120f).sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 1
                    )
                }
            }

            if (timerState.isFinished) {
                Button(
                    onClick = onFinish,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = "ГОТОВО",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.stopTimer(); onFinish() },
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Стоп",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            if (timerState.isPaused) {
                                viewModel.resumeTimer()
                            } else {
                                viewModel.pauseTimer()
                            }
                        },
                        modifier = Modifier
                            .size(80.dp)
                            .background(Color.White.copy(alpha = 0.3f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (timerState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (timerState.isPaused) "Продолжить" else "Пауза",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    IconButton(
                        onClick = { viewModel.skipInterval() },
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = "Пропустить",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            Text(
                text = "Свайп влево — пропустить интервал",
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

private data class IntervalLabel(
    val index: Int,
    val name: String,
    val total: Int
)

@Composable
private fun VideoPhaseBackground(uri: String, isPaused: Boolean, fallbackColor: Color) {
    val context = LocalContext.current
    val videoView = remember(context) { VideoView(context) }
    var prepared by remember(videoView, uri) { mutableStateOf(false) }
    var failed by remember(videoView, uri) { mutableStateOf(false) }

    DisposableEffect(videoView) {
        onDispose {
            videoView.stopPlayback()
        }
    }
    LaunchedEffect(videoView, uri) {
        prepared = false
        failed = false
        videoView.setOnPreparedListener { player: MediaPlayer ->
            player.isLooping = true
            player.setVolume(0f, 0f)
            prepared = true
        }
        videoView.setOnErrorListener { _, what, extra ->
            Log.e("ExecutionScreen", "Unable to play selected video background ($what, $extra)")
            failed = true
            true
        }
        videoView.setVideoURI(Uri.parse(uri))
    }
    LaunchedEffect(prepared, isPaused) {
        if (prepared) {
            if (isPaused) videoView.pause() else videoView.start()
        }
    }

    if (failed) {
        StaticPhaseBackground(fallbackColor)
    } else {
        AndroidView(factory = { videoView }, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
    }
}

@Composable
private fun StaticPhaseBackground(color: Color) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(color, Color(0xFF0B0B0B))))
    )
}

@Composable
private fun AnimatedPhaseBackground(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "workout_background")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 240f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8_000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "background_gradient"
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(color, Color(0xFF0B0B0B)),
                    startY = progress,
                    endY = 1_100f + progress
                )
            )
    )
}

fun parseColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        Color(0xFF007AFF)
    }
}

fun formatTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format(Locale.ROOT, "%02d:%02d", m, s)
}
package com.pulsetimer.ui.screen

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.util.Log
import android.widget.VideoView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulsetimer.data.AppSettingsStore
import com.pulsetimer.viewmodel.TimerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun ExecutionScreen(
    viewModel: TimerViewModel = viewModel(),
    templateName: String,
    onFinish: () -> Unit
) {
    val timerState by viewModel.timerState.collectAsState()
    val settings by AppSettingsStore.settings.collectAsState()

    // Status Bar в тон фазы
    val view = LocalView.current
    val activity = view.context as? Activity
    DisposableEffect(Unit) {
        val window = activity?.window
        val previousColor = window?.statusBarColor
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousLight = controller?.isAppearanceLightStatusBars
        onDispose {
            if (window != null && previousColor != null) window.statusBarColor = previousColor
            if (controller != null && previousLight != null) {
                controller.isAppearanceLightStatusBars = previousLight
            }
        }
    }
    val currentPhaseColor = parseColor(timerState.currentIntervalColor)
    LaunchedEffect(currentPhaseColor) {
        val window = activity?.window ?: return@LaunchedEffect
        window.statusBarColor = currentPhaseColor.toArgb()
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
            currentPhaseColor.luminance() > 0.55f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var totalDrag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        totalDrag += dragAmount
                    },
                    onDragEnd = {
                        // Только вперёд — свайп влево
                        if (totalDrag < -50f) viewModel.skipInterval()
                    }
                )
            }
    ) {
        val smoothBg by animateColorAsState(
            targetValue = currentPhaseColor,
            label = "smooth_bg"
        )
        Box(Modifier.fillMaxSize().background(smoothBg))

        AnimatedContent(
            targetState = PhaseKey(
                index = timerState.currentIntervalIndex,
                name = timerState.currentIntervalName,
                colorHex = timerState.currentIntervalColor,
                backgroundType = timerState.backgroundType,
                backgroundValue = timerState.backgroundValue
            ),
            transitionSpec = {
                val forward = targetState.index >= initialState.index
                val direction = if (forward) 1 else -1
                phaseTransition(settings.intervalAnimation, direction)
            },
            modifier = Modifier.fillMaxSize(),
            label = "phase_transition"
        ) { phase ->
            PhaseFullScreen(
                phase = phase,
                templateName = timerState.templateName.ifEmpty { templateName },
                timeRemaining = timerState.timeRemainingSeconds,
                totalIntervals = timerState.totalIntervals,
                isPaused = timerState.isPaused,
                isFinished = timerState.isFinished,
                animatedBackgrounds = settings.animatedBackgrounds,
                onClose = { viewModel.stopTimer(); onFinish() },
                onPauseToggle = {
                    if (timerState.isPaused) viewModel.resumeTimer() else viewModel.pauseTimer()
                },
                onSkip = { viewModel.skipInterval() },
                onStop = { viewModel.stopTimer(); onFinish() },
                onFinish = onFinish
            )
        }
    }
}

private data class PhaseKey(
    val index: Int,
    val name: String,
    val colorHex: String,
    val backgroundType: String,
    val backgroundValue: String
)

private fun phaseTransition(animation: String, direction: Int): ContentTransform {
    return when (animation) {
        "FADE" -> fadeIn(tween(400)) togetherWith fadeOut(tween(400))
        "ZOOM" -> (
                fadeIn(tween(300)) + scaleIn(tween(450), initialScale = 0.85f)
                ) togetherWith (
                fadeOut(tween(300)) + scaleOut(tween(450), targetScale = 1.15f)
                )
        "GLIDE" -> (
                slideInVertically(tween(450), initialOffsetY = { h -> h * direction }) + fadeIn(tween(300))
                ) togetherWith (
                slideOutVertically(tween(450), targetOffsetY = { h -> -h * direction }) + fadeOut(tween(300))
                )
        "BOUNCE" -> {
            val spec: FiniteAnimationSpec<IntOffset> = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
            slideInHorizontally(spec, initialOffsetX = { w -> w * direction }) togetherWith
                    slideOutHorizontally(spec, targetOffsetX = { w -> -w * direction })
        }
        else -> (
                slideInHorizontally(tween(420), initialOffsetX = { w -> w * direction }) + fadeIn(tween(240))
                ) togetherWith (
                slideOutHorizontally(tween(420), targetOffsetX = { w -> -w * direction }) + fadeOut(tween(240))
                )
    }
}

@Composable
private fun PhaseFullScreen(
    phase: PhaseKey,
    templateName: String,
    timeRemaining: Int,
    totalIntervals: Int,
    isPaused: Boolean,
    isFinished: Boolean,
    animatedBackgrounds: Boolean,
    onClose: () -> Unit,
    onPauseToggle: () -> Unit,
    onSkip: () -> Unit,
    onStop: () -> Unit,
    onFinish: () -> Unit
) {
    val phaseColor = parseColor(phase.colorHex)
    // Размер шрифта таймера — без BoxWithConstraints (Lint ругался на неиспользуемый scope)
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.toFloat()
    val timerFontSize = ((screenWidthDp - 48f) / 3.6f).coerceIn(56f, 120f).sp

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            animatedBackgrounds && phase.backgroundType == "VIDEO" && phase.backgroundValue.isNotBlank() ->
                VideoPhaseBackground(
                    uri = phase.backgroundValue,
                    isPaused = isPaused,
                    fallbackColor = phaseColor
                )
            phase.backgroundType == "CUSTOM_IMAGE" && phase.backgroundValue.isNotBlank() ->
                PhaseImageBackground(uri = phase.backgroundValue, fallbackColor = phaseColor)
            animatedBackgrounds -> AnimatedPhaseBackground(color = phaseColor)
            else -> StaticPhaseBackground(color = phaseColor)
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
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Default.Close, "Закрыть",
                        tint = Color.White, modifier = Modifier.size(32.dp)
                    )
                }
                Text(templateName, color = Color.White, style = MaterialTheme.typography.titleLarge)
                Box(modifier = Modifier.size(48.dp))
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = phase.name,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Интервал ${phase.index + 1} из $totalIntervals",
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = formatTime(timeRemaining),
                    color = Color.White,
                    fontSize = timerFontSize,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 1
                )
            }

            if (isFinished) {
                Button(
                    onClick = onFinish,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.3f)
                    )
                ) {
                    Text("ГОТОВО", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onStop,
                        modifier = Modifier.size(64.dp).background(Color.White.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(Icons.Default.Stop, "Стоп", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                    IconButton(
                        onClick = onPauseToggle,
                        modifier = Modifier.size(80.dp).background(Color.White.copy(alpha = 0.3f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (isPaused) "Продолжить" else "Пауза",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    IconButton(
                        onClick = onSkip,
                        modifier = Modifier.size(64.dp).background(Color.White.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(Icons.Default.KeyboardArrowRight, "Пропустить", tint = Color.White, modifier = Modifier.size(32.dp))
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

@Composable
private fun PhaseImageBackground(uri: String, fallbackColor: Color) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            decodeSampledBitmap(context, uri)
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
    } else {
        StaticPhaseBackground(fallbackColor)
    }
}

/**
 * Загружает bitmap с понижением разрешения, чтобы избежать OOM на больших картинках.
 */
private fun decodeSampledBitmap(
    context: android.content.Context,
    uri: String,
    reqSize: Int = 1080
): Bitmap? {
    return try {
        val parsedUri = uri.toUri()
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(parsedUri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
        var sample = 1
        while (opts.outWidth / (sample * 2) >= reqSize && opts.outHeight / (sample * 2) >= reqSize) {
            sample *= 2
        }
        val opts2 = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(parsedUri)?.use {
            BitmapFactory.decodeStream(it, null, opts2)
        }
    } catch (e: Exception) {
        Log.e("ExecutionScreen", "Unable to decode background", e)
        null
    }
}

@Composable
private fun VideoPhaseBackground(uri: String, isPaused: Boolean, fallbackColor: Color) {
    val context = LocalContext.current
    val videoView = remember(context) { VideoView(context) }
    var prepared by remember(videoView, uri) { mutableStateOf(false) }
    var failed by remember(videoView, uri) { mutableStateOf(false) }

    DisposableEffect(videoView) {
        onDispose { videoView.stopPlayback() }
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
            Log.e("ExecutionScreen", "Video error ($what, $extra)")
            failed = true
            true
        }
        videoView.setVideoURI(uri.toUri())
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

fun parseColor(hex: String): Color = try {
    Color(hex.toColorInt())
} catch (e: Exception) {
    Color(0xFF007AFF)
}

fun formatTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format(Locale.ROOT, "%02d:%02d", m, s)
}
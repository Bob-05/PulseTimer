package com.pulsetimer.ui.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val LaunchBackground = Color(0xFF0B0B0B)
private val Accent = Color(0xFFA6C4B1)
private val WorkColor = Color(0xFFFF3B30)
private val RestColor = Color(0xFF007AFF)

@Composable
fun LaunchAnimationScreen(onFinished: () -> Unit) {
    val needleAngle = remember { Animatable(-30f) }
    val waveProgress = remember { Animatable(0f) }
    val ringProgress = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    val currentOnFinished = rememberUpdatedState(onFinished)

    LaunchedEffect(Unit) {
        coroutineScope {
            launch {
                needleAngle.animateTo(
                    targetValue = 330f,
                    animationSpec = tween(
                        durationMillis = 700,
                        easing = FastOutSlowInEasing
                    )
                )
            }
            launch {
                delay(350)
                waveProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 900,
                        easing = FastOutSlowInEasing
                    )
                )
            }
            launch {
                delay(900)
                ringProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 700,
                        easing = FastOutSlowInEasing
                    )
                )
            }
            launch {
                delay(1350)
                fade.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = 250,
                        easing = FastOutSlowInEasing
                    )
                )
            }

            delay(1600)
            currentOnFinished.value()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LaunchBackground)
            .graphicsLayer { alpha = fade.value },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val progress = ringProgress.value
            drawCircle(
                color = Accent.copy(alpha = 0.6f * (1f - progress)),
                radius = (60f + 100f * progress).dp.toPx(),
                center = Offset(size.width / 2f, size.height / 2f),
                style = Stroke(width = 1.5.dp.toPx())
            )
        }

        Box(
            modifier = Modifier.size(240.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val baselineY = centerY + 30.dp.toPx()
                val leftX = centerX - 62.dp.toPx()
                val rightX = centerX + 62.dp.toPx()

                val fullPath = Path().apply {
                    moveTo(leftX, baselineY)
                    cubicTo(
                        leftX + 14.dp.toPx(),
                        baselineY,
                        leftX + 23.dp.toPx(),
                        baselineY,
                        leftX + 31.dp.toPx(),
                        baselineY
                    )
                    lineTo(leftX + 35.dp.toPx(), baselineY + 4.dp.toPx())
                    lineTo(leftX + 49.dp.toPx(), baselineY - 30.dp.toPx())
                    lineTo(leftX + 62.dp.toPx(), baselineY + 32.dp.toPx())
                    lineTo(leftX + 72.dp.toPx(), baselineY)
                    lineTo(rightX, baselineY)
                }
                val pathMeasure = PathMeasure().apply {
                    setPath(fullPath, false)
                }
                val visiblePath = Path()
                pathMeasure.getSegment(
                    startDistance = 0f,
                    stopDistance = pathMeasure.length * waveProgress.value,
                    destination = visiblePath,
                    startWithMoveTo = true
                )

                drawPath(
                    path = visiblePath,
                    brush = Brush.linearGradient(
                        colors = listOf(WorkColor, RestColor),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f)
                    ),
                    style = Stroke(
                        width = 4.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = 45.dp)
                    .width(90.dp)
                    .height(3.dp)
                    .graphicsLayer {
                        rotationZ = needleAngle.value
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
                    .background(Color.White, CircleShape)
            )

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(12.dp)
                    .background(Color.White, CircleShape)
            )
        }
    }
}

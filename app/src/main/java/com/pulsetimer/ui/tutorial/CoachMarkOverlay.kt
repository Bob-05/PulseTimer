package com.pulsetimer.ui.tutorial

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Полноэкранный слой обучения: затемняет интерфейс, оставляет «дырку» вокруг
 * целевого элемента и показывает подсказку с кнопками «Назад / Пропустить / Далее».
 *
 * Координаты цели хранятся в оконной системе (см. [TutorialTargetRegistry]),
 * поэтому сначала пересчитываем их в локальные координаты слоя — так разметка
 * не зависит от того, где именно в иерархии расположен сам слой.
 */
@Composable
fun CoachMarkOverlay(
    steps: List<TutorialStep>,
    targets: TutorialTargetRegistry,
    onFinish: () -> Unit
) {
    if (steps.isEmpty()) return

    var stepIndex by remember { mutableIntStateOf(0) }
    val step = steps[stepIndex.coerceIn(0, steps.lastIndex)]

    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    var overlaySize by remember { mutableStateOf(IntSize.Zero) }
    var tooltipSize by remember { mutableStateOf(IntSize.Zero) }

    val density = LocalDensity.current
    val scrimColor = Color.Black.copy(alpha = 0.72f)
    val accent = MaterialTheme.colorScheme.primary
    val holeInsetPx = with(density) { 6.dp.toPx() }
    val cornerPx = with(density) { 20.dp.toPx() }
    val gapPx = with(density) { 12.dp.toPx() }
    val marginPx = with(density) { 16.dp.toPx() }
    val strokePx = with(density) { 3.dp.toPx() }

    val pulse = rememberInfiniteTransition(label = "coach_mark_pulse")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "coach_mark_pulse_alpha"
    )

    // Цель может отсутствовать (пустой список тренировок) — тогда шаг
    // показывается по центру без подсветки.
    val holeRect = step.targetKey
        ?.let { key -> targets.bounds(key) }
        ?.let { bounds ->
            val shifted = bounds.translate(-overlayOrigin.x, -overlayOrigin.y)
            Rect(
                left = shifted.left - holeInsetPx,
                top = shifted.top - holeInsetPx,
                right = shifted.right + holeInsetPx,
                bottom = shifted.bottom + holeInsetPx
            )
        }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                overlayOrigin = coordinates.positionInWindow()
                overlaySize = coordinates.size
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scrim = Path().apply {
                addRect(Rect(0f, 0f, size.width, size.height))
                holeRect?.let { hole ->
                    addRoundRect(RoundRect(hole, CornerRadius(cornerPx, cornerPx)))
                }
                fillType = PathFillType.EvenOdd
            }
            drawPath(scrim, scrimColor)
            holeRect?.let { hole ->
                drawRoundRect(
                    color = accent.copy(alpha = pulseAlpha),
                    topLeft = hole.topLeft,
                    size = hole.size,
                    cornerRadius = CornerRadius(cornerPx, cornerPx),
                    style = Stroke(width = strokePx)
                )
            }
        }

        // Слой, который съедает нажатия по затемнению: шаги переключаются
        // только кнопками, случайный тап не должен запустить тренировку или
        // удалить карточку. Подсказка лежит ВЫШЕ этого слоя, поэтому её
        // кнопки продолжают работать.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { } }
        )

        val tooltipX = if (overlaySize.width > 0 && tooltipSize.width > 0) {
            val desiredCenter = holeRect?.center?.x ?: (overlaySize.width / 2f)
            val maxX = (overlaySize.width - tooltipSize.width - marginPx).coerceAtLeast(marginPx)
            (desiredCenter - tooltipSize.width / 2f)
                .coerceIn(marginPx, maxX)
                .roundToInt()
        } else {
            0
        }
        val tooltipY = if (overlaySize.height > 0 && tooltipSize.height > 0) {
            val centered = ((overlaySize.height - tooltipSize.height) / 2f).roundToInt()
            if (holeRect == null) {
                centered
            } else {
                val below = holeRect.bottom + gapPx
                val above = holeRect.top - gapPx - tooltipSize.height
                when {
                    below + tooltipSize.height <= overlaySize.height - marginPx ->
                        below.roundToInt()
                    above >= marginPx -> above.roundToInt()
                    // Подсказка не влезает ни снизу, ни сверху — прижимаем к верху.
                    else -> marginPx.roundToInt()
                }
            }
        } else {
            0
        }

        CoachMarkTooltip(
            step = step,
            stepIndex = stepIndex,
            stepCount = steps.size,
            onBack = { if (stepIndex > 0) stepIndex-- },
            onNext = {
                if (stepIndex < steps.lastIndex) stepIndex++ else onFinish()
            },
            onSkip = onFinish,
            modifier = Modifier
                .offset { IntOffset(tooltipX, tooltipY) }
                .onSizeChanged { size -> tooltipSize = size }
        )
    }
}

@Composable
private fun CoachMarkTooltip(
    step: TutorialStep,
    stepIndex: Int,
    stepCount: Int,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .padding(horizontal = 8.dp)
            .widthIn(max = 340.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Шаг ${stepIndex + 1} из $stepCount",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = step.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = step.text,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (stepIndex > 0) {
                    TextButton(onClick = onBack) { Text("Назад") }
                } else {
                    TextButton(onClick = onSkip) { Text("Пропустить") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (stepIndex > 0) {
                        TextButton(onClick = onSkip) { Text("Пропустить") }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Button(onClick = onNext) {
                        Text(if (stepIndex == stepCount - 1) "Готово" else "Далее")
                    }
                }
            }
        }
    }
}

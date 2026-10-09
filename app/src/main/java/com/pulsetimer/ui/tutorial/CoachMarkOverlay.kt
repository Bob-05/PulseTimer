package com.pulsetimer.ui.tutorial

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateValueAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** Прозрачность затемнения обучения по умолчанию. */
const val DEFAULT_TUTORIAL_SCRIM_ALPHA = 0.72f

/**
 * Затемнение для экранов, где важно видеть происходящее за подсказкой —
 * например, идущая тренировка с бегущим таймером.
 */
const val LIGHT_TUTORIAL_SCRIM_ALPHA = 0.55f

/**
 * Полноэкранный слой обучения: затемняет интерфейс, оставляет «дырку» вокруг
 * целевого элемента и показывает подсказку с кнопками «Назад / Пропустить / Далее».
 *
 * Слой живёт в `MainActivity` поверх графа навигации, поэтому подсветка работает
 * одинаково на всех экранах. Координаты цели приходят в оконной системе
 * (см. [TutorialTargetRegistry]) и пересчитываются в локальные координаты слоя —
 * разметка не зависит от того, в каком слое лежит сам слой.
 *
 * Тап по затемнению переводит на следующий шаг: так устроены современные coach
 * marks, целиться в кнопку не нужно. При этом нажатия не доходят до интерфейса
 * под слоем — случайный тап не запустит тренировку и не удалит карточку.
 */
@Composable
fun CoachMarkOverlay(
    steps: List<TutorialStep>,
    targets: TutorialTargetRegistry,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    scrimAlpha: Float = DEFAULT_TUTORIAL_SCRIM_ALPHA
) {
    if (steps.isEmpty()) return

    var stepIndex by remember(steps) { mutableIntStateOf(0) }
    val step = steps[stepIndex.coerceIn(0, steps.lastIndex)]

    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    var overlaySize by remember { mutableStateOf(IntSize.Zero) }
    var tooltipSize by remember { mutableStateOf(IntSize.Zero) }

    // Появление: затемнение и подсказка проявляются плавно, без «вспышки».
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val appearAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "coach_mark_appear"
    )

    val density = LocalDensity.current
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

    // Цель может отсутствовать (пустой список тренировок, свёрнутая группа
    // настроек) — тогда шаг показывается по центру, без подсветки.
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

    // «Дырка» переезжает к новой цели не рывком, а за 280 мс: переход между
    // шагами читается как перевод фокуса.
    val animatedHole by animateValueAsState(
        targetValue = holeRect ?: Rect.Zero,
        typeConverter = RectToAnimationVector,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "coach_mark_hole"
    )
    val holeToDraw = if (holeRect != null) animatedHole else null

    val goNext: () -> Unit = {
        if (stepIndex < steps.lastIndex) stepIndex++ else onFinish()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                overlayOrigin = coordinates.positionInWindow()
                overlaySize = coordinates.size
            }
            .graphicsLayer { alpha = appearAlpha }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scrim = Path().apply {
                addRect(Rect(0f, 0f, size.width, size.height))
                holeToDraw?.let { hole ->
                    addRoundRect(RoundRect(hole, CornerRadius(cornerPx, cornerPx)))
                }
                fillType = PathFillType.EvenOdd
            }
            drawPath(scrim, Color.Black.copy(alpha = scrimAlpha))
            holeToDraw?.let { hole ->
                drawRoundRect(
                    color = accent.copy(alpha = pulseAlpha),
                    topLeft = hole.topLeft,
                    size = hole.size,
                    cornerRadius = CornerRadius(cornerPx, cornerPx),
                    style = Stroke(width = strokePx)
                )
            }
        }

        // Слой, который съедает нажатия по затемнению: элементы под обучением
        // «не живые», но сам тап переводит на следующий шаг.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(steps) {
                    detectTapGestures(onTap = { goNext() })
                }
        )

        val tooltipX = if (overlaySize.width > 0 && tooltipSize.width > 0) {
            val desiredCenter = holeToDraw?.center?.x ?: (overlaySize.width / 2f)
            val maxX = (overlaySize.width - tooltipSize.width - marginPx)
                .coerceAtLeast(marginPx)
            (desiredCenter - tooltipSize.width / 2f)
                .coerceIn(marginPx, maxX)
                .roundToInt()
        } else {
            0
        }
        val tooltipY = if (overlaySize.height > 0 && tooltipSize.height > 0) {
            if (holeToDraw == null) {
                ((overlaySize.height - tooltipSize.height) / 2f).roundToInt()
            } else {
                val below = holeToDraw.bottom + gapPx
                val above = holeToDraw.top - gapPx - tooltipSize.height
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
            onNext = goNext,
            onSkip = onFinish,
            modifier = Modifier
                .offset { IntOffset(tooltipX, tooltipY) }
                .onSizeChanged { size -> tooltipSize = size }
        )
    }
}

/**
 * Подсказка обучения: значок, шаг, заголовок, текст и навигация.
 *
 * Текст шага меняется через [AnimatedContent] — при перелистывании контент
 * «подъезжает» снизу, а не подменяется мгновенно.
 */
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
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(24.dp)
    Card(
        modifier = modifier
            .padding(horizontal = 8.dp)
            .widthIn(max = 360.dp)
            .border(1.dp, accent.copy(alpha = 0.35f), shape),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    (
                        slideInVertically { height -> height / 4 } +
                            fadeIn(tween(durationMillis = 220))
                        ) togetherWith (
                        slideOutVertically { height -> -height / 4 } +
                            fadeOut(tween(durationMillis = 120))
                        )
                },
                label = "coach_mark_step_content"
            ) { currentStep ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        currentStep.emoji?.let { emoji ->
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(accent.copy(alpha = 0.14f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = emoji, fontSize = 20.sp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Шаг ${stepIndex + 1} из $stepCount",
                                style = MaterialTheme.typography.labelMedium,
                                color = accent
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = currentStep.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = currentStep.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoachMarkDots(count = stepCount, current = stepIndex)
                Spacer(modifier = Modifier.weight(1f))
                if (stepIndex < stepCount - 1) {
                    Text(
                        text = "тап по фону — далее",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onSkip) {
                    Text(if (stepIndex == stepCount - 1) "Закрыть" else "Пропустить")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (stepIndex > 0) {
                        TextButton(onClick = onBack) { Text("Назад") }
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

/** Полоска прогресса: пройденные точки акцентные, текущая — вытянутая. */
@Composable
private fun CoachMarkDots(count: Int, current: Int) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { index ->
            val dotWidth by animateDpAsState(
                targetValue = if (index == current) 18.dp else 6.dp,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                label = "coach_mark_dot_$index"
            )
            Box(
                modifier = Modifier
                    .width(dotWidth)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(
                        if (index <= current) accent else accent.copy(alpha = 0.25f)
                    )
            )
        }
    }
}

/**
 * Rect → AnimationVector4D: без своего конвертера интерполировать положение и
 * размер «дырки» между шагами нечем — Compose умеет анимировать только векторы.
 */
private val RectToAnimationVector: TwoWayConverter<Rect, AnimationVector4D> =
    TwoWayConverter(
        convertToVector = { rect ->
            AnimationVector4D(rect.left, rect.top, rect.right, rect.bottom)
        },
        convertFromVector = { vector ->
            Rect(vector.v1, vector.v2, vector.v3, vector.v4)
        }
    )


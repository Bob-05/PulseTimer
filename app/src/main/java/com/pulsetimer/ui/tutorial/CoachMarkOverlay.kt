package com.pulsetimer.ui.tutorial

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min
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
 * (см. [TutorialTargetRegistry]) и пересчитываются в локальные координаты слоя.
 *
 * Показом управляет [TutorialController]: он сначала доводит экран до нужного
 * состояния (переход, раскрытие блоков, прокрутка и центрирование цели) и лишь
 * потом выставляет [TutorialController.isRevealed] — подсказка проявляется
 * плавно, уже над нужным местом. Затемнение при этом видно с первого кадра
 * подготовки, а пока идёт подготовка, по центру крутится индикатор: слой не
 * выглядит зависшим, и понятно, что подсказка вот-вот появится. Тап по
 * затемнению переводит на следующий шаг: целиться в кнопку не нужно, а если шаг
 * ещё готовится — тап сокращает ожидание. При этом нажатия не доходят до
 * интерфейса под слоем — случайный тап не запустит тренировку и не удалит
 * карточку.
 */
@Composable
fun CoachMarkOverlay(
    controller: TutorialController,
    modifier: Modifier = Modifier
) {
    val steps = controller.steps
    if (steps.isEmpty()) return
    val targets = controller.targets
    val stepIndex = controller.stepIndex.coerceIn(0, steps.lastIndex)
    val step = steps[stepIndex]

    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    var overlaySize by remember { mutableStateOf(IntSize.Zero) }
    var tooltipSize by remember { mutableStateOf(IntSize.Zero) }

    // Затемнение проявляется сразу, а подсказка — только когда шаг готов
    // (см. [TutorialController.isRevealed]). Так видно, что приложение не
    // зависло, а выполняет переход и прокрутку под управлением обучения.
    val scrimReveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        scrimReveal.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
        )
    }

    // Подсказка появляется только после подготовки шага: пока обучение
    // переходит на другой экран, раскрывает блоки и прокручивает список, слой
    // показывает только затемнение — зато нажатия он перехватывает всегда.
    val reveal by animateFloatAsState(
        targetValue = if (controller.isRevealed) 1f else 0f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "coach_mark_reveal"
    )
    val scrimAlpha = controller.scrimAlpha

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

    // Цель может отсутствовать (пустой список тренировок, цель ещё не
    // скомпонована) — тогда шаг показывается по центру, без подсветки.
    val holeRect = step.targetKey
        ?.let { key -> targets.bounds(key) }
        ?.let { bounds ->
            val shifted = bounds.translate(-overlayOrigin.x, -overlayOrigin.y)
            spotlightRect(shifted, overlaySize, holeInsetPx)
        }

    // «Дырка» переезжает к новой цели не рывком, а за 280 мс: переход между
    // шагами читается как перевод фокуса. Пока шаг готовится, видно только
    // затемнение, поэтому к моменту показа подсказки «дырка» уже на месте.
    val animatedHole by animateValueAsState(
        targetValue = holeRect ?: Rect.Zero,
        typeConverter = RectToAnimationVector,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "coach_mark_hole"
    )
    val holeToDraw = if (holeRect != null) animatedHole else null

    Box(
        modifier = modifier
            .fillMaxSize()
            // Для скринридера слой — отдельная область с заголовком текущего шага:
            // без этого он читал бы интерфейс под затемнением, до которого
            // пользователь сейчас всё равно не может добраться.
            .semantics { paneTitle = "Обучение: ${step.title}" }
            .onSizeChanged { size ->
                // Размер окна нужен обучению, чтобы центрировать цель прокруткой.
                controller.viewport = size
                overlaySize = size
            }
            .onGloballyPositioned { coordinates ->
                overlayOrigin = coordinates.positionInWindow()
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scrim = Path().apply {
                addRect(Rect(0f, 0f, size.width, size.height))
                holeToDraw?.let { hole ->
                    addRoundRect(RoundRect(hole, CornerRadius(cornerPx, cornerPx)))
                }
                fillType = PathFillType.EvenOdd
            }
            drawPath(scrim, Color.Black.copy(alpha = scrimAlpha * scrimReveal.value))
            holeToDraw?.let { hole ->
                drawRoundRect(
                    color = accent.copy(alpha = pulseAlpha * reveal),
                    topLeft = hole.topLeft,
                    size = hole.size,
                    cornerRadius = CornerRadius(cornerPx, cornerPx),
                    style = Stroke(width = strokePx)
                )
            }
        }

        // Пока шаг готовится (переход на другой экран, раскрытие группы,
        // прокрутка списка), подсказка ещё прозрачна, и затемнение без индикатора
        // читается как «приложение зависло». Кружок проявляется сразу и гаснет,
        // как только подсказка показана.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(36.dp)
                .graphicsLayer { alpha = 1f - reveal },
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = "Подготовка подсказки" },
                color = accent,
                strokeWidth = 3.dp
            )
        }

        // Слой, который съедает нажатия: элементы под обучением «не живые», а
        // тап по фону переводит на следующий шаг. Пока шаг готовится (переход,
        // прокрутка), нажатия не проходят, но тап сокращает ожидание разметки.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(controller) {
                    detectTapGestures(onTap = { controller.next() })
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

        // Подсказка «доезжает» до нужного места: к моменту показа (после
        // прокрутки и раскрытия блоков) она уже стоит там, где надо.
        val tooltipOffset by animateOffsetAsState(
            targetValue = Offset(tooltipX.toFloat(), tooltipY.toFloat()),
            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
            label = "coach_mark_tooltip"
        )

        CoachMarkTooltip(
            step = step,
            stepIndex = stepIndex,
            stepCount = steps.size,
            canGoBack = controller.canGoBack,
            interactive = controller.isRevealed,
            onBack = { controller.back() },
            onNext = { controller.next() },
            onSkip = { controller.finish() },
            modifier = Modifier
                .offset {
                    IntOffset(tooltipOffset.x.roundToInt(), tooltipOffset.y.roundToInt())
                }
                .graphicsLayer {
                    alpha = reveal
                    val scale = 0.94f + 0.06f * reveal
                    scaleX = scale
                    scaleY = scale
                }
                .onSizeChanged { size -> tooltipSize = size }
        )
    }
}

/**
 * Подсказка обучения: значок, шаг, заголовок, текст и навигация.
 *
 * Текст шага меняется через [AnimatedContent] — при перелистывании контент
 * «подъезжает» снизу, а не подменяется мгновенно.
 *
 * @param interactive кнопки нажимаются, только когда шаг уже показан: пока
 *   обучение ведёт пользователя (переход, прокрутка), подсказка прозрачна и
 *   не должна ловить случайные нажатия по невидимым кнопкам.
 */
@Composable
private fun CoachMarkTooltip(
    step: TutorialStep,
    stepIndex: Int,
    stepCount: Int,
    canGoBack: Boolean,
    interactive: Boolean,
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
        Column(
            modifier = Modifier
                .padding(20.dp)
                // Текст шага меняется на месте, без смены экрана: помечаем его
                // «живой областью», чтобы скринридер сам объявлял новую подсказку.
                .semantics { liveRegion = LiveRegionMode.Polite }
        ) {
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
                // В длинном туре точек больше, чем помещается по ширине подсказки:
                // там номер шага уже показан в заголовке.
                if (stepCount <= MAX_PROGRESS_DOTS) {
                    CoachMarkDots(count = stepCount, current = stepIndex)
                }
                Spacer(modifier = Modifier.weight(1f))
                if (stepIndex < stepCount - 1) {
                    Text(
                        text = if (step.pressTarget) {
                            "обучение нажмёт за вас"
                        } else {
                            "тап по фону — далее"
                        },
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
                TextButton(onClick = onSkip, enabled = interactive) {
                    Text(if (stepIndex == stepCount - 1) "Закрыть" else "Пропустить")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (canGoBack) {
                        TextButton(onClick = onBack, enabled = interactive) { Text("Назад") }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Button(onClick = onNext, enabled = interactive) {
                        Text(
                            text = when {
                                stepIndex == stepCount - 1 -> "Готово"
                                step.actionLabel != null -> step.actionLabel
                                else -> "Далее"
                            }
                        )
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

/** Сколько точек прогресса помещается в подсказку, не ломая её ширину. */
private const val MAX_PROGRESS_DOTS = 8

/**
 * Максимальная высота «дырки» относительно экрана: больше половины — и от
 * затемнения ничего не остаётся, а подсказке некуда встать.
 */
private const val SPOTLIGHT_MAX_HEIGHT_RATIO = 0.5f

/** Максимальная ширина «дырки» относительно экрана. */
private const val SPOTLIGHT_MAX_WIDTH_RATIO = 0.92f

/**
 * «Дырка» вокруг цели: цель плюс отступ по краям, но не выше
 * [SPOTLIGHT_MAX_HEIGHT_RATIO] экрана и не шире [SPOTLIGHT_MAX_WIDTH_RATIO].
 *
 * Большие карточки (параметры тренировки в редакторе, раскрытая группа
 * настроек) выше экрана: без ограничения затемнение исчезло бы совсем.
 * Сжимаем подсветку симметрично относительно центра цели — она остаётся там
 * же, куда обучение привело цель прокруткой, — и не выпускаем её за экран.
 */
private fun spotlightRect(target: Rect, viewport: IntSize, inset: Float): Rect {
    val inflated = Rect(
        left = target.left - inset,
        top = target.top - inset,
        right = target.right + inset,
        bottom = target.bottom + inset
    )
    if (viewport.width <= 0 || viewport.height <= 0) return inflated
    val width = min(inflated.width, viewport.width * SPOTLIGHT_MAX_WIDTH_RATIO)
    val height = min(inflated.height, viewport.height * SPOTLIGHT_MAX_HEIGHT_RATIO)
    val center = inflated.center
    return Rect(
        left = (center.x - width / 2f)
            .coerceIn(0f, (viewport.width - width).coerceAtLeast(0f)),
        top = (center.y - height / 2f)
            .coerceIn(0f, (viewport.height - height).coerceAtLeast(0f)),
        right = (center.x + width / 2f)
            .coerceIn(width, viewport.width.toFloat()),
        bottom = (center.y + height / 2f)
            .coerceIn(height, viewport.height.toFloat())
    )
}


package com.pulsetimer.ui.tutorial

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/**
 * Состояние интерактивного обучения.
 *
 * Обучение живёт вне экранов: слой подсветки рисуется в `MainActivity` поверх
 * графа навигации, а сами экраны только регистрируют цели, планы их показа и
 * действия, которыми обучение ведёт пользователя дальше ([TutorialTrigger],
 * [TutorialScreenTargets]).
 *
 * Перед каждым шагом обучение само приводит экран в нужное состояние: переходит
 * на другой экран (нажимая за пользователя нужную кнопку), раскрывает группы
 * настроек, прокручивает список к цели и центрирует её по середине экрана — и
 * только после этого плавно показывает подсказку. Пока идёт подготовка,
 * затемнение уже видно (по нему понятно, что приложение под управлением
 * обучения), нажатия перехватываются, а тап по фону сокращает ожидание.
 *
 * Реестр целей общий: координаты у всех экранов в одной оконной системе, а
 * ключи разведены префиксами (`main.`, `settings.`, `editor.`, `execution.`).
 *
 * @param scope область жизни обучения: корутины подготовки живут, пока жив
 *   экран, создавший контроллер (в приложении — пока жива активность).
 */
class TutorialController(private val scope: CoroutineScope) {

    /** Координаты подсвечиваемых элементов; заполняется экранами через [tutorialTarget]. */
    val targets: TutorialTargetRegistry = TutorialTargetRegistry()

    /** Шаги текущего обучения; пустой список — обучения на экране нет. */
    var steps: List<TutorialStep> by mutableStateOf(emptyList())
        private set

    /** Номер текущего шага: сквозной тур ведёт сразу через несколько экранов. */
    var stepIndex: Int by mutableIntStateOf(0)
        private set

    /** Затемнение по умолчанию; шаг может переопределить его ([TutorialStep.scrimAlpha]). */
    private var baseScrimAlpha: Float = DEFAULT_TUTORIAL_SCRIM_ALPHA

    /**
     * Прозрачность затемнения текущего шага: на экране тренировки она ниже, чтобы
     * видеть таймер под подсказкой.
     */
    val scrimAlpha: Float
        get() = currentStep?.scrimAlpha ?: baseScrimAlpha

    /**
     * `false` — обучение ведёт пользователя: переходит на другой экран,
     * раскрывает блоки и прокручивает список. Подсказка в это время не показана
     * (затемнение уже видно), нажатия перехватываются, а тап по фону сокращает
     * ожидание.
     */
    var isRevealed: Boolean by mutableStateOf(false)
        private set

    /**
     * Размер слоя обучения в пикселях окна: по нему обучение центрирует цель.
     * Публикует [CoachMarkOverlay]; читает только корутина обучения.
     */
    internal var viewport: IntSize = IntSize.Zero

    val isRunning: Boolean get() = steps.isNotEmpty()

    val currentStep: TutorialStep? get() = steps.getOrNull(stepIndex)

    /**
     * «Назад» доступен только внутри одного экрана: тур не возвращается на
     * предыдущий экран, поэтому шаг назад через границу экранов не предлагаем.
     */
    val canGoBack: Boolean
        get() = stepIndex > 0 &&
            steps.getOrNull(stepIndex)?.screenPrefix ==
            steps.getOrNull(stepIndex - 1)?.screenPrefix

    private var onFinish: () -> Unit = {}
    private var prepareJob: Job? = null

    /** Шаг-переход только что «нажал» цель, следующий шаг ждёт появления экрана. */
    private var awaitingScreen = false

    /**
     * Пользователь тапнул, пока шаг готовился: обучение перестаёт ждать разметку и
     * показывает подсказку сразу. Иначе между шагами копится «мёртвая» пауза, пока
     * доигрывают прокрутка и раскрытие блоков.
     */
    private var hurryUp = false

    /**
     * Начинает обучение.
     *
     * Повторный вызов во время идущего обучения игнорируется: два набора шагов
     * одновременно показать нельзя, а перебивать уже начатый тур — плохая идея
     * (пользователь как раз читает подсказку).
     */
    fun start(
        steps: List<TutorialStep>,
        onFinish: () -> Unit,
        scrimAlpha: Float = DEFAULT_TUTORIAL_SCRIM_ALPHA
    ) {
        if (isRunning) return
        this.onFinish = onFinish
        baseScrimAlpha = scrimAlpha
        this.steps = steps
        stepIndex = 0
        isRevealed = false
        awaitingScreen = false
        prepare()
    }

    /**
     * Кнопка «Далее» и тап по фону. На шаге-переходе обучение сначала «нажимает»
     * цель за пользователя и только потом двигается к следующему шагу.
     */
    fun next() {
        if (!isRunning) return
        val step = currentStep ?: return
        // Шаг ещё готовится: тап не пропускаем, а ускоряем его — так слой отвечает
        // на нажатие, вместо того чтобы выглядеть зависшим.
        if (!isRevealed) {
            if (!awaitingScreen) hurryUp = true
            return
        }
        if (step.pressTarget && step.targetKey != null) {
            pressTarget(step)
            return
        }
        goTo(stepIndex + 1)
    }

    /** Шаг назад: доступен, пока предыдущий шаг относится к тому же экрану. */
    fun back() {
        if (!isRunning) return
        if (!isRevealed) {
            if (!awaitingScreen) hurryUp = true
            return
        }
        if (!canGoBack) return
        goTo(stepIndex - 1)
    }

    /**
     * Закрывает обучение. Вызывается, когда шаги закончились, пользователь
     * нажал «Пропустить» или системный «Назад». Что делать после — решает
     * колбэк экрана: обычно он выставляет флаг «обучение пройдено».
     */
    fun finish() {
        if (!isRunning) return
        prepareJob?.cancel()
        prepareJob = null
        hurryUp = false
        val callback = onFinish
        onFinish = {}
        steps = emptyList()
        isRevealed = false
        callback()
    }

    private fun goTo(index: Int) {
        if (!isRunning) return
        if (index > steps.lastIndex) {
            finish()
            return
        }
        stepIndex = index.coerceAtLeast(0)
        // Прежняя подсказка гаснет: пока экран готовится, слой прозрачен.
        isRevealed = false
        prepare()
    }

    /**
     * «Нажимает» цель за пользователя: так обучение само ведёт его на другой
     * экран. Индекс шага двигается до нажатия — следующий шаг сразу начинает
     * ждать цель уже на новом экране.
     *
     * Действия может не оказаться: экран не зарегистрировал его или уже ушёл из
     * композиции. Тогда вести пользователя некуда, и тур закрывается — иначе
     * обучение молча «нажало» бы пустоту, ушло на шаги следующего экрана и
     * оставило тёмный слой висеть поверх чужого экрана.
     */
    private fun pressTarget(step: TutorialStep) {
        val key = step.targetKey ?: return
        val action = targets.action(key) ?: run {
            finish()
            return
        }
        awaitingScreen = true
        goTo(stepIndex + 1)
        action()
    }

    /**
     * Готовит экран к текущему шагу и показывает подсказку.
     *
     * Порядок такой: дождаться цели (экран мог только что смениться) → выполнить
     * план показа (раскрыть группу настроек, пролистать пейджер) → прокрутить
     * список к элементу → центрировать цель по середине экрана → дождаться, пока
     * разметка устоится, — и только затем плавно показать модальное окно. Шаг,
     * для которого цели на экране нет (например, пустой список тренировок),
     * пропускается: рассказывать про несуществующий элемент нечего.
     */
    private fun prepare() {
        val afterScreenChange = awaitingScreen
        awaitingScreen = false
        // Новый шаг начинаем ждать терпеливо: «ускорение» относилось к прошлому.
        hurryUp = false
        prepareJob?.cancel()
        prepareJob = scope.launch {
            var timeout = if (afterScreenChange) {
                SCREEN_CHANGE_TIMEOUT_MILLIS
            } else {
                TARGET_TIMEOUT_MILLIS
            }
            repeat(steps.size + 1) {
                val step = steps.getOrNull(stepIndex) ?: return@launch
                if (prepareStep(step, timeout)) return@launch
                timeout = TARGET_TIMEOUT_MILLIS
                if (stepIndex >= steps.lastIndex) {
                    finish()
                    return@launch
                }
                stepIndex++
            }
        }
    }

    /** @return true, если шаг показан: дальше ждать нечего. */
    private suspend fun prepareStep(step: TutorialStep, timeoutMillis: Long): Boolean {
        // «Ускорение» относится к одному шагу: следующий ждём заново.
        hurryUp = false
        val key = step.targetKey ?: run {
            reveal()
            return true
        }
        // Экран мог только что смениться: цель появится в реестре не мгновенно.
        if (!awaitRegistered(key, timeoutMillis)) return false
        val plan = targets.plan(key)
        plan?.prepare?.invoke()
        // Ждём, пока цель окажется в разметке и координаты устоятся: раскрытие
        // группы и анимации двигают подсветку по несколько кадров.
        if (!awaitStableBounds(key)) return false
        plan?.scrollToItem?.invoke()
        awaitStableBounds(key)
        centerTarget(key, plan)
        awaitStableBounds(key)
        reveal()
        return true
    }

    /** Ждёт, пока экран зарегистрирует цель или план её показа. */
    private suspend fun awaitRegistered(key: String, timeoutMillis: Long): Boolean =
        withTimeoutOrNull(timeoutMillis) {
            while (!hurryUp && targets.bounds(key) == null && targets.plan(key) == null) {
                delay(LAYOUT_POLL_MILLIS)
            }
            true
        } ?: false

    /**
     * Ждёт устойчивых координат цели: прокрутка, раскрытие блока и переход между
     * экранами двигают цель несколько кадров подряд.
     *
     * @return false, если цели всё нет — шаг пропускается. Отсчёт ведём
     *   накопленной паузой, а не часами: ожидание не зависит от системного
     *   времени и не ломается в тестах.
     */
    private suspend fun awaitStableBounds(key: String): Boolean =
        withTimeoutOrNull(LAYOUT_SETTLE_TIMEOUT_MILLIS) {
            var previous: Rect? = targets.bounds(key)
            var stableFrames = 0
            var waitedMillis = 0L
            while (stableFrames < STABLE_FRAMES && !hurryUp) {
                delay(LAYOUT_POLL_MILLIS)
                waitedMillis += LAYOUT_POLL_MILLIS
                val current = targets.bounds(key)
                when {
                    current == null -> {
                        // Цель так и не появилась — держать пользователя незачем.
                        if (waitedMillis >= TARGET_TIMEOUT_MILLIS) {
                            return@withTimeoutOrNull false
                        }
                        stableFrames = 0
                        previous = null
                    }

                    previous?.let { last -> isInPlace(current, last) } == true ->
                        stableFrames++

                    else -> {
                        stableFrames = 0
                        previous = current
                    }
                }
            }
            true
        } ?: false

    /**
     * Цель стоит на месте: сравниваем положение с точностью до пикселя, а не
     * прямоугольник целиком.
     *
     * Иначе цель, у которой меняется только размер — например, таймер тренировки:
     * цифры шириной в один символ раз в секунду, — считалась бы движущейся, и
     * обучение перед каждой подсказкой выжидало бы полный таймаут раскладки.
     */
    private fun isInPlace(current: Rect, previous: Rect): Boolean =
        abs(current.left - previous.left) <= STABILITY_TOLERANCE_PX &&
            abs(current.top - previous.top) <= STABILITY_TOLERANCE_PX

    /**
     * Доводит цель до середины экрана: подсказка встаёт рядом с подсветкой, а не
     * у края окна. Прокрутка идёт по фактическим координатам цели, поэтому
     * работает и для ленты, и для пейджера.
     */
    private suspend fun centerTarget(key: String, plan: TutorialScrollPlan?) {
        val scrollBy = plan?.scrollBy ?: return
        val bounds = targets.bounds(key) ?: return
        if (viewport.height <= 0) return
        val offset = bounds.center.y - viewport.height / 2f
        if (abs(offset) < CENTER_TOLERANCE_PX) return
        scrollBy(offset)
    }

    private fun reveal() {
        isRevealed = true
    }
}

/** Пауза между проверками разметки: примерно кадр при 60 Гц. */
private const val LAYOUT_POLL_MILLIS = 16L

/** Сколько подряд устойчивых кадров считаем «разметка устоялась». */
private const val STABLE_FRAMES = 2

/**
 * Насколько цель может сдвинуться и всё ещё считаться стоящей на месте (в
 * пикселях). Допуск нужен для целей, которые «дышат» размером: таймер тренировки
 * меняет ширину цифр, и по строгому сравнению прямоугольников обучение каждый раз
 * ждало бы полный таймаут раскладки.
 */
private const val STABILITY_TOLERANCE_PX = 1f

/**
 * Сколько ждём цель на текущем экране, прежде чем считать шаг недоступным.
 * Значения подобраны так, чтобы пауза между подсказками не читалась как
 * зависание: пользователь всё это время видит затемнение, но нажатия не проходят.
 */
private const val TARGET_TIMEOUT_MILLIS = 600L

/** Сколько ждём цель после перехода на другой экран — экран должен разложиться. */
private const val SCREEN_CHANGE_TIMEOUT_MILLIS = 1500L

/** Верхняя граница ожидания прокрутки и центрирования цели. */
private const val LAYOUT_SETTLE_TIMEOUT_MILLIS = 800L

/** Меньше этого смещения цель уже считается стоящей по центру (в пикселях). */
private const val CENTER_TOLERANCE_PX = 24f

/**
 * Запускает обучение экрана при первом на него заходе и подчищает за собой
 * координаты целей. Сам ничего не рисует: слой обучения живёт в `MainActivity`.
 *
 * @param steps шаги экрана.
 * @param enabled экран открыт впервые (условие по флагу из [com.pulsetimer.data.AppSettings]).
 * @param prefix префикс ключей целей экрана — по нему они удаляются при выходе,
 *   иначе в реестре остаются координаты несуществующих элементов.
 * @param startDelayMillis пауза перед показом: даём разметке устояться, иначе
 *   подсветка «дрожит» вместе с анимацией входа экрана.
 * @param onCompleted колбэк завершения — здесь экран помечает обучение пройденным.
 */
@Composable
fun TutorialTrigger(
    controller: TutorialController,
    steps: List<TutorialStep>,
    enabled: Boolean,
    prefix: String,
    onCompleted: () -> Unit,
    startDelayMillis: Long = 600L,
    scrimAlpha: Float = DEFAULT_TUTORIAL_SCRIM_ALPHA
) {
    // Колбэк живёт дольше одного кадра: к моменту закрытия обучения он должен
    // быть актуальным, даже если экран успел перекомпоноваться.
    val latestOnCompleted by rememberUpdatedState(onCompleted)

    LaunchedEffect(enabled, steps) {
        if (!enabled) return@LaunchedEffect
        delay(startDelayMillis)
        controller.start(
            steps = steps,
            scrimAlpha = scrimAlpha,
            onFinish = { latestOnCompleted() }
        )
    }

    DisposableEffect(controller, prefix) {
        onDispose { controller.targets.removeByPrefix(prefix) }
    }
}

/**
 * Публикует то, чем обучение ведёт пользователя по экрану: планы показа целей
 * ([plans]) и действия ([actions]), которые обучение «нажимает» за пользователя.
 *
 * Без этого плана шаг с целью, спрятанной в свёрнутой группе настроек или за
 * пределами экрана, показывался бы без подсветки, а шаг-переход не смог бы
 * открыть другой экран.
 *
 * Регистрация идёт после каждой композиции ([SideEffect]): лямбды экрана
 * закрываются над его состоянием, и в реестре всегда должны лежать свежие.
 *
 * @param prefix префикс ключей экрана — по нему цели удаляются при выходе.
 */
@Composable
fun TutorialScreenTargets(
    registry: TutorialTargetRegistry,
    prefix: String,
    plans: Map<String, TutorialScrollPlan> = emptyMap(),
    actions: Map<String, () -> Unit> = emptyMap()
) {
    SideEffect {
        plans.forEach { (key, plan) -> registry.putPlan(key, plan) }
        actions.forEach { (key, action) -> registry.putAction(key, action) }
    }

    DisposableEffect(registry, prefix) {
        onDispose { registry.removeByPrefix(prefix) }
    }
}


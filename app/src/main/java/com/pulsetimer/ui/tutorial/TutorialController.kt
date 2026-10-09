package com.pulsetimer.ui.tutorial

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * Состояние интерактивного обучения.
 *
 * Обучение живёт вне экранов: слой подсветки рисуется в `MainActivity` поверх
 * графа навигации, а сами экраны только регистрируют цели и просят запустить
 * свои шаги ([TutorialTrigger]). Так одну и ту же механику можно показать
 * на главном экране, в настройках, в редакторе и на экране идущей тренировки,
 * не размазывая слой обучения по каждой вёрстке.
 *
 * Реестр целей тоже общий: координаты у всех экранов в одной оконной системе,
 * а ключи разведены префиксами (`main.`, `settings.`, `editor.`, `execution.`).
 */
class TutorialController {

    /** Координаты подсвечиваемых элементов; заполняется экранами через [tutorialTarget]. */
    val targets: TutorialTargetRegistry = TutorialTargetRegistry()

    /** Шаги текущего обучения; пустой список — обучения на экране нет. */
    var steps: List<TutorialStep> by mutableStateOf(emptyList())
        private set

    /** Прозрачность затемнения: на экране тренировки она ниже, чтобы видеть таймер. */
    var scrimAlpha: Float by mutableFloatStateOf(DEFAULT_TUTORIAL_SCRIM_ALPHA)
        private set

    val isRunning: Boolean get() = steps.isNotEmpty()

    private var onFinish: () -> Unit = {}

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
        this.scrimAlpha = scrimAlpha
        this.steps = steps
    }

    /**
     * Закрывает обучение. Вызывается, когда шаги закончились, пользователь
     * нажал «Пропустить» или системный «Назад». Что делать после — решает
     * колбэк экрана: обычно он выставляет флаг «обучение пройдено».
     */
    fun finish() {
        if (!isRunning) return
        val callback = onFinish
        onFinish = {}
        steps = emptyList()
        callback()
    }
}

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

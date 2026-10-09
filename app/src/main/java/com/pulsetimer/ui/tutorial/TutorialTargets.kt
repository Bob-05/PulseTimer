package com.pulsetimer.ui.tutorial

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Ключи элементов интерфейса, к которым привязываются шаги обучения.
 *
 * Это строки, а не enum: цель может отсутствовать на экране (например, список
 * тренировок пуст и карточки просто нет) — и это нормальная ситуация, шаг
 * тогда показывается по центру без подсветки.
 */
object TutorialTargets {
    /** Префиксы ключей экранов: по ним экран вычищает свои цели при выходе. */
    const val PREFIX_MAIN = "main."
    const val PREFIX_SETTINGS = "settings."
    const val PREFIX_EDITOR = "editor."
    const val PREFIX_EXECUTION = "execution."

    // --- Главный экран ---
    const val ADD_TEMPLATE = "main.addTemplate"
    const val PAGER = "main.pager"
    const val CARD = "main.card"
    const val CARD_ACTIONS = "main.cardActions"

    /** Карандаш первой карточки: обучение «нажимает» его, чтобы открыть редактор. */
    const val CARD_EDIT = "main.cardEdit"
    const val START = "main.start"

    /** Кнопка настроек в шапке: через неё обучение ведёт пользователя в настройки. */
    const val SETTINGS_ICON = "main.settingsIcon"

    // --- Настройки ---
    const val SETTINGS_NOTIFICATIONS = "settings.notifications"
    const val SETTINGS_APP_GROUP = "settings.appGroup"
    const val SETTINGS_WORKOUT_GROUP = "settings.workoutGroup"
    const val SETTINGS_HISTORY = "settings.history"
    const val SETTINGS_TUTORIAL = "settings.tutorial"

    /** Стрелка «назад»: обучение возвращает пользователя на главный экран. */
    const val SETTINGS_BACK = "settings.back"

    // --- Редактор тренировки ---
    const val EDITOR_TEMPLATE = "editor.template"
    const val EDITOR_INTERVAL = "editor.interval"
    const val EDITOR_INTERVAL_ACTIONS = "editor.intervalActions"
    const val EDITOR_ADD_INTERVAL = "editor.addInterval"

    /** Стрелка «назад» в редакторе. */
    const val EDITOR_BACK = "editor.back"

    // --- Экран идущей тренировки ---
    const val EXECUTION_TIMER = "execution.timer"
    const val EXECUTION_CONTROLS = "execution.controls"
}

/**
 * Координаты целей обучения (в координатах окна).
 *
 * Хранилище — `mutableStateMapOf`, поэтому обучение само перерисовывается,
 * если цель сдвинулась (прокрутка, анимация, поворот экрана).
 */
/**
 * Как привести экран в состояние, в котором цель видно.
 *
 * Цель может прятаться за прокруткой (интервалы в редакторе), в свёрнутой
 * группе настроек или на другой странице пейджера. Обучение выполняет план до
 * показа подсказки: сначала [prepare] (раскрыть группу, пролистать пейджер),
 * затем [scrollToItem] (элемент ленты — к верхнему краю) и [scrollBy]
 * (довести цель до середины экрана).
 */
class TutorialScrollPlan(
    /** Асинхронная подготовка: её дожидаются до появления цели в разметке. */
    val prepare: (suspend () -> Unit)? = null,
    /** Приближение элемента ленты к экрану. */
    val scrollToItem: (suspend () -> Unit)? = null,
    /** Докрутка контейнера на смещение в пикселях — центрирование цели. */
    val scrollBy: (suspend (Float) -> Unit)? = null
)

/**
 * План для цели внутри ленты: элемент [index] подтягивается к экрану, а затем
 * обучение центрирует цель по её фактическим координатам.
 */
fun lazyListScrollPlan(
    state: LazyListState,
    index: Int,
    prepare: (suspend () -> Unit)? = null
): TutorialScrollPlan = TutorialScrollPlan(
    prepare = prepare,
    scrollToItem = { state.animateScrollToItem(index) },
    scrollBy = { offset -> state.animateScrollBy(offset) }
)

/**
 * Координаты целей обучения (в координатах окна) плюс то, как обучение само
 * доводит пользователя до этих целей.
 *
 * Хранилище координат — `mutableStateMapOf`, поэтому обучение само
 * перерисовывается, если цель сдвинулась (прокрутка, анимация, поворот экрана).
 * Планы и действия — обычные карты: их читает только корутина обучения, а не
 * композиция, поэтому лишних рекомпозиций они не вызывают.
 */
class TutorialTargetRegistry {
    private val boundsByKey = mutableStateMapOf<String, Rect>()
    private val plansByKey = mutableMapOf<String, TutorialScrollPlan>()
    private val actionsByKey = mutableMapOf<String, () -> Unit>()

    fun update(key: String, bounds: Rect) {
        boundsByKey[key] = bounds
    }

    fun remove(key: String) {
        boundsByKey.remove(key)
        plansByKey.remove(key)
        actionsByKey.remove(key)
    }

    /**
     * Забывает все цели экрана при его уходе из композиции: иначе в реестре
     * копятся координаты уже не существующих элементов, и следующий показ
     * обучения может подсветить «пустое место».
     */
    fun removeByPrefix(prefix: String) {
        boundsByKey.keys.filter { key -> key.startsWith(prefix) }.forEach { key ->
            boundsByKey.remove(key)
        }
        plansByKey.keys.filter { key -> key.startsWith(prefix) }.forEach { key ->
            plansByKey.remove(key)
        }
        actionsByKey.keys.filter { key -> key.startsWith(prefix) }.forEach { key ->
            actionsByKey.remove(key)
        }
    }

    fun bounds(key: String): Rect? = boundsByKey[key]

    /** План прокрутки/раскрытия для цели; `null` — цель уже видна как есть. */
    fun plan(key: String): TutorialScrollPlan? = plansByKey[key]

    /** Что «нажать», чтобы обучение перешло на другой экран; `null` — некуда. */
    fun action(key: String): (() -> Unit)? = actionsByKey[key]

    fun putPlan(key: String, plan: TutorialScrollPlan) {
        plansByKey[key] = plan
    }

    fun putAction(key: String, action: () -> Unit) {
        actionsByKey[key] = action
    }
}

/**
 * Помечает Composable как цель обучения.
 *
 * `boundsInWindow`, а не `boundsInRoot`: оверлей обучения лежит поверх всего
 * экрана, и общая система координат у него с целью — оконная.
 */
fun Modifier.tutorialTarget(registry: TutorialTargetRegistry, key: String): Modifier =
    onGloballyPositioned { coordinates ->
        registry.update(key, coordinates.boundsInWindow())
    }

package com.pulsetimer.ui.tutorial

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
    const val START = "main.start"

    // --- Настройки ---
    const val SETTINGS_NOTIFICATIONS = "settings.notifications"
    const val SETTINGS_APP_GROUP = "settings.appGroup"
    const val SETTINGS_WORKOUT_GROUP = "settings.workoutGroup"
    const val SETTINGS_HISTORY = "settings.history"
    const val SETTINGS_TUTORIAL = "settings.tutorial"

    // --- Редактор тренировки ---
    const val EDITOR_TEMPLATE = "editor.template"
    const val EDITOR_INTERVAL = "editor.interval"
    const val EDITOR_INTERVAL_ACTIONS = "editor.intervalActions"
    const val EDITOR_ADD_INTERVAL = "editor.addInterval"

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
class TutorialTargetRegistry {
    private val boundsByKey = mutableStateMapOf<String, Rect>()

    fun update(key: String, bounds: Rect) {
        boundsByKey[key] = bounds
    }

    fun remove(key: String) {
        boundsByKey.remove(key)
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
    }

    fun bounds(key: String): Rect? = boundsByKey[key]
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

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
    const val ADD_TEMPLATE = "main.addTemplate"
    const val PAGER = "main.pager"
    const val CARD = "main.card"
    const val CARD_ACTIONS = "main.cardActions"
    const val START = "main.start"
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

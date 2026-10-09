package com.pulsetimer.ui.tutorial

import com.pulsetimer.data.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Проверки данных интерактивного обучения: набор шагов тура и то, какие экраны
 * тур помечает пройденными.
 *
 * Подготовку экрана к шагу (переход, раскрытие блоков, прокрутка и
 * центрирование) выполняет [TutorialController] — для неё нужна настоящая
 * разметка, поэтому здесь проверяется только то, что считается без UI.
 */
class TutorialStepsTest {

    @Test
    fun fullTourLeadsThroughEditorSettingsAndWorkout() {
        val steps = TutorialSteps.guidedTour(hasWorkouts = true)

        val pressSteps = steps.filter { step -> step.pressTarget }
        assertEquals(
            listOf(
                TutorialTargets.CARD_EDIT,
                TutorialTargets.EDITOR_BACK,
                TutorialTargets.SETTINGS_ICON,
                TutorialTargets.SETTINGS_BACK,
                TutorialTargets.START
            ),
            pressSteps.map { step -> step.targetKey }
        )
        // У шага-перехода всегда есть подпись кнопки: иначе пользователь не
        // поймёт, что «Далее» откроет другой экран.
        assertTrue(pressSteps.all { step -> step.actionLabel != null })
    }

    @Test
    fun tourCoversEveryScreenOfTheApp() {
        val steps = TutorialSteps.guidedTour(hasWorkouts = true)

        val screens = steps.map { step -> step.screenPrefix }.toSet()
        assertTrue(TutorialTargets.PREFIX_MAIN in screens)
        assertTrue(TutorialTargets.PREFIX_EDITOR in screens)
        assertTrue(TutorialTargets.PREFIX_SETTINGS in screens)
        assertTrue(TutorialTargets.PREFIX_EXECUTION in screens)
    }

    @Test
    fun tourWithoutWorkoutsStaysOnMainScreen() {
        val steps = TutorialSteps.guidedTour(hasWorkouts = false)

        assertTrue(steps.none { step -> step.pressTarget })
        // Шаг по центру экрана не принадлежит ни одному экрану: по префиксу
        // контроллер решает, доступна ли кнопка «Назад».
        assertEquals("", steps.first().screenPrefix)
    }

    @Test
    fun fullTourMarksAllScreensCompleted() {
        val completed = AppSettings()
            .markTourCompleted(TutorialSteps.guidedTour(hasWorkouts = true))

        assertTrue(completed.tutorialCompleted)
        assertTrue(completed.settingsTutorialCompleted)
        assertTrue(completed.editorTutorialCompleted)
        assertTrue(completed.executionTutorialCompleted)
        assertFalse(completed.tutorialRequested)
    }

    @Test
    fun tourWithoutWorkoutsKeepsOtherScreensPending() {
        val completed = AppSettings(tutorialRequested = true)
            .markTourCompleted(TutorialSteps.guidedTour(hasWorkouts = false))

        assertTrue(completed.tutorialCompleted)
        assertFalse(completed.tutorialRequested)
        // Редактор, настройки и идущую тренировку тур не показывал — подсказки
        // по ним должны появиться при первом заходе на соответствующий экран.
        assertFalse(completed.settingsTutorialCompleted)
        assertFalse(completed.editorTutorialCompleted)
        assertFalse(completed.executionTutorialCompleted)
    }
}

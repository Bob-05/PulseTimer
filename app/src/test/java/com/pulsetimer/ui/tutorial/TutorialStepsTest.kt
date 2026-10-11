package com.pulsetimer.ui.tutorial

import com.pulsetimer.data.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
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
                TutorialTargets.START,
                TutorialTargets.EXECUTION_PAUSE,
                TutorialTargets.EXECUTION_RESUME,
                // Тур сам запустил демонстрационную тренировку — сам же её и
                // завершает, иначе отсчёт остался бы идти после обучения.
                TutorialTargets.EXECUTION_CLOSE
            ),
            pressSteps.map { step -> step.targetKey }
        )
        // Подпись кнопки у шага-перехода проверяет сам TutorialStep: без неё
        // пользователь не отличит «обучение нажмёт за вас» от кнопки «Далее».
    }

    @Test
    fun standaloneHintsDoNotCloseSomeoneElsesWorkout() {
        val steps = TutorialSteps.EXECUTION_SCREEN

        // Подсказки при первом заходе на экран отсчёта рассказывают про «✕», но
        // не завершают чужую тренировку: её запустил пользователь, а не обучение.
        val closeStep = steps.first { step -> step.targetKey == TutorialTargets.EXECUTION_CLOSE }
        assertFalse(closeStep.pressTarget)
        assertEquals(null, closeStep.actionLabel)
    }

    @Test
    fun tourTellsAboutWorkoutProcessItself() {
        val keys = TutorialSteps.guidedTour(hasWorkouts = true)
            .map { step -> step.targetKey }

        // Тур не заканчивается запуском: отсчёт, перелистывание интервалов,
        // управление и завершение рассказываются отдельными шагами.
        assertTrue(TutorialTargets.EXECUTION_TIMER in keys)
        assertTrue(TutorialTargets.EXECUTION_SWIPE in keys)
        assertTrue(TutorialTargets.EXECUTION_CONTROLS in keys)
        assertTrue(TutorialTargets.EXECUTION_CLOSE in keys)

        // Пауза и продолжение — соседние шаги: обучение само останавливает
        // отсчёт, а следующим шагом возвращает его.
        val pauseIndex = keys.indexOf(TutorialTargets.EXECUTION_PAUSE)
        assertTrue(pauseIndex > 0)
        assertEquals(TutorialTargets.EXECUTION_RESUME, keys[pauseIndex + 1])
    }

    @Test
    fun standaloneWorkoutHintsTeachTheSameProcess() {
        val keys = TutorialSteps.EXECUTION_SCREEN.map { step -> step.targetKey }

        // Подсказки при первом заходе на экран отсчёта не беднее тура.
        assertTrue(TutorialTargets.EXECUTION_TIMER in keys)
        assertTrue(TutorialTargets.EXECUTION_SWIPE in keys)
        assertTrue(TutorialTargets.EXECUTION_PAUSE in keys)
        assertTrue(TutorialTargets.EXECUTION_RESUME in keys)
        assertTrue(TutorialTargets.EXECUTION_CLOSE in keys)
    }

    @Test
    fun workoutStepsUseLighterScrim() {
        val workoutSteps = TutorialSteps.guidedTour(hasWorkouts = true)
            .filter { step -> step.screenPrefix == TutorialTargets.PREFIX_EXECUTION }

        assertTrue(workoutSteps.isNotEmpty())
        // За подсказкой на экране отсчёта должно быть видно бегущий таймер.
        assertTrue(
            workoutSteps.all { step -> step.scrimAlpha == LIGHT_TUTORIAL_SCRIM_ALPHA }
        )
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
    fun pressStepWithoutActionLabelIsRejected() {
        // Подпись кнопки у шага-перехода — часть контракта, а не пожелание:
        // без неё пользователь не отличит «обучение нажмёт за вас» от обычной
        // кнопки «Далее».
        assertThrows(IllegalArgumentException::class.java) {
            TutorialStep(
                targetKey = TutorialTargets.CARD_EDIT,
                title = "Откроем редактор",
                text = "Текст",
                pressTarget = true
            )
        }
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

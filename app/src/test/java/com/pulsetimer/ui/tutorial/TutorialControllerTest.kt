package com.pulsetimer.ui.tutorial

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Проверки логики [TutorialController]: как тур стартует, ведёт пользователя по
 * шагам и что делает, когда цель шага на экране не появилась.
 *
 * Подготовку шага контроллер выполняет корутинами с паузами ожидания разметки,
 * поэтому тесты идут на виртуальном времени: прогон на несколько секунд вперёд
 * занимает микросекунды реального времени.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TutorialControllerTest {

    /** Шаг без специфики: тестам важны только ключ цели и подпись. */
    private fun step(targetKey: String?) = TutorialStep(
        targetKey = targetKey,
        title = "Заголовок",
        text = "Текст"
    )

    /**
     * Прогоняет виртуальное время дальше любой паузы подготовки шага.
     *
     * Именно `advanceTimeBy`, а не `advanceUntilIdle`: корутины подготовки живут
     * в [TestScope.backgroundScope], а он не считается передним планом, и
     * `advanceUntilIdle` до них не доходит.
     */
    private fun TestScope.advancePastPreparation() = advanceTimeBy(PREPARATION_LIMIT_MILLIS)

    @Test
    fun startIsIgnoredWhileTheTourIsRunning() = runTest {
        val controller = TutorialController(backgroundScope)
        controller.start(listOf(step(null)), onFinish = {})
        advancePastPreparation()

        controller.start(
            steps = listOf(step(null), step(null)),
            onFinish = { fail("повторный запуск не должен начинать второй тур") }
        )

        // Идущий тур не перебиваем: пользователь как раз читает подсказку.
        assertEquals(1, controller.steps.size)
    }

    @Test
    fun finishRunsTheCallbackOnceAndStopsTheTour() = runTest {
        val controller = TutorialController(backgroundScope)
        var finished = 0
        controller.start(listOf(step(null)), onFinish = { finished++ })
        advancePastPreparation()

        controller.finish()
        controller.finish()

        assertEquals(1, finished)
        assertFalse(controller.isRunning)
        assertFalse(controller.isRevealed)
    }

    @Test
    fun stepWithoutTargetIsRevealedRightAway() = runTest {
        val controller = TutorialController(backgroundScope)
        controller.start(listOf(step(null)), onFinish = {})

        // Ждать нечего: подсветки нет, подсказка показывается по центру сразу.
        advancePastPreparation()
        assertTrue(controller.isRevealed)
    }

    @Test
    fun stepWithMissingTargetIsShownCenteredInsteadOfBeingSkipped() = runTest {
        val controller = TutorialController(backgroundScope)
        controller.start(listOf(step("main.card")), onFinish = {})
        advancePastPreparation()

        // Цели на экране нет (пустой список тренировок), но шаг не пропускаем:
        // иначе часть рассказа проскакивала бы мимо пользователя.
        assertEquals(0, controller.stepIndex)
        assertTrue(controller.isRunning)
        assertTrue(controller.isRevealed)
    }

    @Test
    fun stepIsRevealedOnlyOnceItsTargetIsRegistered() = runTest {
        val controller = TutorialController(backgroundScope)
        controller.start(listOf(step("main.card")), onFinish = {})

        advanceTimeBy(100)
        assertFalse(controller.isRevealed)

        controller.targets.update("main.card", Rect(0f, 0f, 100f, 50f))
        advancePastPreparation()

        assertTrue(controller.isRevealed)
    }

    @Test
    fun tappingWhilePreparingShortensTheWait() = runTest {
        val controller = TutorialController(backgroundScope)
        controller.start(listOf(step("main.card")), onFinish = {})

        // Тап по ещё не показанной подсказке не пропускает шаг, а ускоряет его.
        advanceTimeBy(50)
        controller.next()
        advanceTimeBy(16)

        assertTrue(controller.isRevealed)
        assertEquals(0, controller.stepIndex)
    }

    @Test
    fun backIsNotOfferedAcrossScreens() = runTest {
        val controller = TutorialController(backgroundScope)
        controller.start(
            steps = listOf(step("main.card"), step("settings.back")),
            onFinish = {}
        )
        advancePastPreparation()

        controller.next()
        advancePastPreparation()
        assertEquals(1, controller.stepIndex)

        // Тур не возвращается на предыдущий экран: шаг назад через границу
        // экранов оставил бы подсветку на элементе чужого экрана.
        assertFalse(controller.canGoBack)
        controller.back()
        advancePastPreparation()
        assertEquals(1, controller.stepIndex)
    }

    @Test
    fun backIsOfferedInsideOneScreen() = runTest {
        val controller = TutorialController(backgroundScope)
        controller.start(
            steps = listOf(step("main.card"), step("main.start")),
            onFinish = {}
        )
        advancePastPreparation()

        controller.next()
        advancePastPreparation()
        assertTrue(controller.canGoBack)

        controller.back()
        advancePastPreparation()
        assertEquals(0, controller.stepIndex)
    }

    @Test
    fun pressStepMovesToTheNextStepBeforePressingTheTarget() = runTest {
        val controller = TutorialController(backgroundScope)
        val pressedAtStep = mutableListOf<Int>()
        controller.targets.putAction("main.cardEdit") {
            pressedAtStep += controller.stepIndex
        }
        controller.start(
            steps = listOf(
                TutorialStep(
                    targetKey = "main.cardEdit",
                    title = "Откроем редактор",
                    text = "Текст",
                    pressTarget = true,
                    actionLabel = "Открыть редактор"
                ),
                step(null)
            ),
            onFinish = {}
        )
        advancePastPreparation()
        assertTrue(controller.isRevealed)

        controller.next()
        advancePastPreparation()

        // Индекс двигается до нажатия: следующий шаг сразу ждёт цель уже на
        // новом экране.
        assertEquals(listOf(1), pressedAtStep)
        assertEquals(1, controller.stepIndex)
        assertTrue(controller.isRevealed)
    }

    @Test
    fun pressStepWithoutRegisteredActionFinishesTheTour() = runTest {
        val controller = TutorialController(backgroundScope)
        var finished = 0
        controller.start(
            steps = listOf(
                TutorialStep(
                    targetKey = "main.cardEdit",
                    title = "Откроем редактор",
                    text = "Текст",
                    pressTarget = true,
                    actionLabel = "Открыть редактор"
                ),
                step(null)
            ),
            onFinish = { finished++ }
        )
        advancePastPreparation()

        // Экран не зарегистрировал действие: вести пользователя некуда, и тур
        // закрывается, вместо того чтобы «нажать» пустоту.
        controller.next()
        advancePastPreparation()

        assertEquals(1, finished)
        assertFalse(controller.isRunning)
    }

    @Test
    fun targetIsCenteredByTheScrollPlanBeforeRevealing() = runTest {
        val controller = TutorialController(backgroundScope)
        controller.viewport = IntSize(1000, 1000)
        var scrolledBy = 0f
        controller.targets.putPlan(
            key = "editor.template",
            plan = TutorialScrollPlan(scrollBy = { offset -> scrolledBy = offset })
        )
        controller.targets.update("editor.template", Rect(0f, 900f, 100f, 1000f))

        controller.start(listOf(step("editor.template")), onFinish = {})
        advancePastPreparation()

        // Центр цели — 950, середина окна — 500: прокрутка на 450 вверх.
        assertEquals(450f, scrolledBy, 1f)
        assertTrue(controller.isRevealed)
    }

    @Test
    fun stepScrimOverridesTheTourScrim() = runTest {
        val controller = TutorialController(backgroundScope)
        controller.start(
            steps = listOf(step(null).copy(scrimAlpha = LIGHT_TUTORIAL_SCRIM_ALPHA)),
            onFinish = {},
            scrimAlpha = DEFAULT_TUTORIAL_SCRIM_ALPHA
        )
        advancePastPreparation()

        assertEquals(LIGHT_TUTORIAL_SCRIM_ALPHA, controller.scrimAlpha, 0.001f)
    }

    @Test
    fun tourFinishesAfterTheLastStep() = runTest {
        val controller = TutorialController(backgroundScope)
        var finished = 0
        controller.start(listOf(step(null)), onFinish = { finished++ })
        advancePastPreparation()

        controller.next()
        advancePastPreparation()

        assertEquals(1, finished)
        assertFalse(controller.isRunning)
    }
}

/**
 * Заведомо больше самого долгого ожидания разметки в контроллере (1.5 с после
 * смены экрана): после такой паузы подготовка шага гарантированно закончилась.
 */
private const val PREPARATION_LIMIT_MILLIS = 5_000L


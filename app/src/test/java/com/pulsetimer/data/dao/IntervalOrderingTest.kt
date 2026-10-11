package com.pulsetimer.data.dao

import com.pulsetimer.data.entity.IntervalEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Проверки порядка интервалов при дублировании.
 *
 * Лента сортируется по `orderIndex ASC, id ASC`, поэтому копия попадает строго
 * под оригиналом только если всем интервалам ниже оригинала номер сдвинут на
 * единицу — иначе копия и следующий интервал получили бы одинаковый
 * `orderIndex`, и порядок решил бы `id` (копия новее — встала бы ниже).
 */
class IntervalOrderingTest {

    private fun interval(id: Long, orderIndex: Int) = IntervalEntity(
        id = id,
        templateId = 1L,
        name = "Интервал $id",
        durationSeconds = 30,
        colorHex = "#FF3B30",
        orderIndex = orderIndex
    )

    @Test
    fun copyOfFirstIntervalLeavesItOnTop() {
        val shifted = shiftOrderIndexesForCopy(
            listOf(interval(1, 0), interval(2, 1), interval(3, 2)),
            sourceIndex = 0
        )

        // Копия вставляется с orderIndex = 1 и встаёт сразу под оригиналом,
        // а следующие интервалы сдвигаются на единицу.
        assertEquals(listOf(1L, 2L, 3L), shifted.map { it.id })
        assertEquals(listOf(0, 2, 3), shifted.map { it.orderIndex })
    }

    @Test
    fun copyOfMiddleIntervalKeepsFollowingOrder() {
        val shifted = shiftOrderIndexesForCopy(
            listOf(interval(1, 0), interval(2, 1), interval(3, 2)),
            sourceIndex = 1
        )

        assertEquals(listOf(0, 1, 3), shifted.map { it.orderIndex })
    }

    @Test
    fun copyOfLastIntervalStaysAtTheBottom() {
        val shifted = shiftOrderIndexesForCopy(
            listOf(interval(1, 0), interval(2, 1), interval(3, 2)),
            sourceIndex = 2
        )

        // Оригинал последний: сдвигать нечего, а копия получает номер за ним.
        assertEquals(listOf(0, 1, 2), shifted.map { it.orderIndex })
    }

    @Test
    fun strayOrderIndexesAreNormalizedIntoContiguousRange() {
        // «Дыры» и разросшиеся значения, накопившиеся в orderIndex, исчезают:
        // после дублирования нумерация снова 0..n-1, иначе значение росло бы
        // при каждом копировании.
        val shifted = shiftOrderIndexesForCopy(
            listOf(interval(1, 5), interval(2, 9), interval(3, 12)),
            sourceIndex = 1
        )

        assertEquals(listOf(0, 1, 3), shifted.map { it.orderIndex })
    }

    @Test
    fun otherFieldsAreLeftUntouched() {
        val shifted = shiftOrderIndexesForCopy(
            listOf(interval(1, 0), interval(2, 1)),
            sourceIndex = 0
        )

        assertEquals("Интервал 2", shifted[1].name)
        assertEquals(30, shifted[1].durationSeconds)
        assertEquals("#FF3B30", shifted[1].colorHex)
        assertEquals(1L, shifted[1].templateId)
    }
}

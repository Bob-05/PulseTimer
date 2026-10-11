package com.pulsetimer.data.dao

import com.pulsetimer.data.entity.IntervalEntity

/**
 * Освобождает позицию `sourceIndex + 1` для копии интервала.
 *
 * Интервалы ленты сортируются по `orderIndex ASC, id ASC`, поэтому одной
 * вставки копии с тем же `orderIndex`, что у следующего интервала, недостаточно:
 * при равных значениях порядок решил бы `id`, и более новая копия встала бы не
 * под оригиналом, а после следующего интервала. Поэтому всем интервалам ниже
 * оригинала номер увеличивается на единицу.
 *
 * Заодно номера нормализуются в `0..n-1`: «дыры» и дубли, накопившиеся в
 * `orderIndex`, исчезают, и значение не растёт бесконечно при повторном
 * дублировании.
 *
 * @param ordered интервалы тренировки в текущем порядке (`orderIndex ASC, id ASC`).
 * @param sourceIndex позиция оригинала в [ordered].
 */
internal fun shiftOrderIndexesForCopy(
    ordered: List<IntervalEntity>,
    sourceIndex: Int
): List<IntervalEntity> = ordered.mapIndexed { index, interval ->
    interval.copy(orderIndex = if (index <= sourceIndex) index else index + 1)
}

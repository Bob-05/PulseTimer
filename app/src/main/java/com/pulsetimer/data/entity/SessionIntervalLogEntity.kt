package com.pulsetimer.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Построчный лог интервала внутри конкретной сессии тренировки.
 *
 * Записи создаются в момент старта тренировки (по одной на каждый интервал)
 * и обновляются по ходу прохождения. Такой подход позволяет показать детальную
 * историю даже если сессия была прервана системой: строки уже в базе, а
 * `actualSeconds`/`wasSkipped` отражают, что успел сделать пользователь.
 *
 * `plannedSeconds` снимется на момент старта, поэтому последующие правки
 * шаблона (переименование, смена длительности/цвета) историю не переписывают.
 */
@Entity(
    tableName = "session_interval_logs",
    foreignKeys = [
        ForeignKey(
            entity = SessionLogEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionLogId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionLogId"])]
)
data class SessionIntervalLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionLogId: Long,
    val name: String,
    @ColumnInfo(defaultValue = "'⏱️'")
    val iconEmoji: String = "⏱️",
    @ColumnInfo(defaultValue = "'#FF3B30'")
    val colorHex: String = "#FF3B30",
    /** Плановая длительность интервала на момент старта, сек. */
    val plannedSeconds: Int,
    /** Фактически отработанное время, сек. */
    @ColumnInfo(defaultValue = "0")
    val actualSeconds: Int = 0,
    /** Позиция интервала внутри тренировки (0 — первый). */
    val orderIndex: Int,
    /** true — интервал был пропущен кнопкой «Пропустить». */
    @ColumnInfo(defaultValue = "0")
    val wasSkipped: Boolean = false
)

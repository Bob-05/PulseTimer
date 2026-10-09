package com.pulsetimer.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "session_logs",
    foreignKeys = [
        ForeignKey(
            entity = TemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["templateId"])]
)
data class SessionLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val templateId: Long,
    val templateName: String,
    val startedAt: Long,
    val completedAt: Long? = null,
    val totalDurationSeconds: Int = 0,
    /** Плановая длительность сессии (Σ длительностей интервалов), сек. */
    @ColumnInfo(defaultValue = "0")
    val plannedDurationSeconds: Int = 0,
    /** Сколько интервалов реально было отработано (факт > 0 либо завершён). */
    @ColumnInfo(defaultValue = "0")
    val completedIntervals: Int = 0,
    /** Сколько интервалов было в тренировке на момент старта. */
    @ColumnInfo(defaultValue = "0")
    val totalIntervals: Int = 0,
    /** Индекс интервала, на котором сессия оборвалась (-1 — неизвестно). */
    @ColumnInfo(defaultValue = "-1")
    val lastIntervalIndex: Int = -1,
    /**
     * true — сессия прервана системой (сервис убит / вытеснен новой сессией),
     * false — сессия закончилась штатно (завершена или остановлена пользователем).
     */
    @ColumnInfo(defaultValue = "0")
    val interrupted: Boolean = false
)
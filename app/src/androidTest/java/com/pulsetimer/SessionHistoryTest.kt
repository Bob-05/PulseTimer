package com.pulsetimer

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pulsetimer.data.database.AppDatabase
import com.pulsetimer.data.entity.SessionIntervalLogEntity
import com.pulsetimer.data.entity.SessionLogEntity
import com.pulsetimer.data.entity.IntervalEntity
import com.pulsetimer.data.entity.TemplateEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionHistoryTest {
    @Test
    fun intervalsCanBeEditedAndReordered() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val dao = database.timerDao()
            val templateId = dao.insertTemplate(
                TemplateEntity(name = "Test workout", description = "Interval editing test")
            )
            val firstId = dao.insertInterval(
                IntervalEntity(
                    templateId = templateId,
                    name = "Warm up",
                    durationSeconds = 30,
                    colorHex = "#FF3B30",
                    orderIndex = 0
                )
            )
            val secondId = dao.insertInterval(
                IntervalEntity(
                    templateId = templateId,
                    name = "Work",
                    durationSeconds = 45,
                    colorHex = "#FF3B30",
                    orderIndex = 1
                )
            )
            val thirdId = dao.insertInterval(
                IntervalEntity(
                    templateId = templateId,
                    name = "Rest",
                    durationSeconds = 15,
                    colorHex = "#007AFF",
                    orderIndex = 2
                )
            )
            val originalIntervals = dao.getIntervalsByTemplateId(templateId).first()
            val editedInterval = originalIntervals[1].copy(
                name = "Sprint",
                durationSeconds = 60,
                colorHex = "#FFCC00"
            )
            dao.insertInterval(editedInterval)
            dao.reorderIntervals(
                listOf(
                    originalIntervals[2].copy(orderIndex = 0),
                    editedInterval.copy(orderIndex = 1),
                    originalIntervals[0].copy(orderIndex = 2)
                )
            )

            val updatedIntervals = dao.getIntervalsByTemplateId(templateId).first()
            assertEquals(listOf(thirdId, secondId, firstId), updatedIntervals.map { it.id })
            assertEquals("Sprint", updatedIntervals[1].name)
            assertEquals(60, updatedIntervals[1].durationSeconds)
            assertEquals("#FFCC00", updatedIntervals[1].colorHex)
        } finally {
            database.close()
        }
    }

    @Test
    fun workoutSessionCanBeRecordedAndCompleted() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val dao = database.timerDao()
            val templateId = dao.insertTemplate(
                TemplateEntity(name = "Test workout", description = "History test", iconEmoji = "🏃")
            )
            val intervalId = dao.insertInterval(
                IntervalEntity(
                    templateId = templateId,
                    name = "Run",
                    durationSeconds = 30,
                    colorHex = "#88AA99",
                    orderIndex = 0,
                    iconEmoji = "⚡"
                )
            )
            assertEquals("🏃", dao.getTemplateById(templateId).first()?.iconEmoji)
            assertEquals("⚡", dao.getIntervalsByTemplateId(templateId).first().single().iconEmoji)
            val startedAt = System.currentTimeMillis()
            val logId = dao.insertSessionLog(
                SessionLogEntity(
                    templateId = templateId,
                    templateName = "Test workout",
                    startedAt = startedAt
                )
            )

            val startedEntry = dao.getAllSessionLogs().first().single()
            assertEquals(logId, startedEntry.id)
            assertEquals(null, startedEntry.completedAt)

            dao.updateSessionLog(
                startedEntry.copy(
                    completedAt = startedAt + 5_000,
                    totalDurationSeconds = 5
                )
            )

            val completedEntry = dao.getAllSessionLogs().first().single()
            assertNotNull(completedEntry.completedAt)
            assertEquals(5, completedEntry.totalDurationSeconds)

            val secondLogId = dao.insertSessionLog(
                SessionLogEntity(
                    templateId = templateId,
                    templateName = "Test workout",
                    startedAt = startedAt + 10_000
                )
            )
            dao.deleteSessionLogById(logId)
            assertEquals(secondLogId, dao.getAllSessionLogs().first().single().id)
            dao.clearAllSessionLogs()
            assertEquals(0, dao.getAllSessionLogs().first().size)
            assertEquals(intervalId, dao.getIntervalsByTemplateId(templateId).first().single().id)

        } finally {
            database.close()
        }
    }

    @Test
    fun detailedSessionLoggingTracksIntervalProgress() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val dao = database.timerDao()
            val templateId = dao.insertTemplate(
                TemplateEntity(name = "Intervals", description = "Detailed history test")
            )
            val planned = listOf("Warm up" to 30, "Work" to 45, "Rest" to 15)
            planned.forEachIndexed { index, (name, seconds) ->
                dao.insertInterval(
                    IntervalEntity(
                        templateId = templateId,
                        name = name,
                        durationSeconds = seconds,
                        colorHex = "#3B82F6",
                        orderIndex = index
                    )
                )
            }

            val startedAt = System.currentTimeMillis()
            val logId = dao.insertSessionLog(
                SessionLogEntity(
                    templateId = templateId,
                    templateName = "Intervals",
                    startedAt = startedAt
                )
            )
            // Строки интервалов создаются на старте — история остаётся даже
            // если сессию убьёт система.
            dao.insertSessionIntervalLogs(
                planned.mapIndexed { index, (name, seconds) ->
                    SessionIntervalLogEntity(
                        sessionLogId = logId,
                        name = name,
                        plannedSeconds = seconds,
                        orderIndex = index
                    )
                }
            )
            // Прогресс пишется по ходу тренировки, а не только в конце.
            dao.updateSessionProgress(
                logId = logId,
                completedIntervals = 1,
                lastIntervalIndex = 0
            )

            val inProgress = dao.getAllSessionLogs().first().single()
            assertEquals(1, inProgress.completedIntervals)
            assertEquals(0, inProgress.lastIntervalIndex)
            assertEquals(null, inProgress.completedAt)

            // Первый интервал отработан полностью, второй пропущен.
            dao.updateSessionIntervalLog(
                sessionLogId = logId,
                orderIndex = 0,
                actualSeconds = 30,
                wasSkipped = false
            )
            dao.updateSessionIntervalLog(
                sessionLogId = logId,
                orderIndex = 1,
                actualSeconds = 12,
                wasSkipped = true
            )

            dao.finalizeSessionLog(
                logId = logId,
                completedAt = startedAt + 57_000,
                totalDurationSeconds = 57,
                completedIntervals = 2,
                lastIntervalIndex = 1,
                interrupted = false
            )

            val finished = dao.getAllSessionLogs().first().single()
            assertNotNull(finished.completedAt)
            assertEquals(57, finished.totalDurationSeconds)
            assertEquals(2, finished.completedIntervals)
            assertEquals(false, finished.interrupted)

            val intervalLogs = dao.getSessionIntervalLogsBySessionId(logId).first()
            assertEquals(listOf("Warm up", "Work", "Rest"), intervalLogs.map { it.name })
            assertEquals(listOf(0, 1, 2), intervalLogs.map { it.orderIndex })
            assertEquals(listOf(30, 45, 15), intervalLogs.map { it.plannedSeconds })
            // Пропуск сохраняет фактическое время: видно, сколько успели пройти.
            assertEquals(listOf(30, 12, 0), intervalLogs.map { it.actualSeconds })
            assertEquals(listOf(false, true, false), intervalLogs.map { it.wasSkipped })

            // Прерванная сессия: completedAt == null, но детали уже в базе.
            val secondLogId = dao.insertSessionLog(
                SessionLogEntity(
                    templateId = templateId,
                    templateName = "Intervals",
                    startedAt = startedAt + 60_000
                )
            )
            dao.insertSessionIntervalLogs(
                listOf(
                    SessionIntervalLogEntity(
                        sessionLogId = secondLogId,
                        name = "Warm up",
                        plannedSeconds = 30,
                        actualSeconds = 8,
                        orderIndex = 0
                    )
                )
            )
            val allLogs = dao.getAllSessionIntervalLogs().first()
            // Сначала более новая сессия, внутри сессии — порядок интервалов.
            assertEquals(
                listOf(secondLogId, logId, logId, logId),
                allLogs.map { it.sessionLogId }
            )
            assertEquals(listOf(0, 0, 1, 2), allLogs.map { it.orderIndex })

            // Удаление сессии из истории убирает и её детальные строки
            // (ForeignKey CASCADE), иначе в базе копился бы мусор.
            dao.deleteSessionLogById(secondLogId)
            val afterDelete = dao.getAllSessionIntervalLogs().first()
            assertEquals(3, afterDelete.size)
            assertEquals(listOf(logId, logId, logId), afterDelete.map { it.sessionLogId })
        } finally {
            database.close()
        }
    }
}

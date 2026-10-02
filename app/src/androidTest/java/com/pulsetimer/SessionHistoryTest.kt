package com.pulsetimer

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pulsetimer.data.database.AppDatabase
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
}

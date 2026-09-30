package com.example.pulsetimer

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pulsetimer.data.database.AppDatabase
import com.pulsetimer.data.entity.SessionLogEntity
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
    fun workoutSessionCanBeRecordedAndCompleted() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val dao = database.timerDao()
            val templateId = dao.insertTemplate(
                TemplateEntity(name = "Test workout", description = "History test")
            )
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
        } finally {
            database.close()
        }
    }
}

package com.pulsetimer.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pulsetimer.data.dao.TimerDao
import com.pulsetimer.data.entity.IntervalEntity
import com.pulsetimer.data.entity.SessionLogEntity
import com.pulsetimer.data.entity.TemplateEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        TemplateEntity::class,
        IntervalEntity::class,
        SessionLogEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun timerDao(): TimerDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE templates ADD COLUMN backgroundType TEXT NOT NULL DEFAULT 'COLOR'")
                db.execSQL("ALTER TABLE templates ADD COLUMN backgroundValue TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE templates ADD COLUMN audioUri TEXT")
                db.execSQL("ALTER TABLE templates ADD COLUMN vibrationPatternId INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE intervals ADD COLUMN backgroundType TEXT NOT NULL DEFAULT 'COLOR'")
                db.execSQL("ALTER TABLE intervals ADD COLUMN backgroundValue TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE intervals ADD COLUMN audioUri TEXT")
                db.execSQL("ALTER TABLE intervals ADD COLUMN vibrationPatternId INTEGER NOT NULL DEFAULT 1")
            }
        }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE templates ADD COLUMN iconEmoji TEXT NOT NULL DEFAULT '🏋️'")
                db.execSQL("ALTER TABLE intervals ADD COLUMN iconEmoji TEXT NOT NULL DEFAULT '⏱️'")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pulse_timer_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .addMigrations(MIGRATION_2_3)
                    .addCallback(DatabaseCallback(context.applicationContext))
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback(
        private val context: Context
    ) : RoomDatabase.Callback() {

        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            val database = getDatabase(context)
            CoroutineScope(Dispatchers.IO).launch {
                prepopulateDatabase(database.timerDao())
            }
        }

        private suspend fun prepopulateDatabase(dao: TimerDao) {
            val tabataTemplate = TemplateEntity(
                name = "Табата",
                description = "20 сек работы / 10 сек отдыха, 8 раундов"
            )
            val tabataId = dao.insertTemplate(tabataTemplate)

            val tabataIntervals = mutableListOf<IntervalEntity>()
            for (i in 0 until 8) {
                tabataIntervals.add(
                    IntervalEntity(
                        templateId = tabataId,
                        name = "Работа",
                        durationSeconds = 20,
                        colorHex = "#FF3B30",
                        orderIndex = i * 2
                    )
                )
                tabataIntervals.add(
                    IntervalEntity(
                        templateId = tabataId,
                        name = "Отдых",
                        durationSeconds = 10,
                        colorHex = "#007AFF",
                        orderIndex = i * 2 + 1
                    )
                )
            }
            dao.insertIntervals(tabataIntervals)

            val circuitTemplate = TemplateEntity(
                name = "Круговая",
                description = "30 сек работы / 30 сек отдыха, 3 раунда"
            )
            val circuitId = dao.insertTemplate(circuitTemplate)

            val circuitIntervals = mutableListOf<IntervalEntity>()
            for (i in 0 until 3) {
                circuitIntervals.add(
                    IntervalEntity(
                        templateId = circuitId,
                        name = "Работа",
                        durationSeconds = 30,
                        colorHex = "#FF3B30",
                        orderIndex = i * 2
                    )
                )
                circuitIntervals.add(
                    IntervalEntity(
                        templateId = circuitId,
                        name = "Отдых",
                        durationSeconds = 30,
                        colorHex = "#007AFF",
                        orderIndex = i * 2 + 1
                    )
                )
            }
            dao.insertIntervals(circuitIntervals)
        }
    }
}
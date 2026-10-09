package com.pulsetimer.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.pulsetimer.data.entity.IntervalEntity
import com.pulsetimer.data.entity.SessionIntervalLogEntity
import com.pulsetimer.data.entity.SessionLogEntity
import com.pulsetimer.data.entity.TemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TimerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: TemplateEntity): Long

    @Update
    suspend fun updateTemplate(template: TemplateEntity)

    @Delete
    suspend fun deleteTemplate(template: TemplateEntity)

    /**
     * Возвращает тренировки в пользовательском порядке: больше `sortOrder` —
     * выше карточка. `id ASC` — детерминированный tie-break для записей,
     * которые ещё не успели получить порядок (например, вставленных тестами).
     */
    @Query("SELECT * FROM templates ORDER BY sortOrder DESC, id ASC")
    fun getAllTemplates(): Flow<List<TemplateEntity>>

    /** Следующее значение порядка для новой тренировки: `max + 1`. */
    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM templates")
    suspend fun nextSortOrder(): Int

    /**
     * Вставка с автоматическим назначением порядка. Логика живёт в DAO, а не в
     * вызывающем коде, чтобы новый шаблон не мог случайно получить
     * `sortOrder = 0` (значение по умолчанию) и «улететь» в конец списка.
     */
    @Transaction
    suspend fun insertTemplateWithNextSortOrder(template: TemplateEntity): Long {
        return insertTemplate(template.copy(sortOrder = nextSortOrder()))
    }

    @Update
    suspend fun updateTemplates(templates: List<TemplateEntity>)

    /**
     * Переупорядочивание карточек: список приходит уже с новыми `sortOrder`,
     * запись выполняется в одной транзакции — чтобы при перемещении не
     * проскочило промежуточное состояние с дублирующимися значениями.
     */
    @Transaction
    suspend fun reorderTemplates(templates: List<TemplateEntity>) {
        updateTemplates(templates)
    }

    @Query("SELECT * FROM templates WHERE id = :id")
    fun getTemplateById(id: Long): Flow<TemplateEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInterval(interval: IntervalEntity): Long

    @Update
    suspend fun updateIntervals(intervals: List<IntervalEntity>)

    @Transaction
    suspend fun reorderIntervals(intervals: List<IntervalEntity>) {
        updateIntervals(intervals)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIntervals(intervals: List<IntervalEntity>)

    @Delete
    suspend fun deleteInterval(interval: IntervalEntity)

    @Query("SELECT * FROM intervals WHERE templateId = :templateId ORDER BY orderIndex ASC, id ASC")
    fun getIntervalsByTemplateId(templateId: Long): Flow<List<IntervalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessionLog(log: SessionLogEntity): Long

    @Update
    suspend fun updateSessionLog(log: SessionLogEntity)

    /**
     * Закрытие сессии без перезаписи всей строки: запись создаётся на старте
     * тренировки, а в конце меняются только итоговые поля. Точечный UPDATE
     * заодно исключает гонку с параллельной записью прогресса.
     */
    @Query(
        """
        UPDATE session_logs SET
            completedAt = :completedAt,
            totalDurationSeconds = :totalDurationSeconds,
            completedIntervals = :completedIntervals,
            lastIntervalIndex = :lastIntervalIndex,
            interrupted = :interrupted
        WHERE id = :logId
        """
    )
    suspend fun finalizeSessionLog(
        logId: Long,
        completedAt: Long?,
        totalDurationSeconds: Int,
        completedIntervals: Int,
        lastIntervalIndex: Int,
        interrupted: Boolean
    )

    /** Промежуточный прогресс сессии — чтобы он не терялся при обрыве. */
    @Query(
        "UPDATE session_logs SET completedIntervals = :completedIntervals, " +
            "lastIntervalIndex = :lastIntervalIndex WHERE id = :logId"
    )
    suspend fun updateSessionProgress(
        logId: Long,
        completedIntervals: Int,
        lastIntervalIndex: Int
    )

    @Query("SELECT * FROM session_logs ORDER BY startedAt DESC")
    fun getAllSessionLogs(): Flow<List<SessionLogEntity>>

    @Query("DELETE FROM session_logs WHERE id = :logId")
    suspend fun deleteSessionLogById(logId: Long)

    @Query("DELETE FROM session_logs")
    suspend fun clearAllSessionLogs()

    // --- Детальная история: интервалы внутри сессии ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessionIntervalLogs(logs: List<SessionIntervalLogEntity>)

    @Update
    suspend fun updateSessionIntervalLogs(logs: List<SessionIntervalLogEntity>)

    /**
     * Результат интервала ищется по (sessionLogId, orderIndex), а не по id
     * строки: сервису не нужно запоминать идентификаторы сразу после
     * пакетной вставки, а значит нечему рассинхронизироваться.
     */
    @Query(
        "UPDATE session_interval_logs SET actualSeconds = :actualSeconds, " +
            "wasSkipped = :wasSkipped " +
            "WHERE sessionLogId = :sessionLogId AND orderIndex = :orderIndex"
    )
    suspend fun updateSessionIntervalLog(
        sessionLogId: Long,
        orderIndex: Int,
        actualSeconds: Int,
        wasSkipped: Boolean
    )

    @Query("SELECT * FROM session_interval_logs WHERE sessionLogId = :sessionLogId ORDER BY orderIndex ASC, id ASC")
    fun getSessionIntervalLogsBySessionId(sessionLogId: Long): Flow<List<SessionIntervalLogEntity>>

    @Query("SELECT * FROM session_interval_logs ORDER BY sessionLogId DESC, orderIndex ASC")
    fun getAllSessionIntervalLogs(): Flow<List<SessionIntervalLogEntity>>
}
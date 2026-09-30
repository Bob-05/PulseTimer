package com.pulsetimer.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pulsetimer.data.entity.IntervalEntity
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

    @Query("SELECT * FROM templates ORDER BY createdAt DESC")
    fun getAllTemplates(): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM templates WHERE id = :id")
    fun getTemplateById(id: Long): Flow<TemplateEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInterval(interval: IntervalEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIntervals(intervals: List<IntervalEntity>)

    @Delete
    suspend fun deleteInterval(interval: IntervalEntity)

    @Query("DELETE FROM intervals WHERE templateId = :templateId")
    suspend fun deleteIntervalsByTemplateId(templateId: Long)

    @Query("SELECT * FROM intervals WHERE templateId = :templateId ORDER BY orderIndex ASC")
    fun getIntervalsByTemplateId(templateId: Long): Flow<List<IntervalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessionLog(log: SessionLogEntity): Long

    @Update
    suspend fun updateSessionLog(log: SessionLogEntity)

    @Query("SELECT * FROM session_logs ORDER BY startedAt DESC")
    fun getAllSessionLogs(): Flow<List<SessionLogEntity>>

    @Query("SELECT * FROM session_logs WHERE templateId = :templateId ORDER BY startedAt DESC")
    fun getSessionLogsByTemplateId(templateId: Long): Flow<List<SessionLogEntity>>

    @Query("DELETE FROM session_logs WHERE id = :logId")
    suspend fun deleteSessionLogById(logId: Long)

    @Query("DELETE FROM session_logs")
    suspend fun clearAllSessionLogs()
}
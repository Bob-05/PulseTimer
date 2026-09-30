package com.pulsetimer.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pulsetimer.data.database.AppDatabase
import com.pulsetimer.data.entity.IntervalEntity
import com.pulsetimer.data.entity.SessionLogEntity
import com.pulsetimer.data.entity.TemplateEntity
import com.pulsetimer.service.TimerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TimerViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getDatabase(application).timerDao()

    val templates: StateFlow<List<TemplateEntity>> = dao.getAllTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sessionLogs: StateFlow<List<SessionLogEntity>> = dao.getAllSessionLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val selectedTemplateId = MutableStateFlow<Long?>(null)

    val selectedTemplate: StateFlow<TemplateEntity?> = selectedTemplateId
        .combine(templates) { id, list -> list.find { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val selectedTemplateIntervals: StateFlow<List<IntervalEntity>> = selectedTemplateId
        .flatMapLatest { id ->
            if (id != null) dao.getIntervalsByTemplateId(id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val timerState: StateFlow<TimerService.ServiceTimerState> = TimerService.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TimerService.ServiceTimerState())

    fun selectTemplate(id: Long) { selectedTemplateId.value = id }

    fun clearSelectedTemplate() { selectedTemplateId.value = null }

    /**
     * Проверяет, есть ли у шаблона интервалы, до запуска сервиса.
     * Возвращает `false` и при пустом списке, и при ошибке БД — в обоих случаях
     * запускать таймер не имеет смысла, пользователю покажем Snackbar.
     */
    suspend fun hasIntervals(templateId: Long): Boolean {
        return try {
            dao.getIntervalsByTemplateId(templateId).first().isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    fun addTemplate(name: String, description: String) {
        viewModelScope.launch {
            dao.insertTemplate(TemplateEntity(name = name, description = description))
        }
    }

    fun deleteTemplate(template: TemplateEntity) {
        viewModelScope.launch { dao.deleteTemplate(template) }
    }

    fun updateTemplate(template: TemplateEntity) {
        viewModelScope.launch { dao.updateTemplate(template) }
    }

    fun addInterval(templateId: Long, name: String, durationSeconds: Int, colorHex: String) {
        viewModelScope.launch {
            val currentIntervals = dao.getIntervalsByTemplateId(templateId).first()
            dao.insertInterval(
                IntervalEntity(
                    templateId = templateId,
                    name = name,
                    durationSeconds = durationSeconds,
                    colorHex = colorHex,
                    orderIndex = currentIntervals.size
                )
            )
        }
    }

    fun deleteInterval(interval: IntervalEntity) {
        viewModelScope.launch { dao.deleteInterval(interval) }
    }

    fun updateInterval(interval: IntervalEntity) {
        viewModelScope.launch { dao.insertInterval(interval) }
    }

    fun deleteSessionLog(log: SessionLogEntity) {
        viewModelScope.launch { dao.deleteSessionLogById(log.id) }
    }

    fun clearSessionHistory() {
        viewModelScope.launch { dao.clearAllSessionLogs() }
    }

    fun startTimer(templateId: Long, templateName: String) {
        val context = getApplication<Application>()
        val intent = Intent(context, TimerService::class.java).apply {
            action = TimerService.ACTION_START
            putExtra(TimerService.EXTRA_TEMPLATE_ID, templateId)
            putExtra(TimerService.EXTRA_TEMPLATE_NAME, templateName)
        }
        context.startForegroundService(intent)
    }

    fun pauseTimer() {
        val context = getApplication<Application>()
        context.startForegroundService(
            Intent(context, TimerService::class.java).apply { action = TimerService.ACTION_PAUSE }
        )
    }

    fun resumeTimer() {
        val context = getApplication<Application>()
        context.startForegroundService(
            Intent(context, TimerService::class.java).apply { action = TimerService.ACTION_RESUME }
        )
    }

    fun skipInterval() {
        val context = getApplication<Application>()
        context.startForegroundService(
            Intent(context, TimerService::class.java).apply { action = TimerService.ACTION_SKIP }
        )
    }

    fun previousInterval() {
        val context = getApplication<Application>()
        context.startForegroundService(
            Intent(context, TimerService::class.java).apply {
                action = TimerService.ACTION_PREVIOUS
            }
        )
    }

    fun stopTimer() {
        val context = getApplication<Application>()
        context.startForegroundService(
            Intent(context, TimerService::class.java).apply { action = TimerService.ACTION_STOP }
        )
    }
}
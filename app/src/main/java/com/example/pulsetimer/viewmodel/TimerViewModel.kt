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
import kotlinx.coroutines.flow.asStateFlow
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

    private val _selectedTemplateId = MutableStateFlow<Long?>(null)
    val selectedTemplateId: StateFlow<Long?> = _selectedTemplateId.asStateFlow()

    val selectedTemplate: StateFlow<TemplateEntity?> = _selectedTemplateId
        .combine(templates) { id, list ->
            list.find { it.id == id }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val selectedTemplateIntervals: StateFlow<List<IntervalEntity>> = _selectedTemplateId
        .flatMapLatest { id ->
            if (id != null) {
                dao.getIntervalsByTemplateId(id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val timerState: StateFlow<TimerService.ServiceTimerState> = TimerService.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TimerService.ServiceTimerState())

    fun selectTemplate(id: Long) {
        _selectedTemplateId.value = id
    }

    fun clearSelectedTemplate() {
        _selectedTemplateId.value = null
    }

    fun addTemplate(name: String, description: String) {
        viewModelScope.launch {
            dao.insertTemplate(
                TemplateEntity(name = name, description = description)
            )
        }
    }

    fun deleteTemplate(template: TemplateEntity) {
        viewModelScope.launch {
            dao.deleteTemplate(template)
        }
    }

    fun updateTemplate(template: TemplateEntity) {
        viewModelScope.launch {
            dao.updateTemplate(template)
        }
    }

    fun addInterval(
        templateId: Long,
        name: String,
        durationSeconds: Int,
        colorHex: String,
        iconEmoji: String
    ) {
        viewModelScope.launch {
            val currentIntervals = dao.getIntervalsByTemplateId(templateId).first()
            val orderIndex = currentIntervals.size
            dao.insertInterval(
                IntervalEntity(
                    templateId = templateId,
                    name = name,
                    durationSeconds = durationSeconds,
                    colorHex = colorHex,
                    orderIndex = orderIndex,
                    iconEmoji = iconEmoji
                )
            )
        }
    }

    fun deleteInterval(interval: IntervalEntity) {
        viewModelScope.launch {
            dao.deleteInterval(interval)
        }
    }

    fun updateInterval(interval: IntervalEntity) {
        viewModelScope.launch {
            dao.insertInterval(interval)
        }
    }

    fun logSession(templateId: Long, templateName: String, startedAt: Long, completedAt: Long, duration: Int) {
        viewModelScope.launch {
            dao.insertSessionLog(
                SessionLogEntity(
                    templateId = templateId,
                    templateName = templateName,
                    startedAt = startedAt,
                    completedAt = completedAt,
                    totalDurationSeconds = duration
                )
            )
        }
    }

    fun deleteSessionLog(log: SessionLogEntity) {
        viewModelScope.launch {
            dao.deleteSessionLogById(log.id)
        }
    }

    fun deleteSessionLogsForTemplate(templateId: Long) {
        viewModelScope.launch {
            dao.deleteSessionLogsByTemplateId(templateId)
        }
    }

    fun clearSessionHistory() {
        viewModelScope.launch {
            dao.clearAllSessionLogs()
        }
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
            Intent(context, TimerService::class.java).apply {
                action = TimerService.ACTION_PAUSE
            }
        )
    }

    fun resumeTimer() {
        val context = getApplication<Application>()
        context.startForegroundService(
            Intent(context, TimerService::class.java).apply {
                action = TimerService.ACTION_RESUME
            }
        )
    }

    fun skipInterval() {
        val context = getApplication<Application>()
        context.startForegroundService(
            Intent(context, TimerService::class.java).apply {
                action = TimerService.ACTION_SKIP
            }
        )
    }

    fun stopTimer() {
        val context = getApplication<Application>()
        context.startForegroundService(
            Intent(context, TimerService::class.java).apply {
                action = TimerService.ACTION_STOP
            }
        )
    }
}
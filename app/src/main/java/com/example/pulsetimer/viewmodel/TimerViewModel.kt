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

    private val selectedTemplateId = MutableStateFlow<Long?>(null)

    val selectedTemplate: StateFlow<TemplateEntity?> = selectedTemplateId
        .combine(templates) { id, list -> list.find { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val selectedTemplateIntervals: StateFlow<List<IntervalEntity>> = selectedTemplateId
        .flatMapLatest { id ->
            if (id != null) dao.getIntervalsByTemplateId(id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val timerState: StateFlow<TimerService.ServiceTimerState> = TimerService.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TimerService.ServiceTimerState())

    /**
     * ID тренировки, которую пользователь только что добавил.
     * MainScreen использует это значение, чтобы дождаться появления шаблона
     * в списке и запустить последовательность «скролл → анимация появления».
     */
    private val _recentlyAddedTemplateId = MutableStateFlow<Long?>(null)
    val recentlyAddedTemplateId: StateFlow<Long?> = _recentlyAddedTemplateId.asStateFlow()

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
            val id = dao.insertTemplate(TemplateEntity(name = name, description = description))
            _recentlyAddedTemplateId.value = id
        }
    }

    /**
     * Сброс флага после проигрывания анимации появления карточки.
     * Принимает id, чтобы карточка, чья анимация завершилась с задержкой,
     * не сбрасывала флаг уже другой (только что добавленной) тренировки.
     */
    fun clearRecentlyAddedTemplate(id: Long) {
        if (_recentlyAddedTemplateId.value == id) {
            _recentlyAddedTemplateId.value = null
        }
    }

    fun deleteTemplate(template: TemplateEntity) {
        viewModelScope.launch { dao.deleteTemplate(template) }
    }

    fun updateTemplate(template: TemplateEntity) {
        viewModelScope.launch { dao.updateTemplate(template) }
    }

    fun updateTemplateDetails(templateId: Long, name: String, description: String) {
        viewModelScope.launch {
            val template = dao.getTemplateById(templateId).first() ?: return@launch
            dao.updateTemplate(
                template.copy(
                    name = name.trim().ifEmpty { template.name },
                    description = description.trim()
                )
            )
        }
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
                    orderIndex = (currentIntervals.maxOfOrNull { it.orderIndex } ?: -1) + 1
                )
            )
        }
    }

    fun moveInterval(templateId: Long, intervalId: Long, direction: Int) {
        if (direction != -1 && direction != 1) return
        viewModelScope.launch {
            val intervals = dao.getIntervalsByTemplateId(templateId).first().toMutableList()
            val currentIndex = intervals.indexOfFirst { it.id == intervalId }
            val targetIndex = currentIndex + direction
            if (currentIndex == -1 || targetIndex !in intervals.indices) return@launch

            val movedInterval = intervals.removeAt(currentIndex)
            intervals.add(targetIndex, movedInterval)
            dao.reorderIntervals(intervals.mapIndexed { index, interval ->
                interval.copy(orderIndex = index)
            })
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
        sendTimerAction(TimerService.ACTION_PAUSE)
    }

    fun resumeTimer() {
        sendTimerAction(TimerService.ACTION_RESUME)
    }

    fun skipInterval() {
        sendTimerAction(TimerService.ACTION_SKIP)
    }

    fun previousInterval() {
        sendTimerAction(TimerService.ACTION_PREVIOUS)
    }

    fun stopTimer() {
        sendTimerAction(TimerService.ACTION_STOP)
    }

    private fun sendTimerAction(action: String) {
        val context = getApplication<Application>()
        context.startForegroundService(
            Intent(context, TimerService::class.java).setAction(action)
        )
    }
}
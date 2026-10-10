package com.pulsetimer.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pulsetimer.data.database.AppDatabase
import com.pulsetimer.data.entity.IntervalEntity
import com.pulsetimer.data.entity.SessionIntervalLogEntity
import com.pulsetimer.data.entity.SessionLogEntity
import com.pulsetimer.data.entity.TemplateEntity
import com.pulsetimer.service.TimerService
import kotlinx.coroutines.flow.Flow
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

/**
 * Суффикс имени копии интервала. Вынесен в константу, потому что то же значение
 * участвует в «откате» уже добавленного суффикса при повторном дублировании.
 */
private const val DUPLICATE_SUFFIX = " (копия)"

class TimerViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getDatabase(application).timerDao()

    val templates: StateFlow<List<TemplateEntity>> = dao.getAllTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _templatesLoaded = MutableStateFlow(false)

    /**
     * Первый снимок списка тренировок уже пришёл из базы.
     *
     * Нужен главному экрану: набор шагов обучения зависит от того, есть ли
     * тренировки, а до первого чтения `templates` пустой список — это ещё не
     * «тренировок нет», а «пока не знаем». Без этого признака тур, запущенный
     * сразу (например, по кнопке в настройках), показывал бы укороченный набор
     * без карточек и редактора.
     */
    val templatesLoaded: StateFlow<Boolean> = _templatesLoaded.asStateFlow()

    init {
        viewModelScope.launch {
            dao.getAllTemplates().first()
            _templatesLoaded.value = true
        }
    }

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
            val id = dao.insertTemplateWithNextSortOrder(
                TemplateEntity(name = name, description = description)
            )
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

    /**
     * Перемещение тренировки по списку на одну позицию: `-1` — выше, `+1` — ниже.
     *
     * Список из БД уже отсортирован «сверху вниз» (`sortOrder DESC`), поэтому
     * индексы в нём и есть визуальный порядок. После обмена нумерация
     * нормализуется в диапазон `0..n-1` — иначе значения росли бы при каждом
     * перемещении и рано или поздно переполнили бы Int.
     */
    fun moveTemplate(templateId: Long, direction: Int) {
        if (direction != -1 && direction != 1) return
        viewModelScope.launch {
            val orderedTemplates = dao.getAllTemplates().first().toMutableList()
            val currentIndex = orderedTemplates.indexOfFirst { it.id == templateId }
            val targetIndex = currentIndex + direction
            if (currentIndex == -1 || targetIndex !in orderedTemplates.indices) return@launch

            val movedTemplate = orderedTemplates.removeAt(currentIndex)
            orderedTemplates.add(targetIndex, movedTemplate)
            val lastOrder = orderedTemplates.lastIndex
            dao.reorderTemplates(
                orderedTemplates.mapIndexed { index, template ->
                    template.copy(sortOrder = lastOrder - index)
                }
            )
        }
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

    /**
     * Дублирование интервала внутри той же тренировки.
     *
     * Копия вставляется сразу под оригиналом и получает имя с суффиксом
     * «(копия)»; перед добавлением суффикса он снимается, иначе дубликат
     * дубликата превращался бы в «X (копия) (копия) (копия)».
     * Копируются все настройки: длительность, цвет, фон и мелодия.
     * После вставки порядковые номера нормализуются в `0..n-1`, чтобы в
     * `orderIndex` не оставалось «дыр» и дублей.
     */
    fun duplicateInterval(templateId: Long, intervalId: Long) {
        viewModelScope.launch {
            val intervals = dao.getIntervalsByTemplateId(templateId).first()
            val sourceIndex = intervals.indexOfFirst { it.id == intervalId }
            if (sourceIndex == -1) return@launch

            val source = intervals[sourceIndex]
            dao.insertInterval(
                source.copy(
                    id = 0L,
                    name = source.name.removeSuffix(DUPLICATE_SUFFIX) + DUPLICATE_SUFFIX,
                    orderIndex = sourceIndex + 1
                )
            )
            val updated = dao.getIntervalsByTemplateId(templateId).first()
            dao.reorderIntervals(
                updated.mapIndexed { index, interval -> interval.copy(orderIndex = index) }
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

    /**
     * Построчный лог интервалов конкретной сессии — для экрана деталей.
     *
     * Flow отдаётся как есть, без `stateIn`: подписка живёт ровно столько,
     * сколько открыт экран деталей, и кешировать между открытиями нечего.
     */
    fun sessionIntervalLogs(sessionLogId: Long): Flow<List<SessionIntervalLogEntity>> =
        dao.getSessionIntervalLogsBySessionId(sessionLogId)

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
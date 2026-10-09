package com.pulsetimer.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulsetimer.data.entity.SessionIntervalLogEntity
import com.pulsetimer.data.entity.SessionLogEntity
import com.pulsetimer.viewmodel.TimerViewModel

/**
 * Детальная история одной тренировки: сводка по сессии + построчный разбор
 * интервалов (сколько было запланировано и сколько реально отработано).
 *
 * Строки интервалов создаются на старте сессии, поэтому сюда попадают и
 * оборванные сессии: у незавершённых интервалов `actualSeconds` меньше
 * `plannedSeconds`, а у пропущенных стоит флаг `wasSkipped`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    logId: Long,
    onBack: () -> Unit,
    viewModel: TimerViewModel = viewModel()
) {
    val sessionLogs by viewModel.sessionLogs.collectAsStateWithLifecycle()
    val log = remember(sessionLogs, logId) { sessionLogs.firstOrNull { it.id == logId } }
    val intervalLogFlow = remember(logId) { viewModel.sessionIntervalLogs(logId) }
    val intervalLogs by intervalLogFlow.collectAsStateWithLifecycle(
        initialValue = emptyList<SessionIntervalLogEntity>()
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Детали тренировки") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // Небольшой отступ сверху: карточка сводки не должна прилипать
                // к тулбару.
                .padding(start = 16.dp, end = 16.dp, top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                if (log == null) {
                    Text(
                        text = "Запись не найдена — возможно, история была очищена.",
                        modifier = Modifier.padding(vertical = 24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    SessionSummaryCard(log)
                }
            }

            if (intervalLogs.isNotEmpty()) {
                item {
                    Text("Интервалы", style = MaterialTheme.typography.titleLarge)
                }
                items(intervalLogs, key = { intervalLog -> intervalLog.id }) { intervalLog ->
                    IntervalLogCard(intervalLog)
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SessionSummaryCard(log: SessionLogEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = log.templateName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = sessionStatusText(log),
                style = MaterialTheme.typography.bodyMedium,
                color = if (log.completedAt == null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
            Text(
                text = "Начало: ${formatTimestamp(log.startedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            log.completedAt?.let { completedAt ->
                Text(
                    text = "Окончание: ${formatTimestamp(completedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "Длительность: ${log.totalDurationSeconds} сек " +
                    "(план ${log.plannedDurationSeconds} сек)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Интервалов пройдено: ${log.completedIntervals} из ${log.totalIntervals}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (log.interrupted && log.lastIntervalIndex >= 0) {
                Text(
                    text = "Оборвано системой на интервале ${log.lastIntervalIndex + 1}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun IntervalLogCard(intervalLog: SessionIntervalLogEntity) {
    val accent = parseColor(intervalLog.colorHex)
    val fraction = if (intervalLog.plannedSeconds > 0) {
        (intervalLog.actualSeconds.toFloat() / intervalLog.plannedSeconds).coerceIn(0f, 1f)
    } else {
        0f
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(accent, CircleShape)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "${intervalLog.orderIndex + 1}. ${intervalLog.iconEmoji} " +
                        intervalLog.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (intervalLog.wasSkipped) {
                    Text(
                        text = "пропущен",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${intervalLog.actualSeconds} из ${intervalLog.plannedSeconds} сек",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Своя полоска вместо LinearProgressIndicator: у M3-индикатора в
            // проекте нет ни одного использования, а Box не тянет за собой
            // зависимость от версии compose-material3.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(accent)
                )
            }
        }
    }
}

/**
 * Текст статуса сессии.
 *
 * `completedAt != null` — тренировка дошла до конца штатно.
 * Иначе смотрим на `interrupted`: true — сервис был убит системой или
 * вытеснен новой тренировкой, false — пользователь нажал «Стоп».
 */
fun sessionStatusText(log: SessionLogEntity): String = when {
    log.completedAt != null -> "Тренировка завершена"
    log.interrupted -> "Прервана системой"
    else -> "Остановлена пользователем"
}

package com.pulsetimer.ui.screen

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pulsetimer.data.AppSettings
import com.pulsetimer.data.AppSettingsStore
import com.pulsetimer.data.entity.SessionLogEntity
import com.pulsetimer.viewmodel.TimerViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings by AppSettingsStore.settings.collectAsState()
    val viewModel: TimerViewModel = viewModel()
    val logs by viewModel.sessionLogs.collectAsState()
    var showClearHistoryConfirmation by remember { mutableStateOf(false) }
    var pendingDeleteLog by remember { mutableStateOf<SessionLogEntity?>(null) }
    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                AppSettingsStore.update { it.copy(musicUri = uri.toString()) }
            } catch (error: SecurityException) {
                Toast.makeText(context, "Не удалось сохранить доступ к аудиофайлу", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { SectionTitle("Звук и голос") }
            item {
                SettingSwitch(
                    title = "Звуковые сигналы",
                    checked = settings.soundEnabled,
                    onCheckedChange = { updateSettings { copy(soundEnabled = it) } }
                )
            }
            item {
                SettingSwitch(
                    title = "Голосовой помощник",
                    checked = settings.voiceEnabled,
                    onCheckedChange = { updateSettings { copy(voiceEnabled = it) } }
                )
            }
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text("Громкость сигналов: ${(settings.soundVolume * 100).toInt()}%")
                    Slider(
                        value = settings.soundVolume,
                        onValueChange = { volume ->
                            AppSettingsStore.update { it.copy(soundVolume = volume) }
                        },
                        valueRange = 0f..1f
                    )
                }
            }
            item {
                ChoiceCard(
                    title = "Звук сигнала",
                    choices = listOf("CLASSIC" to "Зуммер", "WHISTLE" to "Свисток", "GONG" to "Гонг"),
                    selected = settings.toneType,
                    onSelected = { updateSettings { copy(toneType = it) } }
                )
            }
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(
                        text = if (settings.musicUri == null) "Фоновая музыка не выбрана" else "Фоновая музыка выбрана",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { audioPicker.launch(arrayOf("audio/*")) }) {
                            Text("Выбрать аудио")
                        }
                        if (settings.musicUri != null) {
                            Button(
                                onClick = {
                                    AppSettingsStore.update { it.copy(musicUri = null) }
                                }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null)
                                Text("Удалить мелодию")
                            }
                        }
                    }
                }
            }

            item { SectionTitle("Тактильный отклик") }
            item {
                SettingSwitch(
                    title = "Вибрация",
                    checked = settings.vibrationEnabled,
                    onCheckedChange = { updateSettings { copy(vibrationEnabled = it) } }
                )
            }
            item {
                ChoiceCard(
                    title = "Профиль вибрации",
                    choices = listOf("SOFT" to "Мягкий", "SPORT" to "Спортивный", "EXTREME" to "Экстремальный"),
                    selected = settings.vibrationProfile,
                    onSelected = { updateSettings { copy(vibrationProfile = it) } }
                )
            }

            item { SectionTitle("Интерфейс и тема") }
            item {
                ChoiceCard(
                    title = "Тема приложения",
                    choices = listOf(
                        "LIGHT" to "Светлая",
                        "OLED" to "OLED",
                        "GRAY" to "Серая",
                        "CUSTOM" to "Своя палитра"
                    ),
                    selected = settings.theme,
                    onSelected = { updateSettings { copy(theme = it) } }
                )
            }
            if (settings.theme == "CUSTOM") {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Своя палитра цветов", style = MaterialTheme.typography.titleMedium)
                        PaletteField(
                            label = "Акцент",
                            value = settings.customPrimary,
                            onValueChange = { value -> AppSettingsStore.update { it.copy(customPrimary = value) } }
                        )
                        PaletteField(
                            label = "Фон",
                            value = settings.customBackground,
                            onValueChange = { value -> AppSettingsStore.update { it.copy(customBackground = value) } }
                        )
                        PaletteField(
                            label = "Карточки",
                            value = settings.customSurface,
                            onValueChange = { value -> AppSettingsStore.update { it.copy(customSurface = value) } }
                        )
                        Text(
                            "Введите HEX-цвет, например #9BB9A8",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item {
                SettingSwitch(
                    title = "Анимированные фоны",
                    subtitle = "Отключите для экономии батареи",
                    checked = settings.animatedBackgrounds,
                    onCheckedChange = { updateSettings { copy(animatedBackgrounds = it) } }
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SectionTitle("История тренировок")
                    if (logs.isNotEmpty()) {
                        TextButton(onClick = { showClearHistoryConfirmation = true }) {
                            Text("Очистить")
                        }
                    }
                }
            }
            if (logs.isEmpty()) {
                item {
                    Text(
                        text = "Пока нет завершенных тренировок.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(logs, key = SessionLogEntity::id) { log ->
                    SessionLogCard(log = log, onDelete = { pendingDeleteLog = log })
                }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    pendingDeleteLog?.let { log ->
        AlertDialog(
            onDismissRequest = { pendingDeleteLog = null },
            title = { Text("Удалить запись?") },
            text = { Text("Запись тренировки «${log.templateName}» будет удалена без возможности восстановления.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSessionLog(log)
                        pendingDeleteLog = null
                    }
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteLog = null }) { Text("Отмена") }
            }
        )
    }
    if (showClearHistoryConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearHistoryConfirmation = false },
            title = { Text("Очистить историю?") },
            text = { Text("Все ${logs.size} записей тренировок будут удалены без возможности восстановления.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearSessionHistory()
                        showClearHistoryConfirmation = false
                    }
                ) { Text("Очистить") }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryConfirmation = false }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleLarge
    )
}

@Composable
private fun SettingSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ChoiceCard(
    title: String,
    choices: List<Pair<String, String>>,
    selected: String,
    onSelected: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        choices.forEach { (value, label) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = selected == value, onClick = { onSelected(value) })
                Text(label)
            }
        }
    }
}

@Composable
private fun SessionLogCard(log: SessionLogEntity, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(log.templateName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Удалить запись тренировки",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Дата: ${formatTimestamp(log.startedAt)}", style = MaterialTheme.typography.bodyMedium)
            Text(
                if (log.completedAt == null) "Тренировка остановлена" else "Завершена",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            log.completedAt?.let {
                Text(
                    "Длительность: ${log.totalDurationSeconds} сек",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PaletteField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            val digits = input.filter { it.digitToIntOrNull(16) != null }.take(6)
            onValueChange(if (input.isEmpty()) "" else "#$digits")
        },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}


private fun updateSettings(transform: AppSettings.() -> AppSettings) {
    AppSettingsStore.update { it.transform() }
}

fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

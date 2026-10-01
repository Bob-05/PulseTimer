package com.pulsetimer.ui.screen

import android.content.Intent
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulsetimer.data.AppSettingsStore
import com.pulsetimer.data.entity.SessionLogEntity
import com.pulsetimer.viewmodel.TimerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onShowOnboarding: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenTerms: () -> Unit
) {
    val context = LocalContext.current
    val settings by AppSettingsStore.settings.collectAsState()
    val viewModel: TimerViewModel = viewModel()
    val logs by viewModel.sessionLogs.collectAsState()
    val scope = rememberCoroutineScope()
    var showClearHistoryConfirmation by remember { mutableStateOf(false) }
    var pendingDeleteLog by remember { mutableStateOf<SessionLogEntity?>(null) }
    var deletingLogId by remember { mutableStateOf<Long?>(null) }

    var notificationsEnabled by remember {
        mutableStateOf(
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        )
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsEnabled =
                    NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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

    var localVolume by remember(settings.soundVolume) {
        mutableFloatStateOf(settings.soundVolume)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { SectionTitle("Уведомления") }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (notificationsEnabled) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = null,
                                tint = if (notificationsEnabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onErrorContainer
                                }
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (notificationsEnabled)
                                        "Уведомления разрешены"
                                    else
                                        "Уведомления заблокированы",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (notificationsEnabled) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    }
                                )
                                Text(
                                    text = if (notificationsEnabled)
                                        "Во время тренировки прогресс и кнопки управления отображаются в шторке."
                                    else
                                        "Без разрешения вы не увидите прогресс и кнопки управления во время тренировки.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (notificationsEnabled) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    }
                                )
                            }
                        }
                        if (!notificationsEnabled) {
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    runCatching { context.startActivity(intent) }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Открыть настройки уведомлений")
                            }
                        }
                    }
                }
            }

            item { SectionTitle("Звук и голос") }
            item {
                SettingSwitch(
                    title = "Звуковые сигналы",
                    checked = settings.soundEnabled,
                    onCheckedChange = { value ->
                        AppSettingsStore.update { it.copy(soundEnabled = value) }
                    }
                )
            }
            item {
                SettingSwitch(
                    title = "Голосовой помощник",
                    checked = settings.voiceEnabled,
                    onCheckedChange = { value ->
                        AppSettingsStore.update { it.copy(voiceEnabled = value) }
                    }
                )
            }
            item {
                Column(modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)) {
                    Text("Громкость сигналов: ${(localVolume * 100).toInt()}%")
                    Slider(
                        value = localVolume,
                        onValueChange = { localVolume = it },
                        onValueChangeFinished = {
                            AppSettingsStore.update { it.copy(soundVolume = localVolume) }
                        },
                        valueRange = 0f..1f
                    )
                }
            }
            item {
                ChoiceCard(
                    title = "Звук сигнала",
                    choices = listOf(
                        "CLASSIC" to "Зуммер",
                        "WHISTLE" to "Свисток",
                        "GONG" to "Гонг",
                        "DOUBLE" to "Двойной импульс",
                        "DIGITAL" to "Цифровой сигнал",
                        "CHIME" to "Колокольчик"
                    ),
                    selected = settings.toneType,
                    onSelected = { value ->
                        AppSettingsStore.update { it.copy(toneType = value) }
                    }
                )
            }
            item {
                Column(modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)) {
                    Text(
                        text = if (settings.musicUri == null)
                            "Фоновая музыка не выбрана"
                        else "Фоновая музыка выбрана",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { audioPicker.launch(arrayOf("audio/*")) }) {
                            Text("Выбрать аудио")
                        }
                        if (settings.musicUri != null) {
                            Button(onClick = {
                                AppSettingsStore.update { it.copy(musicUri = null) }
                            }) {
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
                    onCheckedChange = { value ->
                        AppSettingsStore.update { it.copy(vibrationEnabled = value) }
                    }
                )
            }
            item {
                ChoiceCard(
                    title = "Профиль вибрации",
                    choices = listOf(
                        "SOFT" to "Мягкий",
                        "SPORT" to "Спортивный",
                        "EXTREME" to "Экстремальный"
                    ),
                    selected = settings.vibrationProfile,
                    onSelected = { value ->
                        AppSettingsStore.update { it.copy(vibrationProfile = value) }
                    }
                )
            }

            item { SectionTitle("Интерфейс и тема") }
            item {
                ChoiceCard(
                    title = "Тема приложения",
                    choices = listOf(
                        "LIGHT" to "Светлая",
                        "OLED" to "OLED",
                        "GRAY" to "Серая"
                    ),
                    selected = settings.theme,
                    onSelected = { value ->
                        AppSettingsStore.update { it.copy(theme = value) }
                    }
                )
            }
            item {
                SettingSwitch(
                    title = "Анимированные фоны",
                    subtitle = "Отключите для экономии батареи",
                    checked = settings.animatedBackgrounds,
                    onCheckedChange = { value ->
                        AppSettingsStore.update { it.copy(animatedBackgrounds = value) }
                    }
                )
            }
            item {
                ChoiceCard(
                    title = "Анимация смены интервалов",
                    choices = listOf(
                        "SLIDE" to "Слайд",
                        "FADE" to "Затухание",
                        "ZOOM" to "Масштаб",
                        "GLIDE" to "Скольжение",
                        "BOUNCE" to "Отскок",
                        "DEPTH" to "Глубина",
                        "SPRING_UP" to "Пружинный подъём"
                    ),
                    selected = settings.intervalAnimation,
                    onSelected = { value ->
                        AppSettingsStore.update { it.copy(intervalAnimation = value) }
                    }
                )
            }

            item { SectionTitle("Справка") }
            item {
                NavigationCard(
                    emoji = "📖",
                    title = "Обучение",
                    subtitle = "Повторно посмотреть инструкцию по работе с приложением",
                    onClick = onShowOnboarding
                )
            }

            item { SectionTitle("Документы") }
            item {
                NavigationCard(
                    emoji = "📄",
                    title = "Политика конфиденциальности",
                    subtitle = "Как приложение обращается с данными",
                    onClick = onOpenPrivacy
                )
            }
            item {
                NavigationCard(
                    emoji = "📜",
                    title = "Пользовательское соглашение",
                    subtitle = "Условия использования приложения",
                    onClick = onOpenTerms
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
                items(
                    items = logs,
                    key = SessionLogEntity::id
                ) { log ->
                    val isDeleting = deletingLogId == log.id
                    val scale by animateFloatAsState(
                        targetValue = if (isDeleting) 0.7f else 1f,
                        animationSpec = tween(durationMillis = 280),
                        label = "log_scale_${log.id}"
                    )
                    val alpha by animateFloatAsState(
                        targetValue = if (isDeleting) 0f else 1f,
                        animationSpec = tween(durationMillis = 280),
                        label = "log_alpha_${log.id}"
                    )
                    SessionLogCard(
                        log = log,
                        onDelete = { pendingDeleteLog = log },
                        modifier = Modifier
                            .animateItem()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                this.alpha = alpha
                            }
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    pendingDeleteLog?.let { log ->
        AlertDialog(
            onDismissRequest = { pendingDeleteLog = null },
            title = { Text("Удалить запись?") },
            text = {
                Text("Запись тренировки «${log.templateName}» будет удалена без возможности восстановления.")
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeleteLog = null
                    deletingLogId = log.id
                    scope.launch {
                        delay(280)
                        viewModel.deleteSessionLog(log)
                        deletingLogId = null
                    }
                }) { Text("Удалить") }
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
                TextButton(onClick = {
                    viewModel.clearSessionHistory()
                    showClearHistoryConfirmation = false
                }) { Text("Очистить") }
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
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
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 8.dp)) {
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
private fun NavigationCard(
    emoji: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(emoji, fontSize = 28.sp)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SessionLogCard(
    log: SessionLogEntity,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    log.templateName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
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

fun formatTimestamp(timestamp: Long): String {
    return DateFormat.format("dd.MM.yyyy HH:mm", Date(timestamp)).toString()
}
package com.pulsetimer.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulsetimer.data.AppSettingsStore
import com.pulsetimer.data.entity.SessionLogEntity
import com.pulsetimer.ui.component.CustomSignalEditorDialog
import com.pulsetimer.ui.component.SignalPreviewPlayer
import com.pulsetimer.viewmodel.TimerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Locale

/**
 * Состояние выбранного, но ещё не подтверждённого сигнала.
 *
 * @param uri URI файла.
 * @param initialStartMs начальное смещение (при редактировании существующего).
 * @param isReplacement true — пользователь выбрал новый файл в пикере.
 *   Тогда при отмене нужно вернуть persistable-доступ, а при сохранении —
 *   освободить доступ к прежнему URI.
 */
private data class PendingSignal(
    val uri: Uri,
    val initialStartMs: Int,
    val isReplacement: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onShowOnboarding: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenTerms: () -> Unit
) {
    val context = LocalContext.current
    val settings by AppSettingsStore.settings.collectAsStateWithLifecycle()
    val viewModel: TimerViewModel = viewModel()
    val logs by viewModel.sessionLogs.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showClearHistoryConfirmation by remember { mutableStateOf(false) }
    var pendingDeleteLog by remember { mutableStateOf<SessionLogEntity?>(null) }
    var deletingLogId by remember { mutableStateOf<Long?>(null) }
    var appSettingsExpanded by rememberSaveable { mutableStateOf(false) }
    var workoutSettingsExpanded by rememberSaveable { mutableStateOf(false) }
    var helpExpanded by rememberSaveable { mutableStateOf(false) }
    var historyExpanded by rememberSaveable { mutableStateOf(false) }

    var pendingSignal by remember { mutableStateOf<PendingSignal?>(null) }

    // Плеер превью: живёт вместе с экраном, dispose() в onDispose.
    val signalPreview = remember(context) { SignalPreviewPlayer(context) }
    DisposableEffect(signalPreview) {
        onDispose { signalPreview.dispose() }
    }

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
                val previous = settings.musicUri
                AppSettingsStore.update { it.copy(musicUri = uri.toString()) }
                releasePersistedUri(context, previous, keep = uri.toString())
            } catch (error: SecurityException) {
                Toast.makeText(context, "Не удалось сохранить доступ к аудиофайлу", Toast.LENGTH_LONG).show()
            }
        }
    }

    val signalPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            pendingSignal = PendingSignal(
                uri = uri,
                initialStartMs = 0,
                isReplacement = true
            )
        } catch (error: SecurityException) {
            Toast.makeText(
                context,
                "Не удалось сохранить доступ к аудиофайлу",
                Toast.LENGTH_LONG
            ).show()
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
                .padding(padding),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                CollapsibleSettingsGroup(
                    emoji = "⚙️",
                    title = "Настройки приложения",
                    subtitle = "Уведомления, внешний вид и анимации",
                    expanded = appSettingsExpanded,
                    onToggle = { appSettingsExpanded = !appSettingsExpanded }
                ) {
                    SectionTitle("Уведомления")
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (notificationsEnabled) {
                                MaterialTheme.colorScheme.surface
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

                    SectionTitle("Интерфейс и тема")
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
                    SettingSwitch(
                        title = "Анимированные фоны",
                        subtitle = "Отключите для экономии батареи",
                        checked = settings.animatedBackgrounds,
                        onCheckedChange = { value ->
                            AppSettingsStore.update { it.copy(animatedBackgrounds = value) }
                        }
                    )
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
            }

            item {
                CollapsibleSettingsGroup(
                    emoji = "🏋️",
                    title = "Настройки тренировок",
                    subtitle = "Звук, музыка и тактильный отклик",
                    expanded = workoutSettingsExpanded,
                    onToggle = { workoutSettingsExpanded = !workoutSettingsExpanded }
                ) {
                    SectionTitle("Звук и голос")
                    SettingSwitch(
                        title = "Звуковые сигналы",
                        checked = settings.soundEnabled,
                        onCheckedChange = { value ->
                            AppSettingsStore.update { it.copy(soundEnabled = value) }
                        }
                    )
                    SettingSwitch(
                        title = "Голосовой помощник",
                        checked = settings.voiceEnabled,
                        onCheckedChange = { value ->
                            AppSettingsStore.update { it.copy(voiceEnabled = value) }
                        }
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text("Громкость сигналов: ${(localVolume * 100).toInt()}%")
                        Slider(
                            value = localVolume,
                            onValueChange = { localVolume = it },
                            onValueChangeFinished = {
                                AppSettingsStore.update { it.copy(soundVolume = localVolume) }
                                val fresh = AppSettingsStore.settings.value
                                signalPreview.play(
                                    toneType = fresh.toneType,
                                    customUri = fresh.customSignalUri,
                                    customStartMs = fresh.customSignalStartMs,
                                    volume = fresh.soundVolume
                                )
                            },
                            valueRange = 0f..1f
                        )
                    }
                    ChoiceCard(
                        title = "Звук сигнала",
                        choices = listOf(
                            "CLASSIC" to "Зуммер",
                            "WHISTLE" to "Свисток",
                            "GONG" to "Гонг",
                            "DOUBLE" to "Двойной импульс",
                            "DIGITAL" to "Цифровой сигнал",
                            "CHIME" to "Колокольчик"
                        ) + if (settings.customSignalUri != null) {
                            listOf("CUSTOM" to "Свой сигнал")
                        } else {
                            emptyList()
                        },
                        selected = settings.toneType,
                        onSelected = { value ->
                            AppSettingsStore.update { it.copy(toneType = value) }
                            // Свежие значения — после update(), а не снапшот из композиции.
                            val fresh = AppSettingsStore.settings.value
                            signalPreview.play(
                                toneType = value,
                                customUri = fresh.customSignalUri,
                                customStartMs = fresh.customSignalStartMs,
                                volume = fresh.soundVolume
                            )
                        }
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = customSignalDescription(
                                settings.customSignalUri,
                                settings.customSignalStartMs
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { signalPicker.launch(arrayOf("audio/*")) }) {
                                Text(
                                    if (settings.customSignalUri == null)
                                        "Добавить свой сигнал"
                                    else
                                        "Заменить сигнал"
                                )
                            }
                            if (settings.customSignalUri != null) {
                                OutlinedButton(onClick = {
                                    val uriStr = settings.customSignalUri ?: return@OutlinedButton
                                    val parsed = runCatching { Uri.parse(uriStr) }.getOrNull()
                                        ?: return@OutlinedButton
                                    pendingSignal = PendingSignal(
                                        uri = parsed,
                                        initialStartMs = settings.customSignalStartMs,
                                        isReplacement = false
                                    )
                                }) {
                                    Text("Изменить фрагмент")
                                }
                            }
                        }
                        if (settings.customSignalUri != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(onClick = {
                                    val previous = settings.customSignalUri
                                    AppSettingsStore.update {
                                        it.copy(
                                            customSignalUri = null,
                                            customSignalStartMs = 0,
                                            toneType = if (it.toneType == "CUSTOM") "CLASSIC" else it.toneType
                                        )
                                    }
                                    releasePersistedUri(context, previous, keep = null)
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                    Text("Удалить сигнал")
                                }
                            }
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = if (settings.musicUri == null)
                                "Музыка тренировки по умолчанию не выбрана"
                            else
                                "Музыка тренировки по умолчанию выбрана",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { audioPicker.launch(arrayOf("audio/*")) }) {
                                Text("Выбрать аудио")
                            }
                            if (settings.musicUri != null) {
                                Button(onClick = {
                                    val previous = settings.musicUri
                                    AppSettingsStore.update { it.copy(musicUri = null) }
                                    releasePersistedUri(context, previous, keep = null)
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                    Text("Удалить мелодию")
                                }
                            }
                        }
                    }

                    SectionTitle("Тактильный отклик")
                    SettingSwitch(
                        title = "Вибрация",
                        checked = settings.vibrationEnabled,
                        onCheckedChange = { value ->
                            AppSettingsStore.update { it.copy(vibrationEnabled = value) }
                        }
                    )
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
            }

            item {
                CollapsibleSettingsGroup(
                    emoji = "📚",
                    title = "Справка",
                    subtitle = "Обучение и юридические документы",
                    expanded = helpExpanded,
                    onToggle = { helpExpanded = !helpExpanded }
                ) {
                    SectionTitle("Обучение")
                    NavigationCard(
                        emoji = "📖",
                        title = "Обучение",
                        subtitle = "Повторно посмотреть инструкцию по работе с приложением",
                        onClick = onShowOnboarding
                    )

                    SectionTitle("Документы")
                    NavigationCard(
                        emoji = "📄",
                        title = "Политика конфиденциальности",
                        subtitle = "Как приложение обращается с данными",
                        onClick = onOpenPrivacy
                    )
                    NavigationCard(
                        emoji = "📜",
                        title = "Пользовательское соглашение",
                        subtitle = "Условия использования приложения",
                        onClick = onOpenTerms
                    )
                }
            }

            item {
                CollapsibleSettingsGroup(
                    emoji = "📊",
                    title = "История тренировок",
                    subtitle = if (logs.isEmpty()) {
                        "Записей пока нет"
                    } else {
                        "Всего записей: ${logs.size}"
                    },
                    expanded = historyExpanded,
                    onToggle = { historyExpanded = !historyExpanded }
                ) {
                    if (logs.isEmpty()) {
                        Text(
                            text = "Пока нет завершенных тренировок.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Записи",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            TextButton(onClick = { showClearHistoryConfirmation = true }) {
                                Text("Очистить")
                            }
                        }
                        logs.forEach { log ->
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
                                modifier = Modifier.graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    this.alpha = alpha
                                }
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(4.dp)) }
        }
    }

    // Диалог-редактор пользовательского сигнала.
    pendingSignal?.let { pending ->
        CustomSignalEditorDialog(
            signalUri = pending.uri,
            initialStartMs = pending.initialStartMs,
            onSave = { startMs ->
                val previousUri = settings.customSignalUri
                AppSettingsStore.update {
                    it.copy(
                        customSignalUri = pending.uri.toString(),
                        customSignalStartMs = startMs,
                        toneType = "CUSTOM"
                    )
                }
                if (pending.isReplacement &&
                    previousUri != null &&
                    previousUri != pending.uri.toString()
                ) {
                    releasePersistedUri(context, previousUri, keep = pending.uri.toString())
                }
                pendingSignal = null
                // Сразу дать пользователю услышать, что получилось.
                val fresh = AppSettingsStore.settings.value
                signalPreview.play(
                    toneType = fresh.toneType,
                    customUri = fresh.customSignalUri,
                    customStartMs = fresh.customSignalStartMs,
                    volume = fresh.soundVolume
                )
            },
            onDismiss = {
                if (pending.isReplacement &&
                    settings.customSignalUri != pending.uri.toString()
                ) {
                    releasePersistedUri(context, pending.uri.toString(), keep = null)
                }
                pendingSignal = null
            },
            onPreviewError = { message ->
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        )
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

private fun customSignalDescription(uri: String?, startMs: Int): String {
    if (uri == null) {
        return "Можно выбрать собственный аудиофайл для сигнала интервала. " +
                "Если файл длиннее 1 секунды — выберите нужный фрагмент."
    }
    return if (startMs > 0) {
        String.format(
            Locale.getDefault(),
            "Свой сигнал добавлен. Используется фрагмент с %.1f сек, длительностью 1 сек.",
            startMs / 1000f
        )
    } else {
        "Свой аудиосигнал добавлен (обрезается до 1 секунды с начала файла)."
    }
}

/**
 * Освобождает persistable URI-разрешение, если оно больше не используется.
 */
private fun releasePersistedUri(
    context: Context,
    uriString: String?,
    keep: String?
) {
    if (uriString == null) return
    if (uriString == keep) return
    val parsed = runCatching { Uri.parse(uriString) }.getOrNull() ?: return
    runCatching {
        context.contentResolver.releasePersistableUriPermission(
            parsed,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }
}

@Composable
private fun CollapsibleSettingsGroup(
    emoji: String,
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(emoji, fontSize = 20.sp)
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                }
                val rotation by animateFloatAsState(
                    targetValue = if (expanded) 180f else 0f,
                    animationSpec = tween(
                        durationMillis = 320,
                        easing = FastOutSlowInEasing
                    ),
                    label = "chevron_rotation"
                )
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Свернуть" else "Развернуть",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.graphicsLayer { rotationZ = rotation }
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    expandFrom = Alignment.Top
                ) + fadeIn(
                    animationSpec = tween(
                        durationMillis = 220,
                        delayMillis = 60
                    )
                ),
                exit = shrinkVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    shrinkTowards = Alignment.Top
                ) + fadeOut(
                    animationSpec = tween(durationMillis = 160)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        choices.forEach { (value: String, label: String) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selected == value,
                    onClick = { onSelected(value) }
                )
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
        shape = RoundedCornerShape(16.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
            Text(
                "Дата: ${formatTimestamp(log.startedAt)}",
                style = MaterialTheme.typography.bodyMedium
            )
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
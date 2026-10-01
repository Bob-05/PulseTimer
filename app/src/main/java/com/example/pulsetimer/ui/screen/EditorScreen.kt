package com.pulsetimer.ui.screen

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulsetimer.data.entity.IntervalEntity
import com.pulsetimer.viewmodel.TimerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private enum class SaveButtonState { Idle, Saving, Success }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EditorScreen(
    viewModel: TimerViewModel = viewModel(),
    templateId: Long,
    onBack: () -> Unit
) {
    val selectedTemplate by viewModel.selectedTemplate.collectAsState()
    val intervals by viewModel.selectedTemplateIntervals.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember(templateId) { mutableStateOf("") }
    var description by remember(templateId) { mutableStateOf("") }
    var initialized by remember(templateId) { mutableStateOf(false) }
    var saveButtonState by remember { mutableStateOf(SaveButtonState.Idle) }

    LaunchedEffect(templateId) {
        viewModel.selectTemplate(templateId)
    }

    LaunchedEffect(selectedTemplate?.id) {
        val template = selectedTemplate
        if (!initialized && template != null) {
            name = template.name
            description = template.description
            initialized = true
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                selectedTemplate?.let {
                    viewModel.updateTemplate(
                        it.copy(backgroundType = "CUSTOM_IMAGE", backgroundValue = uri.toString())
                    )
                }
            } catch (error: SecurityException) {
                Toast.makeText(context, "Не удалось сохранить доступ к изображению", Toast.LENGTH_LONG).show()
            }
        }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                selectedTemplate?.let {
                    viewModel.updateTemplate(
                        it.copy(backgroundType = "VIDEO", backgroundValue = uri.toString())
                    )
                }
            } catch (error: SecurityException) {
                Toast.makeText(context, "Не удалось сохранить доступ к видео", Toast.LENGTH_LONG).show()
            }
        }
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                selectedTemplate?.let { viewModel.updateTemplate(it.copy(audioUri = uri.toString())) }
            } catch (error: SecurityException) {
                Toast.makeText(context, "Не удалось сохранить доступ к аудиофайлу", Toast.LENGTH_LONG).show()
            }
        }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    val intervalsListState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Редактор") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.clearSelectedTemplate()
                        onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                shadowElevation = 12.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SaveTemplateButton(
                        state = saveButtonState,
                        enabled = selectedTemplate != null,
                        onClick = {
                            val template = selectedTemplate ?: return@SaveTemplateButton
                            if (saveButtonState != SaveButtonState.Idle) return@SaveTemplateButton
                            scope.launch {
                                saveButtonState = SaveButtonState.Saving
                                viewModel.updateTemplate(
                                    template.copy(
                                        name = name.trim().ifEmpty { template.name },
                                        description = description.trim()
                                    )
                                )
                                delay(700)
                                saveButtonState = SaveButtonState.Success
                                delay(1600)
                                saveButtonState = SaveButtonState.Idle
                            }
                        }
                    )
                    FloatingActionButton(
                        onClick = { showAddDialog = true },
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Добавить интервал")
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            state = intervalsListState,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                selectedTemplate?.let { template ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("Параметры тренировки", style = MaterialTheme.typography.titleLarge)
                            Text("Значок тренировки", style = MaterialTheme.typography.titleMedium)
                            WorkoutEmojiPicker(
                                selected = template.iconEmoji,
                                onSelect = { emoji ->
                                    viewModel.updateTemplate(template.copy(iconEmoji = emoji))
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Название тренировки") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = description,
                                onValueChange = { description = it },
                                label = { Text("Описание") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                maxLines = 4
                            )
                            Text("Фон", style = MaterialTheme.typography.titleMedium)
                            Button(
                                onClick = { imagePicker.launch(arrayOf("image/*")) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (template.backgroundType == "CUSTOM_IMAGE")
                                        "Выбрать другое изображение"
                                    else "Выбрать изображение"
                                )
                            }
                            Button(
                                onClick = { videoPicker.launch(arrayOf("video/*")) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (template.backgroundType == "VIDEO")
                                        "Выбрать другое видео"
                                    else "Выбрать видеофон"
                                )
                            }
                            if (template.backgroundType != "COLOR") {
                                androidx.compose.material3.OutlinedButton(
                                    onClick = {
                                        viewModel.updateTemplate(
                                            template.copy(backgroundType = "COLOR", backgroundValue = "")
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Сбросить фон") }
                            }
                            Text("Музыка тренировки", style = MaterialTheme.typography.titleMedium)
                            Button(
                                onClick = { audioPicker.launch(arrayOf("audio/*")) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (template.audioUri == null)
                                        "Добавить мелодию"
                                    else "Заменить мелодию"
                                )
                            }
                            if (template.audioUri != null) {
                                androidx.compose.material3.OutlinedButton(
                                    onClick = {
                                        viewModel.updateTemplate(template.copy(audioUri = null))
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                    Text("Удалить мелодию")
                                }
                            }
                            Text("Вибрация шаблона", style = MaterialTheme.typography.titleMedium)
                            listOf(
                                0 to "Выключена",
                                1 to "Стандартная",
                                2 to "Интенсивная",
                                3 to "Нарастающая"
                            ).forEach { (patternId, label) ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = template.vibrationPatternId == patternId,
                                        onClick = {
                                            viewModel.updateTemplate(template.copy(vibrationPatternId = patternId))
                                        }
                                    )
                                    Text(label)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text("Интервалы", style = MaterialTheme.typography.titleLarge)
            }

            if (intervals.isEmpty()) {
                item {
                    Text(
                        "Интервалов пока нет. Добавьте фазы тренировки кнопкой +.",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(intervals, key = { it.id }) { interval ->
                    val itemDistance by remember(interval.id, intervalsListState) {
                        derivedStateOf {
                            val layout = intervalsListState.layoutInfo
                            val item = layout.visibleItemsInfo.firstOrNull { it.key == interval.id }
                            if (item == null) {
                                1f
                            } else {
                                val center = item.offset + item.size / 2f
                                val viewportCenter =
                                    (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
                                ((center - viewportCenter) / layout.viewportSize.height.coerceAtLeast(1))
                                    .absoluteValue
                                    .coerceIn(0f, 1f)
                            }
                        }
                    }
                    IntervalItem(
                        interval = interval,
                        onDelete = { viewModel.deleteInterval(interval) },
                        onUpdate = viewModel::updateInterval,
                        modifier = Modifier
                            .animateItem()
                            .graphicsLayer {
                                val scale = 1f - itemDistance * 0.08f
                                scaleX = scale
                                scaleY = scale
                                alpha = 1f - itemDistance * 0.25f
                            }
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    if (showAddDialog) {
        AddIntervalDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, duration, color ->
                viewModel.addInterval(templateId, name, duration, color)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun SaveTemplateButton(
    state: SaveButtonState,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val containerColor = when (state) {
        SaveButtonState.Success -> Color(0xFF2E7D32)
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = when (state) {
        SaveButtonState.Success -> Color.White
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    ExtendedFloatingActionButton(
        onClick = { if (enabled) onClick() },
        containerColor = containerColor,
        contentColor = contentColor,
        icon = {
            AnimatedContent(
                targetState = state,
                transitionSpec = {
                    (fadeIn(tween(180)) + scaleIn(tween(220), initialScale = 0.6f))
                        .togetherWith(fadeOut(tween(140)) + scaleOut(tween(180), targetScale = 0.6f))
                },
                label = "save_icon"
            ) { s ->
                when (s) {
                    SaveButtonState.Idle -> Icon(Icons.Default.Save, contentDescription = null)
                    SaveButtonState.Saving -> CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = contentColor
                    )
                    SaveButtonState.Success -> Icon(Icons.Default.Check, contentDescription = null)
                }
            }
        },
        text = {
            AnimatedContent(
                targetState = state,
                transitionSpec = {
                    (fadeIn(tween(180)) + scaleIn(tween(220), initialScale = 0.85f))
                        .togetherWith(fadeOut(tween(140)) + scaleOut(tween(180), targetScale = 0.85f))
                },
                label = "save_text"
            ) { s ->
                Text(
                    text = when (s) {
                        SaveButtonState.Idle -> "Сохранить"
                        SaveButtonState.Saving -> "Сохранение…"
                        SaveButtonState.Success -> "Сохранено!"
                    },
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    )
}

@Composable
fun IntervalItem(
    interval: IntervalEntity,
    onDelete: () -> Unit,
    onUpdate: (IntervalEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accent = parseColor(interval.colorHex)
    val surface = MaterialTheme.colorScheme.surface
    val shape = RoundedCornerShape(22.dp)

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                onUpdate(interval.copy(backgroundType = "CUSTOM_IMAGE", backgroundValue = uri.toString()))
            } catch (error: SecurityException) {
                Toast.makeText(context, "Не удалось сохранить доступ к изображению", Toast.LENGTH_LONG).show()
            }
        }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                onUpdate(interval.copy(backgroundType = "VIDEO", backgroundValue = uri.toString()))
            } catch (error: SecurityException) {
                Toast.makeText(context, "Не удалось сохранить доступ к видео", Toast.LENGTH_LONG).show()
            }
        }
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                onUpdate(interval.copy(audioUri = uri.toString()))
            } catch (error: SecurityException) {
                Toast.makeText(context, "Не удалось сохранить доступ к аудиофайлу", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ⚠️ Без Card и без shadow: Material 3 Card рисует резкую spot-shadow,
    // которая на Android 12+ оставляет прямоугольный «хвост» под скруглённой формой.
    // Используем Box + clip + background + border — визуально то же, но без артефактов.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                shape = shape
            )
    ) {
        // ✅ Градиент только в верхней части карточки (за заголовком),
        // фиксированной высоты. Под кнопками — плоский surface.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.30f),
                            accent.copy(alpha = 0.10f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Акцентная полоска слева
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(6.dp)
                .height(72.dp)
                .background(accent, RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(accent, accent.copy(alpha = 0.55f))
                            ),
                            CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        interval.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${interval.durationSeconds} сек",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Удалить интервал",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            androidx.compose.material3.OutlinedButton(
                onClick = { imagePicker.launch(arrayOf("image/*")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (interval.backgroundType == "CUSTOM_IMAGE")
                        "Заменить изображение"
                    else "Выбрать изображение"
                )
            }
            if (interval.backgroundType == "CUSTOM_IMAGE") {
                androidx.compose.material3.TextButton(
                    onClick = { onUpdate(interval.copy(backgroundType = "COLOR", backgroundValue = "")) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Сбросить изображение") }
            }
            androidx.compose.material3.OutlinedButton(
                onClick = { videoPicker.launch(arrayOf("video/*")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (interval.backgroundType == "VIDEO")
                        "Заменить видеофон"
                    else "Выбрать видеофон"
                )
            }
            if (interval.backgroundType == "VIDEO") {
                androidx.compose.material3.TextButton(
                    onClick = { onUpdate(interval.copy(backgroundType = "COLOR", backgroundValue = "")) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Сбросить видеофон") }
            }
            androidx.compose.material3.OutlinedButton(
                onClick = { audioPicker.launch(arrayOf("audio/*")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (interval.audioUri == null) "Добавить мелодию" else "Заменить мелодию"
                )
            }
            if (interval.audioUri != null) {
                androidx.compose.material3.TextButton(
                    onClick = { onUpdate(interval.copy(audioUri = null)) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Удалить мелодию") }
            }
        }
    }
}

@Composable
fun AddIntervalDialog(
    onDismiss: () -> Unit,
    onAdd: (String, Int, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf("#FF3B30") }

    val colors = listOf(
        "#FF3B30" to "Красный",
        "#007AFF" to "Синий",
        "#FFCC00" to "Желтый",
        "#34C759" to "Зеленый",
        "#AF52DE" to "Фиолетовый"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый интервал") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Длительность (сек)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Цвет фона: ${
                        colors.firstOrNull { it.first == selectedColor }?.second ?: "—"
                    }",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    colors.forEach { (hex, label) ->
                        val isSelected = selectedColor == hex
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(parseColor(hex))
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(
                                                width = 3.dp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                shape = CircleShape
                                            )
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable { selectedColor = hex },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Выбран",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val dur = duration.toIntOrNull() ?: 10
                    if (name.isNotBlank()) {
                        onAdd(name, dur, selectedColor)
                    }
                }
            ) {
                Text("Добавить")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
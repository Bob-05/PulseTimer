package com.pulsetimer.ui.screen

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulsetimer.data.entity.IntervalEntity
import com.pulsetimer.viewmodel.TimerViewModel
import kotlin.math.absoluteValue

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

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                selectedTemplate?.let {
                    viewModel.updateTemplate(it.copy(backgroundType = "CUSTOM_IMAGE", backgroundValue = uri.toString()))
                }
            } catch (error: SecurityException) {
                Toast.makeText(context, "Не удалось сохранить доступ к изображению", Toast.LENGTH_LONG).show()
            }
        }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                selectedTemplate?.let {
                    viewModel.updateTemplate(it.copy(backgroundType = "VIDEO", backgroundValue = uri.toString()))
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

    LaunchedEffect(templateId) {
        viewModel.selectTemplate(templateId)
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
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Добавить интервал")
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
                var name by remember(template.id) { mutableStateOf(template.name) }
                var description by remember(template.id) { mutableStateOf(template.description) }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
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
                                Text(if (template.backgroundType == "CUSTOM_IMAGE") "Выбрать другое изображение" else "Выбрать изображение")
                            }
                            Button(
                                onClick = { videoPicker.launch(arrayOf("video/*")) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (template.backgroundType == "VIDEO") "Выбрать другое видео" else "Выбрать видеофон")
                            }
                            if (template.backgroundType != "COLOR") {
                                androidx.compose.material3.OutlinedButton(
                                    onClick = {
                                        viewModel.updateTemplate(template.copy(backgroundType = "COLOR", backgroundValue = ""))
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Сбросить фон") }
                            }
                            Text("Музыка тренировки", style = MaterialTheme.typography.titleMedium)
                            Button(
                                onClick = { audioPicker.launch(arrayOf("audio/*")) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (template.audioUri == null) "Добавить мелодию" else "Заменить мелодию")
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
                            listOf(0 to "Выключена", 1 to "Стандартная", 2 to "Интенсивная", 3 to "Нарастающая")
                                .forEach { (patternId, label) ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        androidx.compose.material3.RadioButton(
                                            selected = template.vibrationPatternId == patternId,
                                            onClick = {
                                                viewModel.updateTemplate(template.copy(vibrationPatternId = patternId))
                                            }
                                        )
                                        Text(label)
                                    }
                                }
                            Button(
                                onClick = {
                                    viewModel.updateTemplate(template.copy(name = name, description = description))
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Сохранить изменения")
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
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
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
            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }

    if (showAddDialog) {
        AddIntervalDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, duration, color, icon ->
                viewModel.addInterval(templateId, name, duration, color, icon)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun IntervalItem(
    interval: IntervalEntity,
    onDelete: () -> Unit,
    onUpdate: (IntervalEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                onUpdate(interval.copy(backgroundType = "CUSTOM_IMAGE", backgroundValue = uri.toString()))
            } catch (error: SecurityException) {
                Toast.makeText(context, "Не удалось сохранить доступ к изображению", Toast.LENGTH_LONG).show()
            }
        }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                onUpdate(interval.copy(backgroundType = "VIDEO", backgroundValue = uri.toString()))
            } catch (error: SecurityException) {
                Toast.makeText(context, "Не удалось сохранить доступ к видео", Toast.LENGTH_LONG).show()
            }
        }
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                onUpdate(interval.copy(audioUri = uri.toString()))
            } catch (error: SecurityException) {
                Toast.makeText(context, "Не удалось сохранить доступ к аудиофайлу", Toast.LENGTH_LONG).show()
            }
        }
    }
    Card(
        modifier = modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(parseColor(interval.colorHex), CircleShape)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(interval.iconEmoji, style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(interval.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${interval.durationSeconds} сек",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("Значок интервала", style = MaterialTheme.typography.labelLarge)
                WorkoutEmojiPicker(
                    selected = interval.iconEmoji,
                    onSelect = { onUpdate(interval.copy(iconEmoji = it)) },
                    modifier = Modifier.fillMaxWidth()
                )
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
                Text(if (interval.backgroundType == "CUSTOM_IMAGE") "Заменить изображение" else "Выбрать изображение")
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
                Text(if (interval.backgroundType == "VIDEO") "Заменить видеофон" else "Выбрать видеофон")
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
                Text(if (interval.audioUri == null) "Добавить мелодию" else "Заменить мелодию")
            }
            if (interval.audioUri != null) {
                androidx.compose.material3.TextButton(
                    onClick = {
                        onUpdate(interval.copy(audioUri = null))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Удалить мелодию") }
            }
        }
    }
}

@Composable
fun AddIntervalDialog(
    onDismiss: () -> Unit,
    onAdd: (String, Int, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf("#FF3B30") }
    var selectedEmoji by remember { mutableStateOf("⏱️") }

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
                Text("Цвет фона", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    colors.forEach { (hex, label) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(parseColor(hex), CircleShape)
                                    .then(
                                        if (selectedColor == hex) {
                                            Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape)
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable { selectedColor = hex }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(label, fontSize = 10.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("Значок интервала", style = MaterialTheme.typography.labelLarge)
                WorkoutEmojiPicker(
                    selected = selectedEmoji,
                    onSelect = { selectedEmoji = it },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val dur = duration.toIntOrNull() ?: 10
                    if (name.isNotBlank()) {
                        onAdd(name, dur, selectedColor, selectedEmoji)
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

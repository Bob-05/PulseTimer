package com.pulsetimer.ui.screen

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulsetimer.data.entity.IntervalEntity
import com.pulsetimer.viewmodel.TimerViewModel

@OptIn(ExperimentalMaterial3Api::class)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            selectedTemplate?.let { template ->
                var name by remember(template.id) { mutableStateOf(template.name) }
                var description by remember(template.id) { mutableStateOf(template.description) }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название шаблона") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Описание") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("Персонализация тренировки", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { imagePicker.launch(arrayOf("image/*")) }) {
                        Text(if (template.backgroundType == "CUSTOM_IMAGE") "Изменить фон" else "Выбрать фон")
                    }
                    if (template.backgroundType != "COLOR") {
                        Button(onClick = {
                            viewModel.updateTemplate(template.copy(backgroundType = "COLOR", backgroundValue = ""))
                        }) { Text("Убрать фон") }
                    }
                }
                Button(onClick = { videoPicker.launch(arrayOf("video/*")) }) {
                    Text(if (template.backgroundType == "VIDEO") "Изменить видеофон" else "Выбрать видеофон")
                }
                Button(onClick = { audioPicker.launch(arrayOf("audio/*")) }) {
                    Text(if (template.audioUri == null) "Выбрать музыку" else "Изменить музыку")
                }
                if (template.audioUri != null) {
                    Text("Для тренировки выбрана своя музыка", style = MaterialTheme.typography.bodySmall)
                }
                Text("Паттерн вибрации", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(0 to "Выкл", 1 to "Стандарт", 2 to "Интенсивный", 3 to "Нарастающий")
                        .forEach { (patternId, label) ->
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    viewModel.updateTemplate(template.copy(vibrationPatternId = patternId))
                                }
                            ) { Text(label) }
                        }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        viewModel.updateTemplate(template.copy(name = name, description = description))
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Сохранить")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Интервалы",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (intervals.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Нет интервалов. Добавьте фазы тренировки.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(intervals, key = { it.id }) { interval ->
                        IntervalItem(
                            interval = interval,
                            onDelete = { viewModel.deleteInterval(interval) },
                            onUpdate = viewModel::updateInterval
                        )
                    }
                }
            }
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
fun IntervalItem(
    interval: IntervalEntity,
    onDelete: () -> Unit,
    onUpdate: (IntervalEntity) -> Unit
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
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(parseColor(interval.colorHex), CircleShape)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = interval.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${interval.durationSeconds} сек",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row {
                    androidx.compose.material3.TextButton(
                        onClick = { imagePicker.launch(arrayOf("image/*")) }
                    ) { Text(if (interval.backgroundType == "CUSTOM_IMAGE") "Сменить фон" else "Фон") }
                    if (interval.backgroundType == "CUSTOM_IMAGE") {
                        androidx.compose.material3.TextButton(
                            onClick = { onUpdate(interval.copy(backgroundType = "COLOR", backgroundValue = "")) }
                        ) { Text("Убрать") }
                    }
                }
                Row {
                    androidx.compose.material3.TextButton(
                        onClick = { videoPicker.launch(arrayOf("video/*")) }
                    ) { Text(if (interval.backgroundType == "VIDEO") "Сменить видеофон" else "Видеофон") }
                    if (interval.backgroundType == "VIDEO") {
                        androidx.compose.material3.TextButton(
                            onClick = { onUpdate(interval.copy(backgroundType = "COLOR", backgroundValue = "")) }
                        ) { Text("Убрать") }
                    }
                }
                Row {
                    androidx.compose.material3.TextButton(
                        onClick = { audioPicker.launch(arrayOf("audio/*")) }
                    ) { Text(if (interval.audioUri == null) "Музыка" else "Сменить музыку") }
                    if (interval.audioUri != null) {
                        androidx.compose.material3.TextButton(
                            onClick = { onUpdate(interval.copy(audioUri = null)) }
                        ) { Text("Убрать") }
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Удалить",
                    tint = MaterialTheme.colorScheme.error
                )
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
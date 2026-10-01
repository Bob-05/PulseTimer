package com.pulsetimer.ui.screen

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulsetimer.data.entity.TemplateEntity
import com.pulsetimer.service.TimerService
import com.pulsetimer.viewmodel.TimerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    viewModel: TimerViewModel = viewModel(),
    onStartClick: (Long, String) -> Unit,
    onEditClick: (Long) -> Unit,
    onSettingsClick: () -> Unit
) {
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val timerState by viewModel.timerState.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { templates.size })
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var deletingId by remember { mutableStateOf<Long?>(null) }

    val hasActiveWorkout =
        (timerState.isRunning || timerState.isPaused) &&
            !timerState.isFinished &&
            timerState.templateId > 0L

    LaunchedEffect(templates.size) {
        if (templates.isNotEmpty() && pagerState.currentPage >= templates.size) {
            pagerState.animateScrollToPage(templates.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PulseTimer") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Настройки")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (hasActiveWorkout) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                ActiveWorkoutCard(
                    state = timerState,
                    onResume = {
                        onStartClick(timerState.templateId, timerState.templateName)
                    },
                    onStop = { viewModel.stopTimer() }
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedButton(
                onClick = { viewModel.addTemplate("Новая тренировка", "Добавьте описание") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Добавить тренировку")
            }

            if (templates.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Нет шаблонов.\nНажмите + чтобы создать.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 32.dp),
                    pageSpacing = 16.dp
                ) { page ->
                    val template = templates.getOrNull(page) ?: return@HorizontalPager
                    val isDeleting = deletingId == template.id
                    val scale by animateFloatAsState(
                        targetValue = if (isDeleting) 0.7f else 1f,
                        animationSpec = tween(durationMillis = 300),
                        label = "template_scale_${template.id}"
                    )
                    val alpha by animateFloatAsState(
                        targetValue = if (isDeleting) 0f else 1f,
                        animationSpec = tween(durationMillis = 300),
                        label = "template_alpha_${template.id}"
                    )
                    TemplateCard(
                        template = template,
                        modifier = Modifier.graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                        },
                        onDelete = {
                            if (deletingId != null) return@TemplateCard
                            deletingId = template.id
                            scope.launch {
                                delay(300)
                                viewModel.deleteTemplate(template)
                                deletingId = null
                            }
                        },
                        onEdit = { onEditClick(template.id) }
                    )
                }

                Row(
                    modifier = Modifier.padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(templates.size) { iteration ->
                        val color = if (pagerState.currentPage == iteration) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.LightGray
                        }
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(color)
                                .size(12.dp)
                        )
                    }
                }

                val currentTemplate = templates.getOrNull(pagerState.currentPage)
                Button(
                    onClick = {
                        currentTemplate?.let { template ->
                            scope.launch {
                                if (!viewModel.hasIntervals(template.id)) {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "В тренировке «${template.name}» нет интервалов",
                                        actionLabel = "Редактировать",
                                        withDismissAction = true,
                                        duration = SnackbarDuration.Long
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        onEditClick(template.id)
                                    }
                                    return@launch
                                }
                                viewModel.selectTemplate(template.id)
                                viewModel.startTimer(template.id, template.name)
                                onStartClick(template.id, template.name)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 16.dp)
                        .height(64.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = "СТАРТ",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveWorkoutCard(
    state: TimerService.ServiceTimerState,
    onResume: () -> Unit,
    onStop: () -> Unit
) {
    val pulse = rememberInfiniteTransition(label = "active_workout")
    val dotAlpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onResume),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .graphicsLayer { alpha = if (state.isPaused) 0.5f else dotAlpha }
                        .background(
                            color = if (state.isPaused) Color(0xFF9E9E9E) else Color(0xFF4CAF50),
                            shape = CircleShape
                        )
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (state.isPaused) "Пауза" else "Активная тренировка",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = state.templateName.ifEmpty { "Тренировка" },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = state.currentIntervalName.ifEmpty { "…" },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(20.dp))
            Text(
                text = formatTime(state.timeRemainingSeconds),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Интервал ${state.currentIntervalIndex + 1} из ${state.totalIntervals}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onResume,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Вернуться", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onStop,
                    modifier = Modifier.height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Стоп")
                }
            }
        }
    }
}

/**
 * Высота карточки шаблона фиксирована, чтобы все страницы pager'а выглядели
 * одинаково независимо от длины описания. Слот под кнопку «Показать полностью»
 * зарезервирован всегда — даже если кнопка не показывается.
 */
private val CARD_HEIGHT = 380.dp
private const val CARD_DESC_MAX_LINES = 4

@Composable
fun TemplateCard(
    template: TemplateEntity,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showFullDescription by remember(template.id) { mutableStateOf(false) }
    var descriptionOverflows by remember(template.id) { mutableStateOf(false) }
    val description = template.description.trim()

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(CARD_HEIGHT),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = template.iconEmoji,
                    style = MaterialTheme.typography.displayMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = template.name,
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Описание: занимает всё оставшееся место, ограничено 4 строками.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    if (description.isEmpty()) {
                        Text(
                            text = "Без описания",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = CARD_DESC_MAX_LINES,
                            overflow = TextOverflow.Ellipsis,
                            onTextLayout = { result ->
                                descriptionOverflows = result.hasVisualOverflow
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Зарезервированный слот под кнопку «Показать полностью».
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (descriptionOverflows) {
                        TextButton(onClick = { showFullDescription = true }) {
                            Text(
                                text = "Показать полностью",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Редактировать")
                    }
                    Spacer(modifier = Modifier.width(16.dp))
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
    }

    if (showFullDescription) {
        AlertDialog(
            onDismissRequest = { showFullDescription = false },
            title = {
                Text(
                    text = template.name,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = description.ifEmpty { "Без описания" },
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showFullDescription = false }) {
                    Text("Закрыть")
                }
            }
        )
    }
}
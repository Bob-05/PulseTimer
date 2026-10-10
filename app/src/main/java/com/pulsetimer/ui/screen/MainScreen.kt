package com.pulsetimer.ui.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.runtime.snapshotFlow
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
import com.pulsetimer.data.AppSettingsStore
import com.pulsetimer.data.entity.TemplateEntity
import com.pulsetimer.service.TimerService
import com.pulsetimer.ui.tutorial.TutorialController
import com.pulsetimer.ui.tutorial.TutorialScrollPlan
import com.pulsetimer.ui.tutorial.TutorialScreenTargets
import com.pulsetimer.ui.tutorial.TutorialSteps
import com.pulsetimer.ui.tutorial.TutorialTargetRegistry
import com.pulsetimer.ui.tutorial.TutorialTargets
import com.pulsetimer.ui.tutorial.TutorialTrigger
import com.pulsetimer.ui.tutorial.markTourCompleted
import com.pulsetimer.ui.tutorial.tutorialTarget
import com.pulsetimer.viewmodel.TimerViewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    tutorial: TutorialController,
    viewModel: TimerViewModel = viewModel(),
    onStartClick: (Long, String) -> Unit,
    onEditClick: (Long) -> Unit,
    onSettingsClick: () -> Unit
) {
    val settings by AppSettingsStore.settings.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val templatesLoaded by viewModel.templatesLoaded.collectAsStateWithLifecycle()

    // Набор шагов зависит от того, есть ли тренировки: без них показывать
    // редактор и запуск нечего. Список приходит из базы асинхронно, поэтому
    // обучение стартует только после первого снимка (см. templatesLoaded ниже) —
    // иначе тур успевал бы начаться с укороченным набором без карточек.
    val hasWorkouts = templates.isNotEmpty()
    val steps = remember(hasWorkouts) {
        TutorialSteps.guidedTour(hasWorkouts = hasWorkouts)
    }

    // Обучение стартует автоматически после онбординга и по запросу из
    // «Настроек». Запрос — одноразовый: флаг сбрасываем по завершении, иначе
    // обучение возвращалось бы при каждой перерисовке главного экрана.
    TutorialTrigger(
        controller = tutorial,
        steps = steps,
        // Ждём загрузку списка: запрошенный из «Настроек» тур иначе стартовал бы
        // до того, как известно, есть ли тренировки, и часть шагов потерялась бы.
        enabled = templatesLoaded && (
            settings.tutorialRequested ||
                (!settings.tutorialCompleted && settings.onboardingCompleted)
            ),
        prefix = TutorialTargets.PREFIX_MAIN,
        // Тур помечает пройденными только те экраны, которые успел показать:
        // без тренировок он не доходит до редактора и запуска, а подсказки по
        // ним покажет первый заход на соответствующий экран.
        onCompleted = {
            AppSettingsStore.update { current -> current.markTourCompleted(steps) }
        },
        // Первый показ ждёт, пока закончится появление контента, иначе
        // подсветка «дрожит» вместе с анимацией входа главного экрана.
        // Запрошенное из «Настроек» обучение показываем без задержки.
        startDelayMillis = if (settings.tutorialRequested) 0L else 600L
    )

    MainScreenScaffold(
        viewModel = viewModel,
        tutorialTargets = tutorial.targets,
        onStartClick = onStartClick,
        onEditClick = onEditClick,
        onSettingsClick = onSettingsClick
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun MainScreenScaffold(
    viewModel: TimerViewModel,
    tutorialTargets: TutorialTargetRegistry,
    onStartClick: (Long, String) -> Unit,
    onEditClick: (Long) -> Unit,
    onSettingsClick: () -> Unit
) {
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val timerState by viewModel.timerState.collectAsStateWithLifecycle()
    val recentlyAddedTemplateId by viewModel.recentlyAddedTemplateId.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { templates.size })
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var deletingId by remember { mutableStateOf<Long?>(null) }

    // id карточки, для которой УЖЕ завершён скролл и можно запускать
    // анимацию появления. Пока null — карточки, добавленные «только что»,
    // остаются невидимыми (см. инициализацию Animatable ниже).
    var readyToAnimateId by remember { mutableStateOf<Long?>(null) }

    val hasActiveWorkout =
        (timerState.isRunning || timerState.isPaused) &&
                !timerState.isFinished &&
                timerState.templateId > 0L

    /**
     * Запуск тренировки, которая открыта в пейджере. Обучение «нажимает» ту же
     * кнопку за пользователя, поэтому и код здесь один: проверка интервалов,
     * выбор шаблона, старт сервиса и переход на экран отсчёта.
     */
    val startTemplate: (TemplateEntity) -> Unit = { template ->
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

    // Обучение ведёт пользователя через те же элементы, что и он сам: перед
    // показом шага пейджер листается к первой карточке, а «Далее» на шагах-
    // переходах открывает редактор, настройки или запускает тренировку.
    val firstCardPlan = remember(pagerState) {
        TutorialScrollPlan(
            // Прокрутка фиксированной длительности: обучение ждёт её окончания, а
            // пружина по умолчанию растягивает паузу на несколько страниц.
            prepare = {
                if (pagerState.pageCount > 0) {
                    pagerState.animateScrollToPage(
                        page = 0,
                        animationSpec = tween(durationMillis = 240)
                    )
                }
            }
        )
    }
    TutorialScreenTargets(
        registry = tutorialTargets,
        plans = mapOf(
            TutorialTargets.CARD to firstCardPlan,
            TutorialTargets.CARD_ACTIONS to firstCardPlan,
            TutorialTargets.CARD_EDIT to firstCardPlan,
            TutorialTargets.START to firstCardPlan
        ),
        actions = mapOf(
            TutorialTargets.SETTINGS_ICON to onSettingsClick,
            TutorialTargets.CARD_EDIT to {
                templates.firstOrNull()?.let { template -> onEditClick(template.id) }
            },
            TutorialTargets.START to {
                templates.getOrNull(pagerState.currentPage)?.let(startTemplate)
            }
        )
    )

    LaunchedEffect(templates.size) {
        if (templates.isNotEmpty() && pagerState.currentPage >= templates.size) {
            pagerState.animateScrollToPage(templates.size - 1)
        }
    }

    // Последовательная оркестрация: дождаться появления шаблона
    // в СКОМПОНОВАННОМ списке → дать pager-у measure/layout, чтобы
    // его внутренний currentPage отразил сохранение визуальной позиции
    // по key → плавно пролистать → только затем разрешить карточке
    // проиграть анимацию появления.
    LaunchedEffect(Unit) {
        viewModel.recentlyAddedTemplateId
            .filterNotNull()
            .collect { newId ->
                // Ждём именно composed-состояние, а не StateFlow: значение
                // StateFlow обновляется раньше, чем UI перекомпонуется,
                // и animateScrollToPage может уйти в no-op по устаревшему
                // currentPage.
                val index = snapshotFlow { templates.indexOfFirst { it.id == newId } }
                    .filter { it >= 0 }
                    .first()

                // Даём Compose measure/layout: при использовании `key`
                // pager сохраняет визуальную позицию существующих карточек,
                // а внутренний currentPage пересчитывается в layout-фазе.
                delay(50L)

                pagerState.animateScrollToPage(index)
                readyToAnimateId = newId
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PulseTimer") },
                actions = {
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier.tutorialTarget(
                            tutorialTargets,
                            TutorialTargets.SETTINGS_ICON
                        )
                    ) {
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
                    .height(52.dp)
                    .tutorialTarget(tutorialTargets, TutorialTargets.ADD_TEMPLATE),
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
                    pageSpacing = 16.dp,
                    // Ключ по id сохраняет визуальную позицию существующих
                    // страниц при вставке/удалении элементов списка.
                    key = { page -> templates.getOrNull(page)?.id ?: page }
                ) { page ->
                    val template = templates.getOrNull(page) ?: return@HorizontalPager
                    val isDeleting = deletingId == template.id
                    val deleteScale by animateFloatAsState(
                        targetValue = if (isDeleting) 0.7f else 1f,
                        animationSpec = tween(durationMillis = 300),
                        label = "template_delete_scale_${template.id}"
                    )
                    val deleteAlpha by animateFloatAsState(
                        targetValue = if (isDeleting) 0f else 1f,
                        animationSpec = tween(durationMillis = 300),
                        label = "template_delete_alpha_${template.id}"
                    )

                    // Флаг «только что добавленная» фиксируем на момент первой
                    // композиции карточки: если бы читали значение flow-а на
                    // каждой рекомпозиции, сброс флага после анимации заставил
                    // бы карточку переинициализировать Animatable и «мигнуть».
                    val isNewlyAdded = remember(template.id) {
                        viewModel.recentlyAddedTemplateId.value == template.id
                    }
                    val entryScale = remember(template.id) {
                        Animatable(if (isNewlyAdded) 0.85f else 1f)
                    }
                    val entryAlpha = remember(template.id) {
                        Animatable(if (isNewlyAdded) 0f else 1f)
                    }

                    // Ждём сигнала readyToAnimateId == template.id — он приходит
                    // уже ПОСЛЕ завершения скролла. snapshotFlow.first не отменяет
                    // эффект при изменении readyToAnimateId для другой карточки,
                    // поэтому быстрое двойное добавление отработает корректно.
                    LaunchedEffect(template.id) {
                        if (!isNewlyAdded) return@LaunchedEffect
                        snapshotFlow { readyToAnimateId }
                            .first { it == template.id }
                        coroutineScope {
                            launch {
                                entryScale.animateTo(
                                    targetValue = 1f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                            launch {
                                entryAlpha.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(durationMillis = 320)
                                )
                            }
                        }
                        viewModel.clearRecentlyAddedTemplate(template.id)
                    }

                    // Цели обучения ставим только на первую карточку: у остальных
                    // страниц координаты перетирали бы подсветку друг друга,
                    // а обучение всё равно листает пейджер к первой.
                    val isTutorialCard = page == 0
                    val cardTarget = if (isTutorialCard) {
                        Modifier.tutorialTarget(tutorialTargets, TutorialTargets.CARD)
                    } else {
                        Modifier
                    }
                    val actionsTarget = if (isTutorialCard) {
                        Modifier.tutorialTarget(
                            tutorialTargets,
                            TutorialTargets.CARD_ACTIONS
                        )
                    } else {
                        Modifier
                    }
                    // Карандаш: обучение «нажимает» его, чтобы открыть редактор.
                    val editTarget = if (isTutorialCard) {
                        Modifier.tutorialTarget(tutorialTargets, TutorialTargets.CARD_EDIT)
                    } else {
                        Modifier
                    }

                    TemplateCard(
                        template = template,
                        canMoveUp = page > 0,
                        canMoveDown = page < templates.lastIndex,
                        onMoveUp = { viewModel.moveTemplate(template.id, -1) },
                        onMoveDown = { viewModel.moveTemplate(template.id, +1) },
                        actionsModifier = actionsTarget,
                        editModifier = editTarget,
                        modifier = Modifier
                            .then(cardTarget)
                            .graphicsLayer {
                            val combinedScale = deleteScale * entryScale.value
                            scaleX = combinedScale
                            scaleY = combinedScale
                            this.alpha = deleteAlpha * entryAlpha.value
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
                    onClick = { currentTemplate?.let(startTemplate) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 16.dp)
                        .height(64.dp)
                        .tutorialTarget(tutorialTargets, TutorialTargets.START),
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
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Интервал ${state.currentIntervalIndex + 1} из ${state.totalIntervals}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))
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
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    actionsModifier: Modifier = Modifier,
    editModifier: Modifier = Modifier,
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
                // Стрелки перемещения — крайние позиции недоступны, а не
                // скрыты: так подпись места не «прыгает» при перелистывании
                // и понятно, что порядок вообще можно менять.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(actionsModifier),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            contentDescription = "Переместить тренировку выше"
                        )
                    }
                    IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "Переместить тренировку ниже"
                        )
                    }
                    IconButton(onClick = onEdit, modifier = editModifier) {
                        Icon(Icons.Default.Edit, contentDescription = "Редактировать")
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
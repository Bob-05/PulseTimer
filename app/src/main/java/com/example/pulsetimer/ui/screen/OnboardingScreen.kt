package com.pulsetimer.ui.screen

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Тип визуализации на странице онбординга.
 * Вместо одного эмодзи показываем мини-сцену из Compose-примитивов —
 * так пользователю понятнее, о чём именно идёт речь.
 */
private enum class OnboardingVisual {
    Welcome,
    TemplateCreation,
    IntervalSetup,
    WorkoutRun,
    SettingsShowcase
}

private data class OnboardingPage(
    val title: String,
    val description: String,
    val bullets: List<String>,
    val accent: Color,
    val visual: OnboardingVisual
)

private val onboardingPages = listOf(
    OnboardingPage(
        title = "Добро пожаловать в PulseTimer",
        description = "Интервальный таймер для тренировок с полностью локальным хранением данных.",
        bullets = listOf(
            "Работает без интернета",
            "Без рекламы, подписок и встроенных покупок",
            "Данные остаются только на устройстве"
        ),
        accent = Color(0xFF007AFF),
        visual = OnboardingVisual.Welcome
    ),
    OnboardingPage(
        title = "Создавайте шаблоны",
        description = "На главном экране нажмите «Добавить тренировку» — основа готова за секунды.",
        bullets = listOf(
            "Выберите эмодзи и название",
            "Добавьте описание для себя",
            "В комплекте уже есть «Табата» и «Круговая»"
        ),
        accent = Color(0xFF34C759),
        visual = OnboardingVisual.TemplateCreation
    ),
    OnboardingPage(
        title = "Настраивайте интервалы",
        description = "Добавляйте фазы, редактируйте существующие и меняйте их порядок. Изменения сохраняются автоматически.",
        bullets = listOf(
            "Настраивайте название, длительность и цвет фазы",
            "Перемещайте интервалы в нужной последовательности",
            "Выбирайте фон и музыку для тренировки или отдельной фазы"
        ),
        accent = Color(0xFFFF9500),
        visual = OnboardingVisual.IntervalSetup
    ),
    OnboardingPage(
        title = "Запускайте тренировку",
        description = "Нажмите «СТАРТ» — таймер продолжит работать даже в фоне и при выключенном экране.",
        bullets = listOf(
            "Управление из шторки уведомлений",
            "Голосовые подсказки и вибрация",
            "Свайп влево/вправо между интервалами"
        ),
        accent = Color(0xFFFF3B30),
        visual = OnboardingVisual.WorkoutRun
    ),
    OnboardingPage(
        title = "Настройте под себя",
        description = "Звук, голос, вибрация, тема и анимации — всё регулируется в одном месте.",
        bullets = listOf(
            "4 профиля вибрации и 3 звука сигнала",
            "Голосовые подсказки на системном TTS",
            "История тренировок сохраняется автоматически"
        ),
        accent = Color(0xFFAF52DE),
        visual = OnboardingVisual.SettingsShowcase
    )
)

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    onSkip: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { onboardingPages.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == onboardingPages.size - 1
    val isFirstPage = pagerState.currentPage == 0

    val progress by animateFloatAsState(
        targetValue = (pagerState.currentPage + 1f) / onboardingPages.size,
        animationSpec = tween(durationMillis = 300),
        label = "onboarding_progress"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Спасает от наложения на статус-бар, вырез камеры и навигацию.
            // Верхний бар (с «Пропустить») уезжает ниже — кнопка всегда доступна.
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // ——— Верхний бар: шаг + «Пропустить» ———
        // Находится ВНУТРИ Column → ничем не перекрывается,
        // в отличие от прежней версии, где TextButton лежал под Column в Box.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Шаг ${pagerState.currentPage + 1} из ${onboardingPages.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onSkip) {
                Text(
                    text = "Пропустить",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // ——— Прогресс-бар ———
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
        )

        // ——— Содержимое страниц ———
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            OnboardingPageContent(page = onboardingPages[page])
        }

        // ——— Точки-индикаторы ———
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(onboardingPages.size) { index ->
                val isActive = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .clip(CircleShape)
                        .background(
                            if (isActive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                        .size(if (isActive) 10.dp else 8.dp)
                )
            }
        }

        // ——— Кнопки навигации ———
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!isFirstPage) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "Назад",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                }
            }
            Button(
                onClick = {
                    if (isLastPage) {
                        onComplete()
                    } else {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                modifier = Modifier
                    .weight(if (isFirstPage) 1f else 2f)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = if (isLastPage) "Начать тренировки" else "Далее",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        OnboardingVisualBox(visual = page.visual, accent = page.accent)

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = page.description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Мини-список ключевых пунктов — делает страницу содержательнее.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            page.bullets.forEach { bullet ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(page.accent)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = bullet,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

/**
 * Центральная визуализация страницы.
 *
 * Раньше здесь был просто эмодзи в кружке. Сейчас — мини-сцена из
 * Compose-примитивов, которая показывает то, о чём говорит страница:
 * карточка тренировки, чипы интервалов, таймер или список настроек.
 * Плюс лёгкая пульсация для живости.
 */
@Composable
private fun OnboardingVisualBox(
    visual: OnboardingVisual,
    accent: Color
) {
    val pulse = rememberInfiniteTransition(label = "onboarding_pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Box(
        modifier = Modifier
            .size(200.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.35f),
                        accent.copy(alpha = 0.06f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(150.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            when (visual) {
                OnboardingVisual.Welcome -> WelcomeVisual()
                OnboardingVisual.TemplateCreation -> TemplateVisual()
                OnboardingVisual.IntervalSetup -> IntervalVisual()
                OnboardingVisual.WorkoutRun -> TimerVisual(accent)
                OnboardingVisual.SettingsShowcase -> SettingsVisual(accent)
            }
        }
    }
}

@Composable
private fun WelcomeVisual() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("⏱️", fontSize = 56.sp)
        Text(
            text = "PulseTimer",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun TemplateVisual() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("🏋️", fontSize = 40.sp)
        Text(
            text = "Табата",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "20/10 × 8",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun IntervalVisual() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        IntervalChip("Работа", "20с", Color(0xFFFF3B30))
        IntervalChip("Отдых", "10с", Color(0xFF007AFF))
        IntervalChip("Работа", "20с", Color(0xFFFF3B30))
    }
}

@Composable
private fun IntervalChip(name: String, duration: String, color: Color) {
    Row(
        modifier = Modifier
            .width(108.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.25f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = name,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
        Text(
            text = duration,
            fontSize = 10.sp,
            color = color
        )
    }
}

@Composable
private fun TimerVisual(accent: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "03:45",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.3f))
            )
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.3f))
            )
        }
    }
}

@Composable
private fun SettingsVisual(accent: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SettingsRow("Звук", true, accent)
        SettingsRow("Голос", true, accent)
        SettingsRow("Вибрация", false, accent)
    }
}

@Composable
private fun SettingsRow(label: String, checked: Boolean, accent: Color) {
    Row(
        modifier = Modifier.width(108.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Box(
            modifier = Modifier
                .width(26.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(
                    if (checked) accent
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                )
        )
    }
}
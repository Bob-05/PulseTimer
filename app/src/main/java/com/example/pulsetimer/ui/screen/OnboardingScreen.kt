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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

data class OnboardingPage(
    val emoji: String,
    val title: String,
    val description: String,
    val accent: Color,
    val icon: ImageVector
)

private val onboardingPages = listOf(
    OnboardingPage(
        emoji = "⏱️",
        title = "Добро пожаловать в PulseTimer",
        description = "Интервальный таймер для тренировок. Создавайте шаблоны, настраивайте фазы работы и отдыха, следите за прогрессом.",
        accent = Color(0xFF007AFF),
        icon = Icons.Default.PlayArrow
    ),
    OnboardingPage(
        emoji = "🏋️",
        title = "Создавайте шаблоны",
        description = "На главном экране нажмите «Добавить тренировку». Выберите эмодзи, задайте название и описание — основа готова.",
        accent = Color(0xFF34C759),
        icon = Icons.Default.Add
    ),
    OnboardingPage(
        emoji = "✏️",
        title = "Настраивайте интервалы",
        description = "В редакторе добавляйте интервалы: работа, отдых, разминка. Задайте длительность, цвет, фоновое изображение, видео и музыку.",
        accent = Color(0xFFFF9500),
        icon = Icons.Default.Edit
    ),
    OnboardingPage(
        emoji = "▶️",
        title = "Запускайте тренировку",
        description = "Нажмите «СТАРТ» — таймер продолжит работать даже в фоне. Свайпайте влево/вправо для переключения интервалов.",
        accent = Color(0xFFFF3B30),
        icon = Icons.Default.PlayArrow
    ),
    OnboardingPage(
        emoji = "⚙️",
        title = "Настройте под себя",
        description = "В настройках включите голосовые подсказки, вибрацию, выберите звук сигнала, тему и анимации. История тренировок сохраняется автоматически.",
        accent = Color(0xFFAF52DE),
        icon = Icons.Default.Settings
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TextButton(
            onClick = onSkip,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Text("Пропустить", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                OnboardingPageContent(page = onboardingPages[page])
            }

            Row(
                modifier = Modifier.padding(vertical = 24.dp),
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
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = if (isLastPage) "Начать тренировки" else "Далее",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            page.accent.copy(alpha = 0.35f),
                            page.accent.copy(alpha = 0.08f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(page.accent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(page.emoji, fontSize = 64.sp)
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = page.description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 24.sp
        )
    }
}
package com.pulsetimer

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pulsetimer.data.AppSettingsStore
import com.pulsetimer.ui.navigation.Screen
import com.pulsetimer.ui.screen.EditorScreen
import com.pulsetimer.ui.screen.ExecutionScreen
import com.pulsetimer.ui.screen.MainScreen
import com.pulsetimer.ui.screen.OnboardingScreen
import com.pulsetimer.ui.screen.SettingsScreen
import com.pulsetimer.ui.theme.PulseTimerTheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* результат не важен: сервис продолжит работать в любом случае,
         но карточка в шторке появится только при granted = true */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppSettingsStore.initialize(this)
        requestNotificationPermissionIfNeeded()
        setContent {
            val settings by AppSettingsStore.settings.collectAsState()
            PulseTimerTheme(themeName = settings.theme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val startDestination = if (settings.onboardingCompleted) {
                        Screen.Main.route
                    } else {
                        Screen.Onboarding.route
                    }

                    NavHost(
                        navController = navController,
                        startDestination = startDestination
                    ) {
                        composable(Screen.Onboarding.route) {
                            OnboardingScreen(
                                onComplete = {
                                    finishOnboarding(navController) {
                                        AppSettingsStore.update {
                                            it.copy(onboardingCompleted = true)
                                        }
                                    }
                                },
                                onSkip = {
                                    finishOnboarding(navController) {
                                        AppSettingsStore.update {
                                            it.copy(onboardingCompleted = true)
                                        }
                                    }
                                }
                            )
                        }

                        composable(Screen.Main.route) {
                            MainScreen(
                                onStartClick = { templateId, templateName ->
                                    navController.navigate(
                                        "${Screen.Execution.route}/$templateId/${Uri.encode(templateName)}"
                                    )
                                },
                                onEditClick = { templateId ->
                                    navController.navigate("${Screen.Editor.route}/$templateId")
                                },
                                onSettingsClick = {
                                    navController.navigate(Screen.Settings.route)
                                }
                            )
                        }

                        composable(
                            route = "${Screen.Execution.route}/{templateId}/{templateName}",
                            // ⚠️ Явно задаём короткий transition.
                            // Дефолтный fade в Navigation Compose = 700 мс, за это время
                            // оба экрана рендерятся одновременно → фризы.
                            enterTransition = { fadeIn(tween(180)) },
                            exitTransition = { fadeOut(tween(150)) },
                            popEnterTransition = { fadeIn(tween(180)) },
                            popExitTransition = { fadeOut(tween(150)) }
                        ) { backStackEntry ->
                            val templateId = backStackEntry.arguments
                                ?.getString("templateId")?.toLongOrNull() ?: 0L
                            val templateName = Uri.decode(
                                backStackEntry.arguments?.getString("templateName") ?: ""
                            )
                            ExecutionScreen(
                                templateId = templateId,
                                templateName = templateName,
                                onFinish = {
                                    navController.navigate(Screen.Main.route) {
                                        popUpTo(Screen.Main.route) { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable(
                            route = "${Screen.Editor.route}/{templateId}"
                        ) { backStackEntry ->
                            val templateId = backStackEntry.arguments
                                ?.getString("templateId")?.toLongOrNull() ?: 0L
                            EditorScreen(
                                templateId = templateId,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(Screen.Settings.route) {
                            SettingsScreen(
                                onBack = { navController.popBackStack() },
                                onShowOnboarding = {
                                    navController.navigate(Screen.Onboarding.route)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * На Android 13+ (API 33+) для показа ЛЮБЫХ уведомлений — включая
     * уведомление foreground-сервиса — требуется runtime-разрешение
     * POST_NOTIFICATIONS. Без него уведомление молча отбрасывается:
     * сервис работает, но пользователь не видит ни карточки, ни кнопок
     * «Пауза / Пропустить / Стоп» в шторке.
     *
     * Повторный вызов безопасен: если пользователь уже отказал навсегда,
     * система просто вернёт false без показа диалога. Такому пользователю
     * поможет карточка статуса уведомлений в Настройках.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/**
 * Завершение онбординга.
 *
 *  - первый запуск: стек = [Onboarding], previousBackStackEntry == null →
 *    navigate(Main) с очисткой Onboarding;
 *  - вызов из настроек: стек = [Main, Settings, Onboarding] →
 *    popBackStack() возвращает в Settings, второго Main не создаётся.
 */
private fun finishOnboarding(
    navController: NavHostController,
    persist: () -> Unit
) {
    persist()
    if (navController.previousBackStackEntry == null) {
        navController.navigate(Screen.Main.route) {
            popUpTo(Screen.Onboarding.route) { inclusive = true }
        }
    } else {
        navController.popBackStack()
    }
}
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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pulsetimer.data.AppSettingsStore
import com.pulsetimer.ui.navigation.Screen
import com.pulsetimer.ui.screen.EditorScreen
import com.pulsetimer.ui.screen.ExecutionScreen
import com.pulsetimer.ui.screen.LegalConsentScreen
import com.pulsetimer.ui.screen.LegalDocumentScreen
import com.pulsetimer.ui.screen.LaunchAnimationScreen
import com.pulsetimer.ui.screen.MainScreen
import com.pulsetimer.ui.screen.OnboardingScreen
import com.pulsetimer.ui.screen.SettingsScreen
import com.pulsetimer.ui.theme.PulseTimerTheme

class MainActivity : ComponentActivity() {

    private var launchAnimationShown = false

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* результат не важен: сервис продолжит работать в любом случае,
         но карточка в шторке появится только при granted = true */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        launchAnimationShown = savedInstanceState?.getBoolean(
            STATE_LAUNCH_ANIMATION_SHOWN
        ) ?: false
        enableEdgeToEdge()
        AppSettingsStore.initialize(this)
        setContent {
            // saveable — при повороте экрана анимация появления не повторяется.
            var showAppContent by rememberSaveable { mutableStateOf(false) }
            var showLaunchAnimation by remember {
                mutableStateOf(!launchAnimationShown)
            }
            if (showLaunchAnimation) {
                launchAnimationShown = true
                LaunchAnimationScreen {
                    launchAnimationShown = true
                    showLaunchAnimation = false
                }
                return@setContent
            }

            // Запускается ровно один раз — на первом кадре дерева после сплеша.
            LaunchedEffect(Unit) {
                showAppContent = true
            }

            val density = LocalDensity.current
            // graphicsLayer.translationY работает в пикселях, поэтому dp → px
            // конвертируем один раз.
            val initialOffsetPx = remember(density) {
                with(density) { 20.dp.toPx() }
            }

            val contentAlpha by animateFloatAsState(
                targetValue = if (showAppContent) 1f else 0f,
                animationSpec = tween(
                    durationMillis = 700,
                    easing = FastOutSlowInEasing
                ),
                label = "app_content_alpha"
            )
            val contentScale by animateFloatAsState(
                targetValue = if (showAppContent) 1f else 0.94f,
                animationSpec = tween(
                    durationMillis = 800,
                    easing = FastOutSlowInEasing
                ),
                label = "app_content_scale"
            )
            val contentOffsetY by animateFloatAsState(
                targetValue = if (showAppContent) 0f else initialOffsetPx,
                animationSpec = tween(
                    durationMillis = 800,
                    easing = FastOutSlowInEasing
                ),
                label = "app_content_offset_y"
            )
            // Blur 6dp → 0 за 400 мс. На API < 31 Modifier.blur — no-op,
            // на новых версиях даёт эффект «наведения резкости».
            val contentBlur by animateFloatAsState(
                targetValue = if (showAppContent) 0f else 6f,
                animationSpec = tween(
                    durationMillis = 400,
                    easing = FastOutSlowInEasing
                ),
                label = "app_content_blur"
            )

            val settings by AppSettingsStore.settings.collectAsStateWithLifecycle()

            // Разрешение на уведомления запрашиваем ТОЛЬКО после того,
            // как пользователь явно принял политику и соглашение.
            LaunchedEffect(settings.legalConsentAccepted) {
                if (settings.legalConsentAccepted) {
                    requestNotificationPermissionIfNeeded()
                }
            }

            PulseTimerTheme(themeName = settings.theme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Фон Surface всегда непрозрачный — анимируется только
                    // контент, поэтому нет вспышек window background.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(contentBlur.dp)
                            .graphicsLayer {
                                alpha = contentAlpha
                                scaleX = contentScale
                                scaleY = contentScale
                                translationY = contentOffsetY
                            }
                    ) {
                        val navController = rememberNavController()

                        // startDestination фиксируем на момент первой композиции.
                        // Иначе Navigation Compose пересоздаст граф при смене
                        // флага согласия и сбросит стек.
                        val startDestination = remember {
                            val initial = AppSettingsStore.settings.value
                            when {
                                !initial.legalConsentAccepted -> Screen.LegalConsent.route
                                !initial.onboardingCompleted -> Screen.Onboarding.route
                                else -> Screen.Main.route
                            }
                        }

                        NavHost(
                            navController = navController,
                            startDestination = startDestination
                        ) {
                            composable(Screen.LegalConsent.route) {
                                LegalConsentScreen(
                                    onAccept = {
                                        AppSettingsStore.update {
                                            it.copy(legalConsentAccepted = true)
                                        }
                                        val next =
                                            if (AppSettingsStore.settings.value.onboardingCompleted) {
                                                Screen.Main.route
                                            } else {
                                                Screen.Onboarding.route
                                            }
                                        navController.navigate(next) {
                                            popUpTo(Screen.LegalConsent.route) { inclusive = true }
                                        }
                                    },
                                    onDecline = { finish() },
                                    onOpenPrivacy = {
                                        navController.navigate(
                                            Screen.LegalDocument.routeFor(
                                                Screen.LegalDocument.TYPE_PRIVACY
                                            )
                                        )
                                    },
                                    onOpenTerms = {
                                        navController.navigate(
                                            Screen.LegalDocument.routeFor(
                                                Screen.LegalDocument.TYPE_TERMS
                                            )
                                        )
                                    }
                                )
                            }

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
                                    },
                                    onOpenPrivacy = {
                                        navController.navigate(
                                            Screen.LegalDocument.routeFor(
                                                Screen.LegalDocument.TYPE_PRIVACY
                                            )
                                        )
                                    },
                                    onOpenTerms = {
                                        navController.navigate(
                                            Screen.LegalDocument.routeFor(
                                                Screen.LegalDocument.TYPE_TERMS
                                            )
                                        )
                                    }
                                )
                            }

                            composable(
                                route = "${Screen.LegalDocument.route}/{${Screen.LegalDocument.ARG_TYPE}}"
                            ) { backStackEntry ->
                                val type = backStackEntry.arguments
                                    ?.getString(Screen.LegalDocument.ARG_TYPE)
                                    ?: Screen.LegalDocument.TYPE_PRIVACY
                                LegalDocumentScreen(
                                    type = type,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_LAUNCH_ANIMATION_SHOWN, launchAnimationShown)
        super.onSaveInstanceState(outState)
    }

    /**
     * На Android 13+ (API 33+) для показа уведомлений foreground-сервиса
     * требуется runtime-разрешение POST_NOTIFICATIONS. Запрашиваем его
     * только после того, как пользователь явно принял согласие с документами.
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

private const val STATE_LAUNCH_ANIMATION_SHOWN = "launch_animation_shown"

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
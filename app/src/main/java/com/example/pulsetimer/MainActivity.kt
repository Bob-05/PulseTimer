package com.pulsetimer

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pulsetimer.data.AppSettingsStore
import com.pulsetimer.service.TimerService
import com.pulsetimer.ui.navigation.Screen
import com.pulsetimer.ui.screen.EditorScreen
import com.pulsetimer.ui.screen.ExecutionScreen
import com.pulsetimer.ui.screen.MainScreen
import com.pulsetimer.ui.screen.SettingsScreen
import com.pulsetimer.ui.theme.PulseTimerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppSettingsStore.initialize(this)
        setContent {
            val settings by AppSettingsStore.settings.collectAsState()
            PulseTimerTheme(themeName = settings.theme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    // Авто-возврат к активной тренировке при запуске приложения
                    LaunchedEffect(Unit) {
                        val s = TimerService.state.value
                        if (s.isRunning && !s.isFinished && s.templateId > 0L) {
                            val current = navController.currentBackStackEntry?.destination?.route
                            val execRoute = "${Screen.Execution.route}/{templateId}/{templateName}"
                            if (current != execRoute) {
                                navController.navigate(
                                    "${Screen.Execution.route}/${s.templateId}/${Uri.encode(s.templateName)}"
                                ) {
                                    launchSingleTop = true
                                }
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = Screen.Main.route
                    ) {
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
                            route = "${Screen.Execution.route}/{templateId}/{templateName}"
                        ) { backStackEntry ->
                            val templateName = Uri.decode(
                                backStackEntry.arguments?.getString("templateName") ?: ""
                            )
                            ExecutionScreen(
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
                            val templateId = backStackEntry.arguments?.getString("templateId")?.toLongOrNull() ?: 0L
                            EditorScreen(
                                templateId = templateId,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(Screen.Settings.route) {
                            SettingsScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
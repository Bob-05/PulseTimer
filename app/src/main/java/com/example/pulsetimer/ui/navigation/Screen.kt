package com.pulsetimer.ui.navigation

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Main : Screen("main")
    data object Execution : Screen("execution")
    data object Editor : Screen("editor")
    data object Settings : Screen("settings")
}
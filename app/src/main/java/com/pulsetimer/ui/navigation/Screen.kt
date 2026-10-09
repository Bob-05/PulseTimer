package com.pulsetimer.ui.navigation

sealed class Screen(val route: String) {
    data object LegalConsent : Screen("legal_consent")
    data object Onboarding : Screen("onboarding")
    data object Main : Screen("main")
    data object Execution : Screen("execution")
    data object Editor : Screen("editor")
    data object Settings : Screen("settings")

    /**
     * Детальный просмотр одной записи истории: `session_detail/{logId}`.
     * Список интервалов внутри сессии лежит в отдельной таблице, поэтому
     * деталям нужен собственный экран, а не раскрытие карточки.
     */
    data object SessionDetail : Screen("session_detail") {
        const val ARG_LOG_ID = "logId"
        fun routeFor(logId: Long): String = "$route/$logId"
    }

    /**
     * Полноэкранный просмотрщик юридического документа.
     * Маршрут: `legal_document/{type}`, где type = "privacy" | "terms".
     */
    data object LegalDocument : Screen("legal_document") {
        const val ARG_TYPE = "type"
        const val TYPE_PRIVACY = "privacy"
        const val TYPE_TERMS = "terms"
        fun routeFor(type: String): String = "$route/$type"
    }
}
package com.pulsetimer.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

private val GrayColorScheme = darkColorScheme(
    primary = Color(0xFFB0BEC5),
    secondary = Color(0xFF90A4AE),
    tertiary = Color(0xFFCFD8DC),
    background = Color(0xFF202124),
    surface = Color(0xFF303134),
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun PulseTimerTheme(
    themeName: String = "OLED",
    content: @Composable () -> Unit
) {
    val selectedTheme = themeName
    val colorScheme = when (selectedTheme) {
        "LIGHT" -> LightColorScheme
        "GRAY" -> GrayColorScheme
        else -> DarkColorScheme.copy(
            background = Color(0xFF000000),
            surface = Color(0xFF1C1C1E),
            onBackground = Color.White,
            onSurface = Color.White,
            primary = Color(0xFF34C759),
            secondary = Color(0xFFFF9500)
        )
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                selectedTheme == "LIGHT"
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
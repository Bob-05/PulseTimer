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
    primary = Color(0xFF9BB9A8),
    secondary = Color(0xFFB9A7C9),
    tertiary = Color(0xFFD0B79A),
    background = Color(0xFF17191A),
    surface = Color(0xFF242728),
    surfaceVariant = Color(0xFF303536)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF557967),
    secondary = Color(0xFF756482),
    tertiary = Color(0xFF846A4E),
    background = Color(0xFFF6F5F1),
    surface = Color(0xFFFFFEFB),
    surfaceVariant = Color(0xFFECEBE5)
)

private val GrayColorScheme = darkColorScheme(
    primary = Color(0xFFB3C0B7),
    secondary = Color(0xFFC0B5C8),
    tertiary = Color(0xFFD0C0AD),
    background = Color(0xFF252626),
    surface = Color(0xFF303232),
    surfaceVariant = Color(0xFF3A3D3D),
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun PulseTimerTheme(
    themeName: String = "OLED",
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeName) {
        "LIGHT" -> LightColorScheme
        "GRAY" -> GrayColorScheme
        else -> DarkColorScheme.copy(
            background = Color(0xFF000000),
            surface = Color(0xFF1C1C1E),
            onBackground = Color.White,
            onSurface = Color.White,
            primary = Color(0xFFA6C4B1),
            secondary = Color(0xFFD2B48C)
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                themeName == "LIGHT"
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
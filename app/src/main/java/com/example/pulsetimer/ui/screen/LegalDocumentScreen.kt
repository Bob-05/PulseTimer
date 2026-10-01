package com.pulsetimer.ui.screen

import android.content.Intent
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import com.pulsetimer.ui.navigation.Screen
import com.pulsetimer.util.MarkdownRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalDocumentScreen(
    type: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val title = when (type) {
        Screen.LegalDocument.TYPE_TERMS -> "Пользовательское соглашение"
        else -> "Политика конфиденциальности"
    }
    val assetName = when (type) {
        Screen.LegalDocument.TYPE_TERMS -> "legal/TERMS_OF_USE.md"
        else -> "legal/PRIVACY_POLICY.md"
    }
    val githubUrl = when (type) {
        Screen.LegalDocument.TYPE_TERMS ->
            "https://github.com/Bob-05/PulseTimer/blob/master/TERMS_OF_USE.md"
        else ->
            "https://github.com/Bob-05/PulseTimer/blob/master/PRIVACY_POLICY.md"
    }

    // Читаем и парсим Markdown в IO-диспетчере, чтобы не блокировать UI.
    val html by produceState<String?>(initialValue = null, assetName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val md = context.assets.open(assetName)
                    .bufferedReader()
                    .use { it.readText() }
                MarkdownRenderer.toHtml(md)
            }.getOrElse { error ->
                "<p>Не удалось загрузить документ: ${error.message}</p>"
            }
        }
    }

    val bg = MaterialTheme.colorScheme.background
    val surface = MaterialTheme.colorScheme.surface
    val onBg = MaterialTheme.colorScheme.onBackground
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, githubUrl.toUri())
                            )
                        }
                    }) {
                        Icon(
                            Icons.Default.OpenInNew,
                            contentDescription = "Открыть на GitHub"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val currentHtml = html
            if (currentHtml == null) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                HtmlViewer(
                    html = currentHtml,
                    backgroundColor = bg,
                    surfaceColor = surface,
                    textColor = onBg,
                    mutedColor = onSurfaceVariant,
                    accentColor = primary,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun HtmlViewer(
    html: String,
    backgroundColor: Color,
    surfaceColor: Color,
    textColor: Color,
    mutedColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val document = remember(html, backgroundColor, surfaceColor, textColor, mutedColor, accentColor) {
        buildHtmlDocument(
            body = html,
            bg = backgroundColor.toHex(),
            surface = surfaceColor.toHex(),
            text = textColor.toHex(),
            muted = mutedColor.toHex(),
            accent = accentColor.toHex()
        )
    }
    var lastLoaded by remember { mutableStateOf<String?>(null) }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                // Никакого сетевого доступа, никакого JS — только наш локальный HTML.
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.loadsImagesAutomatically = false
                settings.domStorageEnabled = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                setBackgroundColor(backgroundColor.toArgb())
            }
        },
        update = { webView ->
            // Обновляем фон при смене темы — но HTML перезагружаем только если он
            // действительно изменился, чтобы не мигало и не сбрасывало позицию.
            webView.setBackgroundColor(backgroundColor.toArgb())
            if (lastLoaded != document) {
                webView.loadDataWithBaseURL(null, document, "text/html", "utf-8", null)
                lastLoaded = document
            }
        }
    )
}

private fun buildHtmlDocument(
    body: String,
    bg: String,
    surface: String,
    text: String,
    muted: String,
    accent: String
): String = """
<!DOCTYPE html>
<html lang="ru">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<style>
  html, body {
    background: $bg;
    color: $text;
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', sans-serif;
    font-size: 15px;
    line-height: 1.55;
    margin: 0;
    padding: 16px 18px 48px 18px;
    word-wrap: break-word;
    overflow-wrap: break-word;
  }
  h1 { font-size: 22px; margin: 24px 0 12px; color: $text; }
  h2 { font-size: 18px; margin: 22px 0 10px; color: $text; }
  h3 { font-size: 16px; margin: 18px 0 8px; color: $text; }
  h4, h5, h6 { font-size: 15px; margin: 14px 0 6px; color: $text; }
  p { margin: 10px 0; }
  ul { padding-left: 22px; margin: 10px 0; }
  li { margin: 4px 0; }
  hr { border: none; border-top: 1px solid $muted; opacity: 0.4; margin: 22px 0; }
  a { color: $accent; text-decoration: none; word-break: break-all; }
  code { background: $surface; padding: 2px 6px; border-radius: 4px; font-size: 13px; }
  pre { background: $surface; padding: 12px; border-radius: 8px; overflow-x: auto; font-size: 13px; }
  pre code { background: transparent; padding: 0; }
  table { border-collapse: collapse; margin: 12px 0; width: 100%; font-size: 14px; }
  th, td { border: 1px solid $muted; padding: 8px 10px; text-align: left; vertical-align: top; }
  th { background: $surface; font-weight: 600; }
  strong { color: $text; font-weight: 700; }
  blockquote { border-left: 3px solid $muted; margin: 12px 0; padding: 4px 12px; color: $muted; }
</style>
</head>
<body>
$body
</body>
</html>
""".trimIndent()

private fun Color.toHex(): String {
    val argb = toArgb()
    return "#%02X%02X%02X".format(
        (argb shr 16) and 0xFF,
        (argb shr 8) and 0xFF,
        argb and 0xFF
    )
}
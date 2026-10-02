package com.pulsetimer.ui.component

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Та же длительность, что и CUSTOM_SIGNAL_MAX_DURATION_MS в TimerService.
 * Дублирование осознанное: UI-компонент не должен зависеть от сервиса.
 */
private const val PREVIEW_DURATION_MS = 1_000L

/**
 * Диалог подтверждения пользовательского сигнала.
 *
 * Если исходный файл длиннее 1 секунды — показывает слайдер, которым
 * можно выбрать, с какой секунды начинать воспроизведение. Итоговый
 * фрагмент всегда длится [PREVIEW_DURATION_MS]. Та же логика применяется
 * при реальном воспроизведении в TimerService (seekTo + авто-стоп).
 *
 * @param signalUri URI выбранного файла.
 * @param initialStartMs начальное смещение (для «изменить фрагмент» без смены файла).
 * @param onSave вызывается с итоговым startMs.
 * @param onDismiss отмена.
 */
@Composable
fun CustomSignalEditorDialog(
    signalUri: Uri,
    initialStartMs: Int,
    onSave: (startMs: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val displayName = remember(signalUri) {
        queryDisplayName(context, signalUri)
            ?: signalUri.lastPathSegment
            ?: signalUri.toString()
    }

    // Длительность читаем один раз в IO-диспетчере.
    var durationMs by remember(signalUri) { mutableStateOf<Int?>(null) }
    LaunchedEffect(signalUri) {
        durationMs = withContext(Dispatchers.IO) {
            readAudioDurationMs(context, signalUri)
        }
    }

    val maxStartMs = remember(durationMs) {
        val d = durationMs ?: 0
        (d - PREVIEW_DURATION_MS).coerceAtLeast(0L).toInt()
    }

    var startMs by remember(signalUri) {
        mutableIntStateOf(initialStartMs.coerceAtLeast(0))
    }
    // Если сохранившееся значение выходит за границы нового файла — подрезаем.
    LaunchedEffect(maxStartMs) {
        if (startMs > maxStartMs) startMs = maxStartMs
    }

    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    fun stopPreview() {
        val current = player
        player = null
        if (current == null) return
        current.setOnPreparedListener(null)
        current.setOnSeekCompleteListener(null)
        current.setOnCompletionListener(null)
        current.setOnErrorListener(null)
        runCatching { if (current.isPlaying) current.stop() }
        runCatching { current.reset() }
        runCatching { current.release() }
    }

    fun startPreview() {
        stopPreview()
        val previewStartMs = startMs.coerceAtLeast(0)
        val newPlayer = MediaPlayer()
        player = newPlayer
        try {
            newPlayer.setDataSource(context, signalUri)
            newPlayer.setVolume(1f, 1f)
            newPlayer.setOnPreparedListener { prepared ->
                if (player !== prepared) return@setOnPreparedListener
                if (previewStartMs > 0) {
                    prepared.setOnSeekCompleteListener { seeked ->
                        seeked.setOnSeekCompleteListener(null)
                        if (player !== seeked) return@setOnSeekCompleteListener
                        runCatching { seeked.start() }
                    }
                    runCatching {
                        prepared.seekTo(
                            previewStartMs.toLong(),
                            MediaPlayer.SEEK_CLOSEST
                        )
                    }.onFailure {
                        prepared.setOnSeekCompleteListener(null)
                        runCatching { prepared.start() }
                    }
                } else {
                    runCatching { prepared.start() }
                }
            }
            newPlayer.setOnCompletionListener { stopPreview() }
            newPlayer.setOnErrorListener { _, _, _ -> stopPreview(); true }
            newPlayer.prepareAsync()
        } catch (_: Exception) {
            stopPreview()
        }
    }

    // Авто-стоп превью через 1 сек — зеркалит поведение TimerService.
    LaunchedEffect(player) {
        if (player == null) return@LaunchedEffect
        delay(PREVIEW_DURATION_MS)
        stopPreview()
    }

    // Освобождаем плеер, если пользователь ушёл с экрана с открытым диалогом.
    DisposableEffect(Unit) { onDispose { stopPreview() } }

    val isPlaying = player != null
    val showSlider = (durationMs ?: 0) > PREVIEW_DURATION_MS

    AlertDialog(
        onDismissRequest = { stopPreview(); onDismiss() },
        title = { Text("Свой сигнал") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                val durationText = durationMs?.let { formatMs(it) } ?: "…"
                Text(
                    text = "Длительность файла: $durationText. " +
                            "В тренировке сигнал звучит не дольше " +
                            formatMs(PREVIEW_DURATION_MS.toInt()) + ".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (showSlider) {
                    Text(
                        text = "Фрагмент: ${formatMs(startMs)} – " +
                                formatMs(startMs + PREVIEW_DURATION_MS.toInt()) +
                                " из $durationText",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = startMs.toFloat(),
                        onValueChange = { raw ->
                            startMs = raw.toInt().coerceIn(0, maxStartMs)
                        },
                        valueRange = 0f..maxStartMs.toFloat(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedButton(
                    onClick = { if (isPlaying) stopPreview() else startPreview() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isPlaying) "Остановить"
                        else "Прослушать (${formatMs(PREVIEW_DURATION_MS.toInt())})"
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                stopPreview()
                onSave(startMs.coerceIn(0, maxStartMs))
            }) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = { stopPreview(); onDismiss() }) { Text("Отмена") }
        }
    )
}

private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver
        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else null
        }
}.getOrNull()

private fun readAudioDurationMs(context: Context, uri: Uri): Int? = runCatching {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
            ?.coerceAtMost(Int.MAX_VALUE.toLong())
            ?.toInt()
    } finally {
        runCatching { retriever.release() }
    }
}.getOrNull()

private fun formatMs(ms: Int): String =
    String.format(Locale.getDefault(), "%.1f с", ms / 1000f)
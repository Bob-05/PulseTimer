package com.pulsetimer.ui.component

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.pulsetimer.util.ToneGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Лёгкий плеер для превью сигналов в настройках.
 *
 * Не претендует на полноту TimerService: только «нажми → проиграй → стоп».
 *   - встроенные тоны — AudioTrack со сэмплами из [ToneGenerator];
 *   - CUSTOM — MediaPlayer с seekTo на выбранный фрагмент и авто-стопом 1 сек.
 *
 * Наложение сигналов исключено через счётчик [generation]: перед запуском
 * нового сигнала всё предыдущее гарантированно отпускается, а «догоняющие»
 * корутины отсеиваются по устаревшему токену.
 *
 * Потокобезопасность: все публичные методы вызываются с главного потока.
 * AudioTrack создаётся в фоновой корутине, но проверка [generation] и
 * присвоение [toneTrack] выполняются на main.
 */
class SignalPreviewPlayer(context: Context) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var toneTrack: AudioTrack? = null
    private var customPlayer: MediaPlayer? = null
    private var customStopRunnable: Runnable? = null
    private var generation = 0L

    /**
     * Проигрывает сигнал.
     *
     * @param toneType тип из AppSettings (CLASSIC, WHISTLE, ..., CUSTOM).
     * @param customUri URI кастомного файла (только при toneType == "CUSTOM").
     * @param customStartMs смещение начала фрагмента для CUSTOM.
     * @param volume громкость 0..1.
     */
    fun play(
        toneType: String,
        customUri: String?,
        customStartMs: Int,
        volume: Float
    ) {
        stop()
        // stop() инкрементит generation — берём АКТУАЛЬНОЕ значение ПОСЛЕ него.
        val gen = generation
        val safeVolume = volume.coerceIn(0f, 1f)
        if (toneType == "CUSTOM") {
            playCustom(customUri, customStartMs, safeVolume, gen)
        } else {
            playBuiltIn(toneType, safeVolume, gen)
        }
    }

    /** Немедленно останавливает любой текущий сигнал и инвалидирует in-flight работы. */
    fun stop() {
        generation++

        toneTrack?.let { track ->
            toneTrack = null
            runCatching {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
                track.release()
            }
        }

        customStopRunnable?.let(mainHandler::removeCallbacks)
        customStopRunnable = null

        customPlayer?.let { player ->
            customPlayer = null
            releaseMediaPlayer(player)
        }
    }

    /** Освобождает все ресурсы. Вызывать в onDispose композиции. */
    fun dispose() {
        stop()
        scope.cancel()
    }

    private fun playBuiltIn(type: String, volume: Float, gen: Long) {
        val durationMillis = ToneGenerator.durationFor(type, isTransition = true)
        scope.launch {
            val samples = withContext(Dispatchers.Default) {
                ToneGenerator.createSamples(type, durationMillis, volume)
            }
            if (gen != generation) return@launch

            val track = try {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(ToneGenerator.SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(samples.size * Short.SIZE_BYTES)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
            } catch (error: Exception) {
                Log.e(TAG, "Unable to create preview AudioTrack", error)
                return@launch
            }

            try {
                val written = track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                if (written != samples.size) {
                    Log.e(TAG, "Incomplete preview write: $written of ${samples.size}")
                    track.release()
                    return@launch
                }
                if (gen != generation) {
                    track.release()
                    return@launch
                }
                track.setVolume(1f)
                toneTrack = track
                track.play()
                mainHandler.postDelayed({
                    if (toneTrack === track) {
                        toneTrack = null
                        runCatching {
                            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
                            track.release()
                        }
                    }
                }, (durationMillis + 50).toLong())
            } catch (error: Exception) {
                Log.e(TAG, "Unable to play preview tone", error)
                runCatching { track.release() }
            }
        }
    }

    private fun playCustom(
        uriString: String?,
        startMs: Int,
        volume: Float,
        gen: Long
    ) {
        if (uriString == null) {
            Log.e(TAG, "Custom signal preview requested without a URI")
            return
        }
        val uri = try {
            Uri.parse(uriString)
        } catch (error: Exception) {
            Log.e(TAG, "Invalid custom signal URI", error)
            return
        }
        val safeStartMs = startMs.coerceAtLeast(0)
        val player = MediaPlayer()
        customPlayer = player
        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            player.setDataSource(appContext, uri)
            player.setVolume(volume, volume)
            player.setOnPreparedListener { prepared ->
                if (gen != generation || customPlayer !== prepared) {
                    releaseMediaPlayer(prepared)
                    return@setOnPreparedListener
                }
                if (safeStartMs > 0) {
                    prepared.setOnSeekCompleteListener { seeked ->
                        seeked.setOnSeekCompleteListener(null)
                        if (gen != generation || customPlayer !== seeked) {
                            releaseMediaPlayer(seeked)
                            return@setOnSeekCompleteListener
                        }
                        runCatching { seeked.start() }
                            .onFailure {
                                Log.e(TAG, "Unable to start custom signal preview after seek", it)
                                if (customPlayer === seeked) customPlayer = null
                                releaseMediaPlayer(seeked)
                            }
                        scheduleAutoStop(seeked, gen)
                    }
                    runCatching {
                        prepared.seekTo(safeStartMs.toLong(), MediaPlayer.SEEK_CLOSEST)
                    }.onFailure {
                        Log.w(TAG, "seekTo failed, falling back to start", it)
                        prepared.setOnSeekCompleteListener(null)
                        runCatching { prepared.start() }
                            .onFailure { error ->
                                Log.e(TAG, "Unable to start custom signal preview", error)
                                if (customPlayer === prepared) customPlayer = null
                                releaseMediaPlayer(prepared)
                            }
                        if (customPlayer === prepared) scheduleAutoStop(prepared, gen)
                    }
                } else {
                    runCatching { prepared.start() }
                        .onFailure { error ->
                            Log.e(TAG, "Unable to start custom signal preview", error)
                            if (customPlayer === prepared) customPlayer = null
                            releaseMediaPlayer(prepared)
                        }
                    if (customPlayer === prepared) scheduleAutoStop(prepared, gen)
                }
            }
            player.setOnCompletionListener { completed ->
                if (customPlayer === completed) {
                    customPlayer = null
                    releaseMediaPlayer(completed)
                }
            }
            player.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "Preview custom signal error ($what, $extra)")
                if (customPlayer === player) {
                    customPlayer = null
                    releaseMediaPlayer(player)
                }
                true
            }
            player.prepareAsync()
        } catch (error: Exception) {
            Log.e(TAG, "Unable to prepare preview custom signal", error)
            customPlayer = null
            releaseMediaPlayer(player)
        }
    }

    private fun scheduleAutoStop(player: MediaPlayer, gen: Long) {
        customStopRunnable?.let(mainHandler::removeCallbacks)
        val runnable = Runnable {
            customStopRunnable = null
            if (gen != generation) return@Runnable
            if (customPlayer !== player) return@Runnable
            customPlayer = null
            releaseMediaPlayer(player)
        }
        customStopRunnable = runnable
        mainHandler.postDelayed(runnable, ToneGenerator.CUSTOM_SIGNAL_MAX_DURATION_MS)
    }

    private fun releaseMediaPlayer(player: MediaPlayer) {
        player.setOnPreparedListener(null)
        player.setOnSeekCompleteListener(null)
        player.setOnCompletionListener(null)
        player.setOnErrorListener(null)
        runCatching { if (player.isPlaying) player.stop() }
        runCatching { player.reset() }
        runCatching { player.release() }
    }

    private companion object {
        const val TAG = "SignalPreviewPlayer"
    }
}
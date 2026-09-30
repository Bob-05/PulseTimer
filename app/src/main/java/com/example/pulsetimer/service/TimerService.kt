package com.pulsetimer.service

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteException
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.pulsetimer.MainActivity
import com.pulsetimer.data.AppSettingsStore
import com.pulsetimer.data.database.AppDatabase
import com.pulsetimer.data.entity.IntervalEntity
import com.pulsetimer.data.entity.SessionLogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import java.util.Locale

class TimerService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var timerJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var intervals: List<IntervalEntity> = emptyList()
    private var currentIndex: Int = 0
    private var timeRemaining: Int = 0
    private var isPaused: Boolean = false
    private var templateName: String = ""
    private var templateAudioUri: String? = null
    private var templateBackgroundType: String = "COLOR"
    private var templateBackgroundValue: String = ""
    private var templateVibrationPatternId: Int = 1
    private var activeTemplateId: Long? = null
    private var sessionStartedAt: Long = 0L

    private var mediaPlayer: MediaPlayer? = null
    private var playingAudioUri: String? = null
    private var volumeAnimator: ValueAnimator? = null
    private var toneTrack: AudioTrack? = null

    private var textToSpeech: TextToSpeech? = null
    private var textToSpeechReady = false
    private var pendingSpeech: String? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
    }

    private val dao by lazy { AppDatabase.getDatabase(this).timerDao() }

    companion object {
        private const val TAG = "TimerService"
        const val CHANNEL_ID = "pulse_timer_channel"
        const val NOTIFICATION_ID = 1

        const val ACTION_START = "com.pulsetimer.action.START"
        const val ACTION_PAUSE = "com.pulsetimer.action.PAUSE"
        const val ACTION_RESUME = "com.pulsetimer.action.RESUME"
        const val ACTION_SKIP = "com.pulsetimer.action.SKIP"
        const val ACTION_STOP = "com.pulsetimer.action.STOP"

        const val EXTRA_TEMPLATE_ID = "extra_template_id"
        const val EXTRA_TEMPLATE_NAME = "extra_template_name"

        private val _state = MutableStateFlow(ServiceTimerState())
        val state: StateFlow<ServiceTimerState> = _state.asStateFlow()

        fun updateState(newState: ServiceTimerState) {
            _state.value = newState
        }
    }

    data class ServiceTimerState(
        val isRunning: Boolean = false,
        val isPaused: Boolean = false,
        val currentIntervalIndex: Int = 0,
        val currentIntervalName: String = "",
        val currentIntervalColor: String = "#007AFF",
        val timeRemainingSeconds: Int = 0,
        val totalIntervals: Int = 0,
        val templateId: Long = 0L,
        val templateName: String = "",
        val backgroundType: String = "COLOR",
        val backgroundValue: String = "",
        val isFinished: Boolean = false
    )

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "PulseTimer::TimerWakeLock"
        )
        wakeLock?.setReferenceCounted(false)
        AppSettingsStore.initialize(this)
        textToSpeech = TextToSpeech(this) { status ->
            textToSpeechReady = status == TextToSpeech.SUCCESS
            if (textToSpeechReady) {
                textToSpeech?.language = Locale.getDefault()
                textToSpeech?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                pendingSpeech?.let { speech ->
                    if (AppSettingsStore.settings.value.voiceEnabled) {
                        textToSpeech?.speak(speech, TextToSpeech.QUEUE_FLUSH, null, "interval-$currentIndex")
                    }
                    pendingSpeech = null
                }
            } else {
                Log.e(TAG, "TextToSpeech initialization failed: $status")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // ★ КРИТИЧНО: должен быть вызван синхронно и до любого I/O,
        // иначе OS выбросит ForegroundServiceDidNotStartInTimeException.
        val isNewSession = intent?.action == ACTION_START
        startForeground(NOTIFICATION_ID, buildForegroundNotification(isNewSession))

        when (intent?.action) {
            ACTION_START -> {
                val templateId = intent.getLongExtra(EXTRA_TEMPLATE_ID, -1L)
                templateName = intent.getStringExtra(EXTRA_TEMPLATE_NAME) ?: "Тренировка"
                if (templateId != -1L) startTimer(templateId)
            }
            ACTION_PAUSE -> pauseTimer()
            ACTION_RESUME -> resumeTimer()
            ACTION_SKIP -> skipInterval()
            ACTION_STOP -> stopTimer()
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.i(TAG, "Task removed — timer keeps running")
    }

    /**
     * Возвращает «реальное» уведомление, если сессия уже идёт и данные загружены,
     * иначе — плейсхолдер, чтобы успеть зарегистрироваться в foreground за 5 секунд.
     */
    private fun buildForegroundNotification(isNewSession: Boolean): Notification {
        return if (!isNewSession && activeTemplateId != null && intervals.isNotEmpty()) {
            buildNotification()
        } else {
            buildLoadingNotification()
        }
    }

    private fun buildLoadingNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PulseTimer")
            .setContentText("Подготовка тренировки…")
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .build()
    }

    private fun startTimer(templateId: Long) {
        if (activeTemplateId != null) finalizeAbandonedSession()
        timerJob?.cancel()
        cancelVolumeAnimator()
        releasePlayerSafely()
        if (wakeLock?.isHeld == true) wakeLock?.release()

        serviceScope.launch(Dispatchers.IO) {
            val template = try {
                dao.getTemplateById(templateId).first()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load template $templateId", e)
                null
            }
            if (template == null) {
                Log.e(TAG, "Template $templateId was not found")
                withContext(Dispatchers.Main) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                return@launch
            }
            templateAudioUri = template.audioUri
            templateBackgroundType = template.backgroundType
            templateBackgroundValue = template.backgroundValue
            templateVibrationPatternId = template.vibrationPatternId
            intervals = try {
                dao.getIntervalsByTemplateId(templateId).first()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load intervals", e)
                emptyList()
            }
            if (intervals.isEmpty()) {
                withContext(Dispatchers.Main) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                return@launch
            }

            activeTemplateId = templateId
            sessionStartedAt = System.currentTimeMillis()

            currentIndex = 0
            isPaused = false
            timeRemaining = intervals.first().durationSeconds.coerceAtLeast(0)
            acquireWakeLockForRemainingSession()
            requestAudioFocus()
            startMusic(intervals.first().audioUri ?: templateAudioUri ?: AppSettingsStore.settings.value.musicUri)

            // Теперь, когда данные загружены — заменяем плейсхолдер на реальное уведомление
            withContext(Dispatchers.Main) {
                if (activeTemplateId != null) {
                    startForeground(NOTIFICATION_ID, buildNotification())
                }
            }
            runInterval()
        }
    }

    private fun finalizeAbandonedSession() {
        val templateId = activeTemplateId ?: return
        val startedAt = sessionStartedAt
        val workoutName = templateName
        val completedAt = System.currentTimeMillis()
        val duration = ((completedAt - startedAt) / 1_000L)
            .coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        serviceScope.launch(Dispatchers.IO) {
            try {
                dao.insertSessionLog(
                    SessionLogEntity(
                        templateId = templateId,
                        templateName = workoutName,
                        startedAt = startedAt,
                        completedAt = null,
                        totalDurationSeconds = duration
                    )
                )
            } catch (e: SQLiteException) {
                Log.e(TAG, "Unable to persist abandoned session", e)
            }
        }
        activeTemplateId = null
    }

    private fun acquireWakeLockForRemainingSession() {
        val remainingSeconds = timeRemaining.toLong() +
                intervals.drop(currentIndex + 1).sumOf { it.durationSeconds.coerceAtLeast(0).toLong() }
        wakeLock?.acquire((remainingSeconds + 60L) * 1_000L)
    }

    private fun runInterval(startPaused: Boolean = false) {
        if (currentIndex >= intervals.size) {
            finishWorkout()
            return
        }

        val interval = intervals[currentIndex]
        timeRemaining = interval.durationSeconds

        isPaused = startPaused
        updateState(
            ServiceTimerState(
                isRunning = !startPaused,
                isPaused = startPaused,
                currentIntervalIndex = currentIndex,
                currentIntervalName = interval.name,
                currentIntervalColor = interval.colorHex,
                backgroundType = if (interval.backgroundType == "CUSTOM_IMAGE" || interval.backgroundType == "VIDEO") {
                    interval.backgroundType
                } else templateBackgroundType,
                backgroundValue = if (interval.backgroundType == "CUSTOM_IMAGE" || interval.backgroundType == "VIDEO") {
                    interval.backgroundValue
                } else templateBackgroundValue.ifEmpty { interval.colorHex },
                timeRemainingSeconds = timeRemaining,
                totalIntervals = intervals.size,
                templateId = activeTemplateId ?: 0L,
                templateName = templateName,
                isFinished = false
            )
        )

        val intervalWithPattern = interval.copy(
            vibrationPatternId = if (interval.vibrationPatternId == 1) templateVibrationPatternId else interval.vibrationPatternId
        )
        performPhaseFeedback(intervalWithPattern)
        speakInterval(interval.name)
        startMusic(interval.audioUri ?: templateAudioUri ?: AppSettingsStore.settings.value.musicUri)
        if (startPaused) mainHandler.post { mediaPlayer.safePause() }
        updateNotification()

        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (timeRemaining > 0 && !isPaused) {
                delay(1000)
                if (!isPaused) {
                    timeRemaining--
                    val entity = intervals.getOrNull(currentIndex)
                    if (entity != null) {
                        if (timeRemaining in 1..3) {
                            playTone(isTransition = false)
                            vibrateForCountdown(entity.withTemplatePattern())
                            speakCountdown(timeRemaining)
                        }
                        updateState(
                            state.value.copy(
                                timeRemainingSeconds = timeRemaining,
                                isRunning = true,
                                isPaused = false
                            )
                        )
                        updateNotification()
                    }
                }
            }
            if (!isPaused) {
                currentIndex++
                runInterval()
            }
        }
    }

    private fun pauseTimer() {
        isPaused = true
        mainHandler.post { mediaPlayer.safePause() }
        if (wakeLock?.isHeld == true) wakeLock?.release()
        updateState(state.value.copy(isPaused = true, isRunning = false))
        updateNotification()
    }

    private fun resumeTimer() {
        if (!isPaused) return
        isPaused = false
        acquireWakeLockForRemainingSession()
        updateState(state.value.copy(isPaused = false, isRunning = true))
        mainHandler.post { mediaPlayer.safeStart() }
        updateNotification()
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (timeRemaining > 0 && !isPaused) {
                delay(1000)
                if (!isPaused) {
                    timeRemaining--
                    val entity = intervals.getOrNull(currentIndex)
                    if (entity != null) {
                        if (timeRemaining in 1..3) {
                            playTone(isTransition = false)
                            vibrateForCountdown(entity.withTemplatePattern())
                            speakCountdown(timeRemaining)
                        }
                        updateState(
                            state.value.copy(
                                timeRemainingSeconds = timeRemaining,
                                isRunning = true,
                                isPaused = false
                            )
                        )
                        updateNotification()
                    }
                }
            }
            if (!isPaused) {
                currentIndex++
                runInterval()
            }
        }
    }

    private fun skipInterval() {
        timerJob?.cancel()
        currentIndex++
        runInterval()
    }

    private fun stopTimer() {
        timerJob?.cancel()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        persistWorkoutEnd(completed = false)
    }

    private fun finishWorkout() {
        timerJob?.cancel()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        persistWorkoutEnd(completed = true)
    }

    private fun persistWorkoutEnd(completed: Boolean) {
        val templateId = activeTemplateId
        val startedAt = sessionStartedAt
        val workoutName = templateName
        val intervalCount = intervals.size
        val remainingSeconds = timeRemaining
        activeTemplateId = null
        val completedAt = System.currentTimeMillis()
        serviceScope.launch(Dispatchers.IO) {
            if (templateId != null) {
                try {
                    dao.insertSessionLog(
                        SessionLogEntity(
                            templateId = templateId,
                            templateName = workoutName,
                            startedAt = startedAt,
                            completedAt = if (completed) completedAt else null,
                            totalDurationSeconds = ((completedAt - startedAt) / 1_000L)
                                .coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        )
                    )
                } catch (e: SQLiteException) {
                    Log.e(TAG, "Unable to insert workout history", e)
                }
            }
            withContext(Dispatchers.Main) {
                updateState(
                    ServiceTimerState(
                        isRunning = false,
                        isPaused = false,
                        isFinished = completed,
                        templateName = workoutName,
                        totalIntervals = intervalCount,
                        currentIntervalName = if (completed) "Готово!" else "Тренировка остановлена",
                        timeRemainingSeconds = if (completed) 0 else remainingSeconds
                    )
                )
                releaseMediaResources()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "PulseTimer Тренировка",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Уведомления активного таймера"
            setSound(null, null)
            enableVibration(false)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pauseIntent = PendingIntent.getForegroundService(
            this, 1,
            Intent(this, TimerService::class.java).apply {
                action = if (isPaused) ACTION_RESUME else ACTION_PAUSE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val skipIntent = PendingIntent.getForegroundService(
            this, 2,
            Intent(this, TimerService::class.java).apply { action = ACTION_SKIP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getForegroundService(
            this, 3,
            Intent(this, TimerService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val currentInterval = intervals.getOrNull(currentIndex)
        val title = if (currentInterval != null) "$templateName — ${currentInterval.name}" else templateName
        val text = "Осталось: $timeRemaining сек"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .addAction(
                if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (isPaused) "Продолжить" else "Пауза",
                pauseIntent
            )
            .addAction(android.R.drawable.ic_media_next, "Пропустить", skipIntent)
            .addAction(android.R.drawable.ic_delete, "Стоп", stopIntent)
            .build()
    }

    private fun updateNotification() {
        runCatching {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun requestAudioFocus() {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ->
                        mediaPlayer.safeSetVolume(0f)
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ->
                        mediaPlayer.safeSetVolume(
                            AppSettingsStore.settings.value.soundVolume * 0.3f
                        )
                    AudioManager.AUDIOFOCUS_GAIN ->
                        mediaPlayer.safeSetVolume(AppSettingsStore.settings.value.soundVolume)
                }
            }
            .build()
        audioFocusRequest = request
        runCatching { audioManager.requestAudioFocus(request) }
    }

    private fun startMusic(audioUri: String?) {
        if (audioUri == playingAudioUri) return
        mainHandler.post {
            releasePlayerSafely()
            playingAudioUri = audioUri
            if (audioUri == null) return@post
            val player = MediaPlayer()
            mediaPlayer = player
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            player.isLooping = true
            player.setOnPreparedListener {
                if (mediaPlayer === player) {
                    val v = AppSettingsStore.settings.value.soundVolume
                    player.safeSetVolume(v)
                    if (!isPaused) player.safeStart()
                }
            }
            player.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error ($what, $extra)")
                if (mediaPlayer === player) releasePlayerSafely()
                true
            }
            try {
                player.setDataSource(this, audioUri.toUri())
                player.prepareAsync()
            } catch (e: Exception) {
                Log.e(TAG, "Unable to open audio URI: $audioUri", e)
                releasePlayerSafely()
            }
        }
    }

    // === Safe MediaPlayer helpers (защита от краха после release()) ===

    private val MediaPlayer?.safeIsPlaying: Boolean
        get() = try { this?.isPlaying == true } catch (_: IllegalStateException) { false }

    private fun MediaPlayer?.safeSetVolume(v: Float) {
        try { this?.setVolume(v, v) } catch (_: IllegalStateException) { }
    }

    private fun MediaPlayer?.safePause() {
        try { if (this?.isPlaying == true) this.pause() } catch (_: IllegalStateException) { }
    }

    private fun MediaPlayer?.safeStart() {
        try { this?.start() } catch (_: IllegalStateException) { }
    }

    private fun cancelVolumeAnimator() {
        volumeAnimator?.cancel()
        volumeAnimator = null
    }

    private fun releasePlayerSafely() {
        cancelVolumeAnimator()
        val p = mediaPlayer
        mediaPlayer = null
        playingAudioUri = null
        if (p == null) return
        try { if (p.isPlaying) p.stop() } catch (_: IllegalStateException) { }
        try { p.reset() } catch (_: IllegalStateException) { }
        try { p.release() } catch (_: IllegalStateException) { }
    }

    // === Vibration ===

    private fun buildVibrationEffect(timings: LongArray, amplitudes: IntArray): VibrationEffect {
        return if (vibrator.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(timings, amplitudes, -1)
        } else {
            VibrationEffect.createWaveform(timings, -1)
        }
    }

    private fun performPhaseFeedback(interval: IntervalEntity) {
        val isWork = interval.name.contains("работ", true) ||
                interval.name.contains("work", true) ||
                interval.colorHex.equals("#FF3B30", true)
        mainHandler.post {
            playTone(isTransition = true)
            if (AppSettingsStore.settings.value.vibrationEnabled && interval.vibrationPatternId != 0) {
                val s = vibrationStrength()
                val effect: VibrationEffect? = when (interval.vibrationPatternId) {
                    3 -> buildVibrationEffect(
                        longArrayOf(0, 100, 80, 180, 80, 320),
                        intArrayOf(0, s / 4, 0, s / 2, 0, s)
                    )
                    2 -> buildVibrationEffect(
                        longArrayOf(0, 400, 80, 400, 80, 400),
                        intArrayOf(0, s, 0, s, 0, s)
                    )
                    1 -> if (isWork) buildVibrationEffect(
                        longArrayOf(0, 200, 100, 200),
                        intArrayOf(0, s, 0, s)
                    ) else buildVibrationEffect(
                        longArrayOf(0, 500),
                        intArrayOf(0, s)
                    )
                    else -> null
                }
                effect?.let { runCatching { vibrator.vibrate(it) } }
            }
        }
    }

    private fun vibrateForCountdown(interval: IntervalEntity) {
        if (!AppSettingsStore.settings.value.vibrationEnabled || interval.vibrationPatternId == 0) return
        mainHandler.post {
            val amp = vibrationStrength()
            val effect = if (vibrator.hasAmplitudeControl())
                VibrationEffect.createOneShot(100, amp)
            else
                VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
            runCatching { vibrator.vibrate(effect) }
        }
    }

    private fun IntervalEntity.withTemplatePattern(): IntervalEntity =
        if (vibrationPatternId == 1) copy(vibrationPatternId = templateVibrationPatternId) else this

    private fun vibrationStrength(): Int {
        val p = AppSettingsStore.settings.value.vibrationProfile
        return when (p) {
            "SOFT" -> 80
            "EXTREME" -> 255
            else -> 170
        }
    }

    // === Tone (USAGE_MEDIA — не глушится системным потоком) ===

    private fun playTone(isTransition: Boolean) {
        val settings = AppSettingsStore.settings.value
        if (!settings.soundEnabled) return
        mainHandler.post {
            val durationMillis = when {
                settings.toneType == "GONG" && isTransition -> 700
                isTransition -> 450
                else -> 120
            }
            val samples = createToneSamples(settings.toneType, durationMillis, settings.soundVolume)

            toneTrack?.let { previous ->
                runCatching {
                    if (previous.playState == AudioTrack.PLAYSTATE_PLAYING) previous.stop()
                    previous.release()
                }
            }

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
                            .setSampleRate(44_100)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(samples.size * Short.SIZE_BYTES)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
            } catch (e: Exception) {
                Log.e(TAG, "Unable to create AudioTrack", e)
                return@post
            }
            toneTrack = track
            track.setVolume(1f)
            val written = try {
                track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            } catch (e: Exception) {
                track.release(); toneTrack = null; return@post
            }
            if (written != samples.size) {
                track.release(); toneTrack = null; return@post
            }
            runCatching { track.play() }

            val player = mediaPlayer
            val currentVolume = settings.soundVolume
            if (player.safeIsPlaying) {
                cancelVolumeAnimator()
                volumeAnimator = ValueAnimator.ofFloat(1f, 0.5f).apply {
                    duration = 140
                    addUpdateListener { anim ->
                        val level = (anim.animatedValue as Float) * currentVolume
                        if (mediaPlayer === player) player.safeSetVolume(level)
                    }
                    start()
                }
            }

            mainHandler.postDelayed({
                if (toneTrack === track) {
                    runCatching {
                        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
                        track.release()
                    }
                    toneTrack = null
                }
                if (mediaPlayer === player && player.safeIsPlaying) {
                    cancelVolumeAnimator()
                    volumeAnimator = ValueAnimator.ofFloat(0.5f, 1f).apply {
                        duration = 250
                        addUpdateListener { anim ->
                            val level = (anim.animatedValue as Float) * currentVolume
                            if (mediaPlayer === player) player.safeSetVolume(level)
                        }
                        start()
                    }
                }
            }, durationMillis.toLong())
        }
    }

    private fun createToneSamples(type: String, durationMillis: Int, volume: Float): ShortArray {
        val sampleRate = 44_100
        val sampleCount = sampleRate * durationMillis / 1_000
        return ShortArray(sampleCount) { index ->
            val time = index.toDouble() / sampleRate
            val progress = index.toDouble() / sampleCount
            val wave = when (type) {
                "WHISTLE" -> sin(2.0 * PI * (1_400.0 + 800.0 * progress) * time)
                "GONG" -> {
                    val decay = exp(-3.5 * progress)
                    decay * (
                            sin(2.0 * PI * 420.0 * time) +
                                    0.55 * sin(2.0 * PI * 630.0 * time) +
                                    0.3 * sin(2.0 * PI * 1_050.0 * time)
                            ) / 1.85
                }
                else -> sin(2.0 * PI * 880.0 * time)
            }
            val edge = minOf(1.0, progress * 35.0, (1.0 - progress) * 35.0)
            (wave * edge * volume.coerceIn(0f, 1f) * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun speakInterval(name: String) {
        if (!AppSettingsStore.settings.value.voiceEnabled) return
        mainHandler.post {
            if (textToSpeechReady) {
                textToSpeech?.speak(name, TextToSpeech.QUEUE_FLUSH, null, "interval-$currentIndex")
            } else {
                pendingSpeech = name
            }
        }
    }

    private fun speakCountdown(secondsRemaining: Int) {
        if (!AppSettingsStore.settings.value.voiceEnabled) return
        val word = when (secondsRemaining) {
            3 -> "Три"; 2 -> "Два"; 1 -> "Один"; else -> return
        }
        mainHandler.post {
            if (textToSpeechReady) {
                textToSpeech?.speak(word, TextToSpeech.QUEUE_FLUSH, null, "countdown-$currentIndex-$secondsRemaining")
            }
        }
    }

    private fun releaseMediaResources() {
        mainHandler.post {
            cancelVolumeAnimator()
            releasePlayerSafely()
            toneTrack?.let { t ->
                runCatching {
                    if (t.playState == AudioTrack.PLAYSTATE_PLAYING) t.stop()
                    t.release()
                }
            }
            toneTrack = null
            runCatching {
                textToSpeech?.stop()
                textToSpeech?.shutdown()
            }
            textToSpeech = null
            textToSpeechReady = false
            audioFocusRequest?.let { runCatching { audioManager.abandonAudioFocusRequest(it) } }
            audioFocusRequest = null
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        timerJob?.cancel()
        serviceScope.cancel()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        releaseMediaResources()
    }
}
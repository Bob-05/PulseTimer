package com.pulsetimer.service

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
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
import android.os.SystemClock
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
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

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var timerJob: Job? = null
    private var startJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var intervals: List<IntervalEntity> = emptyList()
    private var currentIndex: Int = 0
    private var timeRemaining: Int = 0
    private var intervalDeadlineElapsedRealtime: Long = 0L
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
    private var requestedAudioUri: String? = null
    private var volumeAnimator: ValueAnimator? = null
    private var toneTrack: AudioTrack? = null
    private var toneGeneration: Long = 0L

    private var textToSpeech: TextToSpeech? = null
    private var textToSpeechReady = false
    private var pendingSpeech: String? = null
    private var pendingSpeechIndex: Int = -1
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
        const val ACTION_PREVIOUS = "com.pulsetimer.action.PREVIOUS"
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
                    if (AppSettingsStore.settings.value.voiceEnabled &&
                        activeTemplateId != null &&
                        !isPaused &&
                        pendingSpeechIndex == currentIndex
                    ) {
                        textToSpeech?.speak(speech, TextToSpeech.QUEUE_FLUSH, null, "interval-$currentIndex")
                    }
                    pendingSpeech = null
                    pendingSpeechIndex = -1
                }
            } else {
                Log.e(TAG, "TextToSpeech initialization failed: $status")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
            ACTION_PREVIOUS -> previousInterval()
            ACTION_STOP -> stopTimer()
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.i(TAG, "Task removed — timer keeps running")
    }

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
        startJob?.cancel()
        cancelVolumeAnimator()
        pendingSpeech = null
        pendingSpeechIndex = -1
        textToSpeech?.stop()
        cancelTonePlayback()
        requestedAudioUri = null
        playingAudioUri = null
        releasePlayerSafely()
        if (wakeLock?.isHeld == true) wakeLock?.release()

        startJob = serviceScope.launch {
            val (template, loadedIntervals) = withContext(Dispatchers.IO) {
                coroutineScope {
                    val templateDeferred = async {
                        try {
                            dao.getTemplateById(templateId).first()
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to load template $templateId", e)
                            null
                        }
                    }
                    val intervalsDeferred = async {
                        try {
                            dao.getIntervalsByTemplateId(templateId).first()
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to load intervals for template $templateId", e)
                            emptyList()
                        }
                    }
                    templateDeferred.await() to intervalsDeferred.await()
                }
            }
            if (template == null) {
                Log.e(TAG, "Template $templateId was not found")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@launch
            }
            if (loadedIntervals.isEmpty()) {
                Log.e(TAG, "Template $templateId has no intervals")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@launch
            }

            templateAudioUri = template.audioUri
            templateBackgroundType = template.backgroundType
            templateBackgroundValue = template.backgroundValue
            templateVibrationPatternId = template.vibrationPatternId
            intervals = loadedIntervals

            activeTemplateId = templateId
            sessionStartedAt = System.currentTimeMillis()

            currentIndex = 0
            isPaused = false
            timeRemaining = intervals.first().durationSeconds.coerceAtLeast(1)
            acquireWakeLockForRemainingSession()
            requestAudioFocus()
            startMusic(intervals.first().audioUri ?: templateAudioUri ?: AppSettingsStore.settings.value.musicUri)

            if (activeTemplateId != null) {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
            runInterval()
            startJob = null
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
            intervals.drop(currentIndex + 1).sumOf {
                it.durationSeconds.coerceAtLeast(0).toLong()
            }
        wakeLock?.acquire((remainingSeconds + 60L) * 1_000L)
    }

    private fun runInterval(startPaused: Boolean = false) {
        if (currentIndex >= intervals.size) {
            finishWorkout()
            return
        }

        val interval = intervals[currentIndex]
        timeRemaining = interval.durationSeconds.coerceAtLeast(1)

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

        startCountdownLoop()
    }

    private fun pauseTimer() {
        if (activeTemplateId == null) return
        isPaused = true
        timerJob?.cancel()
        timerJob = null
        pendingSpeech = null
        pendingSpeechIndex = -1
        textToSpeech?.stop()
        cancelTonePlayback()
        if (intervalDeadlineElapsedRealtime > 0L) {
            timeRemaining = ((intervalDeadlineElapsedRealtime - SystemClock.elapsedRealtime() + 999L) /
                1_000L).coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
        cancelVolumeAnimator()
        mediaPlayer.safeSetVolume(AppSettingsStore.settings.value.soundVolume)
        mainHandler.post { mediaPlayer.safePause() }
        if (wakeLock?.isHeld == true) wakeLock?.release()
        updateState(state.value.copy(isPaused = true, isRunning = false))
        updateNotification()
    }

    private fun resumeTimer() {
        if (!isPaused || activeTemplateId == null) return
        isPaused = false
        acquireWakeLockForRemainingSession()
        updateState(state.value.copy(isPaused = false, isRunning = true))
        mainHandler.post {
            mediaPlayer.safeSetVolume(AppSettingsStore.settings.value.soundVolume)
            mediaPlayer.safeStart()
        }
        updateNotification()
        startCountdownLoop()
    }

    private fun startCountdownLoop() {
        timerJob?.cancel()
        intervalDeadlineElapsedRealtime =
            SystemClock.elapsedRealtime() + timeRemaining.coerceAtLeast(0).toLong() * 1_000L
        timerJob = serviceScope.launch {
            var lastPublishedRemaining = timeRemaining
            while (!isPaused) {
                val remainingMillis =
                    intervalDeadlineElapsedRealtime - SystemClock.elapsedRealtime()
                if (remainingMillis <= 0L) {
                    timeRemaining = 0
                    break
                }

                val remainingSeconds = ((remainingMillis + 999L) / 1_000L)
                    .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                timeRemaining = remainingSeconds
                if (remainingSeconds != lastPublishedRemaining) {
                    val entity = intervals.getOrNull(currentIndex)
                    if (entity != null && remainingSeconds in 1..3) {
                        playTone(isTransition = false)
                        vibrateForCountdown(entity.withTemplatePattern())
                        speakCountdown(remainingSeconds)
                    }
                    updateState(
                        state.value.copy(
                            timeRemainingSeconds = remainingSeconds,
                            isRunning = true,
                            isPaused = false
                        )
                    )
                    updateNotification()
                    lastPublishedRemaining = remainingSeconds
                }

                val nextTickDelay = remainingMillis - (remainingSeconds - 1L) * 1_000L
                delay(nextTickDelay.coerceAtLeast(1L))
            }
            if (!isPaused && activeTemplateId != null) {
                currentIndex++
                runInterval()
            }
        }
    }

    private fun skipInterval() {
        if (activeTemplateId == null) return
        timerJob?.cancel()
        timerJob = null
        cancelTonePlayback()
        currentIndex++
        runInterval()
    }

    private fun previousInterval() {
        // На первом интервале возвращаться некуда
        if (activeTemplateId == null || currentIndex <= 0) return
        timerJob?.cancel()
        timerJob = null
        cancelTonePlayback()
        currentIndex--
        runInterval()
    }

    private fun stopTimer() {
        timerJob?.cancel()
        startJob?.cancel()
        startJob = null
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
        pendingSpeech = null
        releaseMediaResources()
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
        audioFocusRequest?.let { previousRequest ->
            runCatching { audioManager.abandonAudioFocusRequest(previousRequest) }
        }
        audioFocusRequest = null
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
        if (audioUri == requestedAudioUri) return
        requestedAudioUri = audioUri
        mainHandler.post {
            if (requestedAudioUri != audioUri) return@post
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
                if (mediaPlayer === player) {
                    requestedAudioUri = null
                    releasePlayerSafely()
                }
                true
            }
            try {
                player.setDataSource(this, audioUri.toUri())
                player.prepareAsync()
            } catch (e: Exception) {
                Log.e(TAG, "Unable to open audio URI: $audioUri", e)
                requestedAudioUri = null
                releasePlayerSafely()
            }
        }
    }

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

    private fun releaseToneTrack(track: AudioTrack) {
        runCatching {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
            track.release()
        }
        if (toneTrack === track) toneTrack = null
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

    private fun buildVibrationEffect(timings: LongArray, amplitudes: IntArray): VibrationEffect {
        return if (vibrator.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(timings, amplitudes, -1)
        } else {
            VibrationEffect.createWaveform(timings, -1)
        }
    }

    private fun performPhaseFeedback(interval: IntervalEntity) {
        val phaseIndex = currentIndex
        val isWork = interval.name.contains("работ", true) ||
                interval.name.contains("work", true) ||
                interval.colorHex.equals("#FF3B30", true)
        mainHandler.post {
            if (activeTemplateId == null || currentIndex != phaseIndex) return@post
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
        val phaseIndex = currentIndex
        mainHandler.post {
            if (isPaused || activeTemplateId == null || currentIndex != phaseIndex) return@post
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

    private fun playTone(isTransition: Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { playTone(isTransition) }
            return
        }
        val settings = AppSettingsStore.settings.value
        if (!settings.soundEnabled) return
        val durationMillis = when {
            settings.toneType == "GONG" && isTransition -> 700
            isTransition -> 450
            else -> 120
        }
        val generation = ++toneGeneration
        toneTrack?.let(::releaseToneTrack)

        serviceScope.launch(Dispatchers.Default) {
            val samples = createToneSamples(settings.toneType, durationMillis, settings.soundVolume)
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
                return@launch
            }
            track.setVolume(1f)
            val written = try {
                track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            } catch (e: Exception) {
                Log.e(TAG, "Unable to write tone samples", e)
                track.release()
                return@launch
            }
            if (written != samples.size) {
                Log.e(TAG, "Incomplete tone write: $written of ${samples.size} samples")
                track.release()
                return@launch
            }

            try {
                withContext(Dispatchers.Main.immediate) {
                    if (generation != toneGeneration ||
                        !AppSettingsStore.settings.value.soundEnabled
                    ) {
                        track.release()
                        return@withContext
                    }

                    toneTrack?.let(::releaseToneTrack)
                    toneTrack = track
                    try {
                        track.play()
                    } catch (e: IllegalStateException) {
                        Log.e(TAG, "Unable to play tone", e)
                        releaseToneTrack(track)
                        return@withContext
                    }

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
                            releaseToneTrack(track)
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
            } catch (e: CancellationException) {
                runCatching { track.release() }
                throw e
            }
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

    private fun cancelTonePlayback() {
        toneGeneration++
        toneTrack?.let(::releaseToneTrack)
    }

    private fun speakInterval(name: String) {
        if (!AppSettingsStore.settings.value.voiceEnabled) return
        val phaseIndex = currentIndex
        mainHandler.post {
            if (activeTemplateId == null || currentIndex != phaseIndex || isPaused) return@post
            if (textToSpeechReady) {
                textToSpeech?.speak(name, TextToSpeech.QUEUE_FLUSH, null, "interval-$currentIndex")
            } else {
                pendingSpeech = name
                pendingSpeechIndex = phaseIndex
            }
        }
    }

    private fun speakCountdown(secondsRemaining: Int) {
        if (!AppSettingsStore.settings.value.voiceEnabled) return
        val word = when (secondsRemaining) {
            3 -> "Три"; 2 -> "Два"; 1 -> "Один"; else -> return
        }
        val phaseIndex = currentIndex
        mainHandler.post {
            if (textToSpeechReady && !isPaused && currentIndex == phaseIndex) {
                textToSpeech?.speak(word, TextToSpeech.QUEUE_ADD, null, "countdown-$currentIndex-$secondsRemaining")
            }
        }
    }

    private fun releaseMediaResources() {
        toneGeneration++
        pendingSpeech = null
        pendingSpeechIndex = -1
        val release = {
            cancelVolumeAnimator()
            requestedAudioUri = null
            releasePlayerSafely()
            toneTrack?.let(::releaseToneTrack)
            runCatching {
                textToSpeech?.stop()
                textToSpeech?.shutdown()
            }
            textToSpeech = null
            textToSpeechReady = false
            audioFocusRequest?.let { runCatching { audioManager.abandonAudioFocusRequest(it) } }
            audioFocusRequest = null
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            release()
        } else {
            mainHandler.post(release)
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
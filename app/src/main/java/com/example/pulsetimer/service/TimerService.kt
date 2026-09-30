package com.pulsetimer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
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
import com.pulsetimer.MainActivity
import com.pulsetimer.data.AppSettingsStore
import com.pulsetimer.data.database.AppDatabase
import com.pulsetimer.data.entity.IntervalEntity
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
    private var mediaPlayer: MediaPlayer? = null
    private var playingAudioUri: String? = null
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
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private val dao by lazy { AppDatabase.getDatabase(this).timerDao() }

    companion object {
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
                Log.e("TimerService", "TextToSpeech initialization failed: $status")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val templateId = intent.getLongExtra(EXTRA_TEMPLATE_ID, -1L)
                templateName = intent.getStringExtra(EXTRA_TEMPLATE_NAME) ?: "Тренировка"
                if (templateId != -1L) {
                    startTimer(templateId)
                }
            }
            ACTION_PAUSE -> pauseTimer()
            ACTION_RESUME -> resumeTimer()
            ACTION_SKIP -> skipInterval()
            ACTION_STOP -> stopTimer()
        }
        return START_STICKY
    }

    private fun startTimer(templateId: Long) {
        serviceScope.launch(Dispatchers.IO) {
            val template = dao.getTemplateById(templateId).first()
            if (template == null) {
                Log.e("TimerService", "Template $templateId was not found")
                stopSelf()
                return@launch
            }
            templateAudioUri = template.audioUri
            templateBackgroundType = template.backgroundType
            templateBackgroundValue = template.backgroundValue
            templateVibrationPatternId = template.vibrationPatternId
            intervals = dao.getIntervalsByTemplateId(templateId).first()
            if (intervals.isEmpty()) {
                stopSelf()
                return@launch
            }

            currentIndex = 0
            isPaused = false
            timeRemaining = intervals.first().durationSeconds.coerceAtLeast(0)
            acquireWakeLockForRemainingSession()
            requestAudioFocus()
            startMusic(intervals.first().audioUri ?: templateAudioUri ?: AppSettingsStore.settings.value.musicUri)
            startForeground(NOTIFICATION_ID, buildNotification())
            runInterval()
        }
    }

    private fun acquireWakeLockForRemainingSession() {
        val remainingSeconds = timeRemaining.toLong() +
            intervals.drop(currentIndex + 1).sumOf {
                it.durationSeconds.coerceAtLeast(0).toLong()
            }
        wakeLock?.acquire((remainingSeconds + 60L) * 1_000L)
    }

    private fun runInterval() {
        if (currentIndex >= intervals.size) {
            finishWorkout()
            return
        }

        val interval = intervals[currentIndex]
        timeRemaining = interval.durationSeconds

        updateState(
            ServiceTimerState(
                isRunning = true,
                isPaused = false,
                currentIntervalIndex = currentIndex,
                currentIntervalName = interval.name,
                currentIntervalColor = interval.colorHex,
                backgroundType = if (interval.backgroundType == "CUSTOM_IMAGE" || interval.backgroundType == "VIDEO") {
                    interval.backgroundType
                } else {
                    templateBackgroundType
                },
                backgroundValue = if (interval.backgroundType == "CUSTOM_IMAGE" || interval.backgroundType == "VIDEO") {
                    interval.backgroundValue
                } else {
                    templateBackgroundValue.ifEmpty { interval.colorHex }
                },
                timeRemainingSeconds = timeRemaining,
                totalIntervals = intervals.size,
                templateName = templateName,
                isFinished = false
            )
        )

        val intervalWithPattern = interval.copy(
            vibrationPatternId = if (interval.vibrationPatternId == 1) {
                templateVibrationPatternId
            } else {
                interval.vibrationPatternId
            }
        )
        performPhaseFeedback(intervalWithPattern)
        speakInterval(interval.name)
        startMusic(interval.audioUri ?: templateAudioUri ?: AppSettingsStore.settings.value.musicUri)
        updateNotification()

        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (timeRemaining > 0 && !isPaused) {
                delay(1000)
                if (!isPaused) {
                    timeRemaining--
                    val currentIntervalEntity = intervals.getOrNull(currentIndex)
                    if (currentIntervalEntity != null) {
                        if (timeRemaining in 1..3) {
                            playTone(isTransition = false)
                            vibrateForCountdown(currentIntervalEntity.withTemplatePattern())
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
            if (!isPaused && timeRemaining <= 0) {
                currentIndex++
                runInterval()
            }
        }
    }

    private fun pauseTimer() {
        isPaused = true
        mainHandler.post { if (mediaPlayer?.isPlaying == true) mediaPlayer?.pause() }
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        updateState(state.value.copy(isPaused = true, isRunning = false))
        updateNotification()
    }

    private fun resumeTimer() {
        if (isPaused) {
            isPaused = false
            acquireWakeLockForRemainingSession()
            updateState(state.value.copy(isPaused = false, isRunning = true))
            mainHandler.post { mediaPlayer?.start() }
            updateNotification()
            timerJob?.cancel()
            timerJob = serviceScope.launch {
                while (timeRemaining > 0 && !isPaused) {
                    delay(1000)
                    if (!isPaused) {
                        timeRemaining--
                        val currentIntervalEntity = intervals.getOrNull(currentIndex)
                        if (currentIntervalEntity != null) {
                            if (timeRemaining in 1..3) {
                                playTone(isTransition = false)
                                vibrateForCountdown(currentIntervalEntity.withTemplatePattern())
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
                if (!isPaused && timeRemaining <= 0) {
                    currentIndex++
                    runInterval()
                }
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
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        updateState(ServiceTimerState(isFinished = true))
        releaseMediaResources()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun finishWorkout() {
        timerJob?.cancel()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        updateState(
            ServiceTimerState(
                isRunning = false,
                isPaused = false,
                isFinished = true,
                templateName = templateName,
                totalIntervals = intervals.size,
                currentIntervalName = "Готово!",
                timeRemainingSeconds = 0
            )
        )
        releaseMediaResources()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
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
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = PendingIntent.getForegroundService(
            this,
            1,
            Intent(this, TimerService::class.java).apply {
                action = if (isPaused) ACTION_RESUME else ACTION_PAUSE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val skipIntent = PendingIntent.getForegroundService(
            this,
            2,
            Intent(this, TimerService::class.java).apply { action = ACTION_SKIP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getForegroundService(
            this,
            3,
            Intent(this, TimerService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val currentInterval = intervals.getOrNull(currentIndex)
        val title = if (currentInterval != null) {
            "$templateName — ${currentInterval.name}"
        } else {
            templateName
        }
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
            .addAction(
                android.R.drawable.ic_media_next,
                "Пропустить",
                skipIntent
            )
            .addAction(
                android.R.drawable.ic_delete,
                "Стоп",
                stopIntent
            )
            .build()
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener { change ->
                    if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        mediaPlayer?.setVolume(0f, 0f)
                    } else if (change == AudioManager.AUDIOFOCUS_GAIN) {
                        mediaPlayer?.setVolume(
                            AppSettingsStore.settings.value.soundVolume,
                            AppSettingsStore.settings.value.soundVolume
                        )
                    }
                }
                .build()
            audioFocusRequest = request
            audioManager.requestAudioFocus(request)
        }
    }

    private fun startMusic(audioUri: String?) {
        if (audioUri == playingAudioUri) return
        mainHandler.post {
            releasePlayer()
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
                    val volume = AppSettingsStore.settings.value.soundVolume
                    player.setVolume(volume, volume)
                    if (!isPaused) player.start()
                }
            }
            player.setOnErrorListener { _, what, extra ->
                Log.e("TimerService", "Unable to play audio ($what, $extra)")
                if (mediaPlayer === player) releasePlayer()
                true
            }
            try {
                player.setDataSource(this, Uri.parse(audioUri))
                player.prepareAsync()
            } catch (error: java.io.IOException) {
                Log.e("TimerService", "Unable to open audio URI: $audioUri", error)
                releasePlayer()
            } catch (error: IllegalArgumentException) {
                Log.e("TimerService", "Invalid audio URI: $audioUri", error)
                releasePlayer()
            }
        }
    }

    private fun performPhaseFeedback(interval: IntervalEntity) {
        val isWork = interval.name.contains("работ", ignoreCase = true) ||
            interval.name.contains("work", ignoreCase = true) ||
            interval.colorHex.equals("#FF3B30", ignoreCase = true)
        mainHandler.post {
            playTone(isTransition = true)
            if (AppSettingsStore.settings.value.vibrationEnabled && interval.vibrationPatternId != 0) {
                val strength = vibrationStrength()
                val effect = when {
                    interval.vibrationPatternId == 3 -> VibrationEffect.createWaveform(
                        longArrayOf(0, 120, 100, 240, 100, 360),
                        intArrayOf(0, strength / 3, 0, strength * 2 / 3, 0, strength),
                        -1
                    )
                    interval.vibrationPatternId == 2 && isWork -> VibrationEffect.createWaveform(
                        longArrayOf(0, 450, 100, 450),
                        intArrayOf(0, strength, 0, strength),
                        -1
                    )
                    isWork -> VibrationEffect.createWaveform(
                        longArrayOf(0, 300, 120, 300),
                        intArrayOf(0, strength, 0, strength),
                        -1
                    )
                    else -> VibrationEffect.createWaveform(longArrayOf(0, 600), intArrayOf(0, strength), -1)
                }
                vibrator.vibrate(effect)
            }
        }
    }

    private fun vibrateForCountdown(interval: IntervalEntity) {
        if (!AppSettingsStore.settings.value.vibrationEnabled || interval.vibrationPatternId == 0) return
        mainHandler.post {
            vibrator.vibrate(
                VibrationEffect.createOneShot(100, vibrationStrength())
            )
        }
    }

    private fun IntervalEntity.withTemplatePattern(): IntervalEntity =
        if (vibrationPatternId == 1) copy(vibrationPatternId = templateVibrationPatternId) else this

    private fun vibrationStrength(): Int {
        val profile = AppSettingsStore.settings.value.vibrationProfile
        return when (profile) {
            "SOFT" -> 80
            "EXTREME" -> 255
            else -> 170
        }
    }

    private fun playTone(isTransition: Boolean) {
        val settings = AppSettingsStore.settings.value
        if (!settings.soundEnabled) return
        mainHandler.post {
            val duration = if (isTransition) 450 else 120
            val durationMillis = if (settings.toneType == "GONG" && isTransition) 700 else duration
            val samples = createToneSamples(settings.toneType, durationMillis, settings.soundVolume)
            toneTrack?.let { previous ->
                if (previous.playState == AudioTrack.PLAYSTATE_PLAYING) previous.stop()
                previous.release()
            }
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
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
            toneTrack = track
            track.setVolume(1f)
            val written = track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            if (written != samples.size) {
                Log.e("TimerService", "Only wrote $written of ${samples.size} tone samples")
                track.release()
                toneTrack = null
                return@post
            }
            track.play()
            val player = mediaPlayer
            val currentVolume = settings.soundVolume
            if (player?.isPlaying == true) {
                ValueAnimator.ofFloat(1f, 0.5f).apply {
                    this.duration = 140
                    addUpdateListener {
                        val level = (it.animatedValue as Float) * currentVolume
                        player.setVolume(level, level)
                    }
                    start()
                }
            }
            mainHandler.postDelayed({
                if (toneTrack === track) {
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
                    track.release()
                    toneTrack = null
                }
                if (player?.isPlaying == true) {
                    ValueAnimator.ofFloat(0.5f, 1f).apply {
                        this.duration = 250
                        addUpdateListener {
                            val level = (it.animatedValue as Float) * currentVolume
                            player.setVolume(level, level)
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

    private fun releasePlayer() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) player.stop()
            player.reset()
            player.release()
        }
        mediaPlayer = null
        playingAudioUri = null
    }

    private fun releaseMediaResources() {
        mainHandler.post {
            releasePlayer()
            toneTrack?.let { track ->
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
                track.release()
            }
            toneTrack = null
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            textToSpeech = null
            textToSpeechReady = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let(audioManager::abandonAudioFocusRequest)
            }
            audioFocusRequest = null
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        timerJob?.cancel()
        serviceScope.cancel()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        releaseMediaResources()
    }
}
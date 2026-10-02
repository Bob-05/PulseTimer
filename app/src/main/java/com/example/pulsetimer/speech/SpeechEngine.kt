package com.pulsetimer.speech

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/**
 * Единый на весь процесс держатель TextToSpeech.
 *
 * Зачем синглтон:
 *  - TextToSpeech.initialize() — дорогая операция (300–1500 мс на холодном старте
 *    в зависимости от вендора). Раньше TimerService создавал собственный TTS в
 *    onCreate и блокирующе ждал готовности в startTimer с таймаутом 2 с — это
 *    задерживало старт каждой тренировки.
 *  - Теперь прогреваем движок заранее (MainActivity), а сервис переиспользует
 *    уже поднятый инстанс.
 *
 * Потокобезопасность: все операции с внутренним состоянием — под [lock].
 * Колбэки onReady вызываются вне synchronized-блока, чтобы избежать дедлоков.
 *
 * ВАЖНО: onReady-колбэки удерживаются до готовности TTS. Если владелец колбэка
 * (например, TimerService) уничтожается до готовности — он обязан вызвать
 * clearPendingCallbacks(), иначе произойдёт утечка памяти.
 */
object SpeechEngine {

    private const val TAG = "SpeechEngine"

    @Volatile private var instance: TextToSpeech? = null
    @Volatile private var ready = false
    @Volatile private var initializing = false

    private val lock = Any()
    private val readyCallbacks = mutableListOf<(Boolean) -> Unit>()
    private var progressListener: UtteranceProgressListener? = null

    /**
     * Асинхронно поднимает TTS, если он ещё не поднят. Повторные вызовы — no-op.
     */
    fun warmUp(context: Context) {
        synchronized(lock) {
            if (instance != null || initializing) return
            initializing = true
        }
        val appContext = context.applicationContext
        try {
            TextToSpeech(appContext) { status -> handleInitResult(status) }
                .also { tts ->
                    // OnInitListener вызывается через main-looper ПОСЛЕ возврата
                    // из конструктора. Если warmUp зовут с main-потока — looper
                    // заблокирован нашим стеком, колбэк выполнится позже.
                    // Если с фонового — колбэк всё равно уйдёт на main.
                    // Значит, к моменту handleInitResult() instance уже установлен.
                    synchronized(lock) {
                        instance = tts
                    }
                }
        } catch (error: Throwable) {
            Log.e(TAG, "TextToSpeech constructor failed", error)
            val callbacks: List<(Boolean) -> Unit>
            synchronized(lock) {
                initializing = false
                instance = null
                ready = false
                callbacks = readyCallbacks.toList()
                readyCallbacks.clear()
            }
            callbacks.forEach { it(false) }
        }
    }

    private fun handleInitResult(status: Int) {
        val success = status == TextToSpeech.SUCCESS
        val callbacks: List<(Boolean) -> Unit>
        synchronized(lock) {
            initializing = false
            if (success) {
                val tts = instance
                if (tts != null) {
                    runCatching {
                        tts.language = Locale.getDefault()
                        tts.setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        progressListener?.let { tts.setOnUtteranceProgressListener(it) }
                    }
                    ready = true
                } else {
                    // Теоретически недостижимо: см. комментарий в warmUp.
                    Log.e(TAG, "Init succeeded but instance is null")
                    ready = false
                }
            } else {
                Log.e(TAG, "TextToSpeech initialization failed: $status")
                instance?.let { runCatching { it.shutdown() } }
                instance = null
                ready = false
            }
            callbacks = readyCallbacks.toList()
            readyCallbacks.clear()
        }
        callbacks.forEach { it(success && ready) }
    }

    fun isReady(): Boolean = ready

    /**
     * Однократный колбэк «TTS готов/провалился».
     *  - если движок уже готов → вызов синхронный на текущем потоке;
     *  - если идёт инициализация → колбэк сработает при её завершении;
     *  - если движок вообще не поднимали (или он уже провалился) → синхронный
     *    false, чтобы вызывающий код не ждал вечно.
     *
     * Колбэк удерживается до готовности TTS. Владелец обязан вызвать
     * [clearPendingCallbacks] при уничтожении, если колбэк ещё не сработал.
     */
    fun onReady(callback: (Boolean) -> Unit) {
        var syncResult: Boolean? = null
        synchronized(lock) {
            when {
                ready -> syncResult = true
                initializing -> {
                    readyCallbacks.add(callback)
                    return
                }
                else -> syncResult = false
            }
        }
        callback(syncResult == true)
    }

    /**
     * Сбрасывает все ещё не сработавшие onReady-колбэки.
     * Обязательный вызов из onDestroy владельца — иначе утечка памяти.
     */
    fun clearPendingCallbacks() {
        synchronized(lock) {
            readyCallbacks.clear()
        }
    }

    fun setUtteranceProgressListener(listener: UtteranceProgressListener?) {
        synchronized(lock) {
            progressListener = listener
            instance?.setOnUtteranceProgressListener(listener)
        }
    }

    fun speak(text: String, queueMode: Int, utteranceId: String): Int {
        if (!ready) return TextToSpeech.ERROR
        val tts = instance ?: return TextToSpeech.ERROR
        return runCatching { tts.speak(text, queueMode, null, utteranceId) }
            .getOrDefault(TextToSpeech.ERROR)
    }

    fun stop() {
        runCatching { instance?.stop() }
    }

}
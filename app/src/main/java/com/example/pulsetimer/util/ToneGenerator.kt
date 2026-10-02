package com.pulsetimer.util

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Генератор PCM-сэмплов для встроенных звуковых сигналов.
 *
 * Единый источник правды для TimerService (реальное воспроизведение во время
 * тренировки) и SettingsScreen (превью при выборе/регулировке). Благодаря
 * этому сигнал в настройках звучит ровно так же, как в тренировке.
 */
object ToneGenerator {

    /** Частота дискретизации. Одна для всех сэмплов — 44100 Гц, моно, 16 бит. */
    const val SAMPLE_RATE = 44_100

    /**
     * Максимальная длительность пользовательского сигнала.
     * Начало фрагмента выбирает пользователь, длительность — фиксированная.
     */
    const val CUSTOM_SIGNAL_MAX_DURATION_MS = 1_000L

    /**
     * Длительность встроенного сигнала в зависимости от контекста.
     * Transition (смена интервала) — длиннее, countdown (3-2-1) — короткий.
     */
    fun durationFor(type: String, isTransition: Boolean): Int = when {
        isTransition && type == "GONG" -> 700
        isTransition && type == "CHIME" -> 600
        isTransition && type == "DOUBLE" -> 360
        isTransition && type == "DIGITAL" -> 420
        isTransition -> 450
        else -> 120
    }

    /**
     * Возвращает PCM-сэмплы сигнала указанного типа.
     * @param type тип сигнала (CLASSIC, WHISTLE, GONG, DOUBLE, DIGITAL, CHIME).
     * @param durationMillis длительность.
     * @param volume громкость 0..1, вшивается в амплитуду сэмплов.
     */
    fun createSamples(type: String, durationMillis: Int, volume: Float): ShortArray {
        val sampleCount = SAMPLE_RATE * durationMillis / 1_000
        return ShortArray(sampleCount) { index ->
            val time = index.toDouble() / SAMPLE_RATE
            val progress = index.toDouble() / sampleCount
            val wave = when (type) {
                "WHISTLE" -> sin(2.0 * PI * (1_400.0 + 800.0 * progress) * time)
                "DOUBLE" -> {
                    val pulseProgress = when {
                        progress < 0.42 -> progress / 0.42
                        progress in 0.58..1.0 -> (progress - 0.58) / 0.42
                        else -> 0.0
                    }
                    sin(2.0 * PI * 880.0 * time) *
                            minOf(1.0, pulseProgress * 12.0, (1.0 - pulseProgress) * 12.0)
                }
                "DIGITAL" -> {
                    val noteProgress = (progress * 3.0).coerceAtMost(2.999999)
                    val noteIndex = noteProgress.toInt()
                    val notePhase = noteProgress - noteIndex
                    val frequency = when (noteIndex) {
                        0 -> 523.25
                        1 -> 659.25
                        else -> 783.99
                    }
                    sin(2.0 * PI * frequency * time) *
                            minOf(1.0, notePhase * 12.0, (1.0 - notePhase) * 12.0)
                }
                "CHIME" -> {
                    val decay = exp(-4.0 * progress)
                    decay * (
                            sin(2.0 * PI * 659.25 * time) +
                                    0.45 * sin(2.0 * PI * 1_318.5 * time) +
                                    0.2 * sin(2.0 * PI * 1_977.75 * time)
                            ) / 1.65
                }
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
}
package com.pulsetimer.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val soundEnabled: Boolean = true,
    val voiceEnabled: Boolean = true,
    val soundVolume: Float = 0.8f,
    val toneType: String = "CLASSIC",
    val vibrationEnabled: Boolean = true,
    val vibrationProfile: String = "SPORT",
    val theme: String = "OLED",
    val animatedBackgrounds: Boolean = true,
    val musicUri: String? = null
)

object AppSettingsStore {
    private const val PREFERENCES_NAME = "pulse_timer_settings"
    private val lock = Any()
    private var preferences: SharedPreferences? = null
    private val mutableSettings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = mutableSettings.asStateFlow()

    fun initialize(context: Context): AppSettingsStore {
        synchronized(lock) {
            if (preferences == null) {
                preferences = context.applicationContext.getSharedPreferences(
                    PREFERENCES_NAME,
                    Context.MODE_PRIVATE
                )
                mutableSettings.value = readSettings(preferences!!)
            }
        }
        return this
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        synchronized(lock) {
            val prefs = checkNotNull(preferences) {
                "AppSettingsStore must be initialized before updating settings"
            }
            val updated = transform(mutableSettings.value)
            check(prefs.edit()
                .putBoolean("soundEnabled", updated.soundEnabled)
                .putBoolean("voiceEnabled", updated.voiceEnabled)
                .putFloat("soundVolume", updated.soundVolume.coerceIn(0f, 1f))
                .putString("toneType", updated.toneType)
                .putBoolean("vibrationEnabled", updated.vibrationEnabled)
                .putString("vibrationProfile", updated.vibrationProfile)
                .putString("theme", updated.theme)
                .putBoolean("animatedBackgrounds", updated.animatedBackgrounds)
                .putString("musicUri", updated.musicUri)
                .commit()) { "Unable to save PulseTimer settings" }
            mutableSettings.value = updated.copy(soundVolume = updated.soundVolume.coerceIn(0f, 1f))
        }
    }

    private fun readSettings(prefs: SharedPreferences): AppSettings = AppSettings(
        soundEnabled = prefs.getBoolean("soundEnabled", true),
        voiceEnabled = prefs.getBoolean("voiceEnabled", true),
        soundVolume = prefs.getFloat("soundVolume", 0.8f),
        toneType = prefs.getString("toneType", "CLASSIC") ?: "CLASSIC",
        vibrationEnabled = prefs.getBoolean("vibrationEnabled", true),
        vibrationProfile = prefs.getString("vibrationProfile", "SPORT") ?: "SPORT",
        theme = prefs.getString("theme", "OLED") ?: "OLED",
        animatedBackgrounds = prefs.getBoolean("animatedBackgrounds", true),
        musicUri = prefs.getString("musicUri", null)
    )
}

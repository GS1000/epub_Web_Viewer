package com.example.epubwebviewer.data

import android.content.Context
import android.content.SharedPreferences

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    companion object {
        const val KEY_SLEEP_DELAY_SECONDS = "sleep_delay_seconds"
        const val KEY_SLEEP_ENABLED = "sleep_enabled"
        private const val DEFAULT_SLEEP_DELAY = 30 // seconds
        private const val DEFAULT_SLEEP_ENABLED = true
    }

    fun getSleepDelaySeconds(): Int {
        return prefs.getInt(KEY_SLEEP_DELAY_SECONDS, DEFAULT_SLEEP_DELAY)
    }

    fun setSleepDelaySeconds(seconds: Int) {
        prefs.edit().putInt(KEY_SLEEP_DELAY_SECONDS, seconds).apply()
    }

    fun isSleepEnabled(): Boolean {
        return prefs.getBoolean(KEY_SLEEP_ENABLED, DEFAULT_SLEEP_ENABLED)
    }

    fun setSleepEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SLEEP_ENABLED, enabled).apply()
    }
}
package com.example.epubwebviewer.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.epubwebviewer.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)

    private val _sleepEnabled = MutableStateFlow(repository.isSleepEnabled())
    val sleepEnabled: StateFlow<Boolean> = _sleepEnabled.asStateFlow()

    private val _sleepDelaySeconds = MutableStateFlow(repository.getSleepDelaySeconds())
    val sleepDelaySeconds: StateFlow<Int> = _sleepDelaySeconds.asStateFlow()

    fun setSleepEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setSleepEnabled(enabled)
            _sleepEnabled.value = enabled
        }
    }

    fun setSleepDelaySeconds(seconds: Int) {
        viewModelScope.launch {
            repository.setSleepDelaySeconds(seconds)
            _sleepDelaySeconds.value = seconds
        }
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            repository.setSleepEnabled(true)
            repository.setSleepDelaySeconds(30)
            _sleepEnabled.value = true
            _sleepDelaySeconds.value = 30
        }
    }
}
package by.mlastovsky.kosht

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.mlastovsky.kosht.data.AppSettings
import by.mlastovsky.kosht.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    /** null until the first DataStore emission; the splash screen waits for it. */
    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .map { it as AppSettings? }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** True once Android has been asked about notifications, ever. */
    val notificationsAsked: StateFlow<Boolean> = settingsRepository.notificationsAsked
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun markNotificationsAsked() {
        viewModelScope.launch { settingsRepository.markNotificationsAsked() }
    }
}

package by.mlastovsky.kosht.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.mlastovsky.kosht.data.AppSettings
import by.mlastovsky.kosht.data.SettingsRepository
import by.mlastovsky.kosht.model.AppLanguage
import by.mlastovsky.kosht.model.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val language: StateFlow<AppLanguage> = settingsRepository.language

    fun setLanguage(language: AppLanguage) {
        settingsRepository.setLanguage(language)
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setDynamicColors(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDynamicColors(enabled) }
    }

    fun setCurrency(code: String) {
        viewModelScope.launch { settingsRepository.setCurrencyCode(code) }
    }

    fun setNotifyDailyReminder(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setNotifyDailyReminder(enabled) }
    }

    fun setNotifyRecurringDue(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setNotifyRecurringDue(enabled) }
    }

    fun setNotifyWeeklySummary(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setNotifyWeeklySummary(enabled) }
    }

    companion object {
        val SUPPORTED_CURRENCIES = listOf("BYN", "USD", "EUR", "PLN", "UAH", "RUB", "GBP", "KZT")
    }
}

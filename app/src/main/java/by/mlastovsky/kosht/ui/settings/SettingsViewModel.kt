package by.mlastovsky.kosht.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.mlastovsky.kosht.data.AppSettings
import by.mlastovsky.kosht.data.PhotoStore
import by.mlastovsky.kosht.data.SettingsRepository
import by.mlastovsky.kosht.data.UserProfile
import by.mlastovsky.kosht.model.AppLanguage
import by.mlastovsky.kosht.model.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val photoStore: PhotoStore
) : ViewModel() {

    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val profile: StateFlow<UserProfile?> = settingsRepository.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun saveProfile(name: String, nickname: String) {
        viewModelScope.launch { settingsRepository.setProfile(name, nickname) }
    }

    fun setProfilePhoto(uri: Uri) {
        viewModelScope.launch {
            val old = settingsRepository.profile.first().photoPath
            val saved = photoStore.saveFromUri(uri, subdir = "profile") ?: return@launch
            photoStore.delete(old)
            settingsRepository.setProfilePhoto(saved)
        }
    }

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

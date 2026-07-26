package by.mlastovsky.kosht.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.mlastovsky.kosht.data.AppSettings
import by.mlastovsky.kosht.data.PhotoStore
import by.mlastovsky.kosht.data.SettingsRepository
import by.mlastovsky.kosht.data.UpdateChecker
import by.mlastovsky.kosht.data.UpdateStatus
import by.mlastovsky.kosht.data.UserProfile
import by.mlastovsky.kosht.model.AppLanguage
import by.mlastovsky.kosht.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** State of the manual update check behind the version row. */
sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Checking : UpdateCheckState
    data class Done(val status: UpdateStatus) : UpdateCheckState
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val photoStore: PhotoStore,
    private val currencyChanger: by.mlastovsky.kosht.data.CurrencyChanger,
    private val updateChecker: UpdateChecker
) : ViewModel() {

    private val _updateCheck = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)

    val updateCheck: StateFlow<UpdateCheckState> = _updateCheck.asStateFlow()

    fun checkForUpdate(currentVersionCode: Long) {
        if (_updateCheck.value is UpdateCheckState.Checking) return
        viewModelScope.launch {
            _updateCheck.value = UpdateCheckState.Checking
            _updateCheck.value = UpdateCheckState.Done(updateChecker.check(currentVersionCode))
        }
    }

    fun dismissUpdateCheck() {
        _updateCheck.value = UpdateCheckState.Idle
    }

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

    fun setProfileEmoji(emoji: String) {
        viewModelScope.launch {
            photoStore.delete(settingsRepository.profile.first().photoPath)
            settingsRepository.setProfilePhoto(
                by.mlastovsky.kosht.ui.components.EMOJI_AVATAR_PREFIX + emoji
            )
        }
    }

    fun removeProfilePhoto() {
        viewModelScope.launch {
            photoStore.delete(settingsRepository.profile.first().photoPath)
            settingsRepository.setProfilePhoto(null)
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

    /** Switches the app currency, rescaling stored amounts by the NBRB rate. */
    fun setCurrency(code: String) {
        viewModelScope.launch { currencyChanger.change(code) }
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

    fun setDailyBudget(minor: Long) {
        viewModelScope.launch { settingsRepository.setDailyBudgetMinor(minor) }
    }

    fun setShowGreeting(value: Boolean) {
        viewModelScope.launch { settingsRepository.setShowGreeting(value) }
    }

    fun setShowStreak(value: Boolean) {
        viewModelScope.launch { settingsRepository.setShowStreak(value) }
    }

    fun setShowRates(value: Boolean) {
        viewModelScope.launch { settingsRepository.setShowRates(value) }
    }

    fun setConvertOnCurrencyChange(value: Boolean) {
        viewModelScope.launch { settingsRepository.setConvertOnCurrencyChange(value) }
    }

    fun setAutoCalculator(value: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoCalculator(value) }
    }

    fun setReportPeriod(period: by.mlastovsky.kosht.ui.stats.ReportPeriod) {
        viewModelScope.launch { settingsRepository.setReportPeriod(period.name) }
    }

    fun setReportFields(fields: Set<by.mlastovsky.kosht.model.ReportField>) {
        viewModelScope.launch {
            settingsRepository.setReportFields(fields.map { it.name }.toSet())
        }
    }

    companion object {
        val SUPPORTED_CURRENCIES = listOf("BYN", "USD", "EUR", "PLN", "UAH", "RUB", "GBP", "KZT")
    }
}

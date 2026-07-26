package by.mlastovsky.kosht.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.mlastovsky.kosht.data.AppSettings
import by.mlastovsky.kosht.data.CurrencyChanger
import by.mlastovsky.kosht.data.InstallEvents
import by.mlastovsky.kosht.data.InstallOutcome
import by.mlastovsky.kosht.data.PhotoStore
import by.mlastovsky.kosht.data.SettingsRepository
import by.mlastovsky.kosht.data.UpdateChecker
import by.mlastovsky.kosht.data.UpdateInstaller
import by.mlastovsky.kosht.data.UpdateStatus
import by.mlastovsky.kosht.data.UserProfile
import by.mlastovsky.kosht.model.AppLanguage
import by.mlastovsky.kosht.model.ReportField
import by.mlastovsky.kosht.model.ThemeMode
import by.mlastovsky.kosht.ui.components.EMOJI_AVATAR_PREFIX
import by.mlastovsky.kosht.ui.stats.ReportPeriod
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState

    data object Checking : UpdateCheckState

    data class Done(val status: UpdateStatus) : UpdateCheckState

    data class Downloading(
        val available: UpdateStatus.Available,
        val percent: Int
    ) : UpdateCheckState

    data class Installing(val available: UpdateStatus.Available) : UpdateCheckState

    data class UpdateFailed(val available: UpdateStatus.Available) : UpdateCheckState

    data object SignatureMismatch : UpdateCheckState

    data class NeedsInstallPermission(
        val available: UpdateStatus.Available
    ) : UpdateCheckState
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val photoStore: PhotoStore,
    private val currencyChanger: CurrencyChanger,
    private val updateChecker: UpdateChecker,
    private val updateInstaller: UpdateInstaller
) : ViewModel() {

    private val _updateCheck = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)

    val updateCheck: StateFlow<UpdateCheckState> = _updateCheck.asStateFlow()

    private var updateJob: Job? = null

    init {

        viewModelScope.launch {
            InstallEvents.outcomes.collect { outcome ->
                val installing = _updateCheck.value as? UpdateCheckState.Installing ?: return@collect
                _updateCheck.value = when (outcome) {
                    InstallOutcome.Cancelled -> UpdateCheckState.Idle
                    InstallOutcome.SignatureMismatch ->
                        UpdateCheckState.SignatureMismatch
                    InstallOutcome.Failed ->
                        UpdateCheckState.UpdateFailed(installing.available)
                }
            }
        }
    }

    fun checkForUpdate(currentVersionCode: Long) {
        if (_updateCheck.value !is UpdateCheckState.Idle) return
        viewModelScope.launch {
            _updateCheck.value = UpdateCheckState.Checking
            _updateCheck.value = UpdateCheckState.Done(updateChecker.check(currentVersionCode))
        }
    }

    fun installUpdate(available: UpdateStatus.Available) {
        if (!updateInstaller.canInstall()) {
            _updateCheck.value = UpdateCheckState.NeedsInstallPermission(available)
            return
        }
        updateJob?.cancel()
        updateJob = viewModelScope.launch {
            _updateCheck.value = UpdateCheckState.Downloading(available, 0)
            val apk = updateInstaller.download(available.downloadUrl) { percent ->
                _updateCheck.value = UpdateCheckState.Downloading(available, percent)
            }
            if (apk == null) {
                _updateCheck.value = UpdateCheckState.UpdateFailed(available)
                return@launch
            }

            if (!updateInstaller.signedWithSameKey(apk)) {
                apk.delete()
                _updateCheck.value = UpdateCheckState.SignatureMismatch
                return@launch
            }
            _updateCheck.value = UpdateCheckState.Installing(available)
            if (!updateInstaller.install(apk)) {
                _updateCheck.value = UpdateCheckState.UpdateFailed(available)
            }
        }
    }

    fun unknownSourcesIntent(): android.content.Intent = updateInstaller.unknownSourcesIntent()

    fun dismissUpdateCheck() {
        updateJob?.cancel()
        updateJob = null
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
                EMOJI_AVATAR_PREFIX + emoji
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

    fun setNotifyAwards(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setNotifyAwards(enabled) }
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

    fun setTransferFee(value: Boolean) {
        viewModelScope.launch { settingsRepository.setTransferFee(value) }
    }

    fun setReportPeriod(period: ReportPeriod) {
        viewModelScope.launch { settingsRepository.setReportPeriod(period.name) }
    }

    fun setReportFields(fields: Set<ReportField>) {
        viewModelScope.launch {
            settingsRepository.setReportFields(fields.map { it.name }.toSet())
        }
    }

    companion object {
        val SUPPORTED_CURRENCIES = listOf("BYN", "USD", "EUR", "PLN", "UAH", "RUB", "GBP", "KZT")
    }
}

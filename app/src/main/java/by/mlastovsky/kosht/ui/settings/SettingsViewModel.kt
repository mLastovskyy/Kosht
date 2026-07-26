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

/** State of the update flow behind the version row: check, download, install. */
sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState

    data object Checking : UpdateCheckState

    data class Done(val status: UpdateStatus) : UpdateCheckState

    /** Streaming the APK into app cache; percent is -1 while size is unknown. */
    data class Downloading(
        val available: UpdateStatus.Available,
        val percent: Int
    ) : UpdateCheckState

    /** Bytes handed to the system installer; it takes over from here. */
    data class Installing(val available: UpdateStatus.Available) : UpdateCheckState

    /** Download or install did not go through — offer the same release again. */
    data class UpdateFailed(val available: UpdateStatus.Available) : UpdateCheckState

    /** The release is signed with another key; no retry can fix that. */
    data object SignatureMismatch : UpdateCheckState

    /** "Install unknown apps" is still off for Kosht; needs a one-time grant. */
    data class NeedsInstallPermission(
        val available: UpdateStatus.Available
    ) : UpdateCheckState
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val photoStore: PhotoStore,
    private val currencyChanger: by.mlastovsky.kosht.data.CurrencyChanger,
    private val updateChecker: UpdateChecker,
    private val updateInstaller: by.mlastovsky.kosht.data.UpdateInstaller
) : ViewModel() {

    private val _updateCheck = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)

    val updateCheck: StateFlow<UpdateCheckState> = _updateCheck.asStateFlow()

    private var updateJob: kotlinx.coroutines.Job? = null

    init {
        // The package installer answers asynchronously, through a broadcast.
        viewModelScope.launch {
            by.mlastovsky.kosht.data.InstallEvents.outcomes.collect { outcome ->
                val installing = _updateCheck.value as? UpdateCheckState.Installing ?: return@collect
                _updateCheck.value = when (outcome) {
                    by.mlastovsky.kosht.data.InstallOutcome.Cancelled -> UpdateCheckState.Idle
                    by.mlastovsky.kosht.data.InstallOutcome.SignatureMismatch ->
                        UpdateCheckState.SignatureMismatch
                    by.mlastovsky.kosht.data.InstallOutcome.Failed ->
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

    /** Downloads the release inside the app and hands it to the installer. */
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
            // Cheaper and clearer than letting the system reject it later.
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

package by.mlastovsky.kosht

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.mlastovsky.kosht.data.AppSettings
import by.mlastovsky.kosht.data.SettingsRepository
import by.mlastovsky.kosht.data.sync.SyncAccountRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val settingsRepository: SettingsRepository,
    private val accounts: SyncAccountRepository
) : ViewModel() {

    /** null until the first DataStore emission; the splash screen waits for it. */
    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .map { it as AppSettings? }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** True once Android has been asked about notifications, ever. */
    val notificationsAsked: StateFlow<Boolean> = settingsRepository.notificationsAsked
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /**
     * True when the Terms and the data policy have changed since the version
     * this person was last shown, which is what puts the notice on screen.
     */
    val policyUpdated: StateFlow<Boolean> = settingsRepository.policyVersionSeen
        .map { seen -> seen != null && seen != SyncAccountRepository.POLICY_VERSION }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        // A device with no version recorded has just arrived on the current
        // documents -- at sign-up it accepted them, and without an account they
        // are what it was installed with. So it is stamped rather than asked,
        // and the notice appears the first time the documents actually change.
        viewModelScope.launch {
            if (settingsRepository.policyVersionSeen.first() == null) {
                settingsRepository.setPolicyVersionSeen(SyncAccountRepository.POLICY_VERSION)
            }
        }
        // A face out of the box, dealt once. An avatar that arrives from the
        // account later still wins: applySynced prefers the remote emoji.
        viewModelScope.launch {
            settingsRepository.seedAvatarOnce {
                by.mlastovsky.kosht.ui.components.EMOJI_AVATAR_PREFIX +
                    by.mlastovsky.kosht.ui.components.PRESET_AVATARS.random()
            }
        }
    }

    fun markNotificationsAsked() {
        viewModelScope.launch { settingsRepository.markNotificationsAsked() }
    }

    /** Remembers that the updated documents were shown, and records it. */
    fun acknowledgePolicy() {
        viewModelScope.launch {
            settingsRepository.setPolicyVersionSeen(SyncAccountRepository.POLICY_VERSION)
            accounts.recordPolicyAcceptance()
        }
    }
}

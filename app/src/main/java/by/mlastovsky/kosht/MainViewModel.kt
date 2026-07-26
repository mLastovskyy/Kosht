package by.mlastovsky.kosht

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.mlastovsky.kosht.data.AppSettings
import by.mlastovsky.kosht.data.SettingsRepository
import by.mlastovsky.kosht.data.sync.SyncAccountRepository
import by.mlastovsky.kosht.ui.components.EMOJI_AVATAR_PREFIX
import by.mlastovsky.kosht.ui.components.PRESET_AVATARS
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

    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .map { it as AppSettings? }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val notificationsAsked: StateFlow<Boolean> = settingsRepository.notificationsAsked
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val policyUpdated: StateFlow<Boolean> = settingsRepository.policyVersionSeen
        .map { seen -> seen != null && seen != SyncAccountRepository.POLICY_VERSION }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {

        viewModelScope.launch {
            if (settingsRepository.policyVersionSeen.first() == null) {
                settingsRepository.setPolicyVersionSeen(SyncAccountRepository.POLICY_VERSION)
            }
        }

        viewModelScope.launch {
            settingsRepository.seedAvatarOnce {
                EMOJI_AVATAR_PREFIX +
                    PRESET_AVATARS.random()
            }
        }
    }

    fun markNotificationsAsked() {
        viewModelScope.launch { settingsRepository.markNotificationsAsked() }
    }

    fun acknowledgePolicy() {
        viewModelScope.launch {
            settingsRepository.setPolicyVersionSeen(SyncAccountRepository.POLICY_VERSION)
            accounts.recordPolicyAcceptance()
        }
    }
}

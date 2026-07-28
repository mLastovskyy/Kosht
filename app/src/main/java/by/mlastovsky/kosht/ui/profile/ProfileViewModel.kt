package by.mlastovsky.kosht.ui.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.mlastovsky.kosht.data.PhotoStore
import by.mlastovsky.kosht.data.SettingsRepository
import by.mlastovsky.kosht.data.UserProfile
import by.mlastovsky.kosht.ui.components.EMOJI_AVATAR_PREFIX
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val settingsRepository: SettingsRepository,
    private val photoStore: PhotoStore
) : ViewModel() {

    val profile: StateFlow<UserProfile?> = settingsRepository.profile
        .map { it as UserProfile? }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(nickname: String) {
        viewModelScope.launch { settingsRepository.setProfile(nickname) }
    }

    fun setPhoto(uri: Uri) {
        viewModelScope.launch {
            val old = settingsRepository.profile.first().photoPath
            val saved = photoStore.saveFromUri(uri, subdir = "profile") ?: return@launch
            photoStore.delete(old)
            settingsRepository.setProfilePhoto(saved)
        }
    }

    fun setEmoji(emoji: String) {
        viewModelScope.launch {
            photoStore.delete(settingsRepository.profile.first().photoPath)
            settingsRepository.setProfilePhoto(EMOJI_AVATAR_PREFIX + emoji)
        }
    }

    fun removePhoto() {
        viewModelScope.launch {
            photoStore.delete(settingsRepository.profile.first().photoPath)
            settingsRepository.setProfilePhoto(null)
        }
    }
}

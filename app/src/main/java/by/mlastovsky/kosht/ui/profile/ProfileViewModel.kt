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

/**
 * The name, nickname and avatar — everything the profile dialog needs, wherever
 * that dialog is opened from. It lives on its own rather than inside the
 * settings view model precisely so that tapping the avatar on Home does not
 * have to drag the whole settings screen along with it.
 */
class ProfileViewModel(
    private val settingsRepository: SettingsRepository,
    private val photoStore: PhotoStore
) : ViewModel() {

    /** null until the first DataStore emission, so nothing flashes empty. */
    val profile: StateFlow<UserProfile?> = settingsRepository.profile
        .map { it as UserProfile? }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(name: String, nickname: String) {
        viewModelScope.launch { settingsRepository.setProfile(name, nickname) }
    }

    /** A picked picture replaces whatever was there, file and all. */
    fun setPhoto(uri: Uri) {
        viewModelScope.launch {
            val old = settingsRepository.profile.first().photoPath
            val saved = photoStore.saveFromUri(uri, subdir = "profile") ?: return@launch
            photoStore.delete(old)
            settingsRepository.setProfilePhoto(saved)
        }
    }

    /** A built-in avatar is just text, so the stored picture can go. */
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

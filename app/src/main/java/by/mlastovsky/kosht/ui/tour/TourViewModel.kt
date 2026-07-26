package by.mlastovsky.kosht.ui.tour

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.mlastovsky.kosht.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TourViewModel(private val settings: SettingsRepository) : ViewModel() {

    val seen: StateFlow<Boolean?> = settings.tourSeen
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun finish() {
        viewModelScope.launch { settings.setTourSeen(true) }
    }
}

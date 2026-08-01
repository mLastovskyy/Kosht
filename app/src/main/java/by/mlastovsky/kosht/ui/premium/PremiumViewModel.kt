package by.mlastovsky.kosht.ui.premium

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.mlastovsky.kosht.data.PremiumRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class PremiumViewModel(repository: PremiumRepository) : ViewModel() {

    val premium: StateFlow<Boolean> = repository.premium
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
}

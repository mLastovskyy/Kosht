package by.mlastovsky.kosht.ui.awards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.mlastovsky.kosht.data.awards.AwardTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AwardsViewModel(tracker: AwardTracker) : ViewModel() {

    private val _queue = MutableStateFlow<List<String>>(emptyList())

    val queue: StateFlow<List<String>> = _queue.asStateFlow()

    init {
        viewModelScope.launch {
            tracker.unlocked.collect { key ->

                _queue.update { if (key in it) it else it + key }
            }
        }
    }

    fun dismissFirst() {
        _queue.update { it.drop(1) }
    }
}

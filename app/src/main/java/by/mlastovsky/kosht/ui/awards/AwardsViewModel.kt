package by.mlastovsky.kosht.ui.awards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.mlastovsky.kosht.data.awards.AwardTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds the awards earned while the app was on screen, so each gets its own
 * moment instead of the last one winning. Earning two at once happens — the
 * hundredth record can also be the tenth photograph.
 */
class AwardsViewModel(tracker: AwardTracker) : ViewModel() {

    private val _queue = MutableStateFlow<List<String>>(emptyList())

    /** Award keys waiting to be shown, oldest first. */
    val queue: StateFlow<List<String>> = _queue.asStateFlow()

    init {
        viewModelScope.launch {
            tracker.unlocked.collect { key ->
                // Guard against a repeat of what is already queued: the same
                // award can never be earned twice.
                _queue.update { if (key in it) it else it + key }
            }
        }
    }

    fun dismissFirst() {
        _queue.update { it.drop(1) }
    }
}

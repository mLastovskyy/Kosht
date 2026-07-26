package by.mlastovsky.kosht.data.lock

import by.mlastovsky.kosht.model.LockTimeout
import by.mlastovsky.kosht.util.Pin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface LockState {

    data object Unknown : LockState

    data object Off : LockState

    data object Locked : LockState

    data object Open : LockState
}

class AppLock(
    private val repository: AppLockRepository,
    private val scope: CoroutineScope
) {

    val settings: StateFlow<AppLockSettings?> = repository.settings
        .stateIn(scope, SharingStarted.Eagerly, null)

    private val unlocked = MutableStateFlow(false)

    val state: StateFlow<LockState> = combine(settings, unlocked) { current, open ->
        when {
            current == null -> LockState.Unknown
            !current.enabled -> LockState.Off
            open -> LockState.Open
            else -> LockState.Locked
        }
    }.stateIn(scope, SharingStarted.Eagerly, LockState.Unknown)

    private var leftAt: Long? = null

    private var expectingResult = false

    fun onBackground(now: Long = System.currentTimeMillis()) {
        leftAt = now
    }

    fun onForeground(now: Long = System.currentTimeMillis()) {

        val away = leftAt?.let { now - it } ?: Long.MAX_VALUE
        val timeout = settings.value?.timeoutMillis
            ?: LockTimeout.millis(LockTimeout.DEFAULT_MINUTES)
        if (Pin.shouldLock(away, timeout, expectingResult)) unlocked.value = false
        leftAt = null
        expectingResult = false
    }

    fun expectExternalResult() {
        expectingResult = true
    }

    fun open() {
        unlocked.value = true
        scope.launch { repository.clearFailures() }
    }

    fun lockNow() {
        unlocked.value = false
    }

    suspend fun submit(pin: String): Boolean {
        val current = repository.settings.first()
        val salt = current.pinSalt
        val hash = current.pinHash
        if (salt == null || hash == null) return true
        val correct = withContext(Dispatchers.Default) { Pin.verify(pin, salt, hash) }
        if (correct) open() else repository.recordFailure(System.currentTimeMillis())
        return correct
    }
}

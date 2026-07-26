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
    /** The stored answer is not in yet; the splash screen waits for it. */
    data object Unknown : LockState

    /** No code is set — Kosht opens straight onto Home, as it always did. */
    data object Off : LockState

    data object Locked : LockState

    data object Open : LockState
}

/**
 * Whether the app is open or locked, kept for the whole process rather than
 * inside the activity: recreating the activity — which is how a language change
 * is applied — must not count as a way in, and a cold start must always ask.
 */
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

    /** When the app went into the background; null before it ever came back. */
    private var leftAt: Long? = null

    private var expectingResult = false

    fun onBackground(now: Long = System.currentTimeMillis()) {
        leftAt = now
    }

    fun onForeground(now: Long = System.currentTimeMillis()) {
        // Never having been in the foreground is a cold start: always ask.
        val away = leftAt?.let { now - it } ?: Long.MAX_VALUE
        val timeout = settings.value?.timeoutMillis
            ?: LockTimeout.millis(LockTimeout.DEFAULT_MINUTES)
        if (Pin.shouldLock(away, timeout, expectingResult)) unlocked.value = false
        leftAt = null
        expectingResult = false
    }

    /**
     * Kosht is about to open a screen of another app — the camera, the gallery,
     * the install-permission page. Coming back from one of those is not really
     * coming back, so the code is not demanded for it. See [Pin.shouldLock].
     */
    fun expectExternalResult() {
        expectingResult = true
    }

    /** The person is here: after the right code, a finger, or setting a code. */
    fun open() {
        unlocked.value = true
        scope.launch { repository.clearFailures() }
    }

    fun lockNow() {
        unlocked.value = false
    }

    /**
     * Checks a typed code, off the main thread — the digest is slow on purpose.
     * A wrong one is counted, which is what makes the next tries wait.
     */
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

package by.mlastovsky.kosht.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.mlastovsky.kosht.data.lock.AppLock
import by.mlastovsky.kosht.data.lock.AppLockRepository
import by.mlastovsky.kosht.data.lock.AppLockSettings
import by.mlastovsky.kosht.data.lock.LockState
import by.mlastovsky.kosht.util.Pin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PinGoal { CREATE, CHANGE, DISABLE }

enum class PinStep {

    CURRENT,

    NEW,

    REPEAT
}

enum class PinError { WRONG_CODE, MISMATCH, TOO_SHORT }

data class PinSetup(
    val goal: PinGoal,
    val step: PinStep,
    val entered: String = "",

    val first: String = "",
    val error: PinError? = null,
    val busy: Boolean = false
)

data class LockEntry(
    val entered: String = "",

    val wrong: Boolean = false,

    val message: String? = null,
    val busy: Boolean = false
)

class AppLockViewModel(
    private val appLock: AppLock,
    private val repository: AppLockRepository
) : ViewModel() {

    val settings: StateFlow<AppLockSettings?> = appLock.settings

    val state: StateFlow<LockState> = appLock.state

    private val _entry = MutableStateFlow(LockEntry())

    val entry: StateFlow<LockEntry> = _entry.asStateFlow()

    private val _setup = MutableStateFlow<PinSetup?>(null)

    val setup: StateFlow<PinSetup?> = _setup.asStateFlow()

    fun typeDigit(digit: Char) {
        val current = _entry.value
        val length = settings.value?.pinLength ?: Pin.MIN_LENGTH
        if (current.busy || current.entered.length >= length) return
        val entered = current.entered + digit
        _entry.value = LockEntry(entered = entered, busy = entered.length == length)
        if (entered.length == length) check(entered)
    }

    fun backspace() {
        val current = _entry.value
        if (current.busy) return
        _entry.value = current.copy(entered = current.entered.dropLast(1), wrong = false)
    }

    private fun check(pin: String) {
        viewModelScope.launch {
            if (appLock.submit(pin)) {
                _entry.value = LockEntry()
            } else {
                _entry.value = LockEntry(wrong = true)
            }
        }
    }

    fun biometricAccepted() {
        _entry.value = LockEntry()
        appLock.open()
    }

    fun biometricRefused(message: String?) {
        _entry.value = _entry.value.copy(message = message)
    }

    fun clearMessage() {
        _entry.value = _entry.value.copy(message = null, wrong = false)
    }

    fun startCreate() {
        _setup.value = PinSetup(goal = PinGoal.CREATE, step = PinStep.NEW)
    }

    fun startChange() {
        _setup.value = PinSetup(goal = PinGoal.CHANGE, step = PinStep.CURRENT)
    }

    fun startDisable() {
        _setup.value = PinSetup(goal = PinGoal.DISABLE, step = PinStep.CURRENT)
    }

    fun cancelSetup() {
        _setup.value = null
    }

    fun setupDigit(digit: Char) {
        val current = _setup.value ?: return
        if (current.busy || current.entered.length >= Pin.MAX_LENGTH) return
        val entered = current.entered + digit
        _setup.value = current.copy(entered = entered, error = null)

        val length = settings.value?.pinLength ?: Pin.MIN_LENGTH
        if (current.step == PinStep.CURRENT && entered.length == length) confirmSetup()
    }

    fun setupBackspace() {
        val current = _setup.value ?: return
        if (current.busy) return
        _setup.value = current.copy(entered = current.entered.dropLast(1), error = null)
    }

    fun confirmSetup() {
        val current = _setup.value ?: return
        if (current.busy) return
        when (current.step) {
            PinStep.CURRENT -> verifyCurrent(current)
            PinStep.NEW -> {
                if (!Pin.isValid(current.entered)) {
                    _setup.value = current.copy(error = PinError.TOO_SHORT)
                    return
                }
                _setup.value = current.copy(
                    step = PinStep.REPEAT,
                    first = current.entered,
                    entered = "",
                    error = null
                )
            }

            PinStep.REPEAT -> {
                if (current.entered != current.first) {
                    _setup.value = current.copy(
                        step = PinStep.NEW,
                        entered = "",
                        first = "",
                        error = PinError.MISMATCH
                    )
                    return
                }
                save(current, current.entered)
            }
        }
    }

    private fun verifyCurrent(current: PinSetup) {
        val stored = settings.value ?: return
        val salt = stored.pinSalt
        val hash = stored.pinHash
        if (salt == null || hash == null) return
        _setup.value = current.copy(busy = true)
        viewModelScope.launch {
            val correct = withContext(Dispatchers.Default) {
                Pin.verify(current.entered, salt, hash)
            }
            if (!correct) {
                _setup.value = current.copy(entered = "", error = PinError.WRONG_CODE, busy = false)
                return@launch
            }
            when (current.goal) {
                PinGoal.DISABLE -> {
                    repository.clear()
                    _setup.value = null
                }

                else -> _setup.value = current.copy(
                    step = PinStep.NEW,
                    entered = "",
                    error = null,
                    busy = false
                )
            }
        }
    }

    private fun save(current: PinSetup, pin: String) {
        _setup.value = current.copy(busy = true)
        viewModelScope.launch {
            val salt = Pin.newSalt()
            val hash = withContext(Dispatchers.Default) { Pin.hash(pin, salt) }

            appLock.open()
            repository.setPin(hash = hash, salt = Pin.encode(salt), length = pin.length)
            _setup.value = null
        }
    }

    fun setBiometrics(enabled: Boolean) {
        viewModelScope.launch { repository.setBiometrics(enabled) }
    }

    fun setTimeoutMinutes(minutes: Int) {
        viewModelScope.launch { repository.setTimeoutMinutes(minutes) }
    }
}

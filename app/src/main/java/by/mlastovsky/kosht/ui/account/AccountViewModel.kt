package by.mlastovsky.kosht.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.mlastovsky.kosht.data.sync.AccountState
import by.mlastovsky.kosht.data.sync.AuthOutcome
import by.mlastovsky.kosht.data.sync.SyncAccountRepository
import by.mlastovsky.kosht.data.sync.SyncEngine
import by.mlastovsky.kosht.data.sync.SyncOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the sign-in / sign-up form is doing right now. */
sealed interface AuthFormState {
    data object Idle : AuthFormState

    data object Busy : AuthFormState

    /** The address needs confirming before the account can be used. */
    data object ConfirmEmail : AuthFormState

    data class Rejected(val message: String) : AuthFormState

    data object Offline : AuthFormState
}

/** Result of the last manual "sync now", shown once and then cleared. */
sealed interface SyncReport {
    data class Done(val received: Int) : SyncReport

    data object Offline : SyncReport

    data class Failed(val message: String) : SyncReport
}

class AccountViewModel(
    private val accounts: SyncAccountRepository,
    private val syncEngine: SyncEngine
) : ViewModel() {

    val account: StateFlow<AccountState?> = accounts.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val lastSyncAt: StateFlow<Long> = syncEngine.lastSyncAt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _form = MutableStateFlow<AuthFormState>(AuthFormState.Idle)
    val form: StateFlow<AuthFormState> = _form.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _report = MutableStateFlow<SyncReport?>(null)
    val report: StateFlow<SyncReport?> = _report.asStateFlow()

    val isConfigured: Boolean get() = accounts.isConfigured

    fun signIn(email: String, password: String) = authenticate { accounts.signIn(email, password) }

    fun signUp(email: String, password: String) = authenticate { accounts.signUp(email, password) }

    private fun authenticate(call: suspend () -> AuthOutcome) {
        if (_form.value is AuthFormState.Busy) return
        viewModelScope.launch {
            _form.value = AuthFormState.Busy
            when (val outcome = call()) {
                is AuthOutcome.Success -> {
                    // A device joining an account has to see everything that
                    // is already there, so both watermarks start over.
                    syncEngine.resetCursor()
                    _form.value = AuthFormState.Idle
                    syncNow()
                }

                AuthOutcome.ConfirmEmail -> _form.value = AuthFormState.ConfirmEmail
                AuthOutcome.Offline -> _form.value = AuthFormState.Offline
                is AuthOutcome.Rejected -> _form.value = AuthFormState.Rejected(outcome.message)
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            accounts.signOut()
            // Local data stays; only the link to the cloud copy is dropped.
            syncEngine.resetCursor()
        }
    }

    fun setAutoSync(enabled: Boolean) {
        viewModelScope.launch { accounts.setAutoSync(enabled) }
    }

    /** "Continue without an account" — the app stays fully usable offline. */
    fun skipAccount() {
        viewModelScope.launch { accounts.markOnboarded() }
    }

    fun syncNow() {
        if (_syncing.value) return
        viewModelScope.launch {
            _syncing.value = true
            _report.value = when (val outcome = syncEngine.sync()) {
                is SyncOutcome.Success -> SyncReport.Done(outcome.received)
                SyncOutcome.Offline -> SyncReport.Offline
                SyncOutcome.NotSignedIn -> null
                is SyncOutcome.Failed -> SyncReport.Failed(outcome.message)
            }
            _syncing.value = false
        }
    }

    fun clearForm() {
        _form.value = AuthFormState.Idle
    }

    fun clearReport() {
        _report.value = null
    }
}

package by.mlastovsky.kosht.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.mlastovsky.kosht.data.sync.AccountState
import by.mlastovsky.kosht.data.sync.AuthError
import by.mlastovsky.kosht.data.sync.AuthOutcome
import by.mlastovsky.kosht.data.sync.CodePurpose
import by.mlastovsky.kosht.data.sync.SyncAccountRepository
import by.mlastovsky.kosht.data.sync.SyncEngine
import by.mlastovsky.kosht.data.sync.SyncOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Where the user currently is in the sign-in / sign-up conversation. */
sealed interface AuthStep {

    /** Email and password at once — the everyday way back in. */
    data object SignIn : AuthStep

    /** Ask for the address; [purpose] decides which code is sent next. */
    data class Email(val purpose: CodePurpose) : AuthStep

    data class Code(
        val email: String,
        val purpose: CodePurpose,
        /** Epoch millis the code was sent, for the countdown. */
        val sentAt: Long
    ) : AuthStep

    /** Address is confirmed; all that is left is a password. */
    data class NewPassword(val email: String, val purpose: CodePurpose) : AuthStep
}

/**
 * Something the user has to be told, with the way out implied. Server text is
 * English and phrased for developers, so everything recognisable becomes a
 * proper message and only genuinely unknown failures show the raw detail.
 */
sealed interface AuthMessage {
    /** Signing up with an address that already has an account. */
    data object EmailTaken : AuthMessage

    /** Resetting the password of an address nobody signed up with. */
    data object EmailUnknown : AuthMessage

    data object WrongCode : AuthMessage

    data object CodeExpired : AuthMessage

    data object TooManyRequests : AuthMessage

    data object WrongCredentials : AuthMessage

    data object WeakPassword : AuthMessage

    data object Offline : AuthMessage

    data class Other(val detail: String) : AuthMessage
}

data class AuthUiState(
    val step: AuthStep,
    val email: String = "",
    val busy: Boolean = false,
    val message: AuthMessage? = null,
    /** Ticked on the sign-up form; nothing is sent until it is. */
    val acceptedTerms: Boolean = false,
    /** Separate and optional, as advertising consent has to be. */
    val marketingOptIn: Boolean = false
)

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

    /** Null while no sign-in or sign-up is in progress. */
    private val _auth = MutableStateFlow<AuthUiState?>(null)
    val auth: StateFlow<AuthUiState?> = _auth.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _report = MutableStateFlow<SyncReport?>(null)
    val report: StateFlow<SyncReport?> = _report.asStateFlow()

    val isConfigured: Boolean get() = accounts.isConfigured

    // ---- Entering and leaving the flow ------------------------------------

    fun startSignIn(email: String = "") {
        _auth.value = AuthUiState(AuthStep.SignIn, email = email)
    }

    fun startSignUp(email: String = "") {
        _auth.value = AuthUiState(AuthStep.Email(CodePurpose.SignUp), email = email)
    }

    fun startPasswordReset(email: String = "") {
        _auth.value = AuthUiState(AuthStep.Email(CodePurpose.Reset), email = email)
    }

    /** Back from the code screen to fix a mistyped address. */
    fun changeEmail() {
        val current = _auth.value ?: return
        val purpose = (current.step as? AuthStep.Code)?.purpose ?: CodePurpose.SignUp
        _auth.value = current.copy(step = AuthStep.Email(purpose), message = null, busy = false)
    }

    fun closeAuth() {
        _auth.value = null
    }

    fun clearMessage() {
        _auth.value = _auth.value?.copy(message = null)
    }

    fun setAcceptedTerms(accepted: Boolean) {
        _auth.value = _auth.value?.copy(acceptedTerms = accepted)
    }

    fun setMarketingOptIn(optIn: Boolean) {
        _auth.value = _auth.value?.copy(marketingOptIn = optIn)
    }

    // ---- Steps ------------------------------------------------------------

    /**
     * Checks whether the address is free before sending anything. Without it
     * signing up with an existing address would quietly turn into a sign-in
     * code, and resetting an unknown one would appear to work and never
     * deliver.
     */
    fun submitEmail(email: String) {
        val current = _auth.value ?: return
        val step = current.step as? AuthStep.Email ?: return
        if (current.busy) return
        viewModelScope.launch {
            _auth.value = current.copy(email = email, busy = true, message = null)
            val registered = accounts.emailRegistered(email)
            val clash = when {
                step.purpose == CodePurpose.SignUp && registered == true ->
                    AuthMessage.EmailTaken

                step.purpose == CodePurpose.Reset && registered == false ->
                    AuthMessage.EmailUnknown

                else -> null
            }
            if (clash != null) {
                _auth.value = current.copy(email = email, busy = false, message = clash)
                return@launch
            }
            val outcome = when (step.purpose) {
                CodePurpose.SignUp -> accounts.sendSignUpCode(email)
                CodePurpose.Reset -> accounts.sendResetCode(email)
            }
            _auth.value = when (outcome) {
                AuthOutcome.CodeSent -> AuthUiState(
                    step = AuthStep.Code(email, step.purpose, System.currentTimeMillis()),
                    email = email
                )

                else -> current.copy(email = email, busy = false, message = outcome.toMessage())
            }
        }
    }

    fun resendCode() {
        val current = _auth.value ?: return
        val step = current.step as? AuthStep.Code ?: return
        if (current.busy) return
        viewModelScope.launch {
            _auth.value = current.copy(busy = true, message = null)
            val outcome = when (step.purpose) {
                CodePurpose.SignUp -> accounts.sendSignUpCode(step.email)
                CodePurpose.Reset -> accounts.sendResetCode(step.email)
            }
            _auth.value = when (outcome) {
                AuthOutcome.CodeSent -> current.copy(
                    step = step.copy(sentAt = System.currentTimeMillis()),
                    busy = false
                )

                else -> current.copy(busy = false, message = outcome.toMessage())
            }
        }
    }

    fun submitCode(code: String) {
        val current = _auth.value ?: return
        val step = current.step as? AuthStep.Code ?: return
        if (current.busy) return
        // The server decides too, but a code this device knows is stale is
        // not worth a round trip -- or a confusing error.
        if (System.currentTimeMillis() - step.sentAt >= CODE_LIFETIME_MS) {
            _auth.value = current.copy(message = AuthMessage.CodeExpired)
            return
        }
        viewModelScope.launch {
            _auth.value = current.copy(busy = true, message = null)
            _auth.value = when (val outcome =
                accounts.verifyCode(step.email, code, step.purpose)) {
                is AuthOutcome.Success -> current.copy(
                    step = AuthStep.NewPassword(step.email, step.purpose),
                    busy = false
                )

                else -> current.copy(busy = false, message = outcome.toMessage())
            }
        }
    }

    fun submitNewPassword(password: String) {
        val current = _auth.value ?: return
        val step = current.step as? AuthStep.NewPassword ?: return
        if (current.busy) return
        viewModelScope.launch {
            _auth.value = current.copy(busy = true, message = null)
            when (val outcome = accounts.setPassword(password)) {
                is AuthOutcome.Rejected, AuthOutcome.Offline ->
                    _auth.value = current.copy(busy = false, message = outcome.toMessage())

                else -> {
                    // Recorded once the account exists, so the ledger never
                    // holds consent for an account that was never created.
                    if (step.purpose == CodePurpose.SignUp) {
                        accounts.recordSignUpConsents(current.marketingOptIn)
                    }
                    finish()
                }
            }
        }
    }

    fun submitSignIn(email: String, password: String) {
        val current = _auth.value ?: return
        if (current.busy) return
        viewModelScope.launch {
            _auth.value = current.copy(email = email, busy = true, message = null)
            when (val outcome = accounts.signIn(email, password)) {
                is AuthOutcome.Success -> finish()
                else -> _auth.value =
                    current.copy(email = email, busy = false, message = outcome.toMessage())
            }
        }
    }

    /** Signed in for real: pull everything this device has not seen yet. */
    private suspend fun finish() {
        syncEngine.resetCursor()
        _auth.value = null
        syncNow()
    }

    private fun AuthOutcome.toMessage(): AuthMessage = when (this) {
        AuthOutcome.Offline -> AuthMessage.Offline
        is AuthOutcome.Rejected -> when (reason) {
            AuthError.WrongCode -> AuthMessage.WrongCode
            AuthError.TooManyRequests -> AuthMessage.TooManyRequests
            AuthError.WrongCredentials -> AuthMessage.WrongCredentials
            AuthError.WeakPassword -> AuthMessage.WeakPassword
            AuthError.EmailTaken -> AuthMessage.EmailTaken
            AuthError.Unknown -> AuthMessage.Other(detail)
        }

        else -> AuthMessage.Other("")
    }

    // ---- Account and sync -------------------------------------------------

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

    fun clearReport() {
        _report.value = null
    }

    // ---- Consent and data-subject rights ----------------------------------

    private val _marketing = MutableStateFlow<Boolean?>(null)

    /** Null until the answer is known, so the switch never guesses. */
    val marketingConsent: StateFlow<Boolean?> = _marketing.asStateFlow()

    private val _exported = MutableStateFlow<String?>(null)

    /** The account's data as JSON, once the user has asked for a copy. */
    val exported: StateFlow<String?> = _exported.asStateFlow()

    fun loadMarketingConsent() {
        viewModelScope.launch { _marketing.value = accounts.marketingConsent() ?: false }
    }

    fun setMarketingConsent(granted: Boolean) {
        viewModelScope.launch {
            // Optimistic, then corrected: refusing advertising has to feel
            // instant, and a failed write must not leave it looking accepted.
            _marketing.value = granted
            if (!accounts.setMarketingConsent(granted)) {
                _marketing.value = accounts.marketingConsent() ?: false
            }
        }
    }

    fun exportData() {
        viewModelScope.launch { _exported.value = accounts.exportData() }
    }

    fun clearExport() {
        _exported.value = null
    }

    fun deleteAccount(onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val deleted = accounts.deleteAccount()
            if (deleted) syncEngine.resetCursor()
            onDone(deleted)
        }
    }

    companion object {
        /** Codes are good for five minutes; the server is told the same. */
        const val CODE_LIFETIME_MS = 5 * 60 * 1000L

        /** Supabase refuses a second code sooner than this. */
        const val RESEND_COOLDOWN_MS = 60 * 1000L
    }
}

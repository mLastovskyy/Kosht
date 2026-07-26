package by.mlastovsky.kosht.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.mlastovsky.kosht.data.SettingsRepository
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

sealed interface AuthStep {

    data object SignIn : AuthStep

    data class Email(val purpose: CodePurpose) : AuthStep

    data class Code(
        val email: String,
        val purpose: CodePurpose,

        val sentAt: Long
    ) : AuthStep

    data class NewPassword(val email: String, val purpose: CodePurpose) : AuthStep
}

sealed interface AuthMessage {

    data object EmailTaken : AuthMessage

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

    val acceptedTerms: Boolean = false,

    val marketingOptIn: Boolean = false
)

sealed interface SyncReport {
    data class Done(val received: Int) : SyncReport

    data object Offline : SyncReport

    data class Failed(val message: String) : SyncReport
}

class AccountViewModel(
    private val accounts: SyncAccountRepository,
    private val syncEngine: SyncEngine,
    private val settings: SettingsRepository
) : ViewModel() {

    val account: StateFlow<AccountState?> = accounts.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val lastSyncAt: StateFlow<Long> = syncEngine.lastSyncAt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _auth = MutableStateFlow<AuthUiState?>(null)
    val auth: StateFlow<AuthUiState?> = _auth.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _report = MutableStateFlow<SyncReport?>(null)
    val report: StateFlow<SyncReport?> = _report.asStateFlow()

    val isConfigured: Boolean get() = accounts.isConfigured

    fun startSignIn(email: String = "") {
        _auth.value = AuthUiState(AuthStep.SignIn, email = email)
    }

    fun startSignUp(email: String = "") {
        _auth.value = AuthUiState(AuthStep.Email(CodePurpose.SignUp), email = email)
    }

    fun startPasswordReset(email: String = "") {
        _auth.value = AuthUiState(AuthStep.Email(CodePurpose.Reset), email = email)
    }

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

    fun signOut() {
        viewModelScope.launch {
            accounts.signOut()

            syncEngine.resetCursor()
        }
    }

    fun setAutoSync(enabled: Boolean) {
        viewModelScope.launch { accounts.setAutoSync(enabled) }
    }

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

    private val _marketing = MutableStateFlow<Boolean?>(null)

    val marketingConsent: StateFlow<Boolean?> = _marketing.asStateFlow()

    fun loadMarketingConsent() {
        viewModelScope.launch { _marketing.value = accounts.marketingConsent() ?: false }
    }

    fun setMarketingConsent(granted: Boolean) {
        viewModelScope.launch {

            _marketing.value = granted
            if (!accounts.setMarketingConsent(granted)) {
                _marketing.value = accounts.marketingConsent() ?: false
            }
        }
    }

    fun setPhotoSync(enabled: Boolean, onPurged: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            if (enabled) {
                settings.setSyncPhotos(true)
                accounts.setPhotoConsent(true)
            } else {
                accounts.setPhotoConsent(false)
                val purged = syncEngine.purgePhotos()
                settings.setSyncPhotos(false)
                onPurged(purged)
            }
        }
    }

    fun deleteAccount(onDone: (Boolean) -> Unit) {
        viewModelScope.launch {

            syncEngine.purgePhotos()
            val deleted = accounts.deleteAccount()
            if (deleted) syncEngine.resetCursor()
            onDone(deleted)
        }
    }

    companion object {

        const val CODE_LIFETIME_MS = 5 * 60 * 1000L

        const val RESEND_COOLDOWN_MS = 60 * 1000L
    }
}

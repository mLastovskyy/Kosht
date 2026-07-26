package by.mlastovsky.kosht.ui.account

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import by.mlastovsky.kosht.R
import by.mlastovsky.kosht.data.sync.CodePurpose
import by.mlastovsky.kosht.ui.AppViewModelProvider
import by.mlastovsky.kosht.util.AssetPdf
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private const val MIN_PASSWORD = 6
private const val CODE_LENGTH = 6

/**
 * First-launch question: cloud account or not. Kept as a plain full-screen
 * choice rather than a wizard — the app is fully usable either way, and the
 * answer can be changed later in Settings.
 */
@Composable
fun AccountOnboardingScreen(
    viewModel: AccountViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Rounded.CloudSync,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = { viewModel.startSignUp() },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.account_sign_up)) }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { viewModel.startSignIn() },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.onboarding_have_account)) }
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = viewModel::skipAccount,
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.onboarding_skip)) }
    }

    AuthDialog(viewModel)
}

/**
 * The whole sign-in / sign-up / password-reset conversation in one dialog,
 * one step at a time. Signing up never asks for a password before the
 * address is proven, and every dead end offers the way out of it: a taken
 * address offers signing in, a wrong password offers a reset.
 */
@Composable
fun AuthDialog(viewModel: AccountViewModel) {
    val state by viewModel.auth.collectAsStateWithLifecycle()
    val current = state ?: return

    when (val step = current.step) {
        AuthStep.SignIn -> SignInStep(current, viewModel)
        is AuthStep.Email -> EmailStep(step, current, viewModel)
        is AuthStep.Code -> CodeStep(step, current, viewModel)
        is AuthStep.NewPassword -> PasswordStep(current, viewModel)
    }
}

@Composable
private fun SignInStep(state: AuthUiState, viewModel: AccountViewModel) {
    var email by remember { mutableStateOf(state.email) }
    var password by remember { mutableStateOf("") }
    val canSubmit = email.isValidEmail() && password.isNotEmpty() && !state.busy

    AuthScaffold(
        title = stringResource(R.string.account_sign_in),
        busy = state.busy,
        message = state.message,
        confirmLabel = stringResource(R.string.account_sign_in),
        confirmEnabled = canSubmit,
        onConfirm = { viewModel.submitSignIn(email, password) },
        onDismiss = viewModel::closeAuth,
        extraAction = {
            TextButton(
                enabled = !state.busy,
                onClick = { viewModel.startPasswordReset(email) }
            ) { Text(stringResource(R.string.auth_forgot)) }
        }
    ) {
        EmailField(email, state.busy) { email = it }
        Spacer(Modifier.height(8.dp))
        PasswordField(
            value = password,
            label = stringResource(R.string.account_password),
            enabled = !state.busy,
            onValueChange = { password = it }
        )
    }
}

@Composable
private fun EmailStep(
    step: AuthStep.Email,
    state: AuthUiState,
    viewModel: AccountViewModel
) {
    var email by remember { mutableStateOf(state.email) }
    val signUp = step.purpose == CodePurpose.SignUp

    AuthScaffold(
        title = stringResource(
            if (signUp) R.string.account_sign_up else R.string.auth_reset_title
        ),
        busy = state.busy,
        message = state.message,
        confirmLabel = stringResource(R.string.auth_continue),
        // Nothing is sent before the terms are accepted, and the acceptance
        // is recorded once the account behind it actually exists.
        confirmEnabled = email.isValidEmail() && !state.busy &&
            (!signUp || state.acceptedTerms),
        onConfirm = { viewModel.submitEmail(email) },
        onDismiss = viewModel::closeAuth,
        extraAction = {
            // Every dead end offers the door that actually fits.
            when (state.message) {
                AuthMessage.EmailTaken -> {
                    TextButton(onClick = { viewModel.startSignIn(email) }) {
                        Text(stringResource(R.string.account_sign_in))
                    }
                    TextButton(onClick = { viewModel.startPasswordReset(email) }) {
                        Text(stringResource(R.string.auth_forgot))
                    }
                }

                AuthMessage.EmailUnknown -> TextButton(
                    onClick = { viewModel.startSignUp(email) }
                ) { Text(stringResource(R.string.account_sign_up)) }

                else -> Unit
            }
        }
    ) {
        Text(
            text = stringResource(
                if (signUp) R.string.auth_email_step_signup else R.string.auth_email_step_reset
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        EmailField(email, state.busy) { email = it }
        if (signUp) {
            Spacer(Modifier.height(8.dp))
            ConsentCheckboxes(state, viewModel)
        }
    }
}

/**
 * Accepting the terms is required; being emailed about new features is a
 * separate question that starts unticked and never blocks the account —
 * advertising consent has to be given freely and in advance to count.
 */
@Composable
private fun ConsentCheckboxes(state: AuthUiState, viewModel: AccountViewModel) {
    val context = LocalContext.current
    val pdfError = stringResource(R.string.guide_pdf_error)
    fun openPdf(asset: String, file: String) {
        if (!AssetPdf.open(context, asset, file)) {
            Toast.makeText(context, pdfError, Toast.LENGTH_SHORT).show()
        }
    }

    CheckRow(
        checked = state.acceptedTerms,
        enabled = !state.busy,
        text = stringResource(R.string.legal_accept),
        onCheckedChange = viewModel::setAcceptedTerms
    )
    Row {
        TextButton(onClick = { openPdf("legal/terms.pdf", "kosht-terms.pdf") }) {
            Text(
                text = stringResource(R.string.legal_terms),
                style = MaterialTheme.typography.labelMedium
            )
        }
        TextButton(onClick = { openPdf("legal/privacy-policy.pdf", "kosht-privacy.pdf") }) {
            Text(
                text = stringResource(R.string.legal_privacy),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
    CheckRow(
        checked = state.marketingOptIn,
        enabled = !state.busy,
        text = stringResource(R.string.legal_marketing),
        onCheckedChange = viewModel::setMarketingOptIn
    )
}

@Composable
private fun CheckRow(
    checked: Boolean,
    enabled: Boolean,
    text: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
    ) {
        Checkbox(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp)
        )
    }
}

@Composable
private fun CodeStep(
    step: AuthStep.Code,
    state: AuthUiState,
    viewModel: AccountViewModel
) {
    var code by remember(step.sentAt) { mutableStateOf("") }
    val now = rememberTicker()
    val elapsed = now - step.sentAt
    val leftMs = (AccountViewModel.CODE_LIFETIME_MS - elapsed).coerceAtLeast(0)
    val expired = leftMs == 0L
    val resendIn = (AccountViewModel.RESEND_COOLDOWN_MS - elapsed).coerceAtLeast(0)

    AuthScaffold(
        title = stringResource(R.string.auth_code_title),
        busy = state.busy,
        message = state.message,
        confirmLabel = stringResource(R.string.auth_continue),
        confirmEnabled = code.length == CODE_LENGTH && !expired && !state.busy,
        onConfirm = { viewModel.submitCode(code) },
        onDismiss = viewModel::closeAuth,
        extraAction = {
            TextButton(
                enabled = !state.busy && (resendIn == 0L || expired),
                onClick = viewModel::resendCode
            ) {
                Text(
                    if (resendIn > 0 && !expired) {
                        stringResource(R.string.auth_resend_in, resendIn.asClock())
                    } else {
                        stringResource(R.string.auth_resend)
                    }
                )
            }
            TextButton(enabled = !state.busy, onClick = viewModel::changeEmail) {
                Text(stringResource(R.string.auth_change_email))
            }
        }
    ) {
        Text(
            text = stringResource(R.string.auth_code_sent, step.email),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { input -> code = input.filter { it.isDigit() }.take(CODE_LENGTH) },
            singleLine = true,
            enabled = !state.busy,
            label = { Text(stringResource(R.string.auth_code_label)) },
            isError = expired,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (expired) {
                stringResource(R.string.auth_code_expired)
            } else {
                stringResource(R.string.auth_code_valid_for, leftMs.asClock())
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (expired) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun PasswordStep(state: AuthUiState, viewModel: AccountViewModel) {
    var password by remember { mutableStateOf("") }
    val longEnough = password.length >= MIN_PASSWORD

    AuthScaffold(
        title = stringResource(R.string.auth_password_title),
        busy = state.busy,
        message = state.message,
        confirmLabel = stringResource(R.string.auth_save),
        confirmEnabled = longEnough && !state.busy,
        onConfirm = { viewModel.submitNewPassword(password) },
        onDismiss = viewModel::closeAuth
    ) {
        Text(
            text = stringResource(R.string.auth_password_step),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        PasswordField(
            value = password,
            label = stringResource(R.string.auth_password_new),
            enabled = !state.busy,
            onValueChange = { password = it }
        )
        if (password.isNotEmpty() && !longEnough) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.auth_err_weak_password),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---- Shared pieces --------------------------------------------------------

@Composable
private fun AuthScaffold(
    title: String,
    busy: Boolean,
    message: AuthMessage?,
    confirmLabel: String,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    extraAction: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = {
            Column {
                content()
                message?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = it.text(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                extraAction?.let {
                    Spacer(Modifier.height(4.dp))
                    Column(horizontalAlignment = Alignment.Start) { it() }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = confirmEnabled, onClick = onConfirm) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(confirmLabel)
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun EmailField(value: String, busy: Boolean, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        enabled = !busy,
        label = { Text(stringResource(R.string.account_email)) },
        keyboardOptions = KeyboardOptions(
            // An address is never capitalized, whatever the app does elsewhere.
            capitalization = KeyboardCapitalization.None,
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

/** Password entry with the eye that reveals what was typed. */
@Composable
private fun PasswordField(
    value: String,
    label: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        enabled = enabled,
        label = { Text(label) },
        visualTransformation = if (visible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) {
                        Icons.Rounded.VisibilityOff
                    } else {
                        Icons.Rounded.Visibility
                    },
                    contentDescription = stringResource(
                        if (visible) R.string.auth_hide_password else R.string.auth_show_password
                    )
                )
            }
        },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun AuthMessage.text(): String = when (this) {
    AuthMessage.EmailTaken -> stringResource(R.string.auth_err_email_taken)
    AuthMessage.EmailUnknown -> stringResource(R.string.auth_err_email_unknown)
    AuthMessage.WrongCode -> stringResource(R.string.auth_err_wrong_code)
    AuthMessage.CodeExpired -> stringResource(R.string.auth_code_expired)
    AuthMessage.TooManyRequests -> stringResource(R.string.auth_err_too_many)
    AuthMessage.WrongCredentials -> stringResource(R.string.auth_err_wrong_credentials)
    AuthMessage.WeakPassword -> stringResource(R.string.auth_err_weak_password)
    AuthMessage.Offline -> stringResource(R.string.account_offline)
    is AuthMessage.Other -> detail.ifBlank { stringResource(R.string.account_offline) }
}

/** Ticks once a second, so the countdown on screen keeps up with the clock. */
@Composable
private fun rememberTicker(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    return now
}

private fun Long.asClock(): String {
    val seconds = (this + 999) / 1000
    return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)
}

private fun String.isValidEmail(): Boolean =
    android.util.Patterns.EMAIL_ADDRESS.matcher(trim()).matches()

/** "Last sync: 24.07.2026, 19:40", or a plain "not yet". */
@Composable
fun lastSyncLabel(lastSyncAt: Long): String = if (lastSyncAt <= 0L) {
    stringResource(R.string.account_last_sync_never)
} else {
    stringResource(
        R.string.account_last_sync,
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(lastSyncAt))
    )
}

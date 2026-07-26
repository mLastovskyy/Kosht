package by.mlastovsky.kosht.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import by.mlastovsky.kosht.R
import by.mlastovsky.kosht.ui.AppViewModelProvider
import java.text.DateFormat
import java.util.Date

private const val MIN_PASSWORD = 6

/**
 * First-launch question: cloud account or not. Kept as a plain full-screen
 * choice rather than a wizard — the app is fully usable either way, and the
 * answer can be changed later in Settings.
 */
@Composable
fun AccountOnboardingScreen(
    viewModel: AccountViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    var creating by remember { mutableStateOf<Boolean?>(null) }

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
            onClick = { creating = true },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.account_sign_up)) }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { creating = false },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.onboarding_have_account)) }
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = viewModel::skipAccount,
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.onboarding_skip)) }
    }

    creating?.let { signUp ->
        AccountAuthDialog(
            signUp = signUp,
            viewModel = viewModel,
            onDismiss = {
                creating = null
                viewModel.clearForm()
            }
        )
    }
}

/** Email and password, used both at first launch and from Settings. */
@Composable
fun AccountAuthDialog(
    signUp: Boolean,
    viewModel: AccountViewModel,
    onDismiss: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    // Half-typed input is not a mistake: complaints wait until the field is
    // left, otherwise the second letter of an address is already an error.
    var emailLeft by remember { mutableStateOf(false) }
    var passwordLeft by remember { mutableStateOf(false) }
    val form by viewModel.form.collectAsStateWithLifecycle()
    val busy = form is AuthFormState.Busy

    val emailValid = android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    val passwordValid = password.length >= MIN_PASSWORD
    val canSubmit = emailValid && passwordValid && !busy
    val showEmailError = emailLeft && email.isNotEmpty() && !emailValid
    val showPasswordError = passwordLeft && password.isNotEmpty() && !passwordValid

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            Text(
                stringResource(
                    if (signUp) R.string.account_sign_up else R.string.account_sign_in
                )
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    singleLine = true,
                    enabled = !busy,
                    label = { Text(stringResource(R.string.account_email)) },
                    isError = showEmailError,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { if (!it.isFocused && email.isNotEmpty()) emailLeft = true }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    enabled = !busy,
                    label = { Text(stringResource(R.string.account_password)) },
                    isError = showPasswordError,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged {
                            if (!it.isFocused && password.isNotEmpty()) passwordLeft = true
                        }
                )
                val hint = when {
                    showEmailError -> stringResource(R.string.account_email_invalid)
                    showPasswordError -> stringResource(R.string.account_password_short)
                    form is AuthFormState.ConfirmEmail ->
                        stringResource(R.string.account_confirm_email)

                    form is AuthFormState.Offline -> stringResource(R.string.account_offline)
                    form is AuthFormState.Rejected -> (form as AuthFormState.Rejected).message
                    else -> null
                }
                hint?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSubmit,
                onClick = {
                    if (signUp) {
                        viewModel.signUp(email, password)
                    } else {
                        viewModel.signIn(email, password)
                    }
                }
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        stringResource(
                            if (signUp) R.string.account_sign_up else R.string.account_sign_in
                        )
                    )
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

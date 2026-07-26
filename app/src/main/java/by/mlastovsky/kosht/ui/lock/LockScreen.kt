package by.mlastovsky.kosht.ui.lock

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import by.mlastovsky.kosht.R
import by.mlastovsky.kosht.data.lock.Biometrics
import by.mlastovsky.kosht.ui.AppViewModelProvider
import kotlinx.coroutines.delay

/**
 * The door. It stands in place of the whole app rather than over it, which is
 * the simplest way to be sure nothing shows through — no dialog left open on
 * another screen, no half-written record behind the dots.
 */
@Composable
fun LockScreen(
    viewModel: AppLockViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val entry by viewModel.entry.collectAsStateWithLifecycle()
    val current = settings ?: return
    val activity = LocalActivity.current as? FragmentActivity
    val context = LocalContext.current
    val view = LocalView.current
    var showForgotten by remember { mutableStateOf(false) }

    // Asked of the system once per appearance: it can change while the app is
    // away (a finger added, or every finger removed).
    val fingerReady = remember(current.biometrics) {
        current.biometrics && Biometrics.enrolled(context)
    }
    val promptTitle = stringResource(R.string.lock_prompt_title)
    val promptSubtitle = stringResource(R.string.lock_prompt_subtitle)
    val promptNegative = stringResource(R.string.lock_use_code)
    val askForFinger: () -> Unit = {
        val target = activity
        if (target != null) {
            Biometrics.prompt(
                activity = target,
                title = promptTitle,
                subtitle = promptSubtitle,
                negativeButton = promptNegative,
                onSuccess = viewModel::biometricAccepted,
                onError = viewModel::biometricRefused
            )
        }
    }

    // Offered without being asked for, the way a phone does it, and only once:
    // dismissing the prompt means the code is wanted instead.
    var offered by remember { mutableStateOf(false) }
    LaunchedEffect(fingerReady) {
        if (!fingerReady || offered) return@LaunchedEffect
        offered = true
        askForFinger()
    }

    // Back does not open anything, it just puts Kosht away — same as a phone.
    BackHandler { activity?.moveTaskToBack(true) }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val waitMillis = (current.lockedOutUntil - now).coerceAtLeast(0L)
    LaunchedEffect(current.lockedOutUntil) {
        while (System.currentTimeMillis() < current.lockedOutUntil) {
            now = System.currentTimeMillis()
            delay(250)
        }
        now = System.currentTimeMillis()
    }

    LaunchedEffect(entry.wrong) {
        if (entry.wrong) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(34.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.lock_enter_code),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        PinDots(
            filled = entry.entered.length,
            total = current.pinLength,
            error = entry.wrong
        )
        // A fixed strip for whatever has to be said, so nothing below it moves.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .padding(top = 14.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            val notice = when {
                waitMillis > 0 -> stringResource(R.string.lock_wait, waitLabel(waitMillis))
                entry.wrong -> stringResource(R.string.lock_err_wrong)
                else -> entry.message
            }
            if (notice != null) {
                Text(
                    text = notice,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        }
        Spacer(Modifier.weight(1f))
        // Only once trying has clearly stopped working: the way out of a
        // forgotten code is not something to advertise on every launch.
        if (current.failedAttempts >= 3) {
            TextButton(onClick = { showForgotten = true }) {
                Text(stringResource(R.string.lock_forgot))
            }
        }
        PinKeypad(
            onDigit = viewModel::typeDigit,
            onBackspace = viewModel::backspace,
            enabled = waitMillis == 0L && !entry.busy,
            onBiometric = if (fingerReady) askForFinger else null,
            modifier = Modifier.widthIn(max = 340.dp)
        )
        Spacer(Modifier.height(24.dp))
    }

    if (showForgotten) {
        AlertDialog(
            onDismissRequest = { showForgotten = false },
            icon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
            title = { Text(stringResource(R.string.lock_forgot_title)) },
            text = { Text(stringResource(R.string.lock_forgot_text)) },
            confirmButton = {
                TextButton(onClick = { showForgotten = false }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }
}

/** m:ss for the wait between tries — a countdown reads better than "later". */
private fun waitLabel(millis: Long): String {
    val seconds = ((millis + 999) / 1000).toInt()
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

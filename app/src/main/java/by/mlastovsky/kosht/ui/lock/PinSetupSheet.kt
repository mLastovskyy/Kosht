package by.mlastovsky.kosht.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import by.mlastovsky.kosht.R
import by.mlastovsky.kosht.util.Pin

/**
 * Setting, changing or switching off the code — the same bottom sheet the
 * calculator uses, so the keypad turns up where the app always puts a keypad.
 *
 * Proving the current code has no button: its length is known, so it submits
 * itself. A new one does have a button, because only its owner knows when it
 * is long enough.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinSetupSheet(
    setup: PinSetup,
    storedLength: Int,
    viewModel: AppLockViewModel
) {
    ModalBottomSheet(
        onDismissRequest = viewModel::cancelSetup,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        val proving = setup.step == PinStep.CURRENT
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(
                    when (setup.step) {
                        PinStep.CURRENT -> if (setup.goal == PinGoal.DISABLE) {
                            R.string.lock_confirm_off
                        } else {
                            R.string.lock_enter_current
                        }

                        PinStep.NEW -> R.string.lock_enter_new
                        PinStep.REPEAT -> R.string.lock_repeat_new
                    }
                ),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(
                    if (proving) R.string.lock_current_hint else R.string.lock_new_hint
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, bottom = 22.dp)
            )
            PinDots(
                filled = setup.entered.length,
                // While a new code is being made up the dots grow with it; the
                // minimum is drawn from the start so the length is no secret.
                total = if (proving) {
                    storedLength
                } else {
                    maxOf(Pin.MIN_LENGTH, setup.entered.length)
                },
                error = setup.error != null
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .padding(top = 12.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                setup.error?.let { error ->
                    Text(
                        text = stringResource(
                            when (error) {
                                PinError.WRONG_CODE -> R.string.lock_err_wrong
                                PinError.MISMATCH -> R.string.lock_err_mismatch
                                PinError.TOO_SHORT -> R.string.lock_err_short
                            }
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            }
            PinKeypad(
                onDigit = viewModel::setupDigit,
                onBackspace = viewModel::setupBackspace,
                enabled = !setup.busy
            )
            if (!proving) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = viewModel::cancelSetup,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = MaterialTheme.shapes.large
                    ) { Text(stringResource(R.string.action_cancel)) }
                    Button(
                        onClick = viewModel::confirmSetup,
                        enabled = !setup.busy && Pin.isValid(setup.entered),
                        modifier = Modifier
                            .weight(1.6f)
                            .height(56.dp),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text(
                            text = stringResource(
                                if (setup.step == PinStep.REPEAT) {
                                    R.string.editor_save
                                } else {
                                    R.string.auth_continue
                                }
                            ),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}

package by.mlastovsky.kosht.data.lock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * The phone's own fingerprint or face, borrowed for one question: is this the
 * owner? The code stays the real secret — biometrics only save the typing, and
 * every failure falls back to the keypad — so a class 2 sensor is good enough
 * here, and refusing face unlock on half the phones would not buy anything.
 */
object Biometrics {

    private const val ALLOWED = BiometricManager.Authenticators.BIOMETRIC_WEAK

    /** True when this phone has a sensor and something enrolled on it. */
    fun enrolled(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(ALLOWED) ==
            BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Shows the system prompt. [onError] carries the system's own wording for
     * what went wrong, or null when the person simply dismissed it — that needs
     * no explaining, the keypad is right there.
     */
    fun prompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negativeButton: String,
        onSuccess: () -> Unit,
        onError: (String?) -> Unit
    ) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val dismissed = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                onError(if (dismissed) null else errString.toString())
            }
        }
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            callback
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButtonText(negativeButton)
                .setAllowedAuthenticators(ALLOWED)
                .setConfirmationRequired(false)
                .build()
        )
    }
}

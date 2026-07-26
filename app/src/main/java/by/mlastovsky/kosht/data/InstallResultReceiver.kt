package by.mlastovsky.kosht.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.IntentCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Outcome of a commit, as the UI cares about it.
 */
enum class InstallOutcome {
    /** The user dismissed the system installer — not an error worth a dialog. */
    Cancelled,

    /** The release carries another signing key; Android will never accept it. */
    SignatureMismatch,

    /** Rejected by the package manager (no space, corrupt archive, ...). */
    Failed
}

/** Carries [InstallResultReceiver] results back to whoever started the install. */
object InstallEvents {

    private val _outcomes = MutableSharedFlow<InstallOutcome>(extraBufferCapacity = 4)

    val outcomes: SharedFlow<InstallOutcome> = _outcomes.asSharedFlow()

    fun report(outcome: InstallOutcome) {
        _outcomes.tryEmit(outcome)
    }
}

/**
 * Receives package installer status broadcasts. A successful install replaces
 * the running app, so only the pending-confirmation and failure paths matter.
 */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE
        )
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(
                    intent,
                    Intent.EXTRA_INTENT,
                    Intent::class.java
                ) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirm) }
            }

            PackageInstaller.STATUS_SUCCESS -> Unit

            PackageInstaller.STATUS_FAILURE_ABORTED ->
                InstallEvents.report(InstallOutcome.Cancelled)

            // A conflict on a self-update means one thing in practice: the
            // release was signed with a different key than the running build.
            PackageInstaller.STATUS_FAILURE_CONFLICT ->
                InstallEvents.report(InstallOutcome.SignatureMismatch)

            else -> InstallEvents.report(InstallOutcome.Failed)
        }
    }

    companion object {

        /** Status callback target for [PackageInstaller.Session.commit]. */
        fun statusSender(context: Context, sessionId: Int): IntentSender {
            val intent = Intent(context, InstallResultReceiver::class.java)
                .setPackage(context.packageName)
            // Mutable: the system fills the status extras in on delivery.
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE
                } else {
                    0
                }
            return PendingIntent.getBroadcast(context, sessionId, intent, flags).intentSender
        }
    }
}

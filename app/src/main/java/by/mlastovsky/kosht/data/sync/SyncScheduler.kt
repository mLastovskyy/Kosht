package by.mlastovsky.kosht.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import by.mlastovsky.kosht.KoshtApp
import java.util.concurrent.TimeUnit

/**
 * Runs a sync when there is a connection, and remembers to run one when there
 * is not. WorkManager holds the request until its network constraint is met,
 * which is exactly the "works offline, catches up later" behaviour wanted —
 * no polling and no connectivity listener of its own.
 */
object SyncScheduler {

    private const val NOW = "sync-now"
    private const val PERIODIC = "sync-periodic"

    private val onlineOnly = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /**
     * Queues a sync. Called on app start and after the user changes data;
     * [ExistingWorkPolicy.KEEP] collapses a burst of edits into one run.
     */
    fun syncNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(onlineOnly)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, request)
    }

    /** Background catch-up so a second device sees changes without being opened. */
    fun enablePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(2, TimeUnit.HOURS)
            .setConstraints(onlineOnly)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun disablePeriodic(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
    }
}

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? KoshtApp ?: return Result.success()
        return when (app.container.syncEngine.sync()) {
            is SyncOutcome.Success -> Result.success()
            // Nothing to retry for: no account, or the network died mid-run.
            SyncOutcome.NotSignedIn -> Result.success()
            SyncOutcome.Offline -> Result.retry()
            is SyncOutcome.Failed -> Result.retry()
        }
    }
}

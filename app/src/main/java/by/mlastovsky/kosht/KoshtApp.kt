package by.mlastovsky.kosht

import android.app.Application
import by.mlastovsky.kosht.data.sync.SyncScheduler
import by.mlastovsky.kosht.di.AppContainer
import by.mlastovsky.kosht.notifications.NotificationScheduler
import by.mlastovsky.kosht.notifications.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class KoshtApp : Application() {

    lateinit var container: AppContainer
        private set

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, applicationScope)
        applicationScope.launch {
            container.ratesRepository.refreshIfStale()
        }
        applicationScope.launch {
            // Once per launch, quietly: files whose record is gone and
            // deletions every device already knows about.
            container.housekeeping.run()
        }
        applicationScope.launch {
            // Touching it starts the watching; from here on an award is earned
            // the moment it is deserved, whichever screen happens to be open.
            container.awardTracker.unlocked.collect { key ->
                if (container.settingsRepository.settings.first().notifyAwards) {
                    Notifications.showAward(this@KoshtApp, key)
                }
            }
        }
        applicationScope.launch {
            container.settingsRepository.settings.collectLatest { settings ->
                NotificationScheduler.sync(this@KoshtApp, settings)
            }
        }
        applicationScope.launch {
            // Queued rather than run: with no connection WorkManager holds it
            // until there is one, so an offline launch still catches up later.
            container.syncAccountRepository.state.collectLatest { account ->
                if (account.signedIn && account.autoSync) {
                    SyncScheduler.syncNow(this@KoshtApp)
                    SyncScheduler.enablePeriodic(this@KoshtApp)
                } else {
                    SyncScheduler.disablePeriodic(this@KoshtApp)
                }
            }
        }
    }
}

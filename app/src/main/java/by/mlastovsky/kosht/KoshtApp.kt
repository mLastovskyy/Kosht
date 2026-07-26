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

            container.housekeeping.run()
        }
        applicationScope.launch {

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

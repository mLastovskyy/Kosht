package by.mlastovsky.kosht

import android.app.Application
import by.mlastovsky.kosht.di.AppContainer
import by.mlastovsky.kosht.notifications.NotificationScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class KoshtApp : Application() {

    lateinit var container: AppContainer
        private set

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        applicationScope.launch {
            container.ratesRepository.refreshIfStale()
        }
        applicationScope.launch {
            container.settingsRepository.settings.collectLatest { settings ->
                NotificationScheduler.sync(this@KoshtApp, settings)
            }
        }
    }
}

package by.mlastovsky.kosht

import android.app.Application
import by.mlastovsky.kosht.di.AppContainer

class KoshtApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

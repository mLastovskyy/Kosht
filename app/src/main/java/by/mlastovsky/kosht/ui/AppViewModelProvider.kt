package by.mlastovsky.kosht.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import by.mlastovsky.kosht.KoshtApp
import by.mlastovsky.kosht.MainViewModel
import by.mlastovsky.kosht.ui.home.HomeViewModel

/**
 * Factory for all ViewModels; resolves dependencies from [KoshtApp.container].
 */
object AppViewModelProvider {

    val Factory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            MainViewModel(app().container.settingsRepository)
        }
        initializer {
            HomeViewModel(
                app().container.transactionRepository,
                app().container.settingsRepository
            )
        }
    }

    private fun CreationExtras.app(): KoshtApp =
        this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as KoshtApp
}

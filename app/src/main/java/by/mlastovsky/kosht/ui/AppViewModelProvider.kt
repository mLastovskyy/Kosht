package by.mlastovsky.kosht.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import by.mlastovsky.kosht.KoshtApp
import by.mlastovsky.kosht.MainViewModel
import by.mlastovsky.kosht.ui.editor.EditorViewModel
import by.mlastovsky.kosht.ui.history.HistoryViewModel
import by.mlastovsky.kosht.ui.home.HomeViewModel
import by.mlastovsky.kosht.ui.settings.SettingsViewModel
import by.mlastovsky.kosht.ui.stats.StatsViewModel

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
                app().container.settingsRepository,
                app().container.ratesRepository
            )
        }
        initializer {
            HistoryViewModel(
                app().container.transactionRepository,
                app().container.settingsRepository
            )
        }
        initializer {
            StatsViewModel(
                app().container.transactionRepository,
                app().container.settingsRepository
            )
        }
        initializer {
            SettingsViewModel(app().container.settingsRepository)
        }
        initializer {
            EditorViewModel(
                savedStateHandle = createSavedStateHandle(),
                repository = app().container.transactionRepository,
                settingsRepository = app().container.settingsRepository
            )
        }
    }

    private fun CreationExtras.app(): KoshtApp =
        this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as KoshtApp
}

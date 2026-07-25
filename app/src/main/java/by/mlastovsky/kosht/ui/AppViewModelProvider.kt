package by.mlastovsky.kosht.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import by.mlastovsky.kosht.KoshtApp
import by.mlastovsky.kosht.MainViewModel
import by.mlastovsky.kosht.ui.achievements.AchievementsViewModel
import by.mlastovsky.kosht.ui.editor.EditorViewModel
import by.mlastovsky.kosht.ui.history.HistoryViewModel
import by.mlastovsky.kosht.ui.home.HomeViewModel
import by.mlastovsky.kosht.ui.settings.SettingsViewModel
import by.mlastovsky.kosht.ui.stats.StatsViewModel
import by.mlastovsky.kosht.ui.wallet.WalletViewModel

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
                app().container.ratesRepository,
                app().container.accountRepository
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
                app().container.settingsRepository,
                app().container.walletRepository,
                app().container.accountRepository
            )
        }
        initializer {
            SettingsViewModel(
                app().container.settingsRepository,
                app().container.photoStore,
                app().container.currencyChanger,
                app().container.accountRepository
            )
        }
        initializer {
            AchievementsViewModel(
                app().container.walletRepository,
                app().container.transactionRepository,
                app().container.ratesRepository,
                app().container.settingsRepository
            )
        }
        initializer {
            WalletViewModel(
                app().container.walletRepository,
                app().container.transactionRepository,
                app().container.ratesRepository,
                app().container.settingsRepository
            )
        }
        initializer {
            EditorViewModel(
                savedStateHandle = createSavedStateHandle(),
                repository = app().container.transactionRepository,
                settingsRepository = app().container.settingsRepository,
                receiptScanner = app().container.receiptScanner,
                photoStore = app().container.photoStore,
                ratesRepository = app().container.ratesRepository,
                accountRepository = app().container.accountRepository
            )
        }
    }

    private fun CreationExtras.app(): KoshtApp =
        this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as KoshtApp
}

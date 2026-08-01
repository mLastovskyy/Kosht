package by.mlastovsky.kosht.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import by.mlastovsky.kosht.KoshtApp
import by.mlastovsky.kosht.MainViewModel
import by.mlastovsky.kosht.ui.account.AccountViewModel
import by.mlastovsky.kosht.ui.achievements.AchievementsViewModel
import by.mlastovsky.kosht.ui.awards.AwardsViewModel
import by.mlastovsky.kosht.ui.components.UndoDeleteViewModel
import by.mlastovsky.kosht.ui.editor.EditorViewModel
import by.mlastovsky.kosht.ui.history.HistoryViewModel
import by.mlastovsky.kosht.ui.home.HomeViewModel
import by.mlastovsky.kosht.ui.lock.AppLockViewModel
import by.mlastovsky.kosht.ui.premium.PremiumViewModel
import by.mlastovsky.kosht.ui.profile.ProfileViewModel
import by.mlastovsky.kosht.ui.settings.SettingsViewModel
import by.mlastovsky.kosht.ui.stats.StatsViewModel
import by.mlastovsky.kosht.ui.tour.TourViewModel
import by.mlastovsky.kosht.ui.transfer.TransferViewModel
import by.mlastovsky.kosht.ui.wallet.WalletViewModel

object AppViewModelProvider {

    val Factory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            MainViewModel(
                app().container.settingsRepository,
                app().container.syncAccountRepository
            )
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
                app().container.settingsRepository,
                app().container.accountRepository
            )
        }
        initializer {
            StatsViewModel(
                app().container.transactionRepository,
                app().container.settingsRepository,
                app().container.accountRepository
            )
        }
        initializer {
            SettingsViewModel(
                app().container.settingsRepository,
                app().container.photoStore,
                app().container.currencyChanger,
                app().container.updateChecker,
                app().container.updateInstaller
            )
        }
        initializer {
            AccountViewModel(
                app().container.syncAccountRepository,
                app().container.syncEngine,
                app().container.settingsRepository
            )
        }
        initializer {
            AchievementsViewModel(
                app().container.walletRepository,
                app().container.awardTracker,
                app().container.transactionRepository,
                app().container.ratesRepository,
                app().container.settingsRepository
            )
        }
        initializer {
            AwardsViewModel(app().container.awardTracker)
        }
        initializer {
            PremiumViewModel(app().container.premiumRepository)
        }
        initializer {
            AppLockViewModel(
                app().container.appLock,
                app().container.appLockRepository
            )
        }
        initializer {
            UndoDeleteViewModel(
                app().container.transactionRepository,
                app().container.photoStore
            )
        }
        initializer {
            WalletViewModel(
                app().container.walletRepository,
                app().container.transactionRepository,
                app().container.ratesRepository,
                app().container.settingsRepository,
                app().container.accountRepository
            )
        }
        initializer {
            TourViewModel(app().container.settingsRepository)
        }
        initializer {
            ProfileViewModel(
                app().container.settingsRepository,
                app().container.photoStore
            )
        }
        initializer {
            TransferViewModel(
                app().container.transactionRepository,
                app().container.accountRepository,
                app().container.settingsRepository,
                app().container.ratesRepository
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
                accountRepository = app().container.accountRepository,
                walletRepository = app().container.walletRepository
            )
        }
    }

    private fun CreationExtras.app(): KoshtApp =
        this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as KoshtApp
}

package by.mlastovsky.kosht.di

import android.content.Context
import by.mlastovsky.kosht.data.AccountRepository
import by.mlastovsky.kosht.data.CurrencyChanger
import by.mlastovsky.kosht.data.PhotoStore
import by.mlastovsky.kosht.data.RatesRepository
import by.mlastovsky.kosht.data.SettingsRepository
import by.mlastovsky.kosht.data.TransactionRepository
import by.mlastovsky.kosht.data.WalletRepository
import by.mlastovsky.kosht.data.db.KoshtDatabase
import by.mlastovsky.kosht.data.receipt.ReceiptScanner

/**
 * Simple manual dependency container scoped to the application lifecycle.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    private val database: KoshtDatabase = KoshtDatabase.build(context)

    val transactionRepository: TransactionRepository by lazy {
        TransactionRepository(
            database.transactionDao(),
            database.categoryDao(),
            database.recurringDao()
        )
    }

    val ratesRepository: RatesRepository by lazy {
        RatesRepository(database.rateDao())
    }

    val accountRepository: AccountRepository by lazy {
        AccountRepository(database.accountDao(), database.transactionDao())
    }

    val walletRepository: WalletRepository by lazy {
        WalletRepository(
            database.debtDao(),
            database.savingDao(),
            database.recurringDao(),
            database.transactionDao(),
            database.goalDao(),
            database.challengeDao()
        )
    }

    val receiptScanner: ReceiptScanner by lazy { ReceiptScanner(appContext) }

    val currencyChanger: CurrencyChanger by lazy {
        CurrencyChanger(
            database.transactionDao(),
            database.challengeDao(),
            database.accountDao(),
            settingsRepository,
            ratesRepository
        )
    }

    val photoStore: PhotoStore by lazy { PhotoStore(appContext) }

    val settingsRepository: SettingsRepository = SettingsRepository(context)
}

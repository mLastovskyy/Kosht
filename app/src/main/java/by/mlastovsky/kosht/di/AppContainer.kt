package by.mlastovsky.kosht.di

import android.content.Context
import by.mlastovsky.kosht.data.RatesRepository
import by.mlastovsky.kosht.data.SettingsRepository
import by.mlastovsky.kosht.data.TransactionRepository
import by.mlastovsky.kosht.data.db.KoshtDatabase

/**
 * Simple manual dependency container scoped to the application lifecycle.
 */
class AppContainer(context: Context) {

    private val database: KoshtDatabase = KoshtDatabase.build(context)

    val transactionRepository: TransactionRepository by lazy {
        TransactionRepository(database.transactionDao(), database.categoryDao())
    }

    val ratesRepository: RatesRepository by lazy {
        RatesRepository(database.rateDao())
    }

    val settingsRepository: SettingsRepository = SettingsRepository(context)
}

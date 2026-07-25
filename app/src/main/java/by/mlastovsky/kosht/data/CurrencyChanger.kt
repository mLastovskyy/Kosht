package by.mlastovsky.kosht.data

import by.mlastovsky.kosht.data.db.ChallengeDao
import by.mlastovsky.kosht.data.db.TransactionDao
import kotlinx.coroutines.flow.first
import kotlin.math.roundToLong

/**
 * Switches the app currency and rescales everything stored in it —
 * transactions, challenge limits and the daily budget — using the loaded
 * NBRB cross rate. Frozen historical BYN equivalents are left untouched.
 */
class CurrencyChanger(
    private val transactionDao: TransactionDao,
    private val challengeDao: ChallengeDao,
    private val accountDao: by.mlastovsky.kosht.data.db.AccountDao,
    private val settingsRepository: SettingsRepository,
    private val ratesRepository: RatesRepository
) {

    suspend fun change(newCode: String) {
        val settings = settingsRepository.settings.first()
        val oldCode = settings.currencyCode
        if (oldCode == newCode) return

        val rates = ratesRepository.rates.first()
        val from = rates[oldCode]
        val to = rates[newCode]
        val convertible = settings.convertOnCurrencyChange && from != null && to != null &&
            from.scale > 0 && to.scale > 0 && to.rate > 0.0
        if (convertible) {
            val factor = (from!!.rate / from.scale) / (to!!.rate / to.scale)
            transactionDao.rescaleAmounts(factor)
            challengeDao.rescaleAmounts(factor)
            accountDao.rescaleAdjustments(factor)
            if (settings.dailyBudgetMinor > 0) {
                settingsRepository.setDailyBudgetMinor(
                    (settings.dailyBudgetMinor * factor).roundToLong()
                )
            }
        }
        settingsRepository.setCurrencyCode(newCode)
    }
}

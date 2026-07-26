package by.mlastovsky.kosht.data

import by.mlastovsky.kosht.data.db.AccountDao
import by.mlastovsky.kosht.data.db.ChallengeDao
import by.mlastovsky.kosht.data.db.DebtDao
import by.mlastovsky.kosht.data.db.GoalDao
import by.mlastovsky.kosht.data.db.RateEntity
import by.mlastovsky.kosht.data.db.RecurringDao
import by.mlastovsky.kosht.data.db.SavingDao
import by.mlastovsky.kosht.data.db.TransactionDao
import by.mlastovsky.kosht.data.db.TransactionItemDao
import kotlin.math.roundToLong
import kotlinx.coroutines.flow.first

class CurrencyChanger(
    private val transactionDao: TransactionDao,
    private val itemDao: TransactionItemDao,
    private val challengeDao: ChallengeDao,
    private val accountDao: AccountDao,
    private val savingDao: SavingDao,
    private val goalDao: GoalDao,
    private val debtDao: DebtDao,
    private val recurringDao: RecurringDao,
    private val settingsRepository: SettingsRepository,
    private val ratesRepository: RatesRepository
) {

    suspend fun change(newCode: String) {
        val settings = settingsRepository.settings.first()
        val oldCode = settings.currencyCode
        if (oldCode == newCode) return

        if (settings.convertOnCurrencyChange) {
            val rates = ratesRepository.rates.first()
            rescaleAppCurrency(oldCode, newCode, settings.dailyBudgetMinor, rates)
            convertTagged(newCode, rates)
        }
        settingsRepository.setCurrencyCode(newCode)
    }

    private suspend fun rescaleAppCurrency(
        oldCode: String,
        newCode: String,
        dailyBudgetMinor: Long,
        rates: Map<String, RateEntity>
    ) {

        val factor = RatesRepository.factor(oldCode, newCode, rates) ?: return
        transactionDao.rescaleAmounts(factor)

        itemDao.rescaleAmounts(factor)
        challengeDao.rescaleAmounts(factor)
        accountDao.rescaleAdjustments(factor)
        if (dailyBudgetMinor > 0) {
            settingsRepository.setDailyBudgetMinor((dailyBudgetMinor * factor).roundToLong())
        }
    }

    private suspend fun convertTagged(newCode: String, rates: Map<String, RateEntity>) {
        convert(savingDao.currencies(), newCode, rates, savingDao::convert)
        convert(goalDao.currencies(), newCode, rates, goalDao::convert)
        convert(debtDao.currencies(), newCode, rates, debtDao::convert)
        convert(recurringDao.currencies(), newCode, rates, recurringDao::convert)
    }

    private suspend fun convert(
        currencies: List<String>,
        newCode: String,
        rates: Map<String, RateEntity>,
        apply: suspend (from: String, to: String, factor: Double) -> Unit
    ) {
        currencies
            .filter { it != newCode }
            .forEach { from ->

                val factor = RatesRepository.factor(from, newCode, rates) ?: return@forEach
                apply(from, newCode, factor)
            }
    }
}

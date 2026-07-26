package by.mlastovsky.kosht.data

import by.mlastovsky.kosht.data.db.ChallengeDao
import by.mlastovsky.kosht.data.db.DebtDao
import by.mlastovsky.kosht.data.db.GoalDao
import by.mlastovsky.kosht.data.db.RateEntity
import by.mlastovsky.kosht.data.db.RecurringDao
import by.mlastovsky.kosht.data.db.SavingDao
import by.mlastovsky.kosht.data.db.TransactionDao
import kotlinx.coroutines.flow.first
import kotlin.math.roundToLong

/**
 * Switches the app currency and restates everything money-shaped in the new
 * one, at the NBRB cross rate, when "recalculate amounts" is on.
 *
 * Two kinds of records need different treatment. Transactions, challenge
 * limits, balance corrections and the daily budget are all kept in whatever
 * the app currency happens to be, so one factor rescales them in bulk.
 * Savings, goals, debts and recurring charges each remember the currency they
 * were entered in, so they are converted a currency at a time — a goal in
 * dollars and a debt in roubles do not share a factor — and end up speaking
 * the new currency like everything else.
 *
 * Frozen historical BYN equivalents on transactions are never touched: that is
 * what was actually paid on the day, and no later rate can change it.
 */
class CurrencyChanger(
    private val transactionDao: TransactionDao,
    private val itemDao: by.mlastovsky.kosht.data.db.TransactionItemDao,
    private val challengeDao: ChallengeDao,
    private val accountDao: by.mlastovsky.kosht.data.db.AccountDao,
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

    /** Everything stored in "whatever the app currency is", in one factor. */
    private suspend fun rescaleAppCurrency(
        oldCode: String,
        newCode: String,
        dailyBudgetMinor: Long,
        rates: Map<String, RateEntity>
    ) {
        // No rate for the pair means no honest number to write, so the amounts
        // keep their figures and only the label changes.
        val factor = RatesRepository.factor(oldCode, newCode, rates) ?: return
        transactionDao.rescaleAmounts(factor)
        // The product lines are priced in the app currency like their record.
        itemDao.rescaleAmounts(factor)
        challengeDao.rescaleAmounts(factor)
        accountDao.rescaleAdjustments(factor)
        if (dailyBudgetMinor > 0) {
            settingsRepository.setDailyBudgetMinor((dailyBudgetMinor * factor).roundToLong())
        }
    }

    /** Records that carry their own currency, converted group by group. */
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
                // A currency the National Bank does not price is left as it is,
                // rather than silently multiplied by nothing.
                val factor = RatesRepository.factor(from, newCode, rates) ?: return@forEach
                apply(from, newCode, factor)
            }
    }
}

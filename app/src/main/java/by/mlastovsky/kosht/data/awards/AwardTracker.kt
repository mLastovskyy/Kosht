package by.mlastovsky.kosht.data.awards

import by.mlastovsky.kosht.data.RatesRepository
import by.mlastovsky.kosht.data.SettingsRepository
import by.mlastovsky.kosht.data.TransactionRepository
import by.mlastovsky.kosht.data.WalletRepository
import by.mlastovsky.kosht.data.db.ChallengeEntity
import by.mlastovsky.kosht.data.db.RateEntity
import by.mlastovsky.kosht.data.db.SavingEntity
import by.mlastovsky.kosht.data.db.SavingTotal
import by.mlastovsky.kosht.util.Dates
import by.mlastovsky.kosht.util.Streak
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AwardTracker(
    private val transactions: TransactionRepository,
    private val wallet: WalletRepository,
    rates: RatesRepository,
    settings: SettingsRepository,
    private val scope: CoroutineScope
) {

    private data class Counts(
        val txCount: Int,
        val incomeCount: Int,
        val photoCount: Int,
        val nightCount: Int,
        val categoriesUsed: Int
    )

    private data class History(
        val spend: List<DaySpend>,
        val months: List<AwardMath.Month>,
        val firstRecord: LocalDate?
    )

    private data class Purse(
        val challenges: List<ChallengeEntity>,
        val savings: List<SavingEntity>,
        val savingTotals: List<SavingTotal>,
        val goalsCount: Int,
        val goalsAchieved: Int,
        val debtsClosed: Int
    )

    private data class Env(
        val rates: Map<String, RateEntity>,
        val currencyCode: String,
        val dailyBudgetMinor: Long
    )

    private val counts = combine(
        transactions.observeCount(),
        transactions.observeIncomeCount(),
        transactions.observePhotoCount(),
        transactions.observeNightCount(),
        transactions.observeExpenseCategoryCount(),
        ::Counts
    )

    private val history = combine(
        transactions.observeDailyCategorySpend(),
        transactions.observeMonthlyTotals(),
        transactions.observeFirstTimestamp()
    ) { spend, months, firstTimestamp ->
        History(
            spend = spend.mapNotNull { row ->
                val day = runCatching { LocalDate.parse(row.day) }.getOrNull()
                day?.let { DaySpend(it, row.categoryId, row.total) }
            },
            months = months.mapNotNull { row ->
                val month = runCatching { YearMonth.parse(row.month) }.getOrNull()
                month?.let { AwardMath.Month(it, row.income, row.expense) }
            },
            firstRecord = firstTimestamp?.takeIf { it > 0 }?.let(Dates::toLocalDate)
        )
    }

    private val walletData = combine(
        wallet.observeChallenges(),

        wallet.observeSavingsSince(0),
        wallet.observeSavingTotals(),
        wallet.observeGoals(),
        wallet.observeClosedDebtCount()
    ) { challenges, savings, totals, goals, debtsClosed ->
        Purse(
            challenges = challenges,
            savings = savings,
            savingTotals = totals,
            goalsCount = goals.size,
            goalsAchieved = goals.count { it.achievedAt != null },
            debtsClosed = debtsClosed
        )
    }

    private val env = combine(rates.rates, settings.settings) { loaded, appSettings ->
        Env(loaded, appSettings.currencyCode, appSettings.dailyBudgetMinor)
    }

    val stats: StateFlow<AwardStats?> =
        combine(counts, history, walletData, env) { tally, past, purse, loaded ->
            build(tally, past, purse, loaded)
        }.stateIn(scope, SharingStarted.Eagerly, null)

    private val _unlocked = MutableSharedFlow<String>(extraBufferCapacity = EVENT_BUFFER)

    val unlocked: SharedFlow<String> = _unlocked.asSharedFlow()

    val earned: Flow<Map<String, Long>> = wallet.observeAwardsByKey()

    init {
        scope.launch { watch() }
    }

    private suspend fun watch() {
        combine(stats.filterNotNull(), earned) { current, stored -> current to stored.keys }
            .collect { (current, stored) ->
                val fresh = AwardRules.evaluate(current)
                    .filter { it.met && it.key !in stored }
                    .map { it.key }
                if (fresh.isEmpty()) return@collect
                wallet.unlockAwards(fresh)
                announce(fresh, firstEverReading = stored.isEmpty())
            }
    }

    private suspend fun announce(fresh: List<String>, firstEverReading: Boolean) {
        if (firstEverReading && fresh.size > MAX_ANNOUNCED) return
        fresh.take(MAX_ANNOUNCED).forEach { _unlocked.emit(it) }
    }

    private fun build(
        counts: Counts,
        history: History,
        wallet: Purse,
        env: Env
    ): AwardStats {
        val spendByDay = history.spend
            .groupBy { it.day }
            .mapValues { (_, rows) -> rows.sumOf { it.minor } }
        val budget = env.dailyBudgetMinor.takeIf { it > 0 }
            ?: Streak.autoDailyBudget(spendByDay)
        val thisMonth = history.months.firstOrNull { it.month == YearMonth.now() }

        return AwardStats(
            txCount = counts.txCount,
            incomeCount = counts.incomeCount,
            photoCount = counts.photoCount,
            nightCount = counts.nightCount,
            expenseCategoriesUsed = counts.categoriesUsed,
            streakDays = Streak.budgetStreak(spendByDay, budget, history.firstRecord),
            dailyBudgetMinor = budget,
            monthsTracked = history.months.size,
            surplusMonthsInRow = AwardMath.longestSurplusRun(history.months),
            perfectMonths = AwardMath.perfectMonths(spendByDay, budget, history.firstRecord),
            monthSurplus = thisMonth != null &&
                thisMonth.income > thisMonth.expense && thisMonth.expense > 0,
            savingsAny = wallet.savingTotals.any { it.total > 0 },
            savedBynMinor = wallet.savingTotals.sumOf {
                RatesRepository.toBynMinor(it.total, it.currencyCode, env.rates) ?: 0L
            },
            goalsCount = wallet.goalsCount,
            goalsAchieved = wallet.goalsAchieved,
            debtsClosed = wallet.debtsClosed,
            challenges = wallet.challenges.map {
                ChallengeEvaluator.evaluate(
                    challenge = it,
                    spend = history.spend,
                    savings = wallet.savings,
                    rates = env.rates,
                    currencyCode = env.currencyCode
                )
            }
        )
    }

    private companion object {
        const val EVENT_BUFFER = 16

        const val MAX_ANNOUNCED = 3
    }
}

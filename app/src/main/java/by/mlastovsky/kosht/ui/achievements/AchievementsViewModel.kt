package by.mlastovsky.kosht.ui.achievements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.mlastovsky.kosht.data.RatesRepository
import by.mlastovsky.kosht.data.SettingsRepository
import by.mlastovsky.kosht.data.TransactionRepository
import by.mlastovsky.kosht.data.WalletRepository
import by.mlastovsky.kosht.data.db.CategoryEntity
import by.mlastovsky.kosht.data.db.ChallengeEntity
import by.mlastovsky.kosht.data.db.RateEntity
import by.mlastovsky.kosht.data.db.SavingEntity
import by.mlastovsky.kosht.data.db.TransactionWithCategory
import by.mlastovsky.kosht.model.ChallengeType
import by.mlastovsky.kosht.model.TransactionType
import by.mlastovsky.kosht.util.Dates
import by.mlastovsky.kosht.util.Money
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

enum class ChallengeStatus { ACTIVE, DONE, FAILED }

data class ChallengeUi(
    val entity: ChallengeEntity,
    /** Spent/saved so far vs the limit/target; days for NO_SPEND. */
    val progress: Float,
    val progressLabelMinor: Long,
    val daysPassed: Int,
    val daysTotal: Int,
    val status: ChallengeStatus
)

data class BadgeUi(
    val key: String,
    val unlocked: Boolean,
    /** When the award was earned; null while still locked. */
    val unlockedAt: Long? = null,
    /** "3 / 10"-style progress toward a locked quantitative award. */
    val progressText: String? = null
)

data class AchievementsUiState(
    val loaded: Boolean = false,
    val streakDays: Int = 0,
    val dailyBudgetMinor: Long = 0,
    val challenges: List<ChallengeUi> = emptyList(),
    val badges: List<BadgeUi> = emptyList(),
    val expenseCategories: List<CategoryEntity> = emptyList(),
    val currencyCode: String = SettingsRepository.DEFAULT_CURRENCY
)

class AchievementsViewModel(
    private val walletRepository: WalletRepository,
    transactionRepository: TransactionRepository,
    ratesRepository: RatesRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {

    private val horizonMillis =
        Dates.toEpochMillis(LocalDate.now().minusDays(HORIZON_DAYS))

    private data class Activity(
        val transactions: List<TransactionWithCategory>,
        val createdStamps: List<Long>,
        val txCount: Int,
        val photoCount: Int
    )

    private val activity = combine(
        transactionRepository.observeBetween(horizonMillis, Long.MAX_VALUE),
        transactionRepository.observeCreatedSince(horizonMillis),
        transactionRepository.observeCount(),
        transactionRepository.observePhotoCount(),
        ::Activity
    )

    private data class WalletData(
        val challenges: List<ChallengeEntity>,
        val savings: List<SavingEntity>,
        val goalsCount: Int,
        val goalsAchievedCount: Int,
        val savingTotals: List<by.mlastovsky.kosht.data.db.SavingTotal>,
        /** Earned awards: key → unlock timestamp. */
        val awards: Map<String, Long>
    )

    private val walletData = combine(
        walletRepository.observeChallenges(),
        walletRepository.observeSavingsSince(horizonMillis),
        walletRepository.observeGoals(),
        walletRepository.observeSavingTotals(),
        walletRepository.observeAwards()
    ) { challenges, savings, goals, totals, awards ->
        WalletData(
            challenges = challenges,
            savings = savings,
            goalsCount = goals.size,
            goalsAchievedCount = goals.count { it.achievedAt != null },
            savingTotals = totals,
            awards = awards.associate { it.key to it.unlockedAt }
        )
    }

    private data class Context(
        val rates: Map<String, RateEntity>,
        val settings: by.mlastovsky.kosht.data.AppSettings,
        val categories: List<CategoryEntity>
    )

    private val context = combine(
        ratesRepository.rates,
        settingsRepository.settings,
        transactionRepository.observeCategories(TransactionType.EXPENSE),
        ::Context
    )

    val uiState: StateFlow<AchievementsUiState> = combine(
        activity, walletData, context
    ) { act, wallet, ctx ->
        val spendByDay = by.mlastovsky.kosht.util.Streak.spendByDay(act.transactions)
        val budget = ctx.settings.dailyBudgetMinor.takeIf { it > 0 }
            ?: by.mlastovsky.kosht.util.Streak.autoDailyBudget(spendByDay)
        val firstRecordDay = act.transactions
            .minOfOrNull { it.transaction.timestamp }
            ?.let(Dates::toLocalDate)
        val streak = by.mlastovsky.kosht.util.Streak
            .budgetStreak(spendByDay, budget, firstRecordDay)
        val monthRange = Dates.monthRange(YearMonth.now())
        val monthTx = act.transactions.filter { it.transaction.timestamp in monthRange }
        val monthIncome = monthTx.filter { it.transaction.type == TransactionType.INCOME }
            .sumOf { it.transaction.amountMinor }
        val monthExpense = monthTx.filter { it.transaction.type == TransactionType.EXPENSE }
            .sumOf { it.transaction.amountMinor }
        val challengeUis = wallet.challenges.map {
            evaluate(it, act.transactions, wallet.savings, ctx.rates, ctx.settings.currencyCode)
        }
        val savedBynMinor = wallet.savingTotals.sumOf {
            RatesRepository.toBynMinor(it.total, it.currencyCode, ctx.rates) ?: 0L
        }

        AchievementsUiState(
            loaded = true,
            streakDays = streak,
            dailyBudgetMinor = budget,
            challenges = challengeUis,
            badges = buildAwards(
                stored = wallet.awards,
                txCount = act.txCount,
                photoCount = act.photoCount,
                streak = streak,
                incomeAny = act.transactions.any {
                    it.transaction.type == TransactionType.INCOME
                },
                hasSavings = wallet.savingTotals.any { it.total > 0 },
                savedBynMinor = savedBynMinor,
                goalsCount = wallet.goalsCount,
                goalsAchieved = wallet.goalsAchievedCount,
                challengesDone = challengeUis.count { it.status == ChallengeStatus.DONE },
                surplus = monthIncome > monthExpense && monthExpense > 0
            ),
            expenseCategories = ctx.categories,
            currencyCode = ctx.settings.currencyCode
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AchievementsUiState())

    init {
        // Awards are earned forever: persist the first moment a condition
        // is met so the date survives streak resets and month changes.
        viewModelScope.launch {
            uiState.collect { state ->
                val fresh = state.badges.filter { it.unlocked && it.unlockedAt == null }
                if (fresh.isNotEmpty()) {
                    walletRepository.unlockAwards(fresh.map { it.key })
                }
            }
        }
    }

    /**
     * The full award list, easy to hard. A locked quantitative award also
     * carries a "3 / 10" progress line for its detail dialog.
     */
    private fun buildAwards(
        stored: Map<String, Long>,
        txCount: Int,
        photoCount: Int,
        streak: Int,
        incomeAny: Boolean,
        hasSavings: Boolean,
        savedBynMinor: Long,
        goalsCount: Int,
        goalsAchieved: Int,
        challengesDone: Int,
        surplus: Boolean
    ): List<BadgeUi> {
        fun award(
            key: String,
            met: Boolean,
            current: Long? = null,
            target: Long? = null,
            money: Boolean = false
        ): BadgeUi {
            val storedAt = stored[key]
            val unlocked = met || storedAt != null
            val progressText = if (!unlocked && current != null && target != null) {
                if (money) {
                    Money.format(current.coerceAtMost(target), "BYN") +
                        " / " + Money.format(target, "BYN")
                } else {
                    "${current.coerceAtMost(target)} / $target"
                }
            } else {
                null
            }
            return BadgeUi(key, unlocked, storedAt, progressText)
        }

        return listOf(
            award("first_steps", txCount >= 1),
            award("income_first", incomeAny),
            award("ten", txCount >= 10, txCount.toLong(), 10),
            award("scanner", photoCount >= 1),
            award("saver", hasSavings),
            award("first_goal", goalsCount >= 1),
            award("streak7", streak >= 7, streak.toLong(), 7),
            award("surplus", surplus),
            award("goal_done", goalsAchieved >= 1),
            award("challenge_done", challengesDone >= 1),
            award("photo10", photoCount >= 10, photoCount.toLong(), 10),
            award("hundred", txCount >= 100, txCount.toLong(), 100),
            award("streak30", streak >= 30, streak.toLong(), 30),
            award(
                "big_saver",
                savedBynMinor >= BIG_SAVER_TARGET_MINOR,
                savedBynMinor,
                BIG_SAVER_TARGET_MINOR,
                money = true
            ),
            award("goal_three", goalsAchieved >= 3, goalsAchieved.toLong(), 3),
            award("challenge_five", challengesDone >= 5, challengesDone.toLong(), 5),
            award("five_hundred", txCount >= 500, txCount.toLong(), 500),
            award("streak100", streak >= 100, streak.toLong(), 100)
        )
    }

    private fun evaluate(
        challenge: ChallengeEntity,
        transactions: List<TransactionWithCategory>,
        savings: List<SavingEntity>,
        rates: Map<String, RateEntity>,
        currencyCode: String
    ): ChallengeUi {
        val today = LocalDate.now().toEpochDay()
        val daysTotal = (challenge.endEpochDay - challenge.startEpochDay + 1).toInt()
        val daysPassed = (today - challenge.startEpochDay + 1)
            .coerceIn(0, daysTotal.toLong()).toInt()
        val periodOver = today > challenge.endEpochDay

        val inPeriod = { timestamp: Long ->
            val day = Dates.toLocalDate(timestamp).toEpochDay()
            day in challenge.startEpochDay..challenge.endEpochDay
        }

        return when (challenge.type) {
            ChallengeType.SPEND_LIMIT -> {
                val spent = transactions
                    .filter { it.transaction.type == TransactionType.EXPENSE }
                    .filter { inPeriod(it.transaction.timestamp) }
                    .filter {
                        challenge.categoryId == null || it.category.id == challenge.categoryId
                    }
                    .sumOf { it.transaction.amountMinor }
                val status = when {
                    spent > challenge.amountMinor -> ChallengeStatus.FAILED
                    periodOver -> ChallengeStatus.DONE
                    else -> ChallengeStatus.ACTIVE
                }
                ChallengeUi(
                    entity = challenge,
                    progress = (spent.toFloat() / challenge.amountMinor).coerceIn(0f, 1f),
                    progressLabelMinor = spent,
                    daysPassed = daysPassed,
                    daysTotal = daysTotal,
                    status = status
                )
            }

            ChallengeType.NO_SPEND -> {
                val anyExpense = transactions.any {
                    it.transaction.type == TransactionType.EXPENSE &&
                        inPeriod(it.transaction.timestamp)
                }
                val status = when {
                    anyExpense -> ChallengeStatus.FAILED
                    periodOver -> ChallengeStatus.DONE
                    else -> ChallengeStatus.ACTIVE
                }
                ChallengeUi(
                    entity = challenge,
                    progress = daysPassed.toFloat() / daysTotal,
                    progressLabelMinor = 0,
                    daysPassed = daysPassed,
                    daysTotal = daysTotal,
                    status = status
                )
            }

            ChallengeType.SAVE_TARGET -> {
                val savedByn = savings
                    .filter { it.amountMinor > 0 && inPeriod(it.timestamp) }
                    .sumOf {
                        RatesRepository.toBynMinor(it.amountMinor, it.currencyCode, rates) ?: 0L
                    }
                val targetByn = RatesRepository
                    .toBynMinor(challenge.amountMinor, currencyCode, rates)
                    ?: challenge.amountMinor
                val status = when {
                    savedByn >= targetByn -> ChallengeStatus.DONE
                    periodOver -> ChallengeStatus.FAILED
                    else -> ChallengeStatus.ACTIVE
                }
                ChallengeUi(
                    entity = challenge,
                    progress = if (targetByn > 0) {
                        (savedByn.toFloat() / targetByn).coerceIn(0f, 1f)
                    } else {
                        0f
                    },
                    progressLabelMinor = savedByn,
                    daysPassed = daysPassed,
                    daysTotal = daysTotal,
                    status = status
                )
            }
        }
    }

    fun addChallenge(
        type: ChallengeType,
        title: String,
        amountMinor: Long,
        categoryId: Long?,
        end: LocalDate
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            walletRepository.addChallenge(
                type = type,
                title = title,
                amountMinor = amountMinor,
                categoryId = categoryId,
                start = LocalDate.now(),
                end = end
            )
        }
    }

    fun updateChallenge(
        challenge: ChallengeUi,
        title: String,
        amountMinor: Long,
        categoryId: Long?,
        end: LocalDate
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            walletRepository.updateChallenge(
                challenge.entity.copy(
                    title = title.trim(),
                    amountMinor = amountMinor,
                    categoryId = categoryId,
                    endEpochDay = end.toEpochDay()
                )
            )
        }
    }

    fun deleteChallenge(challenge: ChallengeUi) {
        viewModelScope.launch { walletRepository.deleteChallenge(challenge.entity.id) }
    }

    private companion object {
        const val HORIZON_DAYS = 180L

        /** 1000 BYN in minor units — the "big saver" award target. */
        const val BIG_SAVER_TARGET_MINOR = 100_000L
    }
}

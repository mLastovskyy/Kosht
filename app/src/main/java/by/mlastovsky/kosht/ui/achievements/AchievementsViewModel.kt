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
    val unlocked: Boolean
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
        val goalsAchieved: Boolean,
        val hasSavings: Boolean
    )

    private val walletData = combine(
        walletRepository.observeChallenges(),
        walletRepository.observeSavingsSince(horizonMillis),
        walletRepository.observeGoals(),
        walletRepository.observeSavingTotals()
    ) { challenges, savings, goals, totals ->
        WalletData(
            challenges = challenges,
            savings = savings,
            goalsAchieved = goals.any { it.achievedAt != null },
            hasSavings = totals.any { it.total > 0 }
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

        AchievementsUiState(
            loaded = true,
            streakDays = streak,
            dailyBudgetMinor = budget,
            challenges = wallet.challenges.map {
                evaluate(it, act.transactions, wallet.savings, ctx.rates, ctx.settings.currencyCode)
            },
            badges = listOf(
                BadgeUi("first_steps", act.txCount >= 1),
                BadgeUi("ten", act.txCount >= 10),
                BadgeUi("hundred", act.txCount >= 100),
                BadgeUi("streak7", streak >= 7),
                BadgeUi("streak30", streak >= 30),
                BadgeUi("scanner", act.photoCount >= 1),
                BadgeUi("saver", wallet.hasSavings),
                BadgeUi("goal_done", wallet.goalsAchieved),
                BadgeUi("surplus", monthIncome > monthExpense && monthExpense > 0)
            ),
            expenseCategories = ctx.categories,
            currencyCode = ctx.settings.currencyCode
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AchievementsUiState())

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

    fun deleteChallenge(challenge: ChallengeUi) {
        viewModelScope.launch { walletRepository.deleteChallenge(challenge.entity.id) }
    }

    private companion object {
        const val HORIZON_DAYS = 180L
    }
}

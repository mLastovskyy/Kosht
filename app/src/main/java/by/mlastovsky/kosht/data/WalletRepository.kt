package by.mlastovsky.kosht.data

import by.mlastovsky.kosht.data.db.AwardDao
import by.mlastovsky.kosht.data.db.AwardEntity
import by.mlastovsky.kosht.data.db.ChallengeDao
import by.mlastovsky.kosht.data.db.ChallengeEntity
import by.mlastovsky.kosht.data.db.DebtDao
import by.mlastovsky.kosht.data.db.DebtEntity
import by.mlastovsky.kosht.data.db.GoalDao
import by.mlastovsky.kosht.data.db.GoalProgress
import by.mlastovsky.kosht.data.db.RecurringDao
import by.mlastovsky.kosht.data.db.RecurringEntity
import by.mlastovsky.kosht.data.db.RecurringWithCategory
import by.mlastovsky.kosht.data.db.SavingDao
import by.mlastovsky.kosht.data.db.SavingEntity
import by.mlastovsky.kosht.data.db.SavingGoalEntity
import by.mlastovsky.kosht.data.db.SavingTotal
import by.mlastovsky.kosht.data.db.TransactionDao
import by.mlastovsky.kosht.data.db.TransactionEntity
import by.mlastovsky.kosht.model.ChallengeType
import by.mlastovsky.kosht.model.DebtDirection
import by.mlastovsky.kosht.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Debts, savings, goals, challenges and recurring charges — everything on
 * the Wallet tab and the achievements screen.
 */
class WalletRepository(
    private val debtDao: DebtDao,
    private val savingDao: SavingDao,
    private val recurringDao: RecurringDao,
    private val transactionDao: TransactionDao,
    private val goalDao: GoalDao,
    private val challengeDao: ChallengeDao,
    private val awardDao: AwardDao
) {

    // --- Debts ---

    fun observeDebts(): Flow<List<DebtEntity>> = debtDao.observeActive()

    suspend fun addDebt(
        personName: String,
        direction: DebtDirection,
        amountMinor: Long,
        currencyCode: String,
        note: String
    ): Long = debtDao.insert(
        DebtEntity(
            personName = personName.trim(),
            direction = direction,
            amountMinor = amountMinor,
            currencyCode = currencyCode,
            note = note.trim(),
            createdAt = System.currentTimeMillis()
        )
    )

    /** Reduces the remaining amount; closes the debt when fully repaid. */
    suspend fun repayDebt(debt: DebtEntity, repaymentMinor: Long) {
        val remaining = (debt.amountMinor - repaymentMinor).coerceAtLeast(0)
        debtDao.update(
            debt.copy(
                amountMinor = remaining,
                closedAt = if (remaining == 0L) System.currentTimeMillis() else null
            )
        )
    }

    suspend fun closeDebt(debt: DebtEntity) {
        debtDao.update(debt.copy(closedAt = System.currentTimeMillis()))
    }

    suspend fun deleteDebt(id: Long) = debtDao.deleteById(id)

    // --- Savings ---

    fun observeSavings(limit: Int): Flow<List<SavingEntity>> = savingDao.observeRecent(limit)

    fun observeSavingsSince(from: Long): Flow<List<SavingEntity>> = savingDao.observeSince(from)

    fun observeSavingTotals(): Flow<List<SavingTotal>> = savingDao.observeTotals()

    suspend fun addSaving(
        amountMinor: Long,
        currencyCode: String,
        note: String,
        goalId: Long? = null
    ): Long {
        val id = savingDao.insert(
            SavingEntity(
                amountMinor = amountMinor,
                currencyCode = currencyCode,
                note = note.trim(),
                timestamp = System.currentTimeMillis(),
                goalId = goalId
            )
        )
        if (goalId != null) checkGoalAchieved(goalId)
        return id
    }

    suspend fun deleteSaving(id: Long) = savingDao.deleteById(id)

    // --- Savings goals ---

    fun observeGoals(): Flow<List<SavingGoalEntity>> = goalDao.observeAll()

    fun observeGoalProgress(): Flow<List<GoalProgress>> = goalDao.observeProgress()

    suspend fun addGoal(title: String, targetMinor: Long, currencyCode: String): Long =
        goalDao.insert(
            SavingGoalEntity(
                title = title.trim(),
                targetMinor = targetMinor,
                currencyCode = currencyCode,
                createdAt = System.currentTimeMillis()
            )
        )

    suspend fun deleteGoal(id: Long) {
        goalDao.unlinkSavings(id)
        goalDao.deleteById(id)
    }

    private suspend fun checkGoalAchieved(goalId: Long) {
        val goal = goalDao.getById(goalId) ?: return
        if (goal.achievedAt != null) return
        // One-shot read of the freshly updated progress.
        val total = goalDao.observeProgress().first()
            .firstOrNull { it.goalId == goalId }?.total ?: 0L
        if (total >= goal.targetMinor) {
            goalDao.update(goal.copy(achievedAt = System.currentTimeMillis()))
        }
    }

    // --- Challenges ---

    fun observeChallenges(): Flow<List<ChallengeEntity>> = challengeDao.observeAll()

    suspend fun addChallenge(
        type: ChallengeType,
        title: String,
        amountMinor: Long,
        categoryId: Long?,
        start: LocalDate,
        end: LocalDate
    ): Long = challengeDao.insert(
        ChallengeEntity(
            type = type,
            title = title.trim(),
            amountMinor = amountMinor,
            categoryId = categoryId,
            startEpochDay = start.toEpochDay(),
            endEpochDay = end.toEpochDay(),
            createdAt = System.currentTimeMillis()
        )
    )

    suspend fun updateChallenge(challenge: ChallengeEntity) = challengeDao.update(challenge)

    suspend fun deleteChallenge(id: Long) = challengeDao.deleteById(id)

    // --- Awards ---

    fun observeAwards(): Flow<List<AwardEntity>> = awardDao.observeAll()

    /** Marks freshly met awards as earned; already earned keep their date. */
    suspend fun unlockAwards(keys: List<String>) {
        if (keys.isEmpty()) return
        val now = System.currentTimeMillis()
        awardDao.insertAll(keys.map { AwardEntity(key = it, unlockedAt = now) })
    }

    // --- Recurring charges ---

    fun observeRecurring(): Flow<List<RecurringWithCategory>> = recurringDao.observeAll()

    suspend fun addRecurring(
        title: String,
        amountMinor: Long,
        currencyCode: String,
        categoryId: Long,
        firstDue: LocalDate,
        frequency: by.mlastovsky.kosht.model.RecurringFrequency
    ): Long = recurringDao.insert(
        RecurringEntity(
            title = title.trim(),
            amountMinor = amountMinor,
            currencyCode = currencyCode,
            categoryId = categoryId,
            nextDueEpochDay = firstDue.toEpochDay(),
            frequency = frequency,
            createdAt = System.currentTimeMillis()
        )
    )

    suspend fun updateRecurring(recurring: RecurringEntity) = recurringDao.update(recurring)

    suspend fun deleteRecurring(id: Long) = recurringDao.deleteById(id)

    suspend fun setRecurringEnabled(recurring: RecurringEntity, enabled: Boolean) {
        recurringDao.update(recurring.copy(enabled = enabled))
    }

    /**
     * Confirms a due charge: records the expense transaction (already
     * converted to the app currency by the caller when needed) against the
     * chosen account and advances the next due date by one period.
     */
    suspend fun confirmRecurring(
        recurring: RecurringEntity,
        chargeAmountMinor: Long,
        bynMinor: Long?,
        accountId: Long? = null
    ) {
        val now = System.currentTimeMillis()
        transactionDao.insert(
            TransactionEntity(
                amountMinor = chargeAmountMinor,
                type = TransactionType.EXPENSE,
                categoryId = recurring.categoryId,
                note = recurring.title,
                timestamp = now,
                createdAt = now,
                bynMinor = bynMinor,
                accountId = accountId
            )
        )
        recurringDao.update(recurring.advanced())
    }
}

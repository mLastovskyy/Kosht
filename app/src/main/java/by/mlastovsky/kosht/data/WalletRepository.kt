package by.mlastovsky.kosht.data

import by.mlastovsky.kosht.data.db.AwardDao
import by.mlastovsky.kosht.data.db.AwardEntity
import by.mlastovsky.kosht.data.db.CategoryDao
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
import by.mlastovsky.kosht.model.RecurringFrequency
import by.mlastovsky.kosht.model.TransactionType
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class LedgerEntry(
    val categoryKey: String,
    val type: TransactionType,
    val amountMinor: Long,
    val note: String,
    val bynMinor: Long?,
    val accountId: Long?,

    val debtId: Long? = null,

    val debtDeltaMinor: Long = 0
)

class WalletRepository(
    private val debtDao: DebtDao,
    private val savingDao: SavingDao,
    private val recurringDao: RecurringDao,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val goalDao: GoalDao,
    private val challengeDao: ChallengeDao,
    private val awardDao: AwardDao
) {

    fun observeDebts(): Flow<List<DebtEntity>> = debtDao.observeActive()

    fun observeClosedDebtCount(): Flow<Int> = debtDao.observeClosedCount()

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

    suspend fun repayDebt(debt: DebtEntity, repaymentMinor: Long, entry: LedgerEntry? = null) {
        val remaining = (debt.amountMinor - repaymentMinor).coerceAtLeast(0)
        debtDao.update(
            debt.copy(
                amountMinor = remaining,
                closedAt = if (remaining == 0L) System.currentTimeMillis() else null
            )
        )
        record(entry?.copy(debtId = debt.id, debtDeltaMinor = debt.amountMinor - remaining))
    }

    suspend fun closeDebt(debt: DebtEntity, entry: LedgerEntry? = null) {
        debtDao.update(debt.copy(closedAt = System.currentTimeMillis()))
        record(entry?.copy(debtId = debt.id))
    }

    private suspend fun record(entry: LedgerEntry?) {
        if (entry == null || entry.amountMinor <= 0) return
        val categoryId = categoryDao.getByKey(entry.categoryKey)?.id
            ?: categoryDao.observeByType(entry.type).first().firstOrNull()?.id
            ?: return
        val now = System.currentTimeMillis()
        transactionDao.insert(
            TransactionEntity(
                amountMinor = entry.amountMinor,
                type = entry.type,
                categoryId = categoryId,
                note = entry.note,
                timestamp = now,
                createdAt = now,
                bynMinor = entry.bynMinor,
                accountId = entry.accountId,
                debtId = entry.debtId,
                debtDeltaMinor = entry.debtDeltaMinor
            )
        )
    }

    suspend fun updateDebt(debt: DebtEntity) = debtDao.update(debt)

    suspend fun deleteDebt(id: Long) = debtDao.deleteById(id)

    fun observeSavings(limit: Int): Flow<List<SavingEntity>> = savingDao.observeRecent(limit)

    fun observeSavingsSince(from: Long): Flow<List<SavingEntity>> = savingDao.observeSince(from)

    fun observeSavingTotals(): Flow<List<SavingTotal>> = savingDao.observeTotals()

    suspend fun addSaving(
        amountMinor: Long,
        currencyCode: String,
        note: String,
        goalId: Long? = null,
        entry: LedgerEntry? = null
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
        record(entry)
        if (goalId != null) checkGoalAchieved(goalId)
        return id
    }

    suspend fun deleteSaving(id: Long) = savingDao.deleteById(id)

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

    suspend fun updateGoal(goal: SavingGoalEntity, savingsFactor: Double? = null) {
        goalDao.update(goal)
        if (savingsFactor != null && savingsFactor > 0.0) {
            savingDao.convertForGoal(goal.id, goal.currencyCode, savingsFactor)
        }
        checkGoalAchieved(goal.id)
    }

    private suspend fun checkGoalAchieved(goalId: Long) {
        val goal = goalDao.getById(goalId) ?: return
        if (goal.achievedAt != null) return

        val total = goalDao.observeProgress().first()
            .firstOrNull { it.goalId == goalId }?.total ?: 0L
        if (total >= goal.targetMinor) {
            goalDao.update(goal.copy(achievedAt = System.currentTimeMillis()))
        }
    }

    fun observeChallenges(): Flow<List<ChallengeEntity>> = challengeDao.observeAll()

    suspend fun addChallenge(
        type: ChallengeType,
        title: String,
        amountMinor: Long,
        categoryId: Long?,
        start: LocalDate,
        end: LocalDate,
        currencyCode: String? = null,
        goalId: Long? = null
    ): Long = challengeDao.insert(
        ChallengeEntity(
            type = type,
            title = title.trim(),
            amountMinor = amountMinor,
            currencyCode = currencyCode,
            categoryId = categoryId,
            goalId = goalId,
            startEpochDay = start.toEpochDay(),
            endEpochDay = end.toEpochDay(),
            createdAt = System.currentTimeMillis()
        )
    )

    suspend fun addSavingChallenge(
        title: String,
        amountMinor: Long,
        currencyCode: String,
        start: LocalDate,
        end: LocalDate
    ): Long = addChallenge(
        type = ChallengeType.SAVE_TARGET,
        title = title,
        amountMinor = amountMinor,
        categoryId = null,
        start = start,
        end = end,
        currencyCode = currencyCode,
        goalId = addGoal(title, amountMinor, currencyCode)
    )

    suspend fun updateChallenge(challenge: ChallengeEntity, savingsFactor: Double? = null) {
        challengeDao.update(challenge)
        val goal = challenge.goalId?.let { goalDao.getById(it) } ?: return
        updateGoal(
            goal.copy(
                title = challenge.title,
                targetMinor = challenge.amountMinor,
                currencyCode = challenge.currencyCode ?: goal.currencyCode
            ),
            savingsFactor = savingsFactor
        )
    }

    suspend fun deleteChallenge(id: Long) = challengeDao.deleteById(id)

    fun observeAwards(): Flow<List<AwardEntity>> = awardDao.observeAll()

    fun observeAwardsByKey(): Flow<Map<String, Long>> = awardDao.observeAll()
        .map { awards -> awards.associate { it.key to it.unlockedAt } }

    suspend fun unlockAwards(keys: List<String>) {
        if (keys.isEmpty()) return
        val now = System.currentTimeMillis()
        awardDao.insertAll(keys.map { AwardEntity(key = it, unlockedAt = now) })
    }

    fun observeRecurring(): Flow<List<RecurringWithCategory>> = recurringDao.observeAll()

    suspend fun addRecurring(
        title: String,
        amountMinor: Long,
        currencyCode: String,
        categoryId: Long,
        firstDue: LocalDate,
        frequency: RecurringFrequency,
        type: TransactionType = TransactionType.EXPENSE,
        accountId: Long? = null
    ): Long = recurringDao.insert(
        RecurringEntity(
            title = title.trim(),
            amountMinor = amountMinor,
            currencyCode = currencyCode,
            categoryId = categoryId,
            nextDueEpochDay = firstDue.toEpochDay(),
            frequency = frequency,
            createdAt = System.currentTimeMillis(),
            type = type,
            accountId = accountId
        )
    )

    suspend fun updateRecurring(recurring: RecurringEntity) = recurringDao.update(recurring)

    suspend fun deleteRecurring(id: Long) = recurringDao.deleteById(id)

    suspend fun setRecurringEnabled(recurring: RecurringEntity, enabled: Boolean) {
        recurringDao.update(recurring.copy(enabled = enabled))
    }

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
                type = recurring.type,
                categoryId = recurring.categoryId,
                note = recurring.title,
                timestamp = now,
                createdAt = now,
                bynMinor = bynMinor,
                accountId = accountId ?: recurring.accountId
            )
        )
        recurringDao.update(recurring.advanced())
    }
}

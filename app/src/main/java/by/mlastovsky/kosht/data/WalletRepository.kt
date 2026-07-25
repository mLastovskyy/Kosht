package by.mlastovsky.kosht.data

import by.mlastovsky.kosht.data.db.DebtDao
import by.mlastovsky.kosht.data.db.DebtEntity
import by.mlastovsky.kosht.data.db.RecurringDao
import by.mlastovsky.kosht.data.db.RecurringEntity
import by.mlastovsky.kosht.data.db.RecurringWithCategory
import by.mlastovsky.kosht.data.db.SavingDao
import by.mlastovsky.kosht.data.db.SavingEntity
import by.mlastovsky.kosht.data.db.SavingTotal
import by.mlastovsky.kosht.data.db.TransactionDao
import by.mlastovsky.kosht.data.db.TransactionEntity
import by.mlastovsky.kosht.model.DebtDirection
import by.mlastovsky.kosht.model.TransactionType
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

/**
 * Debts, savings and recurring charges — everything on the Wallet tab.
 */
class WalletRepository(
    private val debtDao: DebtDao,
    private val savingDao: SavingDao,
    private val recurringDao: RecurringDao,
    private val transactionDao: TransactionDao
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

    fun observeSavingTotals(): Flow<List<SavingTotal>> = savingDao.observeTotals()

    suspend fun addSaving(amountMinor: Long, currencyCode: String, note: String): Long =
        savingDao.insert(
            SavingEntity(
                amountMinor = amountMinor,
                currencyCode = currencyCode,
                note = note.trim(),
                timestamp = System.currentTimeMillis()
            )
        )

    suspend fun deleteSaving(id: Long) = savingDao.deleteById(id)

    // --- Recurring charges ---

    fun observeRecurring(): Flow<List<RecurringWithCategory>> = recurringDao.observeAll()

    suspend fun addRecurring(
        title: String,
        amountMinor: Long,
        currencyCode: String,
        categoryId: Long,
        dayOfMonth: Int
    ): Long = recurringDao.insert(
        RecurringEntity(
            title = title.trim(),
            amountMinor = amountMinor,
            currencyCode = currencyCode,
            categoryId = categoryId,
            dayOfMonth = dayOfMonth.coerceIn(1, 31),
            createdAt = System.currentTimeMillis()
        )
    )

    suspend fun updateRecurring(recurring: RecurringEntity) = recurringDao.update(recurring)

    suspend fun deleteRecurring(id: Long) = recurringDao.deleteById(id)

    suspend fun setRecurringEnabled(recurring: RecurringEntity, enabled: Boolean) {
        recurringDao.update(recurring.copy(enabled = enabled))
    }

    /**
     * Confirms this month's charge: records the expense transaction (already
     * converted to the app currency by the caller when needed) and stamps the
     * current period so it is not asked again until next month.
     */
    suspend fun confirmRecurring(
        recurring: RecurringEntity,
        chargeAmountMinor: Long,
        bynMinor: Long?
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
                bynMinor = bynMinor
            )
        )
        recurringDao.update(recurring.copy(lastConfirmed = YearMonth.now().toString()))
    }
}

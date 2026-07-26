package by.mlastovsky.kosht.data

import by.mlastovsky.kosht.data.db.CategoryDao
import by.mlastovsky.kosht.data.db.CategoryEntity
import by.mlastovsky.kosht.data.db.CategoryTotal
import by.mlastovsky.kosht.data.db.ItemInContext
import by.mlastovsky.kosht.data.db.RecurringDao
import by.mlastovsky.kosht.data.db.TransactionDao
import by.mlastovsky.kosht.data.db.TransactionEntity
import by.mlastovsky.kosht.data.db.TransactionItemEntity
import by.mlastovsky.kosht.data.db.TransactionWithCategory
import by.mlastovsky.kosht.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * A product line on its way to being saved: what the editor holds before the
 * record it belongs to has an id.
 */
data class ItemDraft(
    val name: String,
    val amountMinor: Long = 0,
    val quantity: Double? = null
)

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val recurringDao: RecurringDao,
    private val itemDao: by.mlastovsky.kosht.data.db.TransactionItemDao
) {

    fun observeBetween(from: Long, to: Long): Flow<List<TransactionWithCategory>> =
        transactionDao.observeBetween(from, to)

    fun observeRecent(limit: Int): Flow<List<TransactionWithCategory>> =
        transactionDao.observeRecent(limit)

    fun observeBalance(): Flow<Long> = transactionDao.observeBalance()

    fun observeTotal(type: TransactionType, from: Long, to: Long): Flow<Long> =
        transactionDao.observeTotal(type, from, to)

    fun observeCategoryTotals(type: TransactionType, from: Long, to: Long): Flow<List<CategoryTotal>> =
        transactionDao.observeCategoryTotals(type, from, to)

    fun observeCount(): Flow<Int> = transactionDao.observeCount()

    fun observePhotoCount(): Flow<Int> = transactionDao.observePhotoCount()

    fun observeCreatedSince(from: Long): Flow<List<Long>> =
        transactionDao.observeCreatedSince(from)

    fun observeIncomeCount(): Flow<Int> = transactionDao.observeIncomeCount()

    fun observeExpenseCategoryCount(): Flow<Int> = transactionDao.observeExpenseCategoryCount()

    fun observeNightCount(): Flow<Int> = transactionDao.observeNightCount()

    fun observeFirstTimestamp(): Flow<Long?> = transactionDao.observeFirstTimestamp()

    fun observeDailyCategorySpend(): Flow<List<by.mlastovsky.kosht.data.db.DailyCategorySpend>> =
        transactionDao.observeDailyCategorySpend()

    fun observeMonthlyTotals(): Flow<List<by.mlastovsky.kosht.data.db.MonthlyTotals>> =
        transactionDao.observeMonthlyTotals()

    suspend fun getTransaction(id: Long): TransactionWithCategory? = transactionDao.getById(id)

    suspend fun addTransaction(transaction: TransactionEntity): Long =
        transactionDao.insert(transaction)

    /**
     * Puts a deleted record back exactly as it was, product lines included —
     * they were cascaded away with it, so restoring only the record would quietly
     * lose them.
     */
    suspend fun restore(record: by.mlastovsky.kosht.data.DeletedRecord) {
        transactionDao.insert(record.transaction)
        if (record.items.isNotEmpty()) itemDao.insertAll(record.items)
    }

    suspend fun updateTransaction(transaction: TransactionEntity) =
        transactionDao.update(transaction)

    suspend fun deleteTransaction(transaction: TransactionEntity) =
        transactionDao.delete(transaction)

    suspend fun deleteTransactionById(id: Long) = transactionDao.deleteById(id)

    // --- What a record was spent on ----------------------------------------

    fun observeItems(transactionId: Long): Flow<List<TransactionItemEntity>> =
        itemDao.observeFor(transactionId)

    suspend fun itemsOf(transactionId: Long): List<TransactionItemEntity> =
        itemDao.itemsFor(transactionId)

    /** Everything bought in a period, for the product statistics. */
    fun observeItemsBetween(from: Long, to: Long): Flow<List<ItemInContext>> =
        itemDao.observeBetween(from, to)

    /** Item names already used in a category, for suggesting them again. */
    fun observeItemNames(categoryId: Long): Flow<List<String>> =
        itemDao.observeNamesIn(categoryId)

    /**
     * Replaces the product lines of a record. Rewriting them wholesale is what
     * keeps the editor honest: what is on screen is what is stored, and the
     * order the user put them in survives.
     */
    suspend fun saveItems(transactionId: Long, items: List<ItemDraft>) {
        itemDao.deleteFor(transactionId)
        if (items.isEmpty()) return
        itemDao.insertAll(
            items.mapIndexedNotNull { index, draft ->
                val name = normalizeItemName(draft.name) ?: return@mapIndexedNotNull null
                TransactionItemEntity(
                    transactionId = transactionId,
                    name = name,
                    amountMinor = draft.amountMinor.coerceAtLeast(0),
                    quantity = draft.quantity?.takeIf { it > 0 },
                    position = index
                )
            }
        )
    }

    /**
     * One settled spelling per product, so the statistics can group by name:
     * trimmed, single-spaced, and cased the way a list reads best. Kotlin's
     * lowercase understands Cyrillic, which SQLite's own lower() does not —
     * hence doing it here rather than in the query.
     */
    fun normalizeItemName(raw: String): String? {
        val trimmed = raw.trim().replace(Regex("""\s{2,}"""), " ").take(ITEM_NAME_MAX)
        if (trimmed.none { it.isLetterOrDigit() }) return null
        val lower = trimmed.lowercase()
        return lower.replaceFirstChar { it.uppercaseChar() }
    }

    // --- Transfers between the user's own accounts -------------------------

    /**
     * Writes a transfer as one record: the amount, plus what the transfer cost,
     * leaves [fromAccountId] and the amount lands on [toAccountId]. Nothing is
     * spent or earned, so the statistics skip such rows and only the balances
     * move. Returns false when the transfer makes no sense.
     */
    suspend fun saveTransfer(
        original: TransactionEntity?,
        fromAccountId: Long,
        toAccountId: Long,
        amountMinor: Long,
        feeMinor: Long,
        bynMinor: Long?,
        note: String,
        timestamp: Long
    ): Boolean {
        if (amountMinor <= 0 || fromAccountId == toAccountId) return false
        val fee = feeMinor.coerceAtLeast(0)
        if (original != null) {
            transactionDao.update(
                original.copy(
                    amountMinor = amountMinor,
                    note = note.trim(),
                    timestamp = timestamp,
                    accountId = fromAccountId,
                    bynMinor = bynMinor,
                    transferToAccountId = toAccountId,
                    transferFeeMinor = fee
                )
            )
            return true
        }
        val categoryId = transferCategoryId() ?: return false
        val now = System.currentTimeMillis()
        transactionDao.insert(
            TransactionEntity(
                amountMinor = amountMinor,
                // The row hangs off the source account, which is where the
                // money goes out; the destination is the transfer marker.
                type = TransactionType.EXPENSE,
                categoryId = categoryId,
                note = note.trim(),
                timestamp = timestamp,
                createdAt = now,
                accountId = fromAccountId,
                bynMinor = bynMinor,
                transferToAccountId = toAccountId,
                transferFeeMinor = fee
            )
        )
        return true
    }

    /**
     * A transfer belongs to no category, but the column is required, so it
     * borrows the built-in "other" one — invisible either way, since transfers
     * are shown with their own icon and left out of every category total.
     */
    private suspend fun transferCategoryId(): Long? =
        categoryDao.getByKey("other_expense")?.id
            ?: categoryDao.observeByType(TransactionType.EXPENSE).first().firstOrNull()?.id

    private companion object {
        /** A product name longer than this is a description, not a name. */
        const val ITEM_NAME_MAX = 60
    }

    fun observeCategories(): Flow<List<CategoryEntity>> = categoryDao.observeAll()

    fun observeCategories(type: TransactionType): Flow<List<CategoryEntity>> =
        categoryDao.observeByType(type)

    suspend fun getCategory(id: Long): CategoryEntity? = categoryDao.getById(id)

    suspend fun addCategory(name: String, iconKey: String, colorArgb: Long, type: TransactionType): Long {
        val position = categoryDao.maxPosition(type) + 1
        return categoryDao.insert(
            CategoryEntity(
                key = null,
                name = name.trim(),
                iconKey = iconKey,
                colorArgb = colorArgb,
                type = type,
                position = position
            )
        )
    }

    suspend fun updateCategory(category: CategoryEntity) = categoryDao.update(category)

    /**
     * Deletes a category. Existing transactions and recurring charges are
     * moved to the built-in "other" category of the same type so nothing
     * is lost.
     */
    suspend fun deleteCategory(category: CategoryEntity) {
        val fallbackKey =
            if (category.type == TransactionType.EXPENSE) "other_expense" else "other_income"
        val fallback = categoryDao.getByKey(fallbackKey)
        if (fallback != null && fallback.id != category.id) {
            transactionDao.reassignCategory(from = category.id, to = fallback.id)
            recurringDao.reassignCategory(from = category.id, to = fallback.id)
        }
        categoryDao.deleteById(category.id)
    }
}

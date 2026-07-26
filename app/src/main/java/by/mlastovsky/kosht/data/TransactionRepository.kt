package by.mlastovsky.kosht.data

import android.net.Uri
import by.mlastovsky.kosht.data.db.CategoryDao
import by.mlastovsky.kosht.data.db.CategoryEntity
import by.mlastovsky.kosht.data.db.CategoryTotal
import by.mlastovsky.kosht.data.db.DailyCategorySpend
import by.mlastovsky.kosht.data.db.ItemInContext
import by.mlastovsky.kosht.data.db.MonthlyTotals
import by.mlastovsky.kosht.data.db.RecurringDao
import by.mlastovsky.kosht.data.db.TransactionDao
import by.mlastovsky.kosht.data.db.TransactionEntity
import by.mlastovsky.kosht.data.db.TransactionItemDao
import by.mlastovsky.kosht.data.db.TransactionItemEntity
import by.mlastovsky.kosht.data.db.TransactionWithCategory
import by.mlastovsky.kosht.model.TransactionType
import by.mlastovsky.kosht.util.ItemNames
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class ItemDraft(
    val name: String,
    val amountMinor: Long = 0,
    val quantity: Double? = null
)

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val recurringDao: RecurringDao,
    private val itemDao: TransactionItemDao,

    private val photoStore: PhotoStore
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

    fun observeDailyCategorySpend(): Flow<List<DailyCategorySpend>> =
        transactionDao.observeDailyCategorySpend()

    fun observeMonthlyTotals(): Flow<List<MonthlyTotals>> =
        transactionDao.observeMonthlyTotals()

    suspend fun getTransaction(id: Long): TransactionWithCategory? = transactionDao.getById(id)

    suspend fun addTransaction(transaction: TransactionEntity): Long =
        transactionDao.insert(transaction)

    suspend fun restore(record: DeletedRecord) {
        transactionDao.insert(record.transaction)
        if (record.items.isNotEmpty()) itemDao.insertAll(record.items)
    }

    suspend fun updateTransaction(transaction: TransactionEntity) =
        transactionDao.update(transaction)

    suspend fun deleteTransaction(transaction: TransactionEntity) =
        transactionDao.delete(transaction)

    suspend fun deleteTransactionById(id: Long) = transactionDao.deleteById(id)

    fun observeItems(transactionId: Long): Flow<List<TransactionItemEntity>> =
        itemDao.observeFor(transactionId)

    suspend fun itemsOf(transactionId: Long): List<TransactionItemEntity> =
        itemDao.itemsFor(transactionId)

    fun observeItemsBetween(from: Long, to: Long): Flow<List<ItemInContext>> =
        itemDao.observeBetween(from, to)

    fun observeItemNames(categoryId: Long): Flow<List<String>> =
        itemDao.observeNamesIn(categoryId).map { names ->
            names.distinctBy { ItemNames.key(it) }
        }

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

    fun normalizeItemName(raw: String): String? = ItemNames.normalize(raw)

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

    private suspend fun transferCategoryId(): Long? =
        categoryDao.getByKey("other_expense")?.id
            ?: categoryDao.observeByType(TransactionType.EXPENSE).first().firstOrNull()?.id

    fun observeCategories(): Flow<List<CategoryEntity>> = categoryDao.observeAll()

    fun observeCategories(type: TransactionType): Flow<List<CategoryEntity>> =
        categoryDao.observeByType(type)

    suspend fun getCategory(id: Long): CategoryEntity? = categoryDao.getById(id)

    suspend fun addCategory(
        name: String,
        iconKey: String,
        colorArgb: Long,
        type: TransactionType,
        iconUri: Uri? = null
    ): Long {
        val position = categoryDao.maxPosition(type) + 1
        return categoryDao.insert(
            CategoryEntity(
                key = null,
                name = name.trim(),
                iconKey = iconKey,
                colorArgb = colorArgb,
                type = type,
                position = position,
                iconPath = iconUri?.let { photoStore.saveFromUri(it, CATEGORY_ICONS) }
            )
        )
    }

    suspend fun updateCategory(category: CategoryEntity) = categoryDao.update(category)

    suspend fun updateCategory(
        id: Long,
        name: String,
        iconKey: String,
        colorArgb: Long,
        iconUri: Uri? = null,
        clearIcon: Boolean = false
    ) {
        if (name.isBlank()) return
        val existing = categoryDao.getById(id) ?: return
        val saved = iconUri?.let { photoStore.saveFromUri(it, CATEGORY_ICONS) }
        val iconPath = when {
            saved != null -> saved
            clearIcon -> null
            else -> existing.iconPath
        }

        if (iconPath != existing.iconPath) photoStore.delete(existing.iconPath)
        categoryDao.update(
            existing.copy(
                name = name.trim(),
                iconKey = iconKey,
                colorArgb = colorArgb,
                iconPath = iconPath
            )
        )
    }

    suspend fun reorderCategories(ids: List<Long>) {
        if (ids.isEmpty()) return

        val base = categoryDao.minPosition(ids)
        ids.forEachIndexed { index, id -> categoryDao.updatePosition(id, base + index) }
    }

    suspend fun deleteCategory(category: CategoryEntity) {
        val fallbackKey =
            if (category.type == TransactionType.EXPENSE) "other_expense" else "other_income"
        val fallback = categoryDao.getByKey(fallbackKey)
        if (fallback != null && fallback.id != category.id) {
            transactionDao.reassignCategory(from = category.id, to = fallback.id)
            recurringDao.reassignCategory(from = category.id, to = fallback.id)
        }
        categoryDao.deleteById(category.id)
        photoStore.delete(category.iconPath)
    }

    private companion object {

        const val CATEGORY_ICONS = "categories"
    }
}

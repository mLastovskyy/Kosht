package by.mlastovsky.kosht.data

import by.mlastovsky.kosht.data.db.CategoryDao
import by.mlastovsky.kosht.data.db.CategoryEntity
import by.mlastovsky.kosht.data.db.CategoryTotal
import by.mlastovsky.kosht.data.db.RecurringDao
import by.mlastovsky.kosht.data.db.TransactionDao
import by.mlastovsky.kosht.data.db.TransactionEntity
import by.mlastovsky.kosht.data.db.TransactionWithCategory
import by.mlastovsky.kosht.model.TransactionType
import kotlinx.coroutines.flow.Flow

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val recurringDao: RecurringDao
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

    suspend fun getTransaction(id: Long): TransactionWithCategory? = transactionDao.getById(id)

    suspend fun addTransaction(transaction: TransactionEntity): Long =
        transactionDao.insert(transaction)

    suspend fun updateTransaction(transaction: TransactionEntity) =
        transactionDao.update(transaction)

    suspend fun deleteTransaction(transaction: TransactionEntity) =
        transactionDao.delete(transaction)

    suspend fun deleteTransactionById(id: Long) = transactionDao.deleteById(id)

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

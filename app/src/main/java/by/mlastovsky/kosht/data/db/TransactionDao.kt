package by.mlastovsky.kosht.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import by.mlastovsky.kosht.model.TransactionType
import kotlinx.coroutines.flow.Flow

data class CategoryTotal(
    val categoryId: Long,
    val total: Long
)

data class DailyCategorySpend(
    val day: String,
    val categoryId: Long,
    val total: Long
)

data class MonthlyTotals(
    val month: String,
    val income: Long,
    val expense: Long
)

@Dao
interface TransactionDao {

    @Transaction
    @Query(
        "SELECT * FROM transactions " +
            "WHERE timestamp >= :from AND timestamp < :to " +
            "ORDER BY timestamp DESC, id DESC"
    )
    fun observeBetween(from: Long, to: Long): Flow<List<TransactionWithCategory>>

    @Transaction
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TransactionWithCategory>>

    @Transaction
    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionWithCategory?

    @Query(
        "SELECT accountId FROM transactions WHERE accountId IS NOT NULL " +
            "AND transferToAccountId IS NULL ORDER BY timestamp DESC, id DESC LIMIT 1"
    )
    suspend fun lastUsedAccountId(): Long?

    @Query(
        "SELECT COALESCE(SUM(amountMinor), 0) FROM transactions " +
            "WHERE type = :type AND transferToAccountId IS NULL " +
            "AND timestamp >= :from AND timestamp < :to"
    )
    fun observeTotal(type: TransactionType, from: Long, to: Long): Flow<Long>

    @Query(
        "SELECT COALESCE(SUM(CASE WHEN transferToAccountId IS NOT NULL THEN -transferFeeMinor " +
            "WHEN type = 'INCOME' THEN amountMinor ELSE -amountMinor END), 0) " +
            "FROM transactions"
    )
    fun observeBalance(): Flow<Long>

    @Query(
        "SELECT categoryId, COALESCE(SUM(amountMinor), 0) AS total FROM transactions " +
            "WHERE type = :type AND transferToAccountId IS NULL " +
            "AND timestamp >= :from AND timestamp < :to " +
            "GROUP BY categoryId ORDER BY total DESC"
    )
    fun observeCategoryTotals(type: TransactionType, from: Long, to: Long): Flow<List<CategoryTotal>>

    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId = :categoryId")
    suspend fun countByCategory(categoryId: Long): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE transferToAccountId IS NULL")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM transactions WHERE photoPath IS NOT NULL")
    fun observePhotoCount(): Flow<Int>

    @Query(
        "SELECT COUNT(*) FROM transactions " +
            "WHERE type = 'INCOME' AND transferToAccountId IS NULL"
    )
    fun observeIncomeCount(): Flow<Int>

    @Query(
        "SELECT COUNT(DISTINCT categoryId) FROM transactions " +
            "WHERE type = 'EXPENSE' AND transferToAccountId IS NULL"
    )
    fun observeExpenseCategoryCount(): Flow<Int>

    @Query(
        "SELECT COUNT(*) FROM transactions WHERE transferToAccountId IS NULL AND CAST(" +
            "strftime('%H', createdAt / 1000, 'unixepoch', 'localtime') AS INTEGER) < 5"
    )
    fun observeNightCount(): Flow<Int>

    @Query("SELECT MIN(timestamp) FROM transactions WHERE transferToAccountId IS NULL")
    fun observeFirstTimestamp(): Flow<Long?>

    @Query(
        "SELECT strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch', 'localtime') AS day, " +
            "categoryId, COALESCE(SUM(amountMinor), 0) AS total FROM transactions " +
            "WHERE type = 'EXPENSE' AND transferToAccountId IS NULL GROUP BY day, categoryId"
    )
    fun observeDailyCategorySpend(): Flow<List<DailyCategorySpend>>

    @Query(
        "SELECT strftime('%Y-%m', timestamp / 1000, 'unixepoch', 'localtime') AS month, " +
            "COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amountMinor ELSE 0 END), 0) AS income, " +
            "COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amountMinor ELSE 0 END), 0) AS expense " +
            "FROM transactions WHERE transferToAccountId IS NULL " +
            "GROUP BY month ORDER BY month ASC"
    )
    fun observeMonthlyTotals(): Flow<List<MonthlyTotals>>

    @Query(
        "SELECT createdAt FROM transactions " +
            "WHERE createdAt >= :from AND transferToAccountId IS NULL"
    )
    fun observeCreatedSince(from: Long): Flow<List<Long>>

    @Query("UPDATE transactions SET categoryId = :to WHERE categoryId = :from")
    suspend fun reassignCategory(from: Long, to: Long)

    @Query("SELECT * FROM transactions WHERE photoPath IS NOT NULL AND photoKey IS NULL")
    suspend fun photosToUpload(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE photoKey IS NOT NULL AND photoPath IS NULL")
    suspend fun photosToDownload(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE photoKey IS NOT NULL")
    suspend fun photosUploaded(): List<TransactionEntity>

    @Query(
        "SELECT photoPath FROM transactions WHERE photoPath IS NOT NULL " +
            "UNION SELECT receiptDocPath FROM transactions WHERE receiptDocPath IS NOT NULL"
    )
    suspend fun referencedFiles(): List<String>

    @Query("UPDATE transactions SET photoKey = :key WHERE id = :id")
    suspend fun setPhotoKey(id: Long, key: String?)

    @Query("UPDATE transactions SET photoPath = :path WHERE id = :id")
    suspend fun setPhotoPath(id: Long, path: String?)

    @Query("UPDATE transactions SET photoKey = NULL WHERE photoKey IS NOT NULL")
    suspend fun clearPhotoKeys()

    @Query(
        "UPDATE transactions SET amountMinor = CAST(ROUND(amountMinor * :factor) AS INTEGER), " +
            "transferFeeMinor = CAST(ROUND(transferFeeMinor * :factor) AS INTEGER)"
    )
    suspend fun rescaleAmounts(factor: Double)

    @Query("UPDATE transactions SET accountId = :to WHERE accountId = :from OR (:from IS NULL AND accountId IS NULL)")
    suspend fun reassignAccount(from: Long?, to: Long)

    @Query("UPDATE transactions SET transferToAccountId = :to WHERE transferToAccountId = :from")
    suspend fun reassignTransferAccount(from: Long, to: Long)

    @Query(
        "DELETE FROM transactions WHERE transferToAccountId IS NOT NULL AND " +
            "((accountId = :from AND transferToAccountId = :to) OR " +
            "(accountId = :to AND transferToAccountId = :from) OR " +
            "(accountId = :from AND transferToAccountId = :from))"
    )
    suspend fun deleteCollapsedTransfers(from: Long, to: Long)

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)
}

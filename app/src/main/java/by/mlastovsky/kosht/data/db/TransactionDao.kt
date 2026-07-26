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

/** Expenses of one category on one local day, as `yyyy-MM-dd`. */
data class DailyCategorySpend(
    val day: String,
    val categoryId: Long,
    val total: Long
)

/** Both sides of one local month, as `yyyy-MM`. */
data class MonthlyTotals(
    val month: String,
    val income: Long,
    val expense: Long
)

/**
 * Every question about spending and earning ignores transfers between the
 * user's own accounts — moving money is neither — so the aggregates below all
 * carry `transferToAccountId IS NULL`. The balances are the exception: that is
 * the one place a transfer has to be felt.
 */
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
        "SELECT COALESCE(SUM(amountMinor), 0) FROM transactions " +
            "WHERE type = :type AND transferToAccountId IS NULL " +
            "AND timestamp >= :from AND timestamp < :to"
    )
    fun observeTotal(type: TransactionType, from: Long, to: Long): Flow<Long>

    /**
     * A transfer leaves the money in the user's hands, so only what it cost
     * comes off the total.
     */
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

    /** How many different expense categories have ever been used. */
    @Query(
        "SELECT COUNT(DISTINCT categoryId) FROM transactions " +
            "WHERE type = 'EXPENSE' AND transferToAccountId IS NULL"
    )
    fun observeExpenseCategoryCount(): Flow<Int>

    /** Records written between midnight and five in the morning, local time. */
    @Query(
        "SELECT COUNT(*) FROM transactions WHERE transferToAccountId IS NULL AND CAST(" +
            "strftime('%H', createdAt / 1000, 'unixepoch', 'localtime') AS INTEGER) < 5"
    )
    fun observeNightCount(): Flow<Int>

    @Query("SELECT MIN(timestamp) FROM transactions WHERE transferToAccountId IS NULL")
    fun observeFirstTimestamp(): Flow<Long?>

    /**
     * Expenses summed per local day and category. Small enough to keep in
     * memory for years of history, and enough to answer every question the
     * streak, the challenges and the awards ask — without holding on to every
     * single record.
     */
    @Query(
        "SELECT strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch', 'localtime') AS day, " +
            "categoryId, COALESCE(SUM(amountMinor), 0) AS total FROM transactions " +
            "WHERE type = 'EXPENSE' AND transferToAccountId IS NULL GROUP BY day, categoryId"
    )
    fun observeDailyCategorySpend(): Flow<List<DailyCategorySpend>>

    /** Income and expenses per local month, oldest first. */
    @Query(
        "SELECT strftime('%Y-%m', timestamp / 1000, 'unixepoch', 'localtime') AS month, " +
            "COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amountMinor ELSE 0 END), 0) AS income, " +
            "COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amountMinor ELSE 0 END), 0) AS expense " +
            "FROM transactions WHERE transferToAccountId IS NULL " +
            "GROUP BY month ORDER BY month ASC"
    )
    fun observeMonthlyTotals(): Flow<List<MonthlyTotals>>

    /** Creation moments used for the logging-streak computation. */
    @Query(
        "SELECT createdAt FROM transactions " +
            "WHERE createdAt >= :from AND transferToAccountId IS NULL"
    )
    fun observeCreatedSince(from: Long): Flow<List<Long>>

    @Query("UPDATE transactions SET categoryId = :to WHERE categoryId = :from")
    suspend fun reassignCategory(from: Long, to: Long)

    // ---- Receipt photos in the cloud, when the user has asked for it -------

    /** Photos taken here that the cloud has not been given yet. */
    @Query("SELECT * FROM transactions WHERE photoPath IS NOT NULL AND photoKey IS NULL")
    suspend fun photosToUpload(): List<TransactionEntity>

    /** Photos another device uploaded and this one does not have a copy of. */
    @Query("SELECT * FROM transactions WHERE photoKey IS NOT NULL AND photoPath IS NULL")
    suspend fun photosToDownload(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE photoKey IS NOT NULL")
    suspend fun photosUploaded(): List<TransactionEntity>

    @Query("UPDATE transactions SET photoKey = :key WHERE id = :id")
    suspend fun setPhotoKey(id: Long, key: String?)

    @Query("UPDATE transactions SET photoPath = :path WHERE id = :id")
    suspend fun setPhotoPath(id: Long, path: String?)

    /** Withdrawing consent: forget every key, the objects go with them. */
    @Query("UPDATE transactions SET photoKey = NULL WHERE photoKey IS NOT NULL")
    suspend fun clearPhotoKeys()

    /** Rescales every amount when the app currency changes, fees included. */
    @Query(
        "UPDATE transactions SET amountMinor = CAST(ROUND(amountMinor * :factor) AS INTEGER), " +
            "transferFeeMinor = CAST(ROUND(transferFeeMinor * :factor) AS INTEGER)"
    )
    suspend fun rescaleAmounts(factor: Double)

    @Query("UPDATE transactions SET accountId = :to WHERE accountId = :from OR (:from IS NULL AND accountId IS NULL)")
    suspend fun reassignAccount(from: Long?, to: Long)

    /** The other end of a transfer follows the account it pointed at. */
    @Query("UPDATE transactions SET transferToAccountId = :to WHERE transferToAccountId = :from")
    suspend fun reassignTransferAccount(from: Long, to: Long)

    /**
     * A transfer between an account being deleted and the account its records
     * move to would end up pointing at itself, which is no movement at all.
     */
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

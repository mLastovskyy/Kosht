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
            "WHERE type = :type AND timestamp >= :from AND timestamp < :to"
    )
    fun observeTotal(type: TransactionType, from: Long, to: Long): Flow<Long>

    @Query(
        "SELECT COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amountMinor ELSE -amountMinor END), 0) " +
            "FROM transactions"
    )
    fun observeBalance(): Flow<Long>

    @Query(
        "SELECT categoryId, COALESCE(SUM(amountMinor), 0) AS total FROM transactions " +
            "WHERE type = :type AND timestamp >= :from AND timestamp < :to " +
            "GROUP BY categoryId ORDER BY total DESC"
    )
    fun observeCategoryTotals(type: TransactionType, from: Long, to: Long): Flow<List<CategoryTotal>>

    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId = :categoryId")
    suspend fun countByCategory(categoryId: Long): Int

    @Query("UPDATE transactions SET categoryId = :to WHERE categoryId = :from")
    suspend fun reassignCategory(from: Long, to: Long)

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)
}

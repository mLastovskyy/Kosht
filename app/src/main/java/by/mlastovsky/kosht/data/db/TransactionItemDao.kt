package by.mlastovsky.kosht.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import by.mlastovsky.kosht.model.TransactionType
import kotlinx.coroutines.flow.Flow

data class ItemInContext(
    val name: String,
    val amountMinor: Long,
    val quantity: Double?,
    val accountId: Long?,

    val categoryId: Long,

    val type: TransactionType,
    val timestamp: Long
)

@Dao
interface TransactionItemDao {

    @Query(
        "SELECT * FROM transaction_items WHERE transactionId = :transactionId " +
            "ORDER BY position ASC, id ASC"
    )
    fun observeFor(transactionId: Long): Flow<List<TransactionItemEntity>>

    @Query(
        "SELECT * FROM transaction_items WHERE transactionId = :transactionId " +
            "ORDER BY position ASC, id ASC"
    )
    suspend fun itemsFor(transactionId: Long): List<TransactionItemEntity>

    @Query(
        "SELECT i.name AS name, i.amountMinor AS amountMinor, i.quantity AS quantity, " +
            "t.accountId AS accountId, t.categoryId AS categoryId, t.type AS type, " +
            "t.timestamp AS timestamp FROM transaction_items i " +
            "JOIN transactions t ON t.id = i.transactionId " +
            "WHERE t.timestamp >= :from AND t.timestamp < :to " +
            "AND t.transferToAccountId IS NULL " +
            "ORDER BY t.timestamp DESC, i.position ASC"
    )
    fun observeBetween(from: Long, to: Long): Flow<List<ItemInContext>>

    @Query(
        "SELECT i.name FROM transaction_items i " +
            "JOIN transactions t ON t.id = i.transactionId " +
            "WHERE t.categoryId = :categoryId " +
            "GROUP BY i.name ORDER BY COUNT(*) DESC, i.name ASC"
    )
    fun observeNamesIn(categoryId: Long): Flow<List<String>>

    @Insert
    suspend fun insertAll(items: List<TransactionItemEntity>)

    @Query("DELETE FROM transaction_items WHERE transactionId = :transactionId")
    suspend fun deleteFor(transactionId: Long)

    @Query(
        "UPDATE transaction_items " +
            "SET amountMinor = CAST(ROUND(amountMinor * :factor) AS INTEGER)"
    )
    suspend fun rescaleAmounts(factor: Double)
}

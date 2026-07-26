package by.mlastovsky.kosht.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * A product line together with the little of its record that the statistics
 * need: which account it was paid from, so the account filter still means
 * something, and when, so it lands in the right period.
 */
data class ItemInContext(
    val name: String,
    val amountMinor: Long,
    val quantity: Double?,
    val accountId: Long?,
    /** The category of the record it belongs to; products are grouped under it. */
    val categoryId: Long,
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

    /**
     * Everything bought in a period. Transfers are skipped like everywhere
     * else, and so is income: a salary has no products in it.
     */
    @Query(
        "SELECT i.name AS name, i.amountMinor AS amountMinor, i.quantity AS quantity, " +
            "t.accountId AS accountId, t.categoryId AS categoryId, " +
            "t.timestamp AS timestamp FROM transaction_items i " +
            "JOIN transactions t ON t.id = i.transactionId " +
            "WHERE t.timestamp >= :from AND t.timestamp < :to " +
            "AND t.transferToAccountId IS NULL AND t.type = 'EXPENSE' " +
            "ORDER BY t.timestamp DESC, i.position ASC"
    )
    fun observeBetween(from: Long, to: Long): Flow<List<ItemInContext>>

    /**
     * Names already used in a category, most used first — the suggestions that
     * actually fit what is being written down. Rent belongs under housing, not
     * under groceries.
     */
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

    /** Rescales the line prices when the app currency changes. */
    @Query(
        "UPDATE transaction_items " +
            "SET amountMinor = CAST(ROUND(amountMinor * :factor) AS INTEGER)"
    )
    suspend fun rescaleAmounts(factor: Double)
}

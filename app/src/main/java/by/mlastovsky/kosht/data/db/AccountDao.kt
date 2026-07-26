package by.mlastovsky.kosht.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class AccountBalance(
    val accountId: Long?,
    val balance: Long
)

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts ORDER BY position ASC, id ASC")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT COALESCE(MAX(position), 0) FROM accounts")
    suspend fun maxPosition(): Int

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int

    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE accounts SET adjustmentMinor = :adjustment WHERE id = :id")
    suspend fun setAdjustment(id: Long, adjustment: Long)

    @Query("UPDATE accounts SET adjustmentMinor = CAST(ROUND(adjustmentMinor * :factor) AS INTEGER)")
    suspend fun rescaleAdjustments(factor: Double)

    /**
     * Per-account balance; transactions without an account fall in the null
     * group. A transfer counts twice on purpose: the amount and what it cost
     * leave the source account, and the amount arrives at the destination.
     */
    @Query(
        "SELECT accountId, COALESCE(SUM(delta), 0) AS balance FROM (" +
            "SELECT accountId AS accountId, CASE " +
            "WHEN transferToAccountId IS NOT NULL THEN -(amountMinor + transferFeeMinor) " +
            "WHEN type = 'INCOME' THEN amountMinor ELSE -amountMinor END AS delta " +
            "FROM transactions " +
            "UNION ALL " +
            "SELECT transferToAccountId AS accountId, amountMinor AS delta FROM transactions " +
            "WHERE transferToAccountId IS NOT NULL" +
            ") GROUP BY accountId"
    )
    fun observeBalances(): Flow<List<AccountBalance>>
}

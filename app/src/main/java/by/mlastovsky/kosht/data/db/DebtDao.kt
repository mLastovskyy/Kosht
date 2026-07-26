package by.mlastovsky.kosht.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DebtDao {

    @Query("SELECT * FROM debts WHERE closedAt IS NULL ORDER BY createdAt DESC")
    fun observeActive(): Flow<List<DebtEntity>>

    @Query("SELECT * FROM debts WHERE id = :id")
    suspend fun getById(id: Long): DebtEntity?

    @Insert
    suspend fun insert(debt: DebtEntity): Long

    @Update
    suspend fun update(debt: DebtEntity)

    @Query("DELETE FROM debts WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Debts settled in full — the ones that earned an award. */
    @Query("SELECT COUNT(*) FROM debts WHERE closedAt IS NOT NULL")
    fun observeClosedCount(): Flow<Int>

    @Query("SELECT DISTINCT currencyCode FROM debts")
    suspend fun currencies(): List<String>

    /** Restates what is owed in [from] as [to]; see CurrencyChanger. */
    @Query(
        "UPDATE debts SET amountMinor = CAST(ROUND(amountMinor * :factor) AS INTEGER), " +
            "currencyCode = :to WHERE currencyCode = :from"
    )
    suspend fun convert(from: String, to: String, factor: Double)
}

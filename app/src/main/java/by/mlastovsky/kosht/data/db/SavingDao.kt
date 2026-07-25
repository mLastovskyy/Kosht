package by.mlastovsky.kosht.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class SavingTotal(
    val currencyCode: String,
    val total: Long
)

@Dao
interface SavingDao {

    @Query("SELECT * FROM savings ORDER BY timestamp DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SavingEntity>>

    @Query(
        "SELECT currencyCode, COALESCE(SUM(amountMinor), 0) AS total " +
            "FROM savings GROUP BY currencyCode HAVING total != 0"
    )
    fun observeTotals(): Flow<List<SavingTotal>>

    @Insert
    suspend fun insert(saving: SavingEntity): Long

    @Query("DELETE FROM savings WHERE id = :id")
    suspend fun deleteById(id: Long)
}

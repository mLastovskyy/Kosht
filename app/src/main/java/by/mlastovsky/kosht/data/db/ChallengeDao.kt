package by.mlastovsky.kosht.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChallengeDao {

    @Query("SELECT * FROM challenges ORDER BY endEpochDay DESC, id DESC")
    fun observeAll(): Flow<List<ChallengeEntity>>

    @Insert
    suspend fun insert(challenge: ChallengeEntity): Long

    @Query("DELETE FROM challenges WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Rescales limits/targets when the app currency changes. */
    @Query("UPDATE challenges SET amountMinor = CAST(ROUND(amountMinor * :factor) AS INTEGER)")
    suspend fun rescaleAmounts(factor: Double)
}

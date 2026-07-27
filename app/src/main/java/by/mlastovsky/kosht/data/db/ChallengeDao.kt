package by.mlastovsky.kosht.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChallengeDao {

    @Query("SELECT * FROM challenges ORDER BY endEpochDay DESC, id DESC")
    fun observeAll(): Flow<List<ChallengeEntity>>

    @Insert
    suspend fun insert(challenge: ChallengeEntity): Long

    @Update
    suspend fun update(challenge: ChallengeEntity)

    @Query("DELETE FROM challenges WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        "UPDATE challenges SET amountMinor = CAST(ROUND(amountMinor * :factor) AS INTEGER) " +
            "WHERE currencyCode IS NULL"
    )
    suspend fun rescaleAmounts(factor: Double)

    @Query("SELECT DISTINCT currencyCode FROM challenges WHERE currencyCode IS NOT NULL")
    suspend fun currencies(): List<String>

    @Query(
        "UPDATE challenges SET amountMinor = CAST(ROUND(amountMinor * :factor) AS INTEGER), " +
            "currencyCode = :to WHERE currencyCode = :from"
    )
    suspend fun convert(from: String, to: String, factor: Double)
}

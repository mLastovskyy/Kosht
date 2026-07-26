package by.mlastovsky.kosht.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class GoalProgress(
    val goalId: Long,
    val total: Long
)

@Dao
interface GoalDao {

    @Query("SELECT * FROM saving_goals ORDER BY achievedAt IS NOT NULL, createdAt DESC")
    fun observeAll(): Flow<List<SavingGoalEntity>>

    @Query(
        "SELECT goalId, COALESCE(SUM(amountMinor), 0) AS total FROM savings " +
            "WHERE goalId IS NOT NULL GROUP BY goalId"
    )
    fun observeProgress(): Flow<List<GoalProgress>>

    @Insert
    suspend fun insert(goal: SavingGoalEntity): Long

    @Update
    suspend fun update(goal: SavingGoalEntity)

    @Query("SELECT * FROM saving_goals WHERE id = :id")
    suspend fun getById(id: Long): SavingGoalEntity?

    @Query("UPDATE savings SET goalId = NULL WHERE goalId = :goalId")
    suspend fun unlinkSavings(goalId: Long)

    @Query("DELETE FROM saving_goals WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT DISTINCT currencyCode FROM saving_goals")
    suspend fun currencies(): List<String>

    @Query(
        "UPDATE saving_goals SET targetMinor = CAST(ROUND(targetMinor * :factor) AS INTEGER), " +
            "currencyCode = :to WHERE currencyCode = :from"
    )
    suspend fun convert(from: String, to: String, factor: Double)
}

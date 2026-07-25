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
}

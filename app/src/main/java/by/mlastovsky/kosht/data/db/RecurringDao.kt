package by.mlastovsky.kosht.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringDao {

    @Transaction
    @Query("SELECT * FROM recurring ORDER BY nextDueEpochDay ASC, id ASC")
    fun observeAll(): Flow<List<RecurringWithCategory>>

    @Query("SELECT * FROM recurring WHERE id = :id")
    suspend fun getById(id: Long): RecurringEntity?

    @Insert
    suspend fun insert(recurring: RecurringEntity): Long

    @Update
    suspend fun update(recurring: RecurringEntity)

    @Query("DELETE FROM recurring WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE recurring SET categoryId = :to WHERE categoryId = :from")
    suspend fun reassignCategory(from: Long, to: Long)
}

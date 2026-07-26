package by.mlastovsky.kosht.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import by.mlastovsky.kosht.model.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY position ASC, id ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE type = :type ORDER BY position ASC, id ASC")
    fun observeByType(type: TransactionType): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE key = :key LIMIT 1")
    suspend fun getByKey(key: String): CategoryEntity?

    @Query("SELECT COALESCE(MAX(position), 0) FROM categories WHERE type = :type")
    suspend fun maxPosition(type: TransactionType): Int

    @Query("SELECT COALESCE(MIN(position), 0) FROM categories WHERE id IN (:ids)")
    suspend fun minPosition(ids: List<Long>): Int

    @Query("UPDATE categories SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: Long, position: Int)

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: Long)
}

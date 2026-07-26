package by.mlastovsky.kosht.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AwardDao {

    @Query("SELECT * FROM awards")
    fun observeAll(): Flow<List<AwardEntity>>

    /** Earned awards are never re-earned: existing rows keep their date. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(awards: List<AwardEntity>)
}

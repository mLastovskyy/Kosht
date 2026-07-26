package by.mlastovsky.kosht.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "saving_goals", indices = [Index("uid")])
data class SavingGoalEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val targetMinor: Long,
    val currencyCode: String,
    val createdAt: Long,
    val achievedAt: Long? = null,
    @Embedded
    val sync: SyncMeta = SyncMeta()
)

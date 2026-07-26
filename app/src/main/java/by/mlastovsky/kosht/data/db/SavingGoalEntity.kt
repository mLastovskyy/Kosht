package by.mlastovsky.kosht.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A savings goal ("накопить на..."). Deposits linked via SavingEntity.goalId
 * count toward [targetMinor]; the goal locks its own currency.
 */
@Entity(tableName = "saving_goals")
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

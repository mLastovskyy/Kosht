package by.mlastovsky.kosht.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One "set aside" moment. Positive [amountMinor] is a deposit into savings,
 * negative is a withdrawal.
 */
@Entity(tableName = "savings")
data class SavingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amountMinor: Long,
    val currencyCode: String,
    val note: String = "",
    val timestamp: Long,
    /** Optional link to a savings goal. */
    val goalId: Long? = null,
    @Embedded
    val sync: SyncMeta = SyncMeta()
)

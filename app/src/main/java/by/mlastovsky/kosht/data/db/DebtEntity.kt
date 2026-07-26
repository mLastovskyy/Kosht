package by.mlastovsky.kosht.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import by.mlastovsky.kosht.model.DebtDirection

/**
 * A debt being tracked. [amountMinor] is the remaining amount; the debt is
 * closed when [closedAt] is set.
 */
@Entity(tableName = "debts")
data class DebtEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val personName: String,
    val direction: DebtDirection,
    val amountMinor: Long,
    val currencyCode: String,
    val note: String = "",
    val createdAt: Long,
    val closedAt: Long? = null,
    @Embedded
    val sync: SyncMeta = SyncMeta()
)

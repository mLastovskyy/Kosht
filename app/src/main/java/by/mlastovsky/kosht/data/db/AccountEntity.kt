package by.mlastovsky.kosht.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A money source: a card, cash, etc. Built-in accounts carry a stable [key]
 * resolved to a localized name; user-created ones store [name] directly.
 */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val key: String?,
    val name: String,
    val iconKey: String,
    val colorArgb: Long,
    val position: Int,
    /**
     * Manual balance correction: shown balance = transactions + adjustment.
     * Lets the user set the real current balance without fake records.
     */
    @ColumnInfo(defaultValue = "0")
    val adjustmentMinor: Long = 0,
    @Embedded
    val sync: SyncMeta = SyncMeta()
)

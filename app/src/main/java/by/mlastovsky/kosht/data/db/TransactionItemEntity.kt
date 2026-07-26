package by.mlastovsky.kosht.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One line of what a record was actually spent on — a product, a service, a
 * ticket. Optional: a record without any is just as complete, which is why
 * nothing in the app requires one.
 *
 * The lines are what the product statistics group by, so [name] is stored in a
 * settled form (see TransactionRepository.normalizeItemName) — otherwise
 * "МОЛОКО" and "молоко" would be two different products for the rest of time.
 */
@Entity(
    tableName = "transaction_items",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("transactionId"), Index("name"), Index("uid")]
)
data class TransactionItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val transactionId: Long,
    val name: String,
    /** What this line cost; zero when only the name is known. */
    val amountMinor: Long = 0,
    /** How many were bought — 2 pieces, 0.756 kg. Null when not said. */
    val quantity: Double? = null,
    /** Keeps the order they were entered or read off the receipt in. */
    val position: Int = 0,
    @Embedded
    val sync: SyncMeta = SyncMeta()
)

package by.mlastovsky.kosht.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import by.mlastovsky.kosht.model.TransactionType

/**
 * A single money movement. [amountMinor] is stored in minor currency units
 * (e.g. kopecks/cents) to avoid floating point rounding issues.
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("categoryId"),
        Index("timestamp")
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amountMinor: Long,
    val type: TransactionType,
    val categoryId: Long,
    val note: String = "",
    /** Moment the transaction happened, epoch millis. */
    val timestamp: Long,
    /** Moment the record was created, epoch millis. */
    val createdAt: Long,
    /** Attached receipt photo (app-private file path). */
    val photoPath: String? = null,
    /** Money source; null means the primary account. */
    val accountId: Long? = null,
    /**
     * BYN equivalent frozen at the moment the record was saved, so historical
     * values do not drift when exchange rates change.
     */
    val bynMinor: Long? = null,
    /** Electronic receipt a scanned QR led to; travels between devices. */
    val receiptUrl: String? = null,
    /** Offline copy of that receipt. Like the photo, it stays on this device. */
    val receiptDocPath: String? = null,
    @Embedded
    val sync: SyncMeta = SyncMeta()
)

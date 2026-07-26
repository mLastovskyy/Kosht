package by.mlastovsky.kosht.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import by.mlastovsky.kosht.model.TransactionType

/**
 * A single money movement. [amountMinor] is stored in minor currency units
 * (e.g. kopecks/cents) to avoid floating point rounding issues.
 *
 * A transfer between two of the user's own accounts is one row as well, marked
 * by [transferToAccountId]: it moves money from [accountId] to that account
 * without being income or expense anywhere, which is why the statistics leave
 * such rows out and only the balances take them into account.
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
    /**
     * Object name the photo was uploaded under, set only once the user has
     * switched photo sync on. Null means the image has never left the phone —
     * which is every record until somebody asks otherwise.
     */
    val photoKey: String? = null,
    /**
     * The figures were read off a receipt rather than typed. Kept so the app
     * can say where a record came from long after the scan.
     */
    @ColumnInfo(defaultValue = "0")
    val scanned: Boolean = false,
    /**
     * Destination account of a transfer; null for an ordinary record. The
     * money leaves [accountId] and arrives here, so neither side is spending.
     */
    val transferToAccountId: Long? = null,
    /**
     * What the transfer itself cost, charged to the source account on top of
     * [amountMinor]. Zero when the transfer was free.
     */
    @ColumnInfo(defaultValue = "0")
    val transferFeeMinor: Long = 0,
    @Embedded
    val sync: SyncMeta = SyncMeta()
) {
    /** Money moved between the user's own accounts rather than spent or earned. */
    val isTransfer: Boolean get() = transferToAccountId != null

    /** Everything the transfer takes off the source account, fee included. */
    val transferTotalMinor: Long get() = amountMinor + transferFeeMinor
}

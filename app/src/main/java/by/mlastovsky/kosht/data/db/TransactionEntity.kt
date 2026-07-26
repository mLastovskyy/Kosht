package by.mlastovsky.kosht.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import by.mlastovsky.kosht.model.TransactionType

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
        Index("timestamp"),
        Index("uid")
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amountMinor: Long,
    val type: TransactionType,
    val categoryId: Long,
    val note: String = "",

    val timestamp: Long,

    val createdAt: Long,

    val photoPath: String? = null,

    val accountId: Long? = null,

    val bynMinor: Long? = null,

    val receiptUrl: String? = null,

    val receiptDocPath: String? = null,

    val photoKey: String? = null,

    @ColumnInfo(defaultValue = "0")
    val scanned: Boolean = false,

    val transferToAccountId: Long? = null,

    @ColumnInfo(defaultValue = "0")
    val transferFeeMinor: Long = 0,
    @Embedded
    val sync: SyncMeta = SyncMeta()
) {

    val isTransfer: Boolean get() = transferToAccountId != null

    val transferTotalMinor: Long get() = amountMinor + transferFeeMinor
}

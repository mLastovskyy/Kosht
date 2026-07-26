package by.mlastovsky.kosht.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import by.mlastovsky.kosht.model.RecurringFrequency
import by.mlastovsky.kosht.model.TransactionType
import java.time.LocalDate

@Entity(
    tableName = "recurring",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("categoryId"), Index("uid")]
)
data class RecurringEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val amountMinor: Long,

    val currencyCode: String = "BYN",
    val categoryId: Long,

    val nextDueEpochDay: Long,
    val frequency: RecurringFrequency = RecurringFrequency.MONTHLY,
    val enabled: Boolean = true,
    val createdAt: Long,

    @ColumnInfo(defaultValue = "EXPENSE")
    val type: TransactionType = TransactionType.EXPENSE,

    val accountId: Long? = null,
    @Embedded
    val sync: SyncMeta = SyncMeta()
) {
    val nextDueDate: LocalDate
        get() = LocalDate.ofEpochDay(nextDueEpochDay)

    fun isDue(today: LocalDate = LocalDate.now()): Boolean =
        enabled && today.toEpochDay() >= nextDueEpochDay

    fun advanced(): RecurringEntity =
        copy(nextDueEpochDay = frequency.next(nextDueDate).toEpochDay())
}

data class RecurringWithCategory(
    @Embedded
    val recurring: RecurringEntity,
    @Relation(parentColumn = "categoryId", entityColumn = "id")
    val category: CategoryEntity
)

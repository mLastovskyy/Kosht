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

/**
 * A planned payment with a chosen due date and frequency. It never creates
 * transactions silently: when due, the user must confirm it, which records a
 * movement of [type] and advances [nextDueEpochDay] by one period.
 */
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
    /** Currency the charge is defined in; may differ from the app currency. */
    val currencyCode: String = "BYN",
    val categoryId: Long,
    /** Next charge date as epoch day; picked in a calendar. */
    val nextDueEpochDay: Long,
    val frequency: RecurringFrequency = RecurringFrequency.MONTHLY,
    val enabled: Boolean = true,
    val createdAt: Long,
    /**
     * Whether confirming this writes an expense or an income — a salary is as
     * regular as a subscription.
     */
    @ColumnInfo(defaultValue = "EXPENSE")
    val type: TransactionType = TransactionType.EXPENSE,
    /**
     * Which account the confirmed amount goes off (or onto); null means the
     * user has not picked one and the confirmation asks.
     */
    val accountId: Long? = null,
    @Embedded
    val sync: SyncMeta = SyncMeta()
) {
    val nextDueDate: LocalDate
        get() = LocalDate.ofEpochDay(nextDueEpochDay)

    fun isDue(today: LocalDate = LocalDate.now()): Boolean =
        enabled && today.toEpochDay() >= nextDueEpochDay

    /** The entity after one confirmed charge. */
    fun advanced(): RecurringEntity =
        copy(nextDueEpochDay = frequency.next(nextDueDate).toEpochDay())
}

data class RecurringWithCategory(
    @Embedded
    val recurring: RecurringEntity,
    @Relation(parentColumn = "categoryId", entityColumn = "id")
    val category: CategoryEntity
)

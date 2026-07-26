package by.mlastovsky.kosht.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import by.mlastovsky.kosht.model.RecurringFrequency
import java.time.LocalDate

/**
 * A recurring charge with a chosen due date and frequency. It never creates
 * transactions silently: when due, the user must confirm it, which records
 * an expense and advances [nextDueEpochDay] by one period.
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
    indices = [Index("categoryId")]
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

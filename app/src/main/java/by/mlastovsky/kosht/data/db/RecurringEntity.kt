package by.mlastovsky.kosht.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.time.LocalDate
import java.time.YearMonth

/**
 * A monthly recurring charge. It never creates transactions silently:
 * when due, the user must confirm it, which records an expense and stamps
 * [lastConfirmed] with the current period ("2026-07").
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
    val categoryId: Long,
    val dayOfMonth: Int,
    val lastConfirmed: String? = null,
    val enabled: Boolean = true,
    val createdAt: Long
) {
    fun isDue(today: LocalDate = LocalDate.now()): Boolean {
        if (!enabled) return false
        val currentPeriod = YearMonth.from(today)
        val lastPeriod = lastConfirmed?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
        if (lastPeriod != null && lastPeriod >= currentPeriod) return false
        // Unconfirmed periods older than the previous month are overdue immediately.
        if (lastPeriod != null && lastPeriod < currentPeriod.minusMonths(1)) return true
        val effectiveDay = dayOfMonth.coerceAtMost(currentPeriod.lengthOfMonth())
        return today.dayOfMonth >= effectiveDay
    }
}

data class RecurringWithCategory(
    @Embedded
    val recurring: RecurringEntity,
    @Relation(parentColumn = "categoryId", entityColumn = "id")
    val category: CategoryEntity
)

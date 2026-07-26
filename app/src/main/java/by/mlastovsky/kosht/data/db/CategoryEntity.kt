package by.mlastovsky.kosht.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import by.mlastovsky.kosht.model.TransactionType

/**
 * A spending/income category.
 *
 * Built-in categories carry a stable [key] so their display name can be resolved
 * from localized string resources; user-created categories have [key] = null and
 * store the display name directly in [name].
 */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val key: String?,
    val name: String,
    val iconKey: String,
    val colorArgb: Long,
    val type: TransactionType,
    val position: Int,
    @Embedded
    val sync: SyncMeta = SyncMeta()
)

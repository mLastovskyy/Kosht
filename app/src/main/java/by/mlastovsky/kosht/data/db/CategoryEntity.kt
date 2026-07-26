package by.mlastovsky.kosht.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import by.mlastovsky.kosht.model.TransactionType

@Entity(tableName = "categories", indices = [Index("uid")])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val key: String?,
    val name: String,
    val iconKey: String,
    val colorArgb: Long,
    val type: TransactionType,
    val position: Int,

    val iconPath: String? = null,
    @Embedded
    val sync: SyncMeta = SyncMeta()
)

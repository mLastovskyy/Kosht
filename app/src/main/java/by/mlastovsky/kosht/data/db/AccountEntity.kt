package by.mlastovsky.kosht.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "accounts", indices = [Index("uid")])
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val key: String?,
    val name: String,
    val iconKey: String,
    val colorArgb: Long,
    val position: Int,
    val iconPath: String? = null,

    @ColumnInfo(defaultValue = "0")
    val adjustmentMinor: Long = 0,
    @Embedded
    val sync: SyncMeta = SyncMeta()
)

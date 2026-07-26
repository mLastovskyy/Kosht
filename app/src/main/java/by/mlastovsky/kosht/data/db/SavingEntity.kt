package by.mlastovsky.kosht.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "savings", indices = [Index("uid")])
data class SavingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amountMinor: Long,
    val currencyCode: String,
    val note: String = "",
    val timestamp: Long,

    val goalId: Long? = null,
    @Embedded
    val sync: SyncMeta = SyncMeta()
)

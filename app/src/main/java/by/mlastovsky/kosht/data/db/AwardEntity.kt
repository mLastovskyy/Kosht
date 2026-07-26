package by.mlastovsky.kosht.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "awards", indices = [Index("uid")])
data class AwardEntity(
    @PrimaryKey
    val key: String,
    val unlockedAt: Long,

    @Embedded
    val sync: SyncMeta = SyncMeta()
)

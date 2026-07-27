package by.mlastovsky.kosht.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import by.mlastovsky.kosht.model.ChallengeType

@Entity(tableName = "challenges", indices = [Index("uid")])
data class ChallengeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: ChallengeType,
    val title: String,

    val amountMinor: Long,

    val currencyCode: String? = null,

    val categoryId: Long? = null,

    val goalId: Long? = null,

    val startEpochDay: Long,
    val endEpochDay: Long,
    val createdAt: Long,
    @Embedded
    val sync: SyncMeta = SyncMeta()
)

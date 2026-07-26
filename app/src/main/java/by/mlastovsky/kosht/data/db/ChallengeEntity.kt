package by.mlastovsky.kosht.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import by.mlastovsky.kosht.model.ChallengeType

/**
 * A user-configured challenge. Progress and outcome are always computed from
 * live data, never stored.
 */
@Entity(tableName = "challenges")
data class ChallengeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: ChallengeType,
    val title: String,
    /** Limit or target in minor units of the app currency; unused for NO_SPEND. */
    val amountMinor: Long,
    /** Category scope for SPEND_LIMIT; null = all expenses. */
    val categoryId: Long? = null,
    /** Inclusive period bounds as epoch days. */
    val startEpochDay: Long,
    val endEpochDay: Long,
    val createdAt: Long,
    @Embedded
    val sync: SyncMeta = SyncMeta()
)

package by.mlastovsky.kosht.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A permanently earned award. Conditions are computed live, but the fact
 * of earning is stored so an award never "un-unlocks" (e.g. when a streak
 * resets) and the earn date can be shown.
 */
@Entity(tableName = "awards")
data class AwardEntity(
    @PrimaryKey
    val key: String,
    val unlockedAt: Long
)

package by.mlastovsky.kosht.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rates")
data class RateEntity(
    @PrimaryKey val code: String,
    val scale: Int,
    val rate: Double,
    val updatedAt: Long
)

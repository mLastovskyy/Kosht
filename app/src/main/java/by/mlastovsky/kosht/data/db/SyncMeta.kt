package by.mlastovsky.kosht.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

data class SyncMeta(

    @ColumnInfo(defaultValue = "''")
    val uid: String = "",

    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = 0
)

@Entity(tableName = "sync_tombstones", primaryKeys = ["entity", "uid"])
data class SyncTombstoneEntity(
    val entity: String,
    val uid: String,
    val deletedAt: Long
)

enum class SyncEntity(val table: String, val local: Boolean = true) {

    ACCOUNTS("accounts"),
    CATEGORIES("categories"),
    SAVING_GOALS("saving_goals"),
    TRANSACTIONS("transactions"),
    TRANSACTION_ITEMS("transaction_items"),
    RECURRING("recurring"),
    SAVINGS("savings"),
    CHALLENGES("challenges"),
    DEBTS("debts"),
    AWARDS("awards"),

    SETTINGS("settings", local = false);

    companion object {
        fun of(table: String): SyncEntity? = entries.firstOrNull { it.table == table }

        val tables: List<SyncEntity> get() = entries.filter { it.local }
    }
}

@Entity(tableName = "sync_cursor")
data class SyncCursorEntity(
    @PrimaryKey
    val id: Int = 0,

    val pulledThrough: Long = 0,

    val pushedThrough: Long = -1,

    val lastSyncAt: Long = 0
)

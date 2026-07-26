package by.mlastovsky.kosht.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Sync bookkeeping carried by every table that travels between devices.
 *
 * Both columns are maintained by SQLite triggers (see KoshtDatabase), so
 * ordinary inserts and updates anywhere in the app stay sync-correct without
 * remembering to stamp anything. Rows written by the sync engine itself
 * arrive with both fields already set, which is exactly what stops the
 * triggers from overwriting a remote timestamp with the local clock.
 */
data class SyncMeta(
    /** Identity shared across devices. Seeded rows use a derived, stable value. */
    @ColumnInfo(defaultValue = "''")
    val uid: String = "",
    /**
     * Epoch millis of the last local edit; 0 means "never touched since it
     * was seeded", which lets any remote edit win on a freshly installed
     * device instead of resurrecting rows the user deleted elsewhere.
     */
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = 0
)

/**
 * A locally deleted row, remembered until the delete has been pushed. Written
 * by an AFTER DELETE trigger, so it catches every delete path in the app.
 */
@Entity(tableName = "sync_tombstones", primaryKeys = ["entity", "uid"])
data class SyncTombstoneEntity(
    val entity: String,
    val uid: String,
    val deletedAt: Long
)

/**
 * Sync engine's view of one kind of record.
 *
 * All but one are Room tables, which is why [local] exists: preferences live
 * in DataStore, so there is nothing for the uid triggers to be installed on,
 * but they still travel as an ordinary row in the cloud table.
 */
enum class SyncEntity(val table: String, val local: Boolean = true) {
    // Order matters on apply: rows others point at come first.
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

    /** One row per account, holding the app's settings and the profile. */
    SETTINGS("settings", local = false);

    companion object {
        fun of(table: String): SyncEntity? = entries.firstOrNull { it.table == table }

        /** The ones backed by a table, i.e. everything the triggers apply to. */
        val tables: List<SyncEntity> get() = entries.filter { it.local }
    }
}

/** Room needs a concrete holder for the tombstone rows the engine reads. */
@Entity(tableName = "sync_cursor")
data class SyncCursorEntity(
    @PrimaryKey
    val id: Int = 0,
    /** Highest remote updated_at already applied locally. */
    val pulledThrough: Long = 0,
    /** Highest local updatedAt already pushed. -1 pushes everything once. */
    val pushedThrough: Long = -1,
    /** Epoch millis of the last fully successful sync, 0 when never. */
    val lastSyncAt: Long = 0
)

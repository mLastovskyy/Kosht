package by.mlastovsky.kosht.data.sync

import androidx.room.withTransaction
import by.mlastovsky.kosht.data.db.KoshtDatabase
import by.mlastovsky.kosht.data.db.SyncCursorEntity
import by.mlastovsky.kosht.data.db.SyncEntity
import by.mlastovsky.kosht.data.db.SyncMeta
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** How a sync attempt ended, in terms the settings screen can show. */
sealed interface SyncOutcome {
    data class Success(val sent: Int, val received: Int) : SyncOutcome

    data object NotSignedIn : SyncOutcome

    data object Offline : SyncOutcome

    data class Failed(val message: String) : SyncOutcome
}

/**
 * Two-way per-record sync against the cloud `sync_rows` table.
 *
 * Records merge one at a time and the newer `updatedAt` wins, so two devices
 * editing different things offline both keep their work. Deletes travel as
 * tombstones, which is what stops a row deleted on one phone from being
 * resurrected by the other.
 *
 * Push always runs before pull: everything this device already knows is on
 * the server before anything new comes back, which keeps the watermarks
 * honest even when the sync is interrupted halfway.
 */
class SyncEngine(
    private val database: KoshtDatabase,
    private val api: SupabaseApi,
    private val account: SyncAccountRepository,
    private val settings: by.mlastovsky.kosht.data.SettingsRepository,
    private val photos: PhotoSync
) {

    private val dao = database.syncDao()

    // One sync at a time; the app can ask from several places at once.
    private val running = Mutex()

    /** Epoch millis of the last fully successful sync, 0 when never. */
    val lastSyncAt: Flow<Long> = dao.observeCursor().map { it?.lastSyncAt ?: 0 }

    suspend fun sync(): SyncOutcome {
        if (!api.isConfigured) return SyncOutcome.NotSignedIn
        if (!account.state.first().signedIn) return SyncOutcome.NotSignedIn
        return running.withLock {
            val session = account.validAccessToken() ?: return@withLock SyncOutcome.Offline
            try {
                val cursor = dao.cursor() ?: SyncCursorEntity()
                val pushed = push(session, cursor.pushedThrough)
                val pulled = pull(session, cursor.pulledThrough)
                // After the figures, and never at their expense: a photo that
                // does not make it leaves everything else properly synced.
                runCatching { photos.exchange(session) }
                dao.saveCursor(
                    pulled = maxOf(cursor.pulledThrough, pulled.through),
                    // Deliberately not raised to the remote watermark: a local
                    // edit older than someone else's change must still go up.
                    pushed = maxOf(cursor.pushedThrough, pushed.through),
                    lastSyncAt = System.currentTimeMillis()
                )
                SyncOutcome.Success(sent = pushed.count, received = pulled.applied)
            } catch (offline: IOException) {
                SyncOutcome.Offline
            } catch (failure: Exception) {
                SyncOutcome.Failed(failure.message ?: failure.javaClass.simpleName)
            }
        }
    }

    /** Forgets both watermarks so the next sync exchanges everything again. */
    suspend fun resetCursor() = dao.resetCursor()

    /**
     * Deletes every receipt photo this account has in the cloud. Called when
     * photo sync is switched off: a consent withdrawn has to take the files
     * with it, and the originals are on the phone regardless.
     */
    suspend fun purgePhotos(): Boolean {
        val session = account.validAccessToken() ?: return false
        return runCatching { photos.purge(session) }.isSuccess
    }

    // ---- Push -------------------------------------------------------------

    private class Pushed(val through: Long, val count: Int)

    private suspend fun push(session: SupabaseSession, since: Long): Pushed {
        val index = buildIndex()
        // Nulls are rows that cannot travel yet; they are filtered below.
        val rows = mutableListOf<SyncRow?>()

        dao.accountsChanged(since).forEach {
            rows += row(SyncEntity.ACCOUNTS, it.sync, SyncPayloads.of(it))
        }
        dao.categoriesChanged(since).forEach {
            rows += row(SyncEntity.CATEGORIES, it.sync, SyncPayloads.of(it))
        }
        dao.goalsChanged(since).forEach {
            rows += row(SyncEntity.SAVING_GOALS, it.sync, SyncPayloads.of(it))
        }
        dao.transactionsChanged(since).forEach {
            rows += row(SyncEntity.TRANSACTIONS, it.sync, SyncPayloads.of(it, index))
        }
        dao.recurringChanged(since).forEach {
            rows += row(SyncEntity.RECURRING, it.sync, SyncPayloads.of(it, index))
        }
        dao.savingsChanged(since).forEach {
            rows += row(SyncEntity.SAVINGS, it.sync, SyncPayloads.of(it, index))
        }
        dao.challengesChanged(since).forEach {
            rows += row(SyncEntity.CHALLENGES, it.sync, SyncPayloads.of(it, index))
        }
        dao.debtsChanged(since).forEach {
            rows += row(SyncEntity.DEBTS, it.sync, SyncPayloads.of(it))
        }
        dao.awardsChanged(since).forEach {
            rows += row(SyncEntity.AWARDS, it.sync, SyncPayloads.of(it))
        }
        // Preferences are not a table and have no trigger, so they carry their
        // own stamp and there is exactly one row of them per account.
        val prefs = settings.syncSnapshot()
        if (prefs.updatedAt > since) {
            rows += SyncRow(
                entity = SyncEntity.SETTINGS,
                uid = SyncPayloads.SETTINGS_UID,
                updatedAt = prefs.updatedAt,
                deleted = false,
                payload = SyncPayloads.of(prefs)
            )
        }

        val live = rows.filterNotNull()
        val tombstones = dao.tombstones().mapNotNull { stone ->
            SyncEntity.of(stone.entity)?.let {
                SyncRow(it, stone.uid, stone.deletedAt, deleted = true, payload = JSONObject())
            }
        }

        val outgoing = live + tombstones
        if (outgoing.isEmpty()) return Pushed(since, 0)

        outgoing.chunked(PUSH_BATCH).forEach { batch ->
            val array = JSONArray()
            batch.forEach { array.put(it.toRemote(session.userId)) }
            api.push(session.accessToken, array)
        }
        // The tombstone is the last thing that knows a record existed, so the
        // photo it had is deleted while that knowledge is still here.
        runCatching {
            photos.deleteFor(
                session = session,
                uids = tombstones.filter { it.entity == SyncEntity.TRANSACTIONS }.map { it.uid }
            )
        }
        // Only once the server has them is it safe to forget the deletes.
        tombstones.forEach { dao.dropTombstone(it.entity.table, it.uid) }

        return Pushed(
            through = live.maxOfOrNull { it.updatedAt } ?: since,
            count = outgoing.size
        )
    }

    private fun row(entity: SyncEntity, meta: SyncMeta, payload: JSONObject?): SyncRow? {
        // A blank uid means a trigger has not run yet; a null payload means a
        // reference this row needs has not been given an identity either.
        if (meta.uid.isBlank() || payload == null) return null
        return SyncRow(entity, meta.uid, meta.updatedAt, deleted = false, payload = payload)
    }

    private fun SyncRow.toRemote(userId: String): JSONObject = JSONObject()
        .put("user_id", userId)
        .put("entity", entity.table)
        .put("uid", uid)
        .put("updated_at", updatedAt)
        .put("deleted", deleted)
        .put("payload", payload)

    // ---- Pull -------------------------------------------------------------

    private class Pulled(val through: Long, val applied: Int, val skipped: Int)

    private suspend fun pull(session: SupabaseSession, since: Long): Pulled {
        var cursor = since
        var applied = 0
        var skipped = 0
        while (true) {
            val page = api.pull(session.accessToken, cursor, PULL_PAGE)
            val rows = page.toSyncRows()
            if (rows.isEmpty()) break
            // Preferences live outside the database, so they are applied
            // outside its transaction.
            val (prefs, records) = rows.partition { it.entity == SyncEntity.SETTINGS }
            applySettings(prefs)
            val result = apply(records)
            applied += result.applied
            skipped += result.skipped
            val newest = rows.maxOf { it.updatedAt }
            // A page that does not move the watermark would loop forever.
            if (newest <= cursor) break
            cursor = newest
            if (page.length() < PULL_PAGE) break
        }
        return Pulled(cursor, applied, skipped)
    }

    private fun JSONArray.toSyncRows(): List<SyncRow> = (0 until length()).mapNotNull { i ->
        val json = optJSONObject(i) ?: return@mapNotNull null
        val entity = SyncEntity.of(json.optString("entity")) ?: return@mapNotNull null
        SyncRow(
            entity = entity,
            uid = json.optString("uid"),
            updatedAt = json.optLong("updated_at"),
            deleted = json.optBoolean("deleted"),
            payload = json.optJSONObject("payload") ?: JSONObject()
        )
    }

    /**
     * Settings from another device, applied only when they are newer than what
     * this one last changed. A tombstone for them is meaningless: there is one
     * row per account and it is never deleted, only overwritten.
     */
    private suspend fun applySettings(rows: List<SyncRow>) {
        val newest = rows.filterNot { it.deleted }.maxByOrNull { it.updatedAt } ?: return
        val local = settings.syncSnapshot()
        if (newest.updatedAt <= local.updatedAt) return
        settings.applySynced(SyncPayloads.toSettings(newest.payload, newest.updatedAt, local))
    }

    // ---- Merge ------------------------------------------------------------

    private class Applied(var applied: Int = 0, var skipped: Int = 0) {
        operator fun plusAssign(other: Applied) {
            applied += other.applied
            skipped += other.skipped
        }
    }

    /**
     * Rows that others point at are created first and removed last, so a
     * category never disappears while its transactions still reference it.
     */
    private suspend fun apply(rows: List<SyncRow>): Applied = database.withTransaction {
        val byEntity = rows.groupBy { it.entity }
        fun of(entity: SyncEntity, deleted: Boolean) =
            byEntity[entity].orEmpty().filter { it.deleted == deleted }

        val total = Applied()
        total += applyAccounts(of(SyncEntity.ACCOUNTS, deleted = false))
        total += applyCategories(of(SyncEntity.CATEGORIES, deleted = false))
        total += applyGoals(of(SyncEntity.SAVING_GOALS, deleted = false))

        // Anything just inserted now has a local id worth referencing.
        val index = buildIndex()
        total += applyTransactions(byEntity[SyncEntity.TRANSACTIONS].orEmpty(), index)
        total += applyRecurring(byEntity[SyncEntity.RECURRING].orEmpty(), index)
        total += applySavings(byEntity[SyncEntity.SAVINGS].orEmpty(), index)
        total += applyChallenges(byEntity[SyncEntity.CHALLENGES].orEmpty(), index)
        total += applyDebts(byEntity[SyncEntity.DEBTS].orEmpty())
        total += applyAwards(byEntity[SyncEntity.AWARDS].orEmpty())

        total += applyGoals(of(SyncEntity.SAVING_GOALS, deleted = true))
        total += applyCategories(of(SyncEntity.CATEGORIES, deleted = true))
        total += applyAccounts(of(SyncEntity.ACCOUNTS, deleted = true))
        total
    }

    private suspend fun buildIndex() = UidIndex(
        categories = dao.categoryRefs(),
        accounts = dao.accountRefs(),
        goals = dao.goalRefs()
    )

    /** True when the remote copy is the newer one and should be applied. */
    private fun wins(remote: SyncRow, localUpdatedAt: Long?): Boolean =
        localUpdatedAt == null || remote.updatedAt > localUpdatedAt

    /**
     * The local row is gone; drop the tombstone the delete trigger just
     * wrote, or this device would push back a delete it merely mirrored.
     */
    private suspend fun deleted(entity: SyncEntity, uid: String) {
        dao.dropTombstone(entity.table, uid)
    }

    private suspend fun applyAccounts(rows: List<SyncRow>): Applied {
        val result = Applied()
        if (rows.isEmpty()) return result
        val locals = rows.uids().flatMap { dao.accountsByUid(it) }.associateBy { it.sync.uid }
        rows.forEach { row ->
            val local = locals[row.uid]
            if (!wins(row, local?.sync?.updatedAt)) return@forEach
            if (row.deleted) {
                if (local != null) {
                    runCatching { dao.deleteAccount(row.uid) }
                        .onSuccess { result.applied++ }
                        .onFailure { result.skipped++ }
                    deleted(row.entity, row.uid)
                }
                return@forEach
            }
            val entity = SyncPayloads.toAccount(row.payload, row.meta(), local)
            if (local == null) dao.insertAccount(entity) else dao.updateAccount(entity)
            result.applied++
        }
        return result
    }

    private suspend fun applyCategories(rows: List<SyncRow>): Applied {
        val result = Applied()
        if (rows.isEmpty()) return result
        val locals = rows.uids().flatMap { dao.categoriesByUid(it) }.associateBy { it.sync.uid }
        rows.forEach { row ->
            val local = locals[row.uid]
            if (!wins(row, local?.sync?.updatedAt)) return@forEach
            if (row.deleted) {
                if (local != null) {
                    // RESTRICT keeps a category alive while records use it;
                    // the records' own tombstones arrive and free it later.
                    runCatching { dao.deleteCategory(row.uid) }
                        .onSuccess { result.applied++ }
                        .onFailure { result.skipped++ }
                    deleted(row.entity, row.uid)
                }
                return@forEach
            }
            val entity = SyncPayloads.toCategory(row.payload, row.meta(), local)
            if (local == null) dao.insertCategory(entity) else dao.updateCategory(entity)
            result.applied++
        }
        return result
    }

    private suspend fun applyGoals(rows: List<SyncRow>): Applied {
        val result = Applied()
        if (rows.isEmpty()) return result
        val locals = rows.uids().flatMap { dao.goalsByUid(it) }.associateBy { it.sync.uid }
        rows.forEach { row ->
            val local = locals[row.uid]
            if (!wins(row, local?.sync?.updatedAt)) return@forEach
            if (row.deleted) {
                if (local != null) {
                    dao.deleteGoal(row.uid)
                    deleted(row.entity, row.uid)
                    result.applied++
                }
                return@forEach
            }
            val entity = SyncPayloads.toGoal(row.payload, row.meta(), local)
            if (local == null) dao.insertGoal(entity) else dao.updateGoal(entity)
            result.applied++
        }
        return result
    }

    private suspend fun applyTransactions(rows: List<SyncRow>, index: UidIndex): Applied {
        val result = Applied()
        if (rows.isEmpty()) return result
        val locals = rows.uids().flatMap { dao.transactionsByUid(it) }.associateBy { it.sync.uid }
        rows.forEach { row ->
            val local = locals[row.uid]
            if (!wins(row, local?.sync?.updatedAt)) return@forEach
            if (row.deleted) {
                if (local != null) {
                    dao.deleteTransaction(row.uid)
                    deleted(row.entity, row.uid)
                    result.applied++
                }
                return@forEach
            }
            val entity = SyncPayloads.toTransaction(row.payload, row.meta(), index, local)
            if (entity == null) {
                // Its category has not arrived yet; the next sync picks it up.
                result.skipped++
                return@forEach
            }
            if (local == null) dao.insertTransaction(entity) else dao.updateTransaction(entity)
            result.applied++
        }
        return result
    }

    private suspend fun applyRecurring(rows: List<SyncRow>, index: UidIndex): Applied {
        val result = Applied()
        if (rows.isEmpty()) return result
        val locals = rows.uids().flatMap { dao.recurringByUid(it) }.associateBy { it.sync.uid }
        rows.forEach { row ->
            val local = locals[row.uid]
            if (!wins(row, local?.sync?.updatedAt)) return@forEach
            if (row.deleted) {
                if (local != null) {
                    dao.deleteRecurring(row.uid)
                    deleted(row.entity, row.uid)
                    result.applied++
                }
                return@forEach
            }
            val entity = SyncPayloads.toRecurring(row.payload, row.meta(), index, local)
            if (entity == null) {
                result.skipped++
                return@forEach
            }
            if (local == null) dao.insertRecurring(entity) else dao.updateRecurring(entity)
            result.applied++
        }
        return result
    }

    private suspend fun applySavings(rows: List<SyncRow>, index: UidIndex): Applied {
        val result = Applied()
        if (rows.isEmpty()) return result
        val locals = rows.uids().flatMap { dao.savingsByUid(it) }.associateBy { it.sync.uid }
        rows.forEach { row ->
            val local = locals[row.uid]
            if (!wins(row, local?.sync?.updatedAt)) return@forEach
            if (row.deleted) {
                if (local != null) {
                    dao.deleteSaving(row.uid)
                    deleted(row.entity, row.uid)
                    result.applied++
                }
                return@forEach
            }
            val entity = SyncPayloads.toSaving(row.payload, row.meta(), index, local)
            if (local == null) dao.insertSaving(entity) else dao.updateSaving(entity)
            result.applied++
        }
        return result
    }

    private suspend fun applyChallenges(rows: List<SyncRow>, index: UidIndex): Applied {
        val result = Applied()
        if (rows.isEmpty()) return result
        val locals = rows.uids().flatMap { dao.challengesByUid(it) }.associateBy { it.sync.uid }
        rows.forEach { row ->
            val local = locals[row.uid]
            if (!wins(row, local?.sync?.updatedAt)) return@forEach
            if (row.deleted) {
                if (local != null) {
                    dao.deleteChallenge(row.uid)
                    deleted(row.entity, row.uid)
                    result.applied++
                }
                return@forEach
            }
            val entity = SyncPayloads.toChallenge(row.payload, row.meta(), index, local)
            if (local == null) dao.insertChallenge(entity) else dao.updateChallenge(entity)
            result.applied++
        }
        return result
    }

    private suspend fun applyDebts(rows: List<SyncRow>): Applied {
        val result = Applied()
        if (rows.isEmpty()) return result
        val locals = rows.uids().flatMap { dao.debtsByUid(it) }.associateBy { it.sync.uid }
        rows.forEach { row ->
            val local = locals[row.uid]
            if (!wins(row, local?.sync?.updatedAt)) return@forEach
            if (row.deleted) {
                if (local != null) {
                    dao.deleteDebt(row.uid)
                    deleted(row.entity, row.uid)
                    result.applied++
                }
                return@forEach
            }
            val entity = SyncPayloads.toDebt(row.payload, row.meta(), local)
            if (local == null) dao.insertDebt(entity) else dao.updateDebt(entity)
            result.applied++
        }
        return result
    }

    private suspend fun applyAwards(rows: List<SyncRow>): Applied {
        val result = Applied()
        if (rows.isEmpty()) return result
        val locals = rows.uids().flatMap { dao.awardsByUid(it) }.associateBy { it.sync.uid }
        rows.forEach { row ->
            val local = locals[row.uid]
            if (!wins(row, local?.sync?.updatedAt)) return@forEach
            if (row.deleted) {
                if (local != null) {
                    dao.deleteAward(row.uid)
                    deleted(row.entity, row.uid)
                    result.applied++
                }
                return@forEach
            }
            val entity = SyncPayloads.toAward(row.payload, row.meta())
            if (local == null) dao.insertAward(entity) else dao.updateAward(entity)
            result.applied++
        }
        return result
    }

    private fun SyncRow.meta() = SyncMeta(uid = uid, updatedAt = updatedAt)

    /** SQLite caps the parameters one IN clause can carry. */
    private fun List<SyncRow>.uids(): List<List<String>> =
        map { it.uid }.chunked(LOOKUP_CHUNK)

    private companion object {
        const val PUSH_BATCH = 200
        const val PULL_PAGE = 500
        const val LOOKUP_CHUNK = 400
    }
}

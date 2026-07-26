package by.mlastovsky.kosht.data

import by.mlastovsky.kosht.data.db.KoshtDatabase
import kotlinx.coroutines.flow.first

/**
 * Sweeps out what nothing points at any more.
 *
 * Three kinds of leftovers accumulate in ordinary use, and none of them belongs
 * to a record the user can see:
 *
 *  - **Attachment files** whose record is gone: a scan cancelled after the photo
 *    was saved, a record deleted while the app was killed before the undo offer
 *    expired, a photo replaced by another one.
 *  - **Tombstones** for deletions every device already knows about — they exist
 *    to be pushed once, and after that they are pure ballast in a table the
 *    sync engine reads on every run.
 *  - **Downloaded electronic receipts** of records that no longer exist, which
 *    are files like any other and go with the first group.
 *
 * Run once per process start, off the main thread. Everything here is
 * conservative: only the app's own directories, only rows both sides have seen,
 * and a grace period so a file created a second ago is never swept from under
 * the editor holding it.
 */
class Housekeeping(
    private val database: KoshtDatabase,
    private val photos: PhotoStore,
    private val settings: SettingsRepository
) {

    suspend fun run(): Result = runCatching {
        val referenced = buildSet {
            addAll(database.transactionDao().referencedFiles())
            // The profile picture lives outside the transactions, and losing it
            // to a sweep would be a memorable bug.
            settings.profile.first().photoPath?.let(::add)
        }
        val files = photos.purgeOrphans(referenced)

        val cursor = database.syncDao().cursor()
        val tombstones = if (cursor != null && cursor.pushedThrough > 0) {
            // Only what has provably been pushed: a tombstone dropped early is
            // a deletion that never reaches the other phone.
            database.syncDao().dropTombstonesThrough(cursor.pushedThrough)
            true
        } else {
            false
        }
        Result(orphanFiles = files, tombstonesDropped = tombstones)
    }.getOrDefault(Result())

    data class Result(val orphanFiles: Int = 0, val tombstonesDropped: Boolean = false)
}

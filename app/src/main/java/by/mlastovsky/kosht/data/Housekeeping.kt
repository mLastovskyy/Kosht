package by.mlastovsky.kosht.data

import by.mlastovsky.kosht.data.db.KoshtDatabase
import kotlinx.coroutines.flow.first

class Housekeeping(
    private val database: KoshtDatabase,
    private val photos: PhotoStore,
    private val settings: SettingsRepository
) {

    suspend fun run(): Result = runCatching {
        val referenced = buildSet {
            addAll(database.transactionDao().referencedFiles())

            settings.profile.first().photoPath?.let(::add)
        }
        val files = photos.purgeOrphans(referenced)

        val cursor = database.syncDao().cursor()
        val tombstones = if (cursor != null && cursor.pushedThrough > 0) {

            database.syncDao().dropTombstonesThrough(cursor.pushedThrough)
            true
        } else {
            false
        }
        Result(orphanFiles = files, tombstonesDropped = tombstones)
    }.getOrDefault(Result())

    data class Result(val orphanFiles: Int = 0, val tombstonesDropped: Boolean = false)
}

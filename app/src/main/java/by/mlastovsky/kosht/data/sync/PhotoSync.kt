package by.mlastovsky.kosht.data.sync

import by.mlastovsky.kosht.data.PhotoStore
import by.mlastovsky.kosht.data.SettingsRepository
import by.mlastovsky.kosht.data.db.TransactionDao
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * Receipt photos in the cloud — only ever because the user asked.
 *
 * The switch is off out of the box and the policy says images stay on the
 * phone, so nothing here runs until somebody turns it on: no bucket is
 * touched, no key is written. Once on, it works in both directions during an
 * ordinary sync — photos taken here go up, photos another device took come
 * down — and switching it back off deletes what was uploaded, because a
 * withdrawn consent that leaves the files behind is not withdrawn.
 *
 * Everything is best effort. A photo is an attachment to a record, not the
 * record: a failed upload leaves the amount, the date and the note perfectly
 * synced and tries again next time, and never turns into a failed sync.
 */
class PhotoSync(
    private val api: SupabaseApi,
    private val settings: SettingsRepository,
    private val transactions: TransactionDao,
    private val photos: PhotoStore
) {

    suspend fun enabled(): Boolean = settings.settings.first().syncPhotos

    /** Uploads what is new here, fetches what is new there. */
    suspend fun exchange(session: SupabaseSession) {
        if (!enabled()) return
        upload(session)
        download(session)
    }

    private suspend fun upload(session: SupabaseSession) {
        transactions.photosToUpload().forEach { row ->
            val uid = row.sync.uid
            // A record the triggers have not stamped yet has no identity to
            // name the object after; the next sync will have one.
            if (uid.isBlank()) return@forEach
            val file = row.photoPath?.let(::File) ?: return@forEach
            if (!file.exists() || file.length() == 0L) return@forEach
            val key = key(uid)
            if (api.uploadPhoto(session, path(session, key), file.readBytes())) {
                transactions.setPhotoKey(row.id, key)
            }
        }
    }

    private suspend fun download(session: SupabaseSession) {
        transactions.photosToDownload().forEach { row ->
            val key = row.photoKey ?: return@forEach
            val bytes = api.downloadPhoto(session, path(session, key)) ?: return@forEach
            photos.save(bytes, subdir = "receipts")?.let { path ->
                transactions.setPhotoPath(row.id, path)
            }
        }
    }

    /**
     * The objects of records that were just deleted. Called while the
     * tombstones are still in hand — once the deletes have been pushed they
     * are forgotten, and with them the only trace of what to clean up.
     */
    suspend fun deleteFor(session: SupabaseSession, uids: List<String>) {
        if (uids.isEmpty() || !enabled()) return
        uids.forEach { uid -> api.deletePhoto(session, path(session, key(uid))) }
    }

    /**
     * Consent withdrawn: every object this account has in the bucket goes, and
     * the keys go with them. The local files are untouched — they were always
     * the originals.
     */
    suspend fun purge(session: SupabaseSession) {
        api.listPhotos(session).forEach { name ->
            api.deletePhoto(session, "${session.userId}/$name")
        }
        // Belt and braces: a listing capped or refused still leaves the keys
        // pointing at objects, so delete by what the database remembers too.
        transactions.photosUploaded().forEach { row ->
            row.photoKey?.let { api.deletePhoto(session, path(session, it)) }
        }
        transactions.clearPhotoKeys()
    }

    private fun key(uid: String) = "$uid.jpg"

    private fun path(session: SupabaseSession, key: String) = "${session.userId}/$key"
}

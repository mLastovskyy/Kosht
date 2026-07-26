package by.mlastovsky.kosht.data.sync

import by.mlastovsky.kosht.data.PhotoStore
import by.mlastovsky.kosht.data.SettingsRepository
import by.mlastovsky.kosht.data.db.TransactionDao
import java.io.File
import kotlinx.coroutines.flow.first

class PhotoSync(
    private val api: SupabaseApi,
    private val settings: SettingsRepository,
    private val transactions: TransactionDao,
    private val photos: PhotoStore
) {

    suspend fun enabled(): Boolean = settings.settings.first().syncPhotos

    suspend fun exchange(session: SupabaseSession) {
        if (!enabled()) return
        upload(session)
        download(session)
    }

    private suspend fun upload(session: SupabaseSession) {
        transactions.photosToUpload().forEach { row ->
            val uid = row.sync.uid

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

    suspend fun deleteFor(session: SupabaseSession, uids: List<String>) {
        if (uids.isEmpty() || !enabled()) return
        uids.forEach { uid -> api.deletePhoto(session, path(session, key(uid))) }
    }

    suspend fun purge(session: SupabaseSession) {
        api.listPhotos(session).forEach { name ->
            api.deletePhoto(session, "${session.userId}/$name")
        }

        transactions.photosUploaded().forEach { row ->
            row.photoKey?.let { api.deletePhoto(session, path(session, it)) }
        }
        transactions.clearPhotoKeys()
    }

    private fun key(uid: String) = "$uid.jpg"

    private fun path(session: SupabaseSession, key: String) = "${session.userId}/$key"
}

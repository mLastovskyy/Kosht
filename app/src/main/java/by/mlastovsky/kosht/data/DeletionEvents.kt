package by.mlastovsky.kosht.data

import by.mlastovsky.kosht.data.db.TransactionEntity
import by.mlastovsky.kosht.data.db.TransactionItemEntity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * A record on its way out, with everything that has to come back if the user
 * changes their mind: its product lines went with it through the foreign key,
 * and the database cannot hand them back on its own.
 */
data class DeletedRecord(
    val transaction: TransactionEntity,
    val items: List<TransactionItemEntity> = emptyList()
)

/**
 * A record that has just been deleted, on its way to being offered back.
 *
 * Deleting happens in two places — a swipe in History and the button in the
 * editor — and the offer to undo it should look the same and live in the same
 * corner of the screen either way. The alternative was for each screen to
 * grow its own snackbar, which is how one of them ended up underneath the
 * floating add button.
 *
 * The attached files travel with it rather than being deleted on the spot:
 * undoing a delete that has already thrown the receipt photo away would put
 * the record back pointing at nothing.
 */
object DeletionEvents {

    private val _deleted = MutableSharedFlow<DeletedRecord>(extraBufferCapacity = 4)

    val deleted: SharedFlow<DeletedRecord> = _deleted.asSharedFlow()

    fun report(transaction: TransactionEntity, items: List<TransactionItemEntity> = emptyList()) {
        _deleted.tryEmit(DeletedRecord(transaction, items))
    }
}

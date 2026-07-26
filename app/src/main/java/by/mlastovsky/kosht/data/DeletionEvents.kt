package by.mlastovsky.kosht.data

import by.mlastovsky.kosht.data.db.DebtEntity
import by.mlastovsky.kosht.data.db.TransactionEntity
import by.mlastovsky.kosht.data.db.TransactionItemEntity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class DeletedRecord(
    val transaction: TransactionEntity,
    val items: List<TransactionItemEntity> = emptyList(),

    val debt: DebtEntity? = null
)

object DeletionEvents {

    private val _deleted = MutableSharedFlow<DeletedRecord>(extraBufferCapacity = 4)

    val deleted: SharedFlow<DeletedRecord> = _deleted.asSharedFlow()

    fun report(record: DeletedRecord) {
        _deleted.tryEmit(record)
    }
}

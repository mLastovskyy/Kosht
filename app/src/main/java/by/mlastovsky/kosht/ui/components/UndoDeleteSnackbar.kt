package by.mlastovsky.kosht.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import by.mlastovsky.kosht.R
import by.mlastovsky.kosht.data.DeletedRecord
import by.mlastovsky.kosht.data.DeletionEvents
import by.mlastovsky.kosht.data.PhotoStore
import by.mlastovsky.kosht.data.TransactionRepository
import by.mlastovsky.kosht.ui.AppViewModelProvider
import kotlinx.coroutines.launch

/**
 * "Transaction deleted — Undo", wherever the delete came from.
 *
 * Lives at the root, so the snackbar is the Scaffold's own: that is what keeps
 * the floating add button from sitting on top of the Undo action, which is the
 * one part of a snackbar that has to be reachable.
 */
@Composable
fun UndoDeleteSnackbar(
    hostState: SnackbarHostState,
    viewModel: UndoDeleteViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val message = stringResource(R.string.transaction_deleted)
    val undo = stringResource(R.string.undo)

    LaunchedEffect(hostState) {
        DeletionEvents.deleted.collect { deleted ->
            // A second delete replaces the first offer; the first one's files
            // are cleaned up as its snackbar is dismissed.
            val result = hostState.showSnackbar(
                message = message,
                actionLabel = undo,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.restore(deleted)
            } else {
                viewModel.forget(deleted)
            }
        }
    }
}

class UndoDeleteViewModel(
    private val repository: TransactionRepository,
    private val photoStore: PhotoStore
) : ViewModel() {

    /**
     * Puts the record back with its id, its photo, its receipt and the product
     * lines that were cascaded away with it.
     */
    fun restore(record: DeletedRecord) {
        viewModelScope.launch { repository.restore(record) }
    }

    /** The offer expired: now the attachments can go. */
    fun forget(record: DeletedRecord) {
        viewModelScope.launch {
            photoStore.delete(record.transaction.photoPath)
            photoStore.delete(record.transaction.receiptDocPath)
        }
    }
}

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

@Composable
fun UndoDeleteSnackbar(
    hostState: SnackbarHostState,
    viewModel: UndoDeleteViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val message = stringResource(R.string.transaction_deleted)
    val undo = stringResource(R.string.undo)

    LaunchedEffect(hostState) {
        DeletionEvents.deleted.collect { deleted ->

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

    fun restore(record: DeletedRecord) {
        viewModelScope.launch { repository.restore(record) }
    }

    fun forget(record: DeletedRecord) {
        viewModelScope.launch {
            photoStore.delete(record.transaction.photoPath)
            photoStore.delete(record.transaction.receiptDocPath)
        }
    }
}

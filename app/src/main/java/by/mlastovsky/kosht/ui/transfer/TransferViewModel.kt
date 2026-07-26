package by.mlastovsky.kosht.ui.transfer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.mlastovsky.kosht.data.AccountRepository
import by.mlastovsky.kosht.data.DeletionEvents
import by.mlastovsky.kosht.data.RatesRepository
import by.mlastovsky.kosht.data.SettingsRepository
import by.mlastovsky.kosht.data.TransactionRepository
import by.mlastovsky.kosht.data.db.AccountEntity
import by.mlastovsky.kosht.data.db.TransactionEntity
import by.mlastovsky.kosht.util.Dates
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TransferUiState(
    val accounts: List<AccountEntity> = emptyList(),
    val currencyCode: String = SettingsRepository.DEFAULT_CURRENCY,
    /** Whether the dialog offers a fee field; a setting in Display. */
    val feeEnabled: Boolean = false
)

/**
 * Moving money between the user's own accounts. Small enough to serve the
 * dialog wherever it is opened from — the Wallet, where transfers are made,
 * and the lists, where an existing one is corrected or deleted.
 */
class TransferViewModel(
    private val transactions: TransactionRepository,
    accountRepository: AccountRepository,
    settingsRepository: SettingsRepository,
    private val ratesRepository: RatesRepository
) : ViewModel() {

    val uiState: StateFlow<TransferUiState> = combine(
        accountRepository.observeAccounts(),
        settingsRepository.settings
    ) { accounts, settings ->
        TransferUiState(
            accounts = accounts,
            currencyCode = settings.currencyCode,
            feeEnabled = settings.transferFee
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransferUiState())

    /**
     * Writes a new transfer or the corrections to an existing one. [onDone]
     * runs only when something was actually saved.
     */
    fun save(
        original: TransactionEntity?,
        fromAccountId: Long,
        toAccountId: Long,
        amountMinor: Long,
        feeMinor: Long,
        note: String,
        date: LocalDate,
        onDone: () -> Unit
    ) {
        if (amountMinor <= 0 || fromAccountId == toAccountId) return
        viewModelScope.launch {
            val saved = transactions.saveTransfer(
                original = original,
                fromAccountId = fromAccountId,
                toAccountId = toAccountId,
                amountMinor = amountMinor,
                feeMinor = feeMinor,
                bynMinor = bynEquivalent(amountMinor, original),
                note = note,
                timestamp = Dates.momentFor(date, original?.timestamp)
            )
            if (saved) onDone()
        }
    }

    fun delete(transfer: TransactionEntity, onDone: () -> Unit) {
        viewModelScope.launch {
            transactions.deleteTransaction(transfer)
            // Same offer to undo as everywhere else in the app.
            DeletionEvents.report(transfer)
            onDone()
        }
    }

    /**
     * The BYN value frozen at the moment of the transfer, like every other
     * record; an untouched amount keeps the figure it was first saved with.
     */
    private suspend fun bynEquivalent(amountMinor: Long, original: TransactionEntity?): Long? {
        val currency = uiState.value.currencyCode
        if (currency == "BYN") return amountMinor
        if (original != null && original.amountMinor == amountMinor && original.bynMinor != null) {
            return original.bynMinor
        }
        return RatesRepository.toBynMinor(amountMinor, currency, ratesRepository.rates.first())
    }
}

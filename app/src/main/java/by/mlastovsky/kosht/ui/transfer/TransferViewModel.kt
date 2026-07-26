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
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TransferUiState(
    val accounts: List<AccountEntity> = emptyList(),
    val currencyCode: String = SettingsRepository.DEFAULT_CURRENCY,

    val feeEnabled: Boolean = false
)

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

            DeletionEvents.report(transfer)
            onDone()
        }
    }

    private suspend fun bynEquivalent(amountMinor: Long, original: TransactionEntity?): Long? {
        val currency = uiState.value.currencyCode
        if (currency == "BYN") return amountMinor
        if (original != null && original.amountMinor == amountMinor && original.bynMinor != null) {
            return original.bynMinor
        }
        return RatesRepository.toBynMinor(amountMinor, currency, ratesRepository.rates.first())
    }
}

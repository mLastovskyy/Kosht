package by.mlastovsky.kosht.ui.editor

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.mlastovsky.kosht.data.PhotoStore
import by.mlastovsky.kosht.data.RatesRepository
import by.mlastovsky.kosht.data.SettingsRepository
import by.mlastovsky.kosht.data.TransactionRepository
import by.mlastovsky.kosht.data.db.CategoryEntity
import by.mlastovsky.kosht.data.db.TransactionEntity
import by.mlastovsky.kosht.data.receipt.ReceiptScanner
import by.mlastovsky.kosht.model.TransactionType
import kotlinx.coroutines.flow.first
import by.mlastovsky.kosht.ui.navigation.Routes
import by.mlastovsky.kosht.util.Dates
import by.mlastovsky.kosht.util.Money
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

data class EditorUiState(
    val loaded: Boolean = false,
    val isEdit: Boolean = false,
    val type: TransactionType = TransactionType.EXPENSE,
    val amountInput: String = "",
    val note: String = "",
    val date: LocalDate = LocalDate.now(),
    val categoryId: Long? = null,
    val categories: List<CategoryEntity> = emptyList(),
    val currencyCode: String = SettingsRepository.DEFAULT_CURRENCY,
    val scanning: Boolean = false,
    val photoPath: String? = null,
    val pendingScan: PendingScan? = null
) {
    /** An arithmetic operation is typed but not evaluated yet. */
    val hasPendingOperation: Boolean
        get() = by.mlastovsky.kosht.util.Expr.hasPendingOperation(amountInput)

    val canSave: Boolean
        get() = categoryId != null &&
            (by.mlastovsky.kosht.util.Expr.evaluateToMinor(amountInput, currencyCode) ?: 0L) > 0L
}

/** OCR result awaiting user review before it is applied to the draft. */
data class PendingScan(
    val amountInput: String,
    val date: LocalDate?,
    val merchant: String?,
    val photoPath: String?
)

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: TransactionRepository,
    settingsRepository: SettingsRepository,
    private val receiptScanner: ReceiptScanner,
    private val photoStore: PhotoStore,
    private val ratesRepository: RatesRepository
) : ViewModel() {

    private val transactionId: Long = savedStateHandle[Routes.EDITOR_ARG_ID] ?: Routes.NO_ID

    private data class Draft(
        val loaded: Boolean = false,
        val type: TransactionType = TransactionType.EXPENSE,
        val amountInput: String = "",
        val note: String = "",
        val date: LocalDate = LocalDate.now(),
        val categoryId: Long? = null,
        val photoPath: String? = null,
        /** Original entity when editing, to preserve id/createdAt/time of day. */
        val original: TransactionEntity? = null
    )

    private val draft = MutableStateFlow(Draft())

    private val scanning = MutableStateFlow(false)

    private val pendingScan = MutableStateFlow<PendingScan?>(null)

    private val categoriesForType = draft
        .map { it.type }
        .distinctUntilChanged()
        .flatMapLatest { repository.observeCategories(it) }

    val uiState: StateFlow<EditorUiState> = combine(
        draft,
        categoriesForType,
        settingsRepository.settings,
        scanning,
        pendingScan
    ) { d, categories, settings, isScanning, pending ->
        val effectiveCategoryId = when {
            d.categoryId != null && categories.any { it.id == d.categoryId } -> d.categoryId
            else -> categories.firstOrNull()?.id
        }
        EditorUiState(
            loaded = d.loaded,
            isEdit = d.original != null,
            type = d.type,
            amountInput = d.amountInput,
            note = d.note,
            date = d.date,
            categoryId = effectiveCategoryId,
            categories = categories,
            currencyCode = settings.currencyCode,
            scanning = isScanning,
            photoPath = d.photoPath,
            pendingScan = pending
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EditorUiState())

    init {
        viewModelScope.launch {
            if (transactionId != Routes.NO_ID) {
                val existing = repository.getTransaction(transactionId)
                if (existing != null) {
                    val tx = existing.transaction
                    draft.update {
                        it.copy(
                            loaded = true,
                            type = tx.type,
                            amountInput = minorToInput(tx.amountMinor),
                            note = tx.note,
                            date = Dates.toLocalDate(tx.timestamp),
                            categoryId = tx.categoryId,
                            photoPath = tx.photoPath,
                            original = tx
                        )
                    }
                    return@launch
                }
            }
            draft.update { it.copy(loaded = true) }
        }
    }

    fun setType(type: TransactionType) {
        draft.update { it.copy(type = type, categoryId = null) }
    }

    fun selectCategory(id: Long) {
        draft.update { it.copy(categoryId = id) }
    }

    fun setNote(note: String) {
        draft.update { it.copy(note = note.take(200)) }
    }

    fun setDate(date: LocalDate) {
        draft.update { it.copy(date = date) }
    }

    /** The operand after the last operator — keypad rules apply per operand. */
    private fun lastOperand(input: String): String =
        input.takeLastWhile { it.isDigit() || it == '.' }

    fun onDigit(digit: Char) {
        draft.update { d ->
            val operand = lastOperand(d.amountInput)
            val fractionDigits = Money.fractionDigits(currentCurrency())
            val decimalIndex = operand.indexOf('.')
            val next = when {
                decimalIndex >= 0 && operand.length - decimalIndex - 1 >= fractionDigits ->
                    d.amountInput
                decimalIndex < 0 && operand.trimStart('0').length >= MAX_INTEGER_DIGITS ->
                    d.amountInput
                operand == "0" -> d.amountInput.dropLast(1) + digit
                else -> d.amountInput + digit
            }
            d.copy(amountInput = next)
        }
    }

    fun onDecimal() {
        if (Money.fractionDigits(currentCurrency()) == 0) return
        draft.update { d ->
            val operand = lastOperand(d.amountInput)
            val next = when {
                operand.contains('.') -> d.amountInput
                operand.isEmpty() -> d.amountInput + "0."
                else -> d.amountInput + "."
            }
            d.copy(amountInput = next)
        }
    }

    /** Appends a calculator operator (＋ − × ÷) after a digit. */
    fun onOperator(op: Char) {
        draft.update { d ->
            val input = d.amountInput
            val next = when {
                input.isEmpty() -> input
                input.last() == '.' -> input
                input.last().isDigit() -> input + op
                else -> input.dropLast(1) + op
            }
            d.copy(amountInput = next)
        }
    }

    /** Evaluates the typed expression and replaces it with the result. */
    fun onEquals() {
        draft.update { d ->
            val minor = by.mlastovsky.kosht.util.Expr
                .evaluateToMinor(d.amountInput, currentCurrency())
            if (minor == null || minor < 0) {
                d
            } else {
                d.copy(amountInput = minorToInput(minor))
            }
        }
    }

    fun onBackspace() {
        draft.update { it.copy(amountInput = it.amountInput.dropLast(1)) }
    }

    /**
     * Runs on-device OCR over a receipt photo and prefills the draft with the
     * recognized total, date and merchant. Reports success via [onResult].
     */
    fun scanReceipt(uri: Uri, onResult: (Boolean) -> Unit) {
        if (scanning.value) return
        viewModelScope.launch {
            scanning.value = true
            val parsed = receiptScanner.scan(uri)
            val savedPhoto = photoStore.saveFromUri(uri)
            scanning.value = false
            val amount = parsed?.amountMinor
            if (amount != null) {
                // Let the user review/correct the OCR result before applying.
                pendingScan.value = PendingScan(
                    amountInput = minorToInput(amount),
                    date = parsed.date,
                    merchant = parsed.merchant,
                    photoPath = savedPhoto
                )
                onResult(true)
            } else {
                photoStore.delete(savedPhoto)
                onResult(false)
            }
        }
    }

    /** Applies the (possibly corrected) scan result to the draft. */
    fun applyScan(amountInput: String, note: String) {
        val pending = pendingScan.value ?: return
        draft.update { d ->
            if (pending.photoPath != null && d.photoPath != null) {
                photoStore.delete(d.photoPath)
            }
            d.copy(
                type = TransactionType.EXPENSE,
                amountInput = amountInput.replace(',', '.').trim(),
                date = pending.date ?: d.date,
                note = note.trim().take(200),
                photoPath = pending.photoPath ?: d.photoPath
            )
        }
        pendingScan.value = null
    }

    /** Discards the scan result and its photo. */
    fun dismissScan() {
        photoStore.delete(pendingScan.value?.photoPath)
        pendingScan.value = null
    }

    fun attachPhoto(uri: Uri) {
        viewModelScope.launch {
            val saved = photoStore.saveFromUri(uri) ?: return@launch
            draft.update { d ->
                if (d.photoPath != null) photoStore.delete(d.photoPath)
                d.copy(photoPath = saved)
            }
        }
    }

    fun removePhoto() {
        draft.update { d ->
            photoStore.delete(d.photoPath)
            d.copy(photoPath = null)
        }
    }

    fun addCategory(name: String, iconKey: String, colorArgb: Long) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = repository.addCategory(name, iconKey, colorArgb, draft.value.type)
            draft.update { it.copy(categoryId = id) }
        }
    }

    fun save(onDone: () -> Unit) {
        val state = uiState.value
        val amountMinor = by.mlastovsky.kosht.util.Expr
            .evaluateToMinor(state.amountInput, state.currencyCode) ?: return
        val categoryId = state.categoryId ?: return
        if (amountMinor <= 0) return

        viewModelScope.launch {
            val original = draft.value.original
            val timestamp = resolveTimestamp(state.date, original)
            val bynMinor = resolveBynMinor(amountMinor, state.currencyCode, original)
            if (original != null) {
                repository.updateTransaction(
                    original.copy(
                        amountMinor = amountMinor,
                        type = state.type,
                        categoryId = categoryId,
                        note = state.note.trim(),
                        timestamp = timestamp,
                        photoPath = draft.value.photoPath,
                        bynMinor = bynMinor
                    )
                )
            } else {
                repository.addTransaction(
                    TransactionEntity(
                        amountMinor = amountMinor,
                        type = state.type,
                        categoryId = categoryId,
                        note = state.note.trim(),
                        timestamp = timestamp,
                        createdAt = System.currentTimeMillis(),
                        photoPath = draft.value.photoPath,
                        bynMinor = bynMinor
                    )
                )
            }
            onDone()
        }
    }

    /**
     * Freezes the BYN equivalent at save time. An unchanged amount keeps the
     * originally fixed value so old records never drift with the rate.
     */
    private suspend fun resolveBynMinor(
        amountMinor: Long,
        currencyCode: String,
        original: TransactionEntity?
    ): Long? {
        if (currencyCode == "BYN") return amountMinor
        if (original != null && original.amountMinor == amountMinor && original.bynMinor != null) {
            return original.bynMinor
        }
        val rates = ratesRepository.rates.first()
        return RatesRepository.toBynMinor(amountMinor, currencyCode, rates)
    }

    fun delete(onDone: () -> Unit) {
        val original = draft.value.original ?: return
        viewModelScope.launch {
            photoStore.delete(original.photoPath)
            repository.deleteTransaction(original)
            onDone()
        }
    }

    /**
     * Keeps a sensible time of day: "today" gets the current moment, edits on
     * the same day keep the original time, other days land on noon to avoid
     * timezone edge cases.
     */
    private fun resolveTimestamp(date: LocalDate, original: TransactionEntity?): Long {
        val zone = ZoneId.systemDefault()
        if (original != null && Dates.toLocalDate(original.timestamp) == date) {
            return original.timestamp
        }
        return if (date == LocalDate.now()) {
            System.currentTimeMillis()
        } else {
            LocalDateTime.of(date, LocalTime.NOON).atZone(zone).toInstant().toEpochMilli()
        }
    }

    private fun currentCurrency(): String = uiState.value.currencyCode

    private fun minorToInput(amountMinor: Long): String {
        val digits = Money.fractionDigits(uiState.value.currencyCode)
        if (digits == 0) return amountMinor.toString()
        val whole = amountMinor / pow10(digits)
        val fraction = amountMinor % pow10(digits)
        return if (fraction == 0L) {
            whole.toString()
        } else {
            "$whole." + fraction.toString().padStart(digits, '0').trimEnd('0')
        }
    }

    private fun pow10(n: Int): Long {
        var result = 1L
        repeat(n) { result *= 10 }
        return result
    }

    private companion object {
        const val MAX_INTEGER_DIGITS = 9
    }
}

package by.mlastovsky.kosht.ui.editor

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.mlastovsky.kosht.data.ItemDraft
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
import by.mlastovsky.kosht.util.Notes
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
    /** Electronic receipt reached through a scanned QR, if there was one. */
    val receiptUrl: String? = null,
    val receiptDocPath: String? = null,
    val pendingScan: PendingScan? = null,
    val accounts: List<by.mlastovsky.kosht.data.db.AccountEntity> = emptyList(),
    val accountId: Long? = null,
    val multiAccount: Boolean = false,
    /** The figures were read off a receipt rather than typed. */
    val scanned: Boolean = false,
    /** What the record was spent on, line by line. Optional, often empty. */
    val items: List<ItemDraft> = emptyList(),
    /** Item names already used in this category, offered while typing. */
    val itemSuggestions: List<String> = emptyList(),
    /** Built-in key of the chosen category; picks the suggested lines. */
    val itemCategoryKey: String? = null,
    /**
     * The record turned out to be a transfer between accounts, which is edited
     * in its own dialog rather than here. The screen closes on it.
     */
    val isTransfer: Boolean = false,
    /** Open the calculator automatically for a new record. */
    val autoCalculator: Boolean = true,
    /** Working expression of the standalone calculator dialog. */
    val calcInput: String = ""
) {
    /** An arithmetic operation is typed in the calculator but not evaluated yet. */
    val calcPendingOperation: Boolean
        get() = by.mlastovsky.kosht.util.Expr.hasPendingOperation(calcInput)

    /** The calculator expression evaluates to a positive amount. */
    val calcCanApply: Boolean
        get() = (by.mlastovsky.kosht.util.Expr.evaluateToMinor(calcInput, currencyCode) ?: 0L) > 0L

    val canSave: Boolean
        get() = categoryId != null &&
            (by.mlastovsky.kosht.util.Expr.evaluateToMinor(amountInput, currencyCode) ?: 0L) > 0L

    /** What the listed products add up to — shown next to the record's amount. */
    val itemsTotalMinor: Long
        get() = items.sumOf { it.amountMinor }
}

/** Scan result awaiting user review before it is applied to the draft. */
data class PendingScan(
    val amountInput: String,
    val date: LocalDate?,
    val merchant: String?,
    val photoPath: String?,
    /** Set when the figures came from an electronic receipt behind a QR. */
    val receiptUrl: String? = null,
    val receiptDocPath: String? = null,
    /** The shopping the slip listed, when its lines could be read. */
    val items: List<ItemDraft> = emptyList()
) {
    val fromQr: Boolean get() = receiptUrl != null || receiptDocPath != null
}

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: TransactionRepository,
    settingsRepository: SettingsRepository,
    private val receiptScanner: ReceiptScanner,
    private val photoStore: PhotoStore,
    private val ratesRepository: RatesRepository,
    accountRepository: by.mlastovsky.kosht.data.AccountRepository
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
        val accountId: Long? = null,
        val receiptUrl: String? = null,
        val receiptDocPath: String? = null,
        val scanned: Boolean = false,
        val isTransfer: Boolean = false,
        val items: List<ItemDraft> = emptyList(),
        /** Original entity when editing, to preserve id/createdAt/time of day. */
        val original: TransactionEntity? = null
    )

    private val draft = MutableStateFlow(Draft())

    private val scanning = MutableStateFlow(false)

    private val pendingScan = MutableStateFlow<PendingScan?>(null)

    /** Expression typed in the standalone calculator dialog. */
    private val calcInput = MutableStateFlow("")

    private val categoriesForType = draft
        .map { it.type }
        .distinctUntilChanged()
        .flatMapLatest { repository.observeCategories(it) }

    private data class Aux(
        val scanning: Boolean,
        val pending: PendingScan?,
        val accounts: List<by.mlastovsky.kosht.data.db.AccountEntity>,
        val calcInput: String,
        val itemHints: ItemHints
    )

    /** What to offer while listing the items of a record, and where from. */
    private data class ItemHints(
        val names: List<String> = emptyList(),
        /** Built-in key of the chosen category; picks the suggested lines. */
        val categoryKey: String? = null
    )

    /**
     * The category actually in force — the chosen one, or the first of the type
     * when nothing is chosen yet, exactly as [uiState] resolves it.
     */
    private val selectedCategory = combine(
        draft.map { it.categoryId }.distinctUntilChanged(),
        categoriesForType
    ) { id, categories ->
        categories.firstOrNull { it.id == id } ?: categories.firstOrNull()
    }.distinctUntilChanged()

    private val itemHints = selectedCategory.flatMapLatest { category ->
        if (category == null) {
            kotlinx.coroutines.flow.flowOf(ItemHints())
        } else {
            repository.observeItemNames(category.id).map { ItemHints(it, category.key) }
        }
    }

    private val aux = combine(
        scanning,
        pendingScan,
        accountRepository.observeAccounts(),
        calcInput,
        itemHints,
        ::Aux
    )

    val uiState: StateFlow<EditorUiState> = combine(
        draft,
        categoriesForType,
        settingsRepository.settings,
        aux
    ) { d, categories, settings, extras ->
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
            scanning = extras.scanning,
            photoPath = d.photoPath,
            receiptUrl = d.receiptUrl,
            receiptDocPath = d.receiptDocPath,
            pendingScan = extras.pending,
            accounts = extras.accounts,
            accountId = d.accountId ?: extras.accounts.firstOrNull()?.id,
            multiAccount = settings.multiAccount,
            scanned = d.scanned,
            isTransfer = d.isTransfer,
            items = d.items,
            itemSuggestions = extras.itemHints.names,
            itemCategoryKey = extras.itemHints.categoryKey,
            autoCalculator = settings.autoCalculator,
            calcInput = extras.calcInput
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EditorUiState())

    init {
        viewModelScope.launch {
            if (transactionId != Routes.NO_ID) {
                val existing = repository.getTransaction(transactionId)
                if (existing != null) {
                    val tx = existing.transaction
                    val items = repository.itemsOf(tx.id).map {
                        ItemDraft(it.name, it.amountMinor, it.quantity)
                    }
                    draft.update {
                        it.copy(
                            loaded = true,
                            type = tx.type,
                            amountInput = minorToInput(tx.amountMinor),
                            note = tx.note,
                            date = Dates.toLocalDate(tx.timestamp),
                            categoryId = tx.categoryId,
                            photoPath = tx.photoPath,
                            accountId = tx.accountId,
                            receiptUrl = tx.receiptUrl,
                            receiptDocPath = tx.receiptDocPath,
                            scanned = tx.scanned,
                            isTransfer = tx.isTransfer,
                            items = items,
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

    fun selectAccount(id: Long) {
        draft.update { it.copy(accountId = id) }
    }

    fun setNote(note: String) {
        draft.update { it.copy(note = note.take(Notes.MAX_LENGTH)) }
    }

    fun setDate(date: LocalDate) {
        draft.update { it.copy(date = date) }
    }

    // --- What the record was spent on ---

    /**
     * Adds a line of what the record was for. A nameless line says nothing, so
     * it is refused rather than stored — the whole list is optional anyway.
     * [priceMinor] is the price of one, the way a receipt prints it.
     */
    fun addItem(name: String, priceMinor: Long, quantity: Double?) {
        val draftItem = itemOrNull(name, priceMinor, quantity) ?: return
        draft.update { it.copy(items = it.items + draftItem) }
    }

    fun updateItem(index: Int, name: String, priceMinor: Long, quantity: Double?) {
        val draftItem = itemOrNull(name, priceMinor, quantity) ?: return
        draft.update { d ->
            if (index !in d.items.indices) return@update d
            d.copy(items = d.items.toMutableList().also { it[index] = draftItem })
        }
    }

    fun removeItem(index: Int) {
        draft.update { d ->
            if (index !in d.items.indices) return@update d
            d.copy(items = d.items.filterIndexed { at, _ -> at != index })
        }
    }

    /**
     * A price with a quantity is the price of one: two at 1,75 is a line of
     * 3,50, which is what the receipt says and what the statistics add up.
     */
    private fun itemOrNull(name: String, priceMinor: Long, quantity: Double?): ItemDraft? {
        if (name.isBlank()) return null
        val count = quantity?.takeIf { it > 0 }
        val lineTotal = if (count != null) Math.round(priceMinor * count) else priceMinor
        return ItemDraft(
            name = name.trim(),
            amountMinor = lineTotal.coerceAtLeast(0),
            quantity = count
        )
    }

    // --- Calculator dialog ---

    /** Seeds the calculator with the current amount when the dialog opens. */
    fun openCalculator() {
        calcInput.value = draft.value.amountInput
    }

    /** The operand after the last operator — keypad rules apply per operand. */
    private fun lastOperand(input: String): String =
        input.takeLastWhile { it.isDigit() || it == '.' }

    fun onDigit(digit: Char) {
        calcInput.update { input ->
            val operand = lastOperand(input)
            val fractionDigits = Money.fractionDigits(currentCurrency())
            val decimalIndex = operand.indexOf('.')
            when {
                decimalIndex >= 0 && operand.length - decimalIndex - 1 >= fractionDigits ->
                    input
                decimalIndex < 0 && operand.trimStart('0').length >= MAX_INTEGER_DIGITS ->
                    input
                operand == "0" -> input.dropLast(1) + digit
                else -> input + digit
            }
        }
    }

    fun onDecimal() {
        if (Money.fractionDigits(currentCurrency()) == 0) return
        calcInput.update { input ->
            val operand = lastOperand(input)
            when {
                operand.contains('.') -> input
                operand.isEmpty() -> input + "0."
                else -> "$input."
            }
        }
    }

    /** Appends a calculator operator (＋ − × ÷) after a digit. */
    fun onOperator(op: Char) {
        calcInput.update { input ->
            when {
                input.isEmpty() -> input
                input.last() == '.' -> input
                input.last().isDigit() -> input + op
                else -> input.dropLast(1) + op
            }
        }
    }

    /** Evaluates the typed expression and replaces it with the result. */
    fun onEquals() {
        calcInput.update { input ->
            val minor = by.mlastovsky.kosht.util.Expr
                .evaluateToMinor(input, currentCurrency())
            if (minor == null || minor < 0) input else minorToInput(minor)
        }
    }

    fun onBackspace() {
        calcInput.update { it.dropLast(1) }
    }

    /** Writes the evaluated calculator result into the amount input. */
    fun applyCalculator() {
        val minor = by.mlastovsky.kosht.util.Expr
            .evaluateToMinor(calcInput.value, currentCurrency()) ?: return
        if (minor <= 0) return
        draft.update { it.copy(amountInput = minorToInput(minor)) }
    }

    /**
     * Runs on-device OCR over a receipt photo and prefills the draft with the
     * recognized total, date and merchant. Reports success via [onResult].
     */
    fun scanReceipt(uri: Uri, onResult: (Boolean) -> Unit) {
        if (scanning.value) return
        viewModelScope.launch {
            scanning.value = true
            val scanned = receiptScanner.scan(uri)
            val savedPhoto = photoStore.saveFromUri(uri)
            scanning.value = false
            val amount = scanned?.parsed?.amountMinor
            if (amount != null) {
                // Let the user review/correct the result before applying.
                pendingScan.value = PendingScan(
                    amountInput = minorToInput(amount),
                    date = scanned.parsed.date,
                    merchant = scanned.parsed.merchant,
                    photoPath = savedPhoto,
                    receiptUrl = scanned.sourceUrl,
                    receiptDocPath = scanned.documentPath,
                    items = scanned.parsed.items.map {
                        ItemDraft(it.name, it.amountMinor, it.quantity)
                    }
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
            if (pending.receiptDocPath != null) photoStore.delete(d.receiptDocPath)
            d.copy(
                type = TransactionType.EXPENSE,
                amountInput = amountInput.replace(',', '.').trim(),
                date = pending.date ?: d.date,
                note = note.trim().take(Notes.MAX_LENGTH),
                photoPath = pending.photoPath ?: d.photoPath,
                receiptUrl = pending.receiptUrl ?: d.receiptUrl,
                receiptDocPath = pending.receiptDocPath ?: d.receiptDocPath,
                // Where the figures came from, kept with the record itself.
                scanned = true,
                // The slip's own lines, when it had readable ones. A list the
                // user has already typed is theirs and is left alone.
                items = if (d.items.isEmpty()) pending.items else d.items
            )
        }
        pendingScan.value = null
    }

    /** Discards the scan result along with everything it downloaded. */
    fun dismissScan() {
        pendingScan.value?.let {
            photoStore.delete(it.photoPath)
            photoStore.delete(it.receiptDocPath)
        }
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

    /** Detaches the electronic receipt and drops its downloaded copy. */
    fun removeEReceipt() {
        draft.update { d ->
            photoStore.delete(d.receiptDocPath)
            d.copy(receiptUrl = null, receiptDocPath = null)
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
            val timestamp = Dates.momentFor(state.date, original?.timestamp)
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
                        accountId = state.accountId,
                        bynMinor = bynMinor,
                        receiptUrl = draft.value.receiptUrl,
                        receiptDocPath = draft.value.receiptDocPath,
                        scanned = draft.value.scanned
                    )
                )
                repository.saveItems(original.id, draft.value.items)
            } else {
                val id = repository.addTransaction(
                    TransactionEntity(
                        amountMinor = amountMinor,
                        type = state.type,
                        categoryId = categoryId,
                        note = state.note.trim(),
                        timestamp = timestamp,
                        createdAt = System.currentTimeMillis(),
                        photoPath = draft.value.photoPath,
                        accountId = state.accountId,
                        bynMinor = bynMinor,
                        receiptUrl = draft.value.receiptUrl,
                        receiptDocPath = draft.value.receiptDocPath,
                        scanned = draft.value.scanned
                    )
                )
                repository.saveItems(id, draft.value.items)
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
            // Read while they still exist: the delete cascades them away, and
            // the undo offer is what has to hand them back.
            val items = repository.itemsOf(original.id)
            repository.deleteTransaction(original)
            // The photo and the downloaded receipt are deleted only once the
            // undo offer has passed -- restoring a record whose picture is
            // already gone would put it back pointing at nothing.
            by.mlastovsky.kosht.data.DeletionEvents.report(original, items)
            onDone()
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

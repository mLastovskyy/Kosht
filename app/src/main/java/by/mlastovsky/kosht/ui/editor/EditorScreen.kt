package by.mlastovsky.kosht.ui.editor

import android.net.Uri
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.ShoppingBasket
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import by.mlastovsky.kosht.R
import by.mlastovsky.kosht.data.db.AccountEntity
import by.mlastovsky.kosht.ui.components.AccountBadge
import by.mlastovsky.kosht.model.TransactionType
import by.mlastovsky.kosht.ui.AccountVisuals
import by.mlastovsky.kosht.ui.AppViewModelProvider
import by.mlastovsky.kosht.ui.CategoryVisuals
import by.mlastovsky.kosht.ui.components.CategoryActions
import by.mlastovsky.kosht.ui.components.CategoryPickerRow
import by.mlastovsky.kosht.ui.components.TextInput
import by.mlastovsky.kosht.ui.components.TruncatedText
import by.mlastovsky.kosht.ui.components.rememberBitmapFromPath
import by.mlastovsky.kosht.ui.relativeDate
import by.mlastovsky.kosht.ui.theme.KoshtTheme
import by.mlastovsky.kosht.util.Expr
import by.mlastovsky.kosht.util.Money
import by.mlastovsky.kosht.util.Notes
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.util.Currency
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    onClose: () -> Unit,
    viewModel: EditorViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val view = LocalView.current
    val context = LocalContext.current
    var showDatePicker by remember { mutableStateOf(false) }
    val categoryActions = remember(viewModel) {
        CategoryActions(
            add = viewModel::addCategory,
            reorder = viewModel::reorderCategories,
            update = viewModel::updateCategory,
            delete = viewModel::deleteCategory
        )
    }
    var showScanSource by remember { mutableStateOf(false) }
    var showAttachSource by remember { mutableStateOf(false) }
    var showAccountPicker by remember { mutableStateOf(false) }
    var showCalculator by remember { mutableStateOf(false) }
    var showPhotoView by remember { mutableStateOf(false) }
    var showEReceipt by remember { mutableStateOf(false) }
    var showItems by remember { mutableStateOf(false) }
    var askedOverSum by remember { mutableStateOf(false) }
    var cameraTarget by remember { mutableStateOf<Uri?>(null) }
    val scanFailedMessage = stringResource(R.string.scan_failed)

    val onScanResult: (Boolean) -> Unit = { ok ->
        if (!ok) Toast.makeText(context, scanFailedMessage, Toast.LENGTH_SHORT).show()
    }
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.scanReceipt(uri, onScanResult)
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val target = cameraTarget
        if (success && target != null) viewModel.scanReceipt(target, onScanResult)
    }
    val attachGalleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.attachPhoto(uri)
    }
    val attachCameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val target = cameraTarget
        if (success && target != null) viewModel.attachPhoto(target)
    }

    LaunchedEffect(state.isTransfer) {
        if (state.isTransfer) onClose()
    }

    var calcAutoOpened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.loaded) {
        if (state.loaded && state.autoCalculator && !state.isEdit &&
            state.amountInput.isEmpty() && !calcAutoOpened
        ) {
            calcAutoOpened = true
            viewModel.openCalculator()
            showCalculator = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding()
    ) {
        TopAppBar(
            title = {
                Text(
                    stringResource(
                        if (state.isEdit) R.string.editor_title_edit else R.string.editor_title_new
                    )
                )
            },
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_back))
                }
            },
            actions = {
                if (state.isEdit) {
                    IconButton(onClick = { viewModel.delete(onClose) }) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = stringResource(R.string.editor_delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        AnimatedVisibility(visible = state.scanning) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.scan_ai_progress),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        TypeToggle(
            type = state.type,
            onTypeChange = viewModel::setType,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            AmountCard(
                amountInput = state.amountInput,
                currencyCode = state.currencyCode,
                type = state.type,
                scanned = state.scanned,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    viewModel.openCalculator()
                    showCalculator = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .typeSwipe(state.type) { next ->
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        viewModel.setType(next)
                    }
            )

            CategoryPickerRow(
                categories = state.categories,
                selectedId = state.categoryId,
                onSelect = { id ->
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    viewModel.selectCategory(id)
                },
                type = state.type,
                actions = categoryActions,
                contentPadding = PaddingValues(horizontal = 16.dp)
            )

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AssistChip(
                    onClick = { showDatePicker = true },
                    leadingIcon = {
                        Icon(Icons.Rounded.Event, contentDescription = null, Modifier.size(18.dp))
                    },
                    label = { Text(relativeDate(state.date), maxLines = 1) }
                )

                val account = state.accounts.firstOrNull { it.id == state.accountId }
                if (state.accounts.size > 1 && account != null) {
                    AssistChip(
                        onClick = { showAccountPicker = true },
                        leadingIcon = {
                            AccountBadge(
                                iconKey = account.iconKey,
                                color = Color(account.colorArgb),
                                iconPath = account.iconPath,
                                size = 18.dp
                            )
                        },
                        label = {
                            Text(
                                text = AccountVisuals.displayName(account),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = CHIP_LABEL_MAX_WIDTH)
                            )
                        }
                    )
                }

                if (state.itemsAllowed) {
                    AssistChip(
                        onClick = { showItems = true },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.ShoppingBasket,
                                contentDescription = null,
                                Modifier.size(18.dp)
                            )
                        },
                        label = {
                            Text(
                                text = if (state.items.isEmpty()) {
                                    stringResource(R.string.items_add)
                                } else {
                                    stringResource(R.string.items_add) +
                                        " · " + state.items.size
                                },
                                maxLines = 1
                            )
                        }
                    )
                }

                if (state.receiptUrl != null || state.receiptDocPath != null) {
                    AssistChip(
                        onClick = { showEReceipt = true },
                        leadingIcon = {
                            Icon(
                                Icons.AutoMirrored.Rounded.ReceiptLong,
                                contentDescription = null,
                                Modifier.size(18.dp)
                            )
                        },
                        label = { Text(stringResource(R.string.ereceipt_open), maxLines = 1) }
                    )
                }

            }

            AnimatedVisibility(visible = state.debtCategory) {
                OutlinedTextField(
                    value = state.debtPerson,
                    onValueChange = viewModel::setDebtPerson,
                    placeholder = { Text(stringResource(R.string.debt_person_owe)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    keyboardOptions = TextInput.Sentence,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = state.note,
                    onValueChange = viewModel::setNote,
                    placeholder = { Text(stringResource(R.string.editor_note_hint)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    keyboardOptions = TextInput.Sentence,
                    modifier = Modifier.weight(1f)
                )

                FilledTonalIconButton(
                    onClick = { showScanSource = true },
                    enabled = !state.scanning,
                    modifier = Modifier.size(ACTION_BUTTON)
                ) {
                    Icon(
                        Icons.Rounded.DocumentScanner,
                        contentDescription = stringResource(R.string.editor_scan_receipt)
                    )
                }

                val thumbnail = rememberBitmapFromPath(state.photoPath, maxDimension = 128)
                FilledTonalIconButton(
                    onClick = {
                        if (state.photoPath == null) showAttachSource = true else showPhotoView = true
                    },
                    modifier = Modifier.size(ACTION_BUTTON)
                ) {
                    if (thumbnail != null) {
                        Image(
                            bitmap = thumbnail,
                            contentDescription = stringResource(R.string.photo_receipt),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(30.dp)
                                .clip(MaterialTheme.shapes.small)
                        )
                    } else {
                        Icon(
                            Icons.Rounded.AddAPhoto,
                            contentDescription = stringResource(R.string.attach_photo)
                        )
                    }
                }
            }
        }

        Button(
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                if (state.itemsOverSum) askedOverSum = true else viewModel.save(onClose)
            },
            enabled = state.canSave,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .height(56.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Text(
                text = stringResource(R.string.editor_save),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }

    if (showCalculator) {
        CalculatorSheet(
            calcInput = state.calcInput,
            currencyCode = state.currencyCode,
            pendingOperation = state.calcPendingOperation,
            canApply = state.calcCanApply,
            onDigit = { digit ->
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                viewModel.onDigit(digit)
            },
            onDecimal = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                viewModel.onDecimal()
            },
            onOperator = { op ->
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                viewModel.onOperator(op)
            },
            onBackspace = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                viewModel.onBackspace()
            },
            onEquals = {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                viewModel.onEquals()
            },
            onApply = {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                viewModel.applyCalculator()
                showCalculator = false
            },
            onDismiss = { showCalculator = false }
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.date
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            viewModel.setDate(
                                Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            )
                        }
                        showDatePicker = false
                    }
                ) { Text(stringResource(R.string.editor_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState, showModeToggle = false)
        }
    }

    if (showScanSource) {
        PhotoSourceDialog(
            title = stringResource(R.string.editor_scan_receipt),
            onCamera = {
                showScanSource = false
                val uri = newCameraUri(context)
                cameraTarget = uri
                cameraLauncher.launch(uri)
            },
            onGallery = {
                showScanSource = false
                galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onDismiss = { showScanSource = false }
        )
    }

    if (showAttachSource) {
        PhotoSourceDialog(
            title = stringResource(R.string.attach_photo),
            onCamera = {
                showAttachSource = false
                val uri = newCameraUri(context)
                cameraTarget = uri
                attachCameraLauncher.launch(uri)
            },
            onGallery = {
                showAttachSource = false
                attachGalleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onDismiss = { showAttachSource = false }
        )
    }

    if (showAccountPicker) {
        AccountSheet(
            accounts = state.accounts,
            selectedId = state.accountId,
            type = state.type,
            onSelect = { id ->
                viewModel.selectAccount(id)
                showAccountPicker = false
            },
            onDismiss = { showAccountPicker = false }
        )
    }

    state.pendingScan?.let { pending ->
        ScanReviewDialog(
            pending = pending,
            currencyCode = state.currencyCode,
            onApply = { amount, note -> viewModel.applyScan(amount, note) },
            onDismiss = { viewModel.dismissScan() }
        )
    }

    if (showPhotoView && state.photoPath != null) {
        PhotoViewDialog(
            path = state.photoPath!!,
            onRemove = {
                viewModel.removePhoto()
                showPhotoView = false
            },
            onDismiss = { showPhotoView = false }
        )
    }

    if (showItems) {
        ItemsDialog(
            items = state.items,
            suggestions = state.itemSuggestions,
            categoryKey = state.itemCategoryKey,
            currencyCode = state.currencyCode,
            recordAmountMinor = Expr
                .evaluateToMinor(state.amountInput, state.currencyCode) ?: 0L,
            onAdd = viewModel::addItem,
            onUpdate = viewModel::updateItem,
            onRemove = viewModel::removeItem,
            onDismiss = { showItems = false }
        )
    }

    if (askedOverSum) {
        AlertDialog(
            onDismissRequest = { askedOverSum = false },
            title = { Text(stringResource(R.string.items_over_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.items_over_text,
                        Money.format(state.itemsTotalMinor, state.currencyCode),
                        Money.format(
                            Expr.evaluateToMinor(state.amountInput, state.currencyCode) ?: 0L,
                            state.currencyCode
                        )
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        askedOverSum = false
                        viewModel.save(onClose)
                    }
                ) { Text(stringResource(R.string.editor_save)) }
            },
            dismissButton = {
                TextButton(onClick = { askedOverSum = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showEReceipt) {
        EReceiptDialog(
            url = state.receiptUrl,
            documentPath = state.receiptDocPath,
            onRemove = {
                viewModel.removeEReceipt()
                showEReceipt = false
            },
            onDismiss = { showEReceipt = false }
        )
    }
}

private fun newCameraUri(context: android.content.Context): Uri {
    val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
    val file = File(dir, "receipt_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountSheet(
    accounts: List<AccountEntity>,
    selectedId: Long?,
    type: TransactionType,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
        ) {
            Text(
                text = stringResource(
                    if (type == TransactionType.INCOME) {
                        R.string.account_pick_income
                    } else {
                        R.string.account_pick_expense
                    }
                ),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(12.dp))
            accounts.forEach { account ->
                val chosen = account.id == selectedId
                ListItem(
                    headlineContent = {
                        Text(
                            text = AccountVisuals.displayName(account),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingContent = {
                        AccountBadge(
                            iconKey = account.iconKey,
                            color = Color(account.colorArgb),
                            iconPath = account.iconPath
                        )
                    },
                    trailingContent = {
                        if (chosen) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { onSelect(account.id) }
                )
            }
        }
    }
}

@Composable
private fun PhotoSourceDialog(
    title: String,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.scan_from_camera)) },
                    leadingContent = {
                        Icon(Icons.Rounded.PhotoCamera, contentDescription = null)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable(onClick = onCamera)
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.scan_from_gallery)) },
                    leadingContent = {
                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = null)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable(onClick = onGallery)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun ScanReviewDialog(
    pending: PendingScan,
    currencyCode: String,
    onApply: (amount: String, note: String) -> Unit,
    onDismiss: () -> Unit
) {
    var amountText by remember { mutableStateOf(pending.amountInput.replace('.', ',')) }
    var noteText by remember { mutableStateOf(pending.merchant.orEmpty()) }
    val thumbnail = rememberBitmapFromPath(pending.photoPath, maxDimension = 512)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.scan_review_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(
                        if (pending.fromQr) {
                            R.string.ereceipt_from_qr
                        } else {
                            R.string.scan_review_hint
                        }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .clip(MaterialTheme.shapes.medium)
                    )
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.take(12) },
                    label = { Text(stringResource(R.string.amount_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it.take(Notes.MAX_LENGTH) },
                    label = { Text(stringResource(R.string.editor_note_hint)) },
                    singleLine = true,
                    keyboardOptions = TextInput.Sentence,
                    modifier = Modifier.fillMaxWidth()
                )
                if (pending.date != null) {
                    Text(
                        text = stringResource(R.string.scan_review_date, relativeDate(pending.date)),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (pending.items.isNotEmpty()) {
                    val listed = pending.items.sumOf { it.amountMinor }
                    val total = Money.parseToMinor(
                        pending.amountInput.replace('.', ','),
                        currencyCode
                    ) ?: 0L
                    Text(
                        text = if (total > 0 && listed < total) {
                            stringResource(
                                R.string.scan_review_items_partial,
                                pending.items.size,
                                Money.format(listed, currencyCode),
                                Money.format(total, currencyCode)
                            )
                        } else {
                            stringResource(R.string.scan_review_items, pending.items.size)
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = pending.items.take(4).joinToString(" · ") { it.name },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = amountText.isNotBlank(),
                onClick = { onApply(amountText, noteText) }
            ) { Text(stringResource(R.string.action_apply)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun PhotoViewDialog(
    path: String,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    val bitmap = rememberBitmapFromPath(path)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.photo_receipt)) },
        text = {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        dismissButton = {
            TextButton(onClick = onRemove) {
                Text(
                    stringResource(R.string.photo_remove),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeToggle(
    type: TransactionType,
    onTypeChange: (TransactionType) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(
        TransactionType.EXPENSE to stringResource(R.string.type_expense),
        TransactionType.INCOME to stringResource(R.string.type_income)
    )
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { index, (option, label) ->
            SegmentedButton(
                selected = type == option,
                onClick = { onTypeChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun AmountCard(
    amountInput: String,
    currencyCode: String,
    type: TransactionType,
    scanned: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayValue = amountInput.ifEmpty { "0" }.replace('.', ',')
    val symbol = remember(currencyCode) {
        runCatching { Currency.getInstance(currencyCode).getSymbol(Locale.getDefault()) }
            .getOrNull() ?: currencyCode
    }
    val color by animateColorAsState(
        targetValue = when {
            amountInput.isEmpty() -> MaterialTheme.colorScheme.onSurfaceVariant
            type == TransactionType.INCOME -> KoshtTheme.colors.income
            else -> MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(200),
        label = "amountColor"
    )
    val fontSize = when {
        displayValue.length > 12 -> 36.sp
        displayValue.length > 8 -> 44.sp
        else -> 52.sp
    }

    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(vertical = 20.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = displayValue,
                    style = MaterialTheme.typography.displayMedium.copy(fontSize = fontSize),
                    color = color,
                    maxLines = 1
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = symbol,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (scanned) Icons.Rounded.DocumentScanner else Icons.Rounded.Calculate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(
                        if (scanned) R.string.scanned_mark else R.string.editor_tap_amount
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalculatorSheet(
    calcInput: String,
    currencyCode: String,
    pendingOperation: Boolean,
    canApply: Boolean,
    onDigit: (Char) -> Unit,
    onDecimal: () -> Unit,
    onOperator: (Char) -> Unit,
    onBackspace: () -> Unit,
    onEquals: () -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val displayValue = calcInput.ifEmpty { "0" }.replace('.', ',')
            val symbol = remember(currencyCode) {
                runCatching {
                    Currency.getInstance(currencyCode).getSymbol(Locale.getDefault())
                }.getOrNull() ?: currencyCode
            }
            val fontSize = when {
                displayValue.length > 12 -> 28.sp
                displayValue.length > 8 -> 34.sp
                else -> 40.sp
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayValue,
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = fontSize),
                    color = if (calcInput.isEmpty()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = symbol,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Keypad(
                onDigit = onDigit,
                onDecimal = onDecimal,
                onOperator = onOperator,
                onBackspace = onBackspace
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = MaterialTheme.shapes.large
                ) { Text(stringResource(R.string.action_cancel)) }
                Button(
                    onClick = if (pendingOperation) onEquals else onApply,
                    enabled = pendingOperation || canApply,
                    modifier = Modifier
                        .weight(1.6f)
                        .height(56.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Text(
                        text = if (pendingOperation) {
                            "="
                        } else {
                            stringResource(R.string.action_apply)
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun Modifier.typeSwipe(
    type: TransactionType,
    onChange: (TransactionType) -> Unit
): Modifier {
    val threshold = with(LocalDensity.current) { SWIPE_THRESHOLD.toPx() }
    var travelled by remember { mutableFloatStateOf(0f) }
    val state = rememberDraggableState { delta -> travelled += delta }
    return this.draggable(
        state = state,
        orientation = Orientation.Horizontal,
        onDragStarted = { travelled = 0f },
        onDragStopped = {
            val next = when {
                travelled <= -threshold -> TransactionType.INCOME
                travelled >= threshold -> TransactionType.EXPENSE
                else -> null
            }
            travelled = 0f
            if (next != null && next != type) onChange(next)
        }
    )
}

private val SWIPE_THRESHOLD = 56.dp

private val CHIP_LABEL_MAX_WIDTH = 120.dp

private val ACTION_BUTTON = 52.dp

@Composable
private fun Keypad(
    onDigit: (Char) -> Unit,
    onDecimal: () -> Unit,
    onOperator: (Char) -> Unit,
    onBackspace: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        listOf("123" to '÷', "456" to '×', "789" to '−').forEach { (rowDigits, op) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowDigits.forEach { digit ->
                    KeypadButton(
                        modifier = Modifier.weight(1f),
                        onClick = { onDigit(digit) }
                    ) {
                        Text(
                            text = digit.toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                OperatorButton(op, onOperator, Modifier.weight(0.7f))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KeypadButton(modifier = Modifier.weight(1f), onClick = onDecimal) {
                Text(
                    text = ",",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
            }
            KeypadButton(modifier = Modifier.weight(1f), onClick = { onDigit('0') }) {
                Text(
                    text = "0",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
            }
            KeypadButton(modifier = Modifier.weight(1f), onClick = onBackspace) {
                Icon(
                    Icons.AutoMirrored.Rounded.Backspace,
                    contentDescription = stringResource(R.string.cd_backspace),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OperatorButton('+', onOperator, Modifier.weight(0.7f))
        }
    }
}

@Composable
private fun OperatorButton(
    op: Char,
    onOperator: (Char) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(64.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { onOperator(op) },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = op.toString(),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun KeypadButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .height(64.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

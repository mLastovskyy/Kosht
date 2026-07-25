package by.mlastovsky.kosht.ui.editor

import android.net.Uri
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import by.mlastovsky.kosht.R
import by.mlastovsky.kosht.data.db.CategoryEntity
import by.mlastovsky.kosht.model.TransactionType
import by.mlastovsky.kosht.ui.AppViewModelProvider
import by.mlastovsky.kosht.ui.CategoryVisuals
import by.mlastovsky.kosht.ui.components.CategoryBadge
import by.mlastovsky.kosht.ui.components.rememberBitmapFromPath
import by.mlastovsky.kosht.ui.relativeDate
import by.mlastovsky.kosht.ui.theme.KoshtTheme
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
    var showNewCategory by remember { mutableStateOf(false) }
    var showScanSource by remember { mutableStateOf(false) }
    var showAttachSource by remember { mutableStateOf(false) }
    var showAccountPicker by remember { mutableStateOf(false) }
    var showCalculator by remember { mutableStateOf(false) }
    var showPhotoView by remember { mutableStateOf(false) }
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
                IconButton(
                    onClick = { showScanSource = true },
                    enabled = !state.scanning
                ) {
                    Icon(
                        Icons.Rounded.DocumentScanner,
                        contentDescription = stringResource(R.string.editor_scan_receipt)
                    )
                }
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
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }

        TypeToggle(
            type = state.type,
            onTypeChange = viewModel::setType,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        AmountDisplay(
            amountInput = state.amountInput,
            currencyCode = state.currencyCode,
            type = state.type,
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                viewModel.openCalculator()
                showCalculator = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        CategoryPicker(
            categories = state.categories,
            selectedId = state.categoryId,
            onSelect = { id ->
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                viewModel.selectCategory(id)
            },
            onAddNew = { showNewCategory = true }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AssistChip(
                onClick = { showDatePicker = true },
                leadingIcon = {
                    Icon(Icons.Rounded.Event, contentDescription = null, Modifier.size(18.dp))
                },
                label = { Text(relativeDate(state.date)) }
            )
            if (state.multiAccount && state.accounts.size > 1) {
                val account = state.accounts.firstOrNull { it.id == state.accountId }
                if (account != null) {
                    AssistChip(
                        onClick = { showAccountPicker = true },
                        leadingIcon = {
                            Icon(
                                CategoryVisuals.icon(account.iconKey),
                                contentDescription = null,
                                tint = Color(account.colorArgb),
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        label = {
                            Text(
                                by.mlastovsky.kosht.ui.AccountVisuals.displayName(account),
                                maxLines = 1
                            )
                        }
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (state.photoPath == null) {
                IconButton(onClick = { showAttachSource = true }) {
                    Icon(
                        Icons.Rounded.AddAPhoto,
                        contentDescription = stringResource(R.string.attach_photo),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val thumbnail = rememberBitmapFromPath(state.photoPath, maxDimension = 128)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { showPhotoView = true },
                    contentAlignment = Alignment.Center
                ) {
                    if (thumbnail != null) {
                        Image(
                            bitmap = thumbnail,
                            contentDescription = stringResource(R.string.photo_receipt),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            Icons.Rounded.PhotoLibrary,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = state.note,
            onValueChange = viewModel::setNote,
            placeholder = { Text(stringResource(R.string.editor_note_hint)) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            textStyle = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp)
        )

        Button(
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                viewModel.save(onClose)
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
        CalculatorDialog(
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

    if (showNewCategory) {
        NewCategoryDialog(
            onDismiss = { showNewCategory = false },
            onConfirm = { name, iconKey, color ->
                viewModel.addCategory(name, iconKey, color)
                showNewCategory = false
            }
        )
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
        AlertDialog(
            onDismissRequest = { showAccountPicker = false },
            title = { Text(stringResource(R.string.editor_account)) },
            text = {
                Column {
                    state.accounts.forEach { account ->
                        ListItem(
                            headlineContent = {
                                Text(by.mlastovsky.kosht.ui.AccountVisuals.displayName(account))
                            },
                            leadingContent = {
                                Icon(
                                    CategoryVisuals.icon(account.iconKey),
                                    contentDescription = null,
                                    tint = Color(account.colorArgb)
                                )
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent
                            ),
                            modifier = Modifier.clickable {
                                viewModel.selectAccount(account.id)
                                showAccountPicker = false
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAccountPicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    state.pendingScan?.let { pending ->
        ScanReviewDialog(
            pending = pending,
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
}

private fun newCameraUri(context: android.content.Context): Uri {
    val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
    val file = File(dir, "receipt_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
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
                    text = stringResource(R.string.scan_review_hint),
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
                    onValueChange = { noteText = it.take(200) },
                    label = { Text(stringResource(R.string.editor_note_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (pending.date != null) {
                    Text(
                        text = stringResource(R.string.scan_review_date, relativeDate(pending.date)),
                        style = MaterialTheme.typography.bodyMedium
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
private fun AmountDisplay(
    amountInput: String,
    currencyCode: String,
    type: TransactionType,
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
        displayValue.length > 12 -> 40.sp
        displayValue.length > 8 -> 48.sp
        else -> 57.sp
    }

    Box(
        modifier = modifier.clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = displayValue,
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = fontSize),
                    color = color,
                    maxLines = 1
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = symbol,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Calculate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.editor_tap_amount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Standalone calculator: the keypad lives here, not in the editor itself.
 * "=" evaluates a pending expression; Apply writes the result into the
 * editor's amount input.
 */
@Composable
private fun CalculatorDialog(
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.calc_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val displayValue = calcInput.ifEmpty { "0" }.replace('.', ',')
                val symbol = remember(currencyCode) {
                    runCatching {
                        Currency.getInstance(currencyCode).getSymbol(Locale.getDefault())
                    }.getOrNull() ?: currencyCode
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
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (calcInput.isEmpty()) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = symbol,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Keypad(
                    onDigit = onDigit,
                    onDecimal = onDecimal,
                    onOperator = onOperator,
                    onBackspace = onBackspace
                )
            }
        },
        confirmButton = {
            if (pendingOperation) {
                TextButton(onClick = onEquals) {
                    Text("=", style = MaterialTheme.typography.titleMedium)
                }
            } else {
                TextButton(onClick = onApply, enabled = canApply) {
                    Text(stringResource(R.string.action_apply))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun CategoryPicker(
    categories: List<CategoryEntity>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onAddNew: () -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(categories, key = { it.id }) { category ->
            val selected = category.id == selectedId
            val scale by animateFloatAsState(
                targetValue = if (selected) 1.08f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "badgeScale"
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { onSelect(category.id) }
                    .padding(horizontal = 6.dp, vertical = 8.dp)
                    .widthIn(min = 56.dp)
            ) {
                CategoryBadge(
                    iconKey = category.iconKey,
                    color = Color(category.colorArgb),
                    selected = selected,
                    size = 48.dp,
                    modifier = Modifier.scale(scale)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = CategoryVisuals.displayName(category),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 72.dp)
                )
            }
        }
        item(key = "add") {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(onClick = onAddNew)
                    .padding(horizontal = 6.dp, vertical = 8.dp)
                    .widthIn(min = 56.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.category_new),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    modifier = Modifier.widthIn(max = 72.dp)
                )
            }
        }
    }
}

@Composable
private fun Keypad(
    onDigit: (Char) -> Unit,
    onDecimal: () -> Unit,
    onOperator: (Char) -> Unit,
    onBackspace: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // A slim calculator column on the right: sum up items on the fly.
        listOf("123" to '÷', "456" to '×', "789" to '−').forEach { (rowDigits, op) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
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
            horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                    contentDescription = null,
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
            .height(56.dp)
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
            .height(56.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun NewCategoryDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, iconKey: String, colorArgb: Long) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var iconKey by remember { mutableStateOf(CategoryVisuals.pickableIconKeys.first()) }
    var colorArgb by remember { mutableLongStateOf(CategoryVisuals.pickableColors.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.category_new)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    placeholder = { Text(stringResource(R.string.category_name_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    stringResource(R.string.category_icon),
                    style = MaterialTheme.typography.labelLarge
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(CategoryVisuals.pickableIconKeys) { key ->
                        CategoryBadge(
                            iconKey = key,
                            color = Color(colorArgb),
                            selected = key == iconKey,
                            size = 40.dp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { iconKey = key }
                        )
                    }
                }
                Text(
                    stringResource(R.string.category_color),
                    style = MaterialTheme.typography.labelLarge
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(CategoryVisuals.pickableColors) { color ->
                        val selected = color == colorArgb
                        Box(
                            modifier = Modifier
                                .size(if (selected) 40.dp else 32.dp)
                                .clip(CircleShape)
                                .background(Color(color))
                                .clickable { colorArgb = color }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), iconKey, colorArgb) },
                enabled = name.isNotBlank()
            ) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

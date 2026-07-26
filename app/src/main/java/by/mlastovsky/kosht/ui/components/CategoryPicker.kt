package by.mlastovsky.kosht.ui.components

import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import by.mlastovsky.kosht.R
import by.mlastovsky.kosht.data.db.CategoryEntity
import by.mlastovsky.kosht.model.TransactionType
import by.mlastovsky.kosht.ui.CategoryVisuals

@Composable
fun CategoryPickerRow(
    categories: List<CategoryEntity>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,

    type: TransactionType = TransactionType.EXPENSE,

    actions: CategoryActions? = null,
    badgeSize: Dp = 48.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),

    leading: (LazyListScope.() -> Unit)? = null
) {
    val view = LocalView.current
    var showNew by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    val onAddNew: (() -> Unit)? = actions?.let { { showNew = true } }
    val onReorder = actions?.reorder
    val onEdit: ((CategoryEntity) -> Unit)? = actions?.let { { category -> editing = category } }
    val listState = rememberLazyListState()
    val edgePx = with(LocalDensity.current) { AUTO_SCROLL_EDGE.toPx() }

    var order by remember { mutableStateOf<List<Long>?>(null) }
    var dragging by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    var travelled by remember { mutableFloatStateOf(0f) }

    val shown = remember(categories, order) {
        val wanted = order
        when {
            wanted == null -> categories

            wanted.size != categories.size || categories.any { it.id !in wanted } -> categories
            else -> categories.sortedBy { wanted.indexOf(it.id) }
        }
    }
    val shownIds by rememberUpdatedState(shown.map { it.id })

    LaunchedEffect(categories, dragging) {
        if (dragging == null && order != null && categories.map { it.id } == order) order = null
    }

    fun settle() {
        val id = dragging ?: return
        val current = order ?: return
        val info = listState.layoutInfo
        val held = info.visibleItemsInfo.firstOrNull { it.key == id } ?: return
        val center = held.offset + held.size / 2f + dragOffset
        val over = info.visibleItemsInfo.firstOrNull {

            it.key is Long && it.key != id && center >= it.offset && center <= it.offset + it.size
        } ?: return
        val from = current.indexOf(id)
        val to = current.indexOf(over.key as Long)
        if (from < 0 || to < 0 || from == to) return
        order = current.toMutableList().apply { add(to, removeAt(from)) }

        dragOffset += held.offset - over.offset
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    LaunchedEffect(dragging) {
        if (dragging == null) return@LaunchedEffect
        while (true) {
            val info = listState.layoutInfo
            val held = info.visibleItemsInfo.firstOrNull { it.key == dragging }
            if (held != null) {
                val start = held.offset + dragOffset
                val end = start + held.size
                val step = when {
                    end > info.viewportEndOffset - edgePx -> AUTO_SCROLL_STEP
                    start < info.viewportStartOffset + edgePx -> -AUTO_SCROLL_STEP
                    else -> 0f
                }
                if (step != 0f) {
                    dragOffset += listState.scrollBy(step)
                    settle()
                }
            }
            withFrameNanos { }
        }
    }

    LazyRow(
        state = listState,
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        leading?.invoke(this)
        items(shown, key = { it.id }) { category ->
            val held = dragging == category.id
            val selected = category.id == selectedId
            val reveal = rememberFullTextReveal()
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
                    .zIndex(if (held) 1f else 0f)
                    .then(if (held) Modifier else Modifier.animateItem())
                    .graphicsLayer {
                        if (!held) return@graphicsLayer
                        translationX = dragOffset
                        scaleX = HELD_SCALE
                        scaleY = HELD_SCALE
                    }
                    .clip(MaterialTheme.shapes.medium)
                    .clickable {
                        onSelect(category.id)
                        reveal.reveal()
                    }
                    .then(
                        if (onReorder == null && onEdit == null) {
                            Modifier
                        } else {
                            Modifier.pointerInput(category.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        dragging = category.id
                                        dragOffset = 0f
                                        travelled = 0f
                                        order = if (onReorder == null) null else shownIds
                                        view.performHapticFeedback(
                                            HapticFeedbackConstants.LONG_PRESS
                                        )
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        travelled += kotlin.math.abs(amount.x) +
                                            kotlin.math.abs(amount.y)
                                        if (onReorder == null) return@detectDragGesturesAfterLongPress
                                        dragOffset += amount.x
                                        settle()
                                    },
                                    onDragEnd = {
                                        val settled = order
                                        val moved = travelled > MOVE_SLOP
                                        dragging = null
                                        dragOffset = 0f

                                        when {
                                            !moved -> {
                                                order = null
                                                onEdit?.invoke(category)
                                            }

                                            settled != null -> onReorder?.invoke(settled)
                                        }
                                    },
                                    onDragCancel = {
                                        dragging = null
                                        dragOffset = 0f
                                        order = null
                                    }
                                )
                            }
                        }
                    )
                    .padding(horizontal = 6.dp, vertical = 8.dp)
                    .widthIn(min = TILE_MIN_WIDTH)
            ) {
                CategoryTile(
                    iconKey = category.iconKey,
                    color = Color(category.colorArgb),
                    label = CategoryVisuals.displayName(category),
                    selected = selected,
                    badgeSize = badgeSize,
                    badgeScale = scale,
                    iconPath = category.iconPath,
                    reveal = reveal
                )
            }
        }
        if (onAddNew != null) {
            item(key = ADD_KEY) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .animateItem()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable(onClick = onAddNew)
                        .padding(horizontal = 6.dp, vertical = 8.dp)
                        .widthIn(min = TILE_MIN_WIDTH)
                ) {
                    Box(
                        modifier = Modifier
                            .size(badgeSize)
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
                    TileLabel(stringResource(R.string.category_new), selected = false)
                }
            }
        }
    }

    if (showNew && actions != null) {
        CategoryDialog(
            initial = null,
            onDismiss = { showNew = false },
            onConfirm = { edit ->
                actions.add(edit, type) { onSelect(it) }
                showNew = false
            }
        )
    }

    editing?.let { category ->
        if (actions == null) return@let
        CategoryDialog(
            initial = category,
            onDismiss = { editing = null },
            onConfirm = { edit ->
                actions.update(category.id, edit)
                editing = null
            },
            onDelete = {
                actions.delete(category)
                editing = null
            }
        )
    }
}

data class CategoryActions(
    val add: (edit: CategoryEdit, type: TransactionType, onCreated: (Long) -> Unit) -> Unit,
    val reorder: (ids: List<Long>) -> Unit,
    val update: (id: Long, edit: CategoryEdit) -> Unit,
    val delete: (CategoryEntity) -> Unit
)

data class CategoryEdit(
    val name: String,
    val iconKey: String,
    val colorArgb: Long,
    val iconUri: Uri? = null,
    val iconCleared: Boolean = false
)

@Composable
private fun CategoryTile(
    iconKey: String,
    color: Color,
    label: String,
    selected: Boolean,
    badgeSize: Dp,
    badgeScale: Float,
    iconPath: String?,
    reveal: FullTextReveal
) {
    CategoryBadge(
        iconKey = iconKey,
        color = color,
        selected = selected,
        size = badgeSize,
        iconPath = iconPath,
        modifier = Modifier.graphicsLayer {
            scaleX = badgeScale
            scaleY = badgeScale
        }
    )
    Spacer(Modifier.height(4.dp))
    TruncatedText(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        reveal = reveal,
        revealOnClick = false,
        modifier = Modifier.widthIn(max = LABEL_MAX_WIDTH)
    )
}

@Composable
private fun TileLabel(text: String, selected: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.widthIn(max = LABEL_MAX_WIDTH)
    )
}

@Composable
fun CategoryDialog(
    initial: CategoryEntity?,
    onDismiss: () -> Unit,
    onConfirm: (CategoryEdit) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val initialName = initial?.let { CategoryVisuals.displayName(it) }.orEmpty()
    var name by remember(initial?.id) { mutableStateOf(initialName) }
    var iconKey by remember(initial?.id) {
        mutableStateOf(initial?.iconKey ?: CategoryVisuals.pickableIconKeys.first())
    }
    var colorArgb by remember(initial?.id) {
        mutableLongStateOf(initial?.colorArgb ?: CategoryVisuals.pickableColors.first())
    }
    val picture = rememberPictureChoice(initial?.iconPath)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initial == null) R.string.category_new else R.string.category_edit
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(NAME_MAX) },
                    placeholder = { Text(stringResource(R.string.category_name_hint)) },
                    singleLine = true,
                    keyboardOptions = TextInput.Sentence,
                    modifier = Modifier.fillMaxWidth()
                )
                PicturePickerRow(picture)
                if (!picture.hasPicture) {
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
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        CategoryEdit(
                            name = name.trim(),
                            iconKey = iconKey,
                            colorArgb = colorArgb,
                            iconUri = picture.picked,
                            iconCleared = picture.cleared
                        )
                    )
                },
                enabled = name.isNotBlank()
            ) {
                Text(
                    stringResource(
                        if (initial == null) R.string.action_add else R.string.editor_save
                    )
                )
            }
        },
        dismissButton = {
            if (onDelete == null) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            } else {
                TextButton(onClick = onDelete) {
                    Text(
                        stringResource(R.string.editor_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    )
}

private const val ADD_KEY = "add"
private const val NAME_MAX = 40
private const val HELD_SCALE = 1.12f

private const val MOVE_SLOP = 24f

private val TILE_MIN_WIDTH = 56.dp
private val LABEL_MAX_WIDTH = 72.dp
private val AUTO_SCROLL_EDGE = 32.dp
private const val AUTO_SCROLL_STEP = 12f

package by.mlastovsky.kosht.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import android.view.View

/**
 * Holding a tile lifts it out of the row and the others make way. Tiles never
 * overlap: a neighbour steps aside as soon as the held one passes its middle,
 * and the held one cannot be dragged past the ends of the list.
 */
class ReorderList internal constructor(
    private val listState: LazyListState,
    private val idOf: (Any?) -> Long?,
    private val edgePx: Float,
    private val view: View
) {

    var held by mutableStateOf<Long?>(null)
        private set

    var offset by mutableFloatStateOf(0f)
        private set

    private var travelled by mutableFloatStateOf(0f)

    private var order by mutableStateOf<List<Long>?>(null)

    val moved: Boolean get() = travelled > MOVE_SLOP

    fun <T> arrange(items: List<T>, id: (T) -> Long): List<T> {
        val wanted = order ?: return items
        if (wanted.size != items.size || items.any { id(it) !in wanted }) return items
        return items.sortedBy { wanted.indexOf(id(it)) }
    }

    fun forget(current: List<Long>) {
        if (held == null && order == current) order = null
    }

    fun start(id: Long, ids: List<Long>) {
        held = id
        offset = 0f
        travelled = 0f
        order = ids
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    fun drag(amount: Float, distance: Float) {
        travelled += distance
        offset += amount
        clamp()
        settle()
    }

    fun release(): List<Long>? {
        val settled = order
        val shifted = moved
        held = null
        offset = 0f
        if (!shifted) order = null
        return settled.takeIf { shifted }
    }

    fun cancel() {
        held = null
        offset = 0f
        order = null
    }

    suspend fun followEdges() {
        val id = held ?: return
        while (true) {
            val info = listState.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { idOf(it.key) == id }
            if (item != null) {
                val start = item.offset + offset
                val step = when {
                    start + item.size > info.viewportEndOffset - edgePx -> AUTO_SCROLL_STEP
                    start < info.viewportStartOffset + edgePx -> -AUTO_SCROLL_STEP
                    else -> 0f
                }
                if (step != 0f) {
                    offset += listState.scrollBy(step)
                    settle()
                }
            }
            withFrameNanos { }
        }
    }

    private fun neighbours(id: Long): List<LazyListItemInfo> = listState.layoutInfo
        .visibleItemsInfo
        .filter { idOf(it.key) != null && idOf(it.key) != id }

    private fun clamp() {
        val id = held ?: return
        val item = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { idOf(it.key) == id } ?: return
        val others = neighbours(id)
        val first = (others + item).minOf { it.offset }
        val last = (others + item).maxOf { it.offset + it.size }
        val low = (first - item.offset).toFloat()
        val high = (last - item.size - item.offset).toFloat()
        if (low <= high) offset = offset.coerceIn(low, high)
    }

    private fun settle() {
        val id = held ?: return
        val current = order ?: return
        val item = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { idOf(it.key) == id } ?: return
        val centre = item.offset + item.size / 2f + offset
        val others = neighbours(id)
        // A neighbour steps aside once the held one is a third of the way into
        // it, not half: waiting for the middle looks like the two are stuck.
        val over = if (offset > 0f) {
            others.lastOrNull { it.offset > item.offset && centre >= it.offset + it.size * EARLY }
        } else {
            others.firstOrNull {
                it.offset < item.offset && centre <= it.offset + it.size * (1f - EARLY)
            }
        } ?: return

        val from = current.indexOf(id)
        val to = current.indexOf(idOf(over.key))
        if (from < 0 || to < 0 || from == to) return
        order = current.toMutableList().apply { add(to, removeAt(from)) }
        offset += item.offset - over.offset
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    private companion object {
        const val MOVE_SLOP = 24f
        const val AUTO_SCROLL_STEP = 12f
        const val EARLY = 0.32f
    }
}

@Composable
fun rememberReorderList(
    listState: LazyListState,
    idOf: (Any?) -> Long?
): ReorderList {
    val view = LocalView.current
    val edgePx = with(LocalDensity.current) { AUTO_SCROLL_EDGE.toPx() }
    return remember(listState) { ReorderList(listState, idOf, edgePx, view) }
}

private val AUTO_SCROLL_EDGE = 32.dp

@file:OptIn(ExperimentalMaterial3Api::class)

package by.mlastovsky.kosht.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch

class FullTextReveal(private val state: TooltipState, private val show: () -> Unit) {
    var truncated by mutableStateOf(false)
        internal set

    fun reveal() {
        if (truncated) show()
    }

    internal fun tooltipState() = state
}

@Composable
fun rememberFullTextReveal(): FullTextReveal {
    val state = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    return remember(state) { FullTextReveal(state) { scope.launch { state.show() } } }
}

@Composable
fun TruncatedText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    maxLines: Int = 1,
    reveal: FullTextReveal = rememberFullTextReveal(),
    revealOnClick: Boolean = true
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(text) } },
        state = reveal.tooltipState(),
        enableUserInput = false,
        modifier = modifier
    ) {
        Text(
            text = text,
            style = style,
            color = color,
            fontWeight = fontWeight,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { reveal.truncated = it.hasVisualOverflow },
            modifier = if (revealOnClick) {
                Modifier.clickable(enabled = reveal.truncated) { reveal.reveal() }
            } else {
                Modifier
            }
        )
    }
}

package by.mlastovsky.kosht.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun MonthSelector(
    month: YearMonth,
    nextEnabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = null)
        }
        AnimatedContent(
            targetState = month,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally { it / 2 } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it / 2 } + fadeOut())
                } else {
                    (slideInHorizontally { -it / 2 } + fadeIn()) togetherWith
                        (slideOutHorizontally { it / 2 } + fadeOut())
                }
            },
            label = "month",
            modifier = Modifier.weight(1f)
        ) { value ->
            Text(
                text = monthTitle(value),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        IconButton(onClick = onNext, enabled = nextEnabled) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
        }
    }
}

fun monthTitle(month: YearMonth): String {
    val formatted = month.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault()))
    return formatted.replaceFirstChar { it.titlecase(Locale.getDefault()) }
}

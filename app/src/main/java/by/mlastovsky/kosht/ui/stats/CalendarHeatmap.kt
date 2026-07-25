package by.mlastovsky.kosht.ui.stats

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle as JavaTextStyle
import java.time.temporal.WeekFields

/**
 * Month calendar where each day is tinted by how much was spent/earned that
 * day relative to the month's maximum. Tapping a day selects it.
 */
@Composable
fun CalendarHeatmap(
    month: YearMonth,
    daily: List<Long>,
    selectedDay: LocalDate?,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val locale = LocalLocale.current.platformLocale
    val weekFields = remember(locale) { WeekFields.of(locale) }
    val firstDayOfWeek = weekFields.firstDayOfWeek
    val max = (daily.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val today = LocalDate.now()

    Column(modifier = modifier.padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth()) {
            (0..6).forEach { offset ->
                val day = firstDayOfWeek.plus(offset.toLong())
                Text(
                    text = day.getDisplayName(JavaTextStyle.SHORT, locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 4.dp)
                )
            }
        }

        val firstOfMonth = month.atDay(1)
        val leadingBlanks =
            ((firstOfMonth.dayOfWeek.value - firstDayOfWeek.value) + 7) % 7
        val totalCells = leadingBlanks + month.lengthOfMonth()
        val weeks = (totalCells + 6) / 7

        repeat(weeks) { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                (0..6).forEach { column ->
                    val cellIndex = week * 7 + column
                    val dayOfMonth = cellIndex - leadingBlanks + 1
                    if (dayOfMonth in 1..month.lengthOfMonth()) {
                        val date = month.atDay(dayOfMonth)
                        DayCell(
                            date = date,
                            amountMinor = daily[dayOfMonth - 1],
                            intensity = daily[dayOfMonth - 1].toFloat() / max,
                            selected = date == selectedDay,
                            isToday = date == today,
                            onClick = { onDayClick(date) },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Box(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    amountMinor: Long,
    intensity: Float,
    selected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(10.dp)
    val alpha = if (amountMinor > 0) 0.12f + 0.78f * intensity else 0f
    val background by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
        animationSpec = tween(300),
        label = "cellColor"
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.1f else 1f,
        label = "cellScale"
    )
    val textColor = when {
        alpha > 0.55f -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .padding(vertical = 2.dp)
            .aspectRatio(1f)
            .scale(scale)
            .clip(shape)
            .background(background)
            .then(
                when {
                    selected -> Modifier.border(
                        2.dp, MaterialTheme.colorScheme.primary, shape
                    )
                    isToday -> Modifier.border(
                        1.dp, MaterialTheme.colorScheme.outline, shape
                    )
                    else -> Modifier
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = textColor
        )
    }
}

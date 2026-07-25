package by.mlastovsky.kosht.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import by.mlastovsky.kosht.R
import by.mlastovsky.kosht.ui.CategoryVisuals
import by.mlastovsky.kosht.ui.components.CategoryBadge
import by.mlastovsky.kosht.ui.theme.KoshtTheme
import by.mlastovsky.kosht.util.Money
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ReportContent(state: StatsUiState) {
    val report = state.report ?: return
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item(key = "verdict") {
            VerdictCard(report)
        }
        item(key = "metrics") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricRow(
                    label = stringResource(R.string.report_spent),
                    value = Money.format(report.expenseMinor, state.currencyCode),
                    delta = report.deltaPercent
                )
                MetricRow(
                    label = stringResource(R.string.report_income),
                    value = Money.format(report.incomeMinor, state.currencyCode)
                )
                MetricRow(
                    label = stringResource(R.string.report_net),
                    value = (if (report.netMinor > 0) "+" else "") +
                        Money.format(report.netMinor, state.currencyCode),
                    valueColor = if (report.netMinor >= 0) {
                        KoshtTheme.colors.income
                    } else {
                        KoshtTheme.colors.expense
                    }
                )
                MetricRow(
                    label = stringResource(R.string.report_avg_day),
                    value = Money.format(report.avgPerDayMinor, state.currencyCode)
                )
                MetricRow(
                    label = stringResource(R.string.report_free_days),
                    value = report.daysWithoutSpending.toString()
                )
                val top = report.topSlice
                if (top != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.report_top_category),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        CategoryBadge(
                            iconKey = top.category.iconKey,
                            color = Color(top.category.colorArgb),
                            size = 28.dp
                        )
                        Text(
                            text = CategoryVisuals.displayName(top.category) +
                                " · ${(top.share * 100).roundToInt()} %",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }
        }
        item(key = "tips-header") {
            if (report.tips.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.report_tips),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
        report.tips.forEach { tip ->
            item(key = "tip-$tip") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Rounded.Lightbulb,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = tipText(tip, state, report),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun VerdictCard(report: ReportUi) {
    val (emoji, titleRes) = when (report.verdict) {
        ReportVerdict.GREAT -> "🌟" to R.string.report_verdict_great
        ReportVerdict.OK -> "🙂" to R.string.report_verdict_ok
        ReportVerdict.BAD -> "⚠️" to R.string.report_verdict_bad
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = emoji, style = MaterialTheme.typography.displaySmall)
        Column {
            if (report.userName.isNotBlank()) {
                Text(
                    text = report.userName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun MetricRow(
    label: String,
    value: String,
    delta: Int? = null,
    valueColor: Color = Color.Unspecified
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (delta != null && delta != 0) {
            Text(
                text = (if (delta > 0) "+" else "−") + "${abs(delta)} % ",
                style = MaterialTheme.typography.labelMedium,
                color = if (delta > 0) KoshtTheme.colors.expense else KoshtTheme.colors.income
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = valueColor
        )
    }
}

@Composable
private fun tipText(tip: ReportTip, state: StatsUiState, report: ReportUi): String =
    when (tip) {
        ReportTip.OVERSPEND -> stringResource(
            R.string.tip_overspend,
            Money.format(report.expenseMinor - report.incomeMinor, state.currencyCode)
        )
        ReportTip.GROWTH -> stringResource(R.string.tip_growth, report.deltaPercent ?: 0)
        ReportTip.TOP_HEAVY -> stringResource(
            R.string.tip_top_heavy,
            report.topSlice?.let { CategoryVisuals.displayName(it.category) }.orEmpty(),
            ((report.topSlice?.share ?: 0f) * 100).roundToInt()
        )
        ReportTip.START_SAVING -> stringResource(
            R.string.tip_start_saving,
            Money.format(report.netMinor / 10, state.currencyCode)
        )
        ReportTip.KEEP_IT_UP -> stringResource(
            R.string.tip_keep_it_up,
            abs(report.deltaPercent ?: 0)
        )
    }

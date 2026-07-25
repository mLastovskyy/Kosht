package by.mlastovsky.kosht.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import by.mlastovsky.kosht.R
import by.mlastovsky.kosht.ui.AppViewModelProvider
import by.mlastovsky.kosht.ui.components.AnimatedAmountText
import by.mlastovsky.kosht.ui.components.Avatar
import by.mlastovsky.kosht.ui.components.EmptyState
import by.mlastovsky.kosht.ui.components.TransactionRow
import by.mlastovsky.kosht.ui.relativeDate
import by.mlastovsky.kosht.ui.theme.KoshtTheme
import by.mlastovsky.kosht.util.Dates
import by.mlastovsky.kosht.util.Money

@Composable
fun HomeScreen(
    onTransactionClick: (Long) -> Unit,
    onSeeAllClick: () -> Unit,
    onAchievementsClick: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val contentAlpha by animateFloatAsState(
        targetValue = if (state.loaded) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "contentAlpha"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .alpha(contentAlpha),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)
    ) {
        item(key = "greeting") {
            val profile = state.profile
            if (profile != null) {
                Row(
                    modifier = Modifier
                        .statusBarsPadding()
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(
                            R.string.home_greeting,
                            profile.displayName(stringResource(R.string.profile_default_name))
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    if (state.loaded) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable(onClick = onAchievementsClick)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Rounded.LocalFireDepartment,
                                contentDescription = null,
                                tint = Color(0xFFFF7A00),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = state.streakDays.toString(),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                    IconButton(onClick = onAchievementsClick) {
                        Icon(
                            Icons.Rounded.EmojiEvents,
                            contentDescription = stringResource(R.string.achievements_title),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Avatar(
                        photoPath = profile.photoPath,
                        fallbackText = profile.displayName(
                            stringResource(R.string.profile_default_name)
                        )
                    )
                }
            }
        }
        item(key = "balance") {
            BalanceCard(
                balanceMinor = state.balanceMinor,
                balanceBynMinor = state.balanceBynMinor,
                incomeMinor = state.monthIncomeMinor,
                expenseMinor = state.monthExpenseMinor,
                currencyCode = state.currencyCode
            )
        }
        item(key = "recent-header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.home_recent),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (state.recent.isNotEmpty()) {
                    TextButton(onClick = onSeeAllClick) {
                        Text(stringResource(R.string.home_see_all))
                    }
                }
            }
        }
        if (state.loaded && state.recent.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                    title = stringResource(R.string.home_empty_title),
                    subtitle = stringResource(R.string.home_empty_subtitle)
                )
            }
        }
        items(state.recent, key = { it.transaction.id }) { item ->
            TransactionRow(
                item = item,
                currencyCode = state.currencyCode,
                onClick = { onTransactionClick(item.transaction.id) },
                supportingText = item.transaction.note.ifBlank {
                    relativeDate(Dates.toLocalDate(item.transaction.timestamp))
                },
                modifier = Modifier.animateItem()
            )
        }
    }
}

@Composable
private fun BalanceCard(
    balanceMinor: Long,
    balanceBynMinor: Long?,
    incomeMinor: Long,
    expenseMinor: Long,
    currencyCode: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary
                        )
                    )
                )
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = stringResource(R.string.home_balance),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
            )
            AnimatedAmountText(
                text = Money.format(balanceMinor, currencyCode),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onPrimary
            )
            if (balanceBynMinor != null) {
                Text(
                    text = "≈ " + Money.format(balanceBynMinor, "BYN"),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MonthStat(
                    label = stringResource(R.string.home_income),
                    amountText = "+" + Money.format(incomeMinor, currencyCode),
                    icon = Icons.Rounded.ArrowUpward,
                    iconTint = KoshtTheme.colors.income,
                    modifier = Modifier.weight(1f)
                )
                MonthStat(
                    label = stringResource(R.string.home_expenses),
                    amountText = "−" + Money.format(expenseMinor, currencyCode),
                    icon = Icons.Rounded.ArrowDownward,
                    iconTint = KoshtTheme.colors.expense,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MonthStat(
    label: String,
    amountText: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(16.dp)
            )
        }
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
            )
            AnimatedAmountText(
                text = amountText,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}


package by.mlastovsky.kosht.ui.achievements

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.EmojiFlags
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import by.mlastovsky.kosht.R
import by.mlastovsky.kosht.data.db.CategoryEntity
import by.mlastovsky.kosht.model.ChallengeType
import by.mlastovsky.kosht.ui.AppViewModelProvider
import by.mlastovsky.kosht.ui.CategoryVisuals
import by.mlastovsky.kosht.ui.components.CategoryBadge
import by.mlastovsky.kosht.ui.relativeDate
import by.mlastovsky.kosht.ui.theme.KoshtTheme
import by.mlastovsky.kosht.util.Money
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import by.mlastovsky.kosht.ui.components.TextInput

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(
    onBack: () -> Unit,
    viewModel: AchievementsViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddChallenge by remember { mutableStateOf(false) }
    var challengeToEdit by remember { mutableStateOf<ChallengeUi?>(null) }
    var selectedAward by remember { mutableStateOf<BadgeUi?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        TopAppBar(
            title = { Text(stringResource(R.string.achievements_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            item(key = "streak") {
                StreakCard(
                    days = state.streakDays,
                    budgetText = Money.format(state.dailyBudgetMinor, state.currencyCode)
                )
            }

            item(key = "challenges-header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.challenges_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { showAddChallenge = true }) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.action_add),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            if (state.loaded && state.challenges.isEmpty()) {
                item(key = "challenges-empty") {
                    Text(
                        text = stringResource(R.string.challenges_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
            items(state.challenges, key = { it.entity.id }) { challenge ->
                ChallengeCard(
                    challenge = challenge,
                    currencyCode = state.currencyCode,
                    onClick = { challengeToEdit = challenge },
                    onDelete = { viewModel.deleteChallenge(challenge) },
                    modifier = Modifier.animateItem()
                )
            }

            item(key = "badges-header") {
                Text(
                    text = stringResource(R.string.badges_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
            item(key = "badges-pager") {
                AwardPages(
                    badges = state.badges,
                    onAwardClick = { selectedAward = it }
                )
            }
        }
    }

    if (showAddChallenge) {
        AddChallengeDialog(
            categories = state.expenseCategories,
            currencyCode = state.currencyCode,
            onConfirm = { type, title, amount, categoryId, end ->
                viewModel.addChallenge(type, title, amount, categoryId, end)
                showAddChallenge = false
            },
            onDismiss = { showAddChallenge = false }
        )
    }

    challengeToEdit?.let { challenge ->
        EditChallengeDialog(
            challenge = challenge,
            categories = state.expenseCategories,
            currencyCode = state.currencyCode,
            onConfirm = { title, amount, categoryId, end ->
                viewModel.updateChallenge(challenge, title, amount, categoryId, end)
                challengeToEdit = null
            },
            onDismiss = { challengeToEdit = null }
        )
    }

    selectedAward?.let { award ->
        AwardDialog(badge = award, onDismiss = { selectedAward = null })
    }
}

@Composable
private fun StreakCard(days: Int, budgetText: String) {
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
        Icon(
            Icons.Rounded.LocalFireDepartment,
            contentDescription = null,
            tint = Color(0xFFFF7A00),
            modifier = Modifier.size(44.dp)
        )
        Column {
            Text(
                text = if (days > 0) {
                    stringResource(R.string.streak_days, days)
                } else {
                    stringResource(R.string.streak_none)
                },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = stringResource(R.string.streak_hint, budgetText),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun ChallengeCard(
    challenge: ChallengeUi,
    currencyCode: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = when (challenge.status) {
        ChallengeStatus.ACTIVE -> MaterialTheme.colorScheme.primary
        ChallengeStatus.DONE -> KoshtTheme.colors.income
        ChallengeStatus.FAILED -> MaterialTheme.colorScheme.error
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = challenge.entity.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = challengeSubtitle(challenge, currencyCode),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = stringResource(
                    when (challenge.status) {
                        ChallengeStatus.ACTIVE -> R.string.challenge_active
                        ChallengeStatus.DONE -> R.string.challenge_done
                        ChallengeStatus.FAILED -> R.string.challenge_failed
                    }
                ),
                style = MaterialTheme.typography.labelLarge,
                color = statusColor
            )
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = stringResource(R.string.editor_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        LinearProgressIndicator(
            progress = { challenge.progress },
            color = statusColor,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            drawStopIndicator = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
        )
        Text(
            text = stringResource(
                R.string.challenge_days,
                challenge.daysPassed,
                challenge.daysTotal
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun challengeSubtitle(challenge: ChallengeUi, currencyCode: String): String =
    when (challenge.entity.type) {
        ChallengeType.SPEND_LIMIT -> stringResource(
            R.string.challenge_spent_of,
            Money.format(challenge.progressLabelMinor, currencyCode),
            Money.format(challenge.entity.amountMinor, currencyCode)
        )
        ChallengeType.NO_SPEND -> stringResource(R.string.challenge_type_no_spend)
        ChallengeType.SAVE_TARGET -> stringResource(
            R.string.challenge_saved_of,
            Money.format(challenge.progressLabelMinor, "BYN"),
            Money.format(challenge.entity.amountMinor, currencyCode)
        )
    }

private data class BadgeVisual(val icon: ImageVector, val titleRes: Int, val descRes: Int)

private fun badgeVisual(key: String): BadgeVisual = when (key) {
    "first_steps" -> BadgeVisual(
        Icons.Rounded.Star, R.string.badge_first_steps, R.string.badge_first_steps_desc
    )
    "income_first" -> BadgeVisual(
        Icons.Rounded.Payments, R.string.badge_income_first, R.string.badge_income_first_desc
    )
    "ten" -> BadgeVisual(
        Icons.Rounded.CheckCircle, R.string.badge_ten, R.string.badge_ten_desc
    )
    "scanner" -> BadgeVisual(
        Icons.Rounded.CameraAlt, R.string.badge_scanner, R.string.badge_scanner_desc
    )
    "saver" -> BadgeVisual(
        Icons.Rounded.Savings, R.string.badge_saver, R.string.badge_saver_desc
    )
    "first_goal" -> BadgeVisual(
        Icons.Rounded.EmojiFlags, R.string.badge_first_goal, R.string.badge_first_goal_desc
    )
    "streak7" -> BadgeVisual(
        Icons.Rounded.LocalFireDepartment, R.string.badge_streak7, R.string.badge_streak7_desc
    )
    "surplus" -> BadgeVisual(
        Icons.AutoMirrored.Rounded.TrendingUp, R.string.badge_surplus, R.string.badge_surplus_desc
    )
    "goal_done" -> BadgeVisual(
        Icons.Rounded.Flag, R.string.badge_goal_done, R.string.badge_goal_done_desc
    )
    "challenge_done" -> BadgeVisual(
        Icons.Rounded.TaskAlt, R.string.badge_challenge_done, R.string.badge_challenge_done_desc
    )
    "photo10" -> BadgeVisual(
        Icons.Rounded.PhotoLibrary, R.string.badge_photo10, R.string.badge_photo10_desc
    )
    "hundred" -> BadgeVisual(
        Icons.Rounded.EmojiEvents, R.string.badge_hundred, R.string.badge_hundred_desc
    )
    "streak30" -> BadgeVisual(
        Icons.Rounded.LocalFireDepartment, R.string.badge_streak30, R.string.badge_streak30_desc
    )
    "big_saver" -> BadgeVisual(
        Icons.Rounded.AccountBalance, R.string.badge_big_saver, R.string.badge_big_saver_desc
    )
    "goal_three" -> BadgeVisual(
        Icons.Rounded.WorkspacePremium, R.string.badge_goal_three, R.string.badge_goal_three_desc
    )
    "challenge_five" -> BadgeVisual(
        Icons.Rounded.MilitaryTech, R.string.badge_challenge_five,
        R.string.badge_challenge_five_desc
    )
    "five_hundred" -> BadgeVisual(
        Icons.Rounded.Diamond, R.string.badge_five_hundred, R.string.badge_five_hundred_desc
    )
    else -> BadgeVisual(
        Icons.Rounded.Whatshot, R.string.badge_streak100, R.string.badge_streak100_desc
    )
}

/**
 * Awards laid out as swipeable pages (2 rows × 3) with dot indicators —
 * flicking through pages beats one long horizontal scroll.
 */
@Composable
private fun AwardPages(
    badges: List<BadgeUi>,
    onAwardClick: (BadgeUi) -> Unit
) {
    if (badges.isEmpty()) return
    val pages = badges.chunked(AWARDS_PER_PAGE)
    val pagerState = rememberPagerState { pages.size }

    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            pageSpacing = 12.dp,
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.fillMaxWidth()
        ) { pageIndex ->
            val page = pages[pageIndex]
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                page.chunked(AWARDS_PER_ROW).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { badge ->
                            BadgeCell(
                                badge = badge,
                                onClick = { onAwardClick(badge) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(108.dp)
                            )
                        }
                        repeat(AWARDS_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        if (pages.size > 1) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 12.dp)
            ) {
                repeat(pages.size) { index ->
                    val active = index == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .size(if (active) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (active) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                }
                            )
                    )
                }
            }
        }
    }
}

private const val AWARDS_PER_ROW = 3
private const val AWARDS_PER_PAGE = 6

@Composable
private fun BadgeCell(
    badge: BadgeUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visual = badgeVisual(badge.key)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
            .alpha(if (badge.unlocked) 1f else 0.45f)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (badge.unlocked) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (badge.unlocked) visual.icon else Icons.Rounded.Lock,
                contentDescription = null,
                tint = if (badge.unlocked) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        Text(
            text = stringResource(visual.titleRes),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
        )
    }
}

/**
 * Award details: how to earn it, plus the earn date once unlocked or the
 * current progress while still locked.
 */
@Composable
private fun AwardDialog(badge: BadgeUi, onDismiss: () -> Unit) {
    val visual = badgeVisual(badge.key)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        if (badge.unlocked) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (badge.unlocked) visual.icon else Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = if (badge.unlocked) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        },
        title = { Text(stringResource(visual.titleRes)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(visual.descRes),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                val unlockedAt = badge.unlockedAt
                if (badge.unlocked && unlockedAt != null) {
                    Text(
                        text = stringResource(R.string.award_unlocked_at, formatDate(unlockedAt)),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (!badge.unlocked && badge.progressText != null) {
                    Text(
                        text = stringResource(R.string.award_progress, badge.progressText),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    )
}

private fun formatDate(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault()))

/** Edit an existing challenge: title, amount, category and end date. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditChallengeDialog(
    challenge: ChallengeUi,
    categories: List<CategoryEntity>,
    currencyCode: String,
    onConfirm: (title: String, amountMinor: Long, categoryId: Long?, end: LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(challenge.entity.title) }
    var amountText by remember {
        mutableStateOf(
            Money.format(challenge.entity.amountMinor, currencyCode)
                .filter { it.isDigit() || it == ',' }
        )
    }
    var categoryId by remember { mutableStateOf(challenge.entity.categoryId) }
    var end by remember { mutableStateOf(LocalDate.ofEpochDay(challenge.entity.endEpochDay)) }
    var showDatePicker by remember { mutableStateOf(false) }
    val needsAmount = challenge.entity.type != ChallengeType.NO_SPEND
    val amountMinor = Money.parseToMinor(amountText, currencyCode) ?: 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(challenge.entity.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(60) },
                    label = { Text(stringResource(R.string.recurring_title_hint)) },
                    singleLine = true,
                    keyboardOptions = TextInput.Sentence,
                    modifier = Modifier.fillMaxWidth()
                )
                if (needsAmount) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it.take(12) },
                        label = { Text(stringResource(R.string.amount_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (challenge.entity.type == ChallengeType.SPEND_LIMIT) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = categoryId == null,
                                onClick = { categoryId = null },
                                label = { Text(stringResource(R.string.filter_all_categories)) }
                            )
                        }
                        items(categories, key = { it.id }) { category ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable { categoryId = category.id }
                                    .padding(4.dp)
                            ) {
                                CategoryBadge(
                                    iconKey = category.iconKey,
                                    color = Color(category.colorArgb),
                                    selected = category.id == categoryId,
                                    size = 36.dp
                                )
                                Text(
                                    text = CategoryVisuals.displayName(category),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
                AssistChip(
                    onClick = { showDatePicker = true },
                    leadingIcon = {
                        Icon(Icons.Rounded.Event, contentDescription = null, Modifier.size(18.dp))
                    },
                    label = {
                        Text(stringResource(R.string.challenge_until, relativeDate(end)))
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && (!needsAmount || amountMinor > 0),
                onClick = {
                    onConfirm(
                        title,
                        if (needsAmount) amountMinor else 0L,
                        if (challenge.entity.type == ChallengeType.SPEND_LIMIT) {
                            categoryId
                        } else {
                            null
                        },
                        end
                    )
                }
            ) { Text(stringResource(R.string.editor_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = end
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            end = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC).toLocalDate()
                        }
                        showDatePicker = false
                    }
                ) { Text(stringResource(R.string.action_apply)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = pickerState, showModeToggle = false)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddChallengeDialog(
    categories: List<CategoryEntity>,
    currencyCode: String,
    onConfirm: (
        type: ChallengeType,
        title: String,
        amountMinor: Long,
        categoryId: Long?,
        end: LocalDate
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var type by remember { mutableStateOf(ChallengeType.SPEND_LIMIT) }
    var title by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var durationIndex by remember { mutableStateOf(0) }
    val amountMinor = Money.parseToMinor(amountText, currencyCode) ?: 0L
    val needsAmount = type != ChallengeType.NO_SPEND

    val today = LocalDate.now()
    val endDate = when (durationIndex) {
        0 -> today.plusDays(6)
        1 -> YearMonth.now().atEndOfMonth()
        else -> today.plusDays(29)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.challenge_new)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ChallengeType.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = type == option,
                            onClick = { type = option },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = ChallengeType.entries.size
                            ),
                            label = {
                                Text(
                                    stringResource(
                                        when (option) {
                                            ChallengeType.SPEND_LIMIT ->
                                                R.string.challenge_type_limit_short
                                            ChallengeType.NO_SPEND ->
                                                R.string.challenge_type_no_spend_short
                                            ChallengeType.SAVE_TARGET ->
                                                R.string.challenge_type_save_short
                                        }
                                    ),
                                    maxLines = 1
                                )
                            }
                        )
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(60) },
                    label = { Text(stringResource(R.string.recurring_title_hint)) },
                    singleLine = true,
                    keyboardOptions = TextInput.Sentence,
                    modifier = Modifier.fillMaxWidth()
                )
                if (needsAmount) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it.take(12) },
                        label = { Text(stringResource(R.string.amount_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (type == ChallengeType.SPEND_LIMIT) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = categoryId == null,
                                onClick = { categoryId = null },
                                label = { Text(stringResource(R.string.filter_all_categories)) }
                            )
                        }
                        items(categories, key = { it.id }) { category ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable { categoryId = category.id }
                                    .padding(4.dp)
                            ) {
                                CategoryBadge(
                                    iconKey = category.iconKey,
                                    color = Color(category.colorArgb),
                                    selected = category.id == categoryId,
                                    size = 36.dp
                                )
                                Text(
                                    text = CategoryVisuals.displayName(category),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(
                        listOf(
                            R.string.challenge_duration_week,
                            R.string.challenge_duration_month_end,
                            R.string.challenge_duration_30
                        ).withIndex().toList(),
                        key = { it.index }
                    ) { (index, labelRes) ->
                        FilterChip(
                            selected = durationIndex == index,
                            onClick = { durationIndex = index },
                            label = { Text(stringResource(labelRes), maxLines = 1) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && (!needsAmount || amountMinor > 0),
                onClick = {
                    onConfirm(
                        type,
                        title,
                        if (needsAmount) amountMinor else 0L,
                        if (type == ChallengeType.SPEND_LIMIT) categoryId else null,
                        endDate
                    )
                }
            ) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

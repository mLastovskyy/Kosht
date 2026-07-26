package by.mlastovsky.kosht.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.WavingHand
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Summarize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import by.mlastovsky.kosht.ui.components.Avatar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import by.mlastovsky.kosht.R
import by.mlastovsky.kosht.model.AppLanguage
import by.mlastovsky.kosht.model.ThemeMode
import by.mlastovsky.kosht.ui.AppViewModelProvider
import java.util.Currency
import java.util.Locale

@Composable
fun SettingsScreen(
    onOpenGuide: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val current = settings ?: return
    var showThemeDialog by remember { mutableStateOf(false) }
    var showCurrencyDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = LocalActivity.current

    val profile by viewModel.profile.collectAsStateWithLifecycle()
    var showProfileDialog by remember { mutableStateOf(false) }
    var showReportPeriod by remember { mutableStateOf(false) }
    var showReportFields by remember { mutableStateOf(false) }
    val defaultName = stringResource(R.string.profile_default_name)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = stringResource(R.string.nav_settings),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
        )

        profile?.let { p ->
            SettingsCard {
            ListItem(
                headlineContent = {
                    Text(
                        text = p.displayName(defaultName),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                supportingContent = {
                    Text(
                        p.name.ifBlank { stringResource(R.string.profile_edit_hint) }
                    )
                },
                leadingContent = {
                    Avatar(
                        photoPath = p.photoPath,
                        fallbackText = p.displayName(defaultName),
                        size = 52.dp
                    )
                },
                colors = transparentListColors(),
                modifier = Modifier.clickable { showProfileDialog = true }
            )
            }
        }

        SectionHeader(stringResource(R.string.settings_appearance))

        SettingsCard {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_theme)) },
            supportingContent = { Text(themeLabel(current.themeMode)) },
            leadingContent = { Icon(Icons.Rounded.BrightnessMedium, contentDescription = null) },
            colors = transparentListColors(),
            modifier = Modifier.clickable { showThemeDialog = true }
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_dynamic_colors)) },
                supportingContent = { Text(stringResource(R.string.settings_dynamic_colors_desc)) },
                leadingContent = { Icon(Icons.Rounded.Palette, contentDescription = null) },
                trailingContent = {
                    Switch(
                        checked = current.dynamicColors,
                        onCheckedChange = viewModel::setDynamicColors
                    )
                },
                colors = transparentListColors(),
                modifier = Modifier.clickable {
                    viewModel.setDynamicColors(!current.dynamicColors)
                }
            )
        }
        }

        SectionHeader(stringResource(R.string.settings_general))

        SettingsCard {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_language)) },
            supportingContent = { Text(languageLabel(language)) },
            leadingContent = { Icon(Icons.Rounded.Language, contentDescription = null) },
            colors = transparentListColors(),
            modifier = Modifier.clickable { showLanguageDialog = true }
        )

        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_currency)) },
            supportingContent = { Text(currencyLabel(current.currencyCode)) },
            leadingContent = { Icon(Icons.Rounded.Payments, contentDescription = null) },
            colors = transparentListColors(),
            modifier = Modifier.clickable { showCurrencyDialog = true }
        )

        var showBudgetDialog by remember { mutableStateOf(false) }
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_daily_budget)) },
            supportingContent = {
                Text(
                    if (current.dailyBudgetMinor > 0) {
                        by.mlastovsky.kosht.util.Money.format(
                            current.dailyBudgetMinor, current.currencyCode
                        )
                    } else {
                        stringResource(R.string.daily_budget_auto)
                    }
                )
            },
            leadingContent = { Icon(Icons.Rounded.Speed, contentDescription = null) },
            colors = transparentListColors(),
            modifier = Modifier.clickable { showBudgetDialog = true }
        )
        if (showBudgetDialog) {
            DailyBudgetDialog(
                currencyCode = current.currencyCode,
                onSet = { minor ->
                    viewModel.setDailyBudget(minor)
                    showBudgetDialog = false
                },
                onDismiss = { showBudgetDialog = false }
            )
        }
        }

        SectionHeader(stringResource(R.string.settings_display))

        SettingsCard {
            NotificationToggle(
                titleRes = R.string.show_greeting,
                descRes = R.string.show_greeting_desc,
                icon = Icons.Rounded.WavingHand,
                checked = current.showGreeting,
                onChange = viewModel::setShowGreeting
            )
            NotificationToggle(
                titleRes = R.string.show_streak,
                descRes = R.string.show_streak_desc,
                icon = Icons.Rounded.LocalFireDepartment,
                checked = current.showStreak,
                onChange = viewModel::setShowStreak
            )
            NotificationToggle(
                titleRes = R.string.show_rates,
                descRes = R.string.show_rates_desc,
                icon = Icons.Rounded.CurrencyExchange,
                checked = current.showRates,
                onChange = viewModel::setShowRates
            )
            NotificationToggle(
                titleRes = R.string.auto_convert,
                descRes = R.string.auto_convert_desc,
                icon = Icons.Rounded.Payments,
                checked = current.convertOnCurrencyChange,
                onChange = viewModel::setConvertOnCurrencyChange
            )
            NotificationToggle(
                titleRes = R.string.auto_calc,
                descRes = R.string.auto_calc_desc,
                icon = Icons.Rounded.Calculate,
                checked = current.autoCalculator,
                onChange = viewModel::setAutoCalculator
            )
        }

        SectionHeader(stringResource(R.string.settings_report))

        SettingsCard {
            val activePeriod = remember(current.reportPeriod) {
                by.mlastovsky.kosht.ui.stats.ReportPeriod.entries
                    .firstOrNull { it.name == current.reportPeriod }
                    ?: by.mlastovsky.kosht.ui.stats.ReportPeriod.MONTH
            }
            val activeFields = remember(current.reportFields) {
                current.reportFields.mapNotNull { name ->
                    by.mlastovsky.kosht.model.ReportField.entries.firstOrNull { it.name == name }
                }.toSet()
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_report_period)) },
                supportingContent = { Text(stringResource(reportPeriodLabel(activePeriod))) },
                leadingContent = { Icon(Icons.Rounded.DateRange, contentDescription = null) },
                colors = transparentListColors(),
                modifier = Modifier.clickable { showReportPeriod = true }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.report_fields_title)) },
                supportingContent = {
                    Text(
                        stringResource(
                            R.string.settings_report_fields_desc,
                            activeFields.size,
                            by.mlastovsky.kosht.model.ReportField.entries.size
                        )
                    )
                },
                leadingContent = { Icon(Icons.Rounded.Tune, contentDescription = null) },
                colors = transparentListColors(),
                modifier = Modifier.clickable { showReportFields = true }
            )

            if (showReportPeriod) {
                ReportPeriodDialog(
                    selected = activePeriod,
                    onConfirm = { period ->
                        viewModel.setReportPeriod(period)
                        showReportPeriod = false
                    },
                    onDismiss = { showReportPeriod = false }
                )
            }
            if (showReportFields) {
                ReportFieldsDialog(
                    selected = activeFields,
                    onConfirm = { fields ->
                        viewModel.setReportFields(fields)
                        showReportFields = false
                    },
                    onDismiss = { showReportFields = false }
                )
            }
        }

        SectionHeader(stringResource(R.string.settings_notifications))

        SettingsCard {
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { }
        val requestPermissionIfNeeded = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        NotificationToggle(
            titleRes = R.string.notif_setting_daily,
            descRes = R.string.notif_setting_daily_desc,
            icon = Icons.Rounded.NotificationsActive,
            checked = current.notifyDailyReminder,
            onChange = { enabled ->
                if (enabled) requestPermissionIfNeeded()
                viewModel.setNotifyDailyReminder(enabled)
            }
        )
        NotificationToggle(
            titleRes = R.string.notif_setting_recurring,
            descRes = R.string.notif_setting_recurring_desc,
            icon = Icons.Rounded.Repeat,
            checked = current.notifyRecurringDue,
            onChange = { enabled ->
                if (enabled) requestPermissionIfNeeded()
                viewModel.setNotifyRecurringDue(enabled)
            }
        )
        NotificationToggle(
            titleRes = R.string.notif_setting_weekly,
            descRes = R.string.notif_setting_weekly_desc,
            icon = Icons.Rounded.Summarize,
            checked = current.notifyWeeklySummary,
            onChange = { enabled ->
                if (enabled) requestPermissionIfNeeded()
                viewModel.setNotifyWeeklySummary(enabled)
            }
        )
        }

        SectionHeader(stringResource(R.string.settings_about))

        SettingsCard {
        ListItem(
            headlineContent = { Text(stringResource(R.string.guide_title)) },
            supportingContent = { Text(stringResource(R.string.guide_settings_entry_desc)) },
            leadingContent = { Icon(Icons.AutoMirrored.Rounded.HelpOutline, contentDescription = null) },
            colors = transparentListColors(),
            modifier = Modifier.clickable(onClick = onOpenGuide)
        )

        val versionName = remember {
            runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull() ?: "—"
        }
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_version)) },
            supportingContent = { Text(versionName) },
            leadingContent = { Icon(Icons.Rounded.Info, contentDescription = null) },
            colors = transparentListColors()
        )
        }

        androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 24.dp))
    }

    if (showThemeDialog) {
        ThemeDialog(
            current = current.themeMode,
            onSelect = { mode ->
                viewModel.setThemeMode(mode)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    if (showCurrencyDialog) {
        CurrencyDialog(
            current = current.currencyCode,
            onSelect = { code ->
                viewModel.setCurrency(code)
                showCurrencyDialog = false
            },
            onDismiss = { showCurrencyDialog = false }
        )
    }

    if (showProfileDialog) {
        profile?.let { p ->
            ProfileDialog(
                initialName = p.name,
                initialNickname = p.nickname,
                photoPath = p.photoPath,
                defaultName = defaultName,
                onPickPhoto = viewModel::setProfilePhoto,
                onPickEmoji = viewModel::setProfileEmoji,
                onRemovePhoto = viewModel::removeProfilePhoto,
                onSave = { name, nickname ->
                    viewModel.saveProfile(name, nickname)
                    showProfileDialog = false
                },
                onDismiss = { showProfileDialog = false }
            )
        }
    }

    if (showLanguageDialog) {
        LanguageDialog(
            current = language,
            onSelect = { lang ->
                showLanguageDialog = false
                if (lang != language) {
                    viewModel.setLanguage(lang)
                    // Re-create the activity so attachBaseContext applies the locale.
                    activity?.recreate()
                }
            },
            onDismiss = { showLanguageDialog = false }
        )
    }
}

@Composable
private fun languageLabel(language: AppLanguage): String = when (language) {
    AppLanguage.SYSTEM -> stringResource(R.string.lang_system)
    AppLanguage.RUSSIAN -> "Русский"
    AppLanguage.ENGLISH -> "English"
}

@Composable
private fun LanguageDialog(
    current: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_language)) },
        text = {
            Column {
                AppLanguage.entries.forEach { lang ->
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = current == lang,
                                onClick = { onSelect(lang) }
                            )
                            .padding(vertical = 10.dp)
                    ) {
                        RadioButton(selected = current == lang, onClick = null)
                        Text(
                            text = languageLabel(lang),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun DailyBudgetDialog(
    currencyCode: String,
    onSet: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    val minor = by.mlastovsky.kosht.util.Money.parseToMinor(amountText, currencyCode) ?: 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_daily_budget)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.daily_budget_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.take(10) },
                    placeholder = { Text(stringResource(R.string.amount_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )
                Text(
                    text = stringResource(R.string.daily_budget_auto),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSet(0L) }
                        .padding(vertical = 12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = minor > 0,
                onClick = { onSet(minor) }
            ) { Text(stringResource(R.string.editor_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

private fun reportPeriodLabel(period: by.mlastovsky.kosht.ui.stats.ReportPeriod): Int =
    when (period) {
        by.mlastovsky.kosht.ui.stats.ReportPeriod.WEEK -> R.string.report_period_week
        by.mlastovsky.kosht.ui.stats.ReportPeriod.MONTH -> R.string.report_period_month
        by.mlastovsky.kosht.ui.stats.ReportPeriod.QUARTER -> R.string.report_period_quarter
        by.mlastovsky.kosht.ui.stats.ReportPeriod.YEAR -> R.string.report_period_year
    }

/** Which window the statistics report covers. */
@Composable
private fun ReportPeriodDialog(
    selected: by.mlastovsky.kosht.ui.stats.ReportPeriod,
    onConfirm: (by.mlastovsky.kosht.ui.stats.ReportPeriod) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_report_period)) },
        text = {
            Column {
                by.mlastovsky.kosht.ui.stats.ReportPeriod.entries.forEach { period ->
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = period == selected,
                                onClick = { onConfirm(period) }
                            )
                            .padding(vertical = 10.dp)
                    ) {
                        RadioButton(
                            selected = period == selected,
                            onClick = { onConfirm(period) }
                        )
                        Text(
                            text = stringResource(reportPeriodLabel(period)),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    )
}

/** Checkbox list of report metric rows; the choice is persisted. */
@Composable
private fun ReportFieldsDialog(
    selected: Set<by.mlastovsky.kosht.model.ReportField>,
    onConfirm: (Set<by.mlastovsky.kosht.model.ReportField>) -> Unit,
    onDismiss: () -> Unit
) {
    var fields by remember { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.report_fields_title)) },
        text = {
            Column {
                by.mlastovsky.kosht.model.ReportField.entries.forEach { field ->
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                fields = if (field in fields) fields - field else fields + field
                            }
                            .padding(vertical = 2.dp)
                    ) {
                        Checkbox(
                            checked = field in fields,
                            onCheckedChange = { checked ->
                                fields = if (checked) fields + field else fields - field
                            }
                        )
                        Text(
                            text = stringResource(
                                when (field) {
                                    by.mlastovsky.kosht.model.ReportField.SPENT ->
                                        R.string.report_spent
                                    by.mlastovsky.kosht.model.ReportField.INCOME ->
                                        R.string.report_income
                                    by.mlastovsky.kosht.model.ReportField.NET ->
                                        R.string.report_net
                                    by.mlastovsky.kosht.model.ReportField.AVG_DAY ->
                                        R.string.report_avg_day
                                    by.mlastovsky.kosht.model.ReportField.FREE_DAYS ->
                                        R.string.report_free_days
                                    by.mlastovsky.kosht.model.ReportField.TOP_CATEGORY ->
                                        R.string.report_top_category
                                }
                            ),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(fields) }) {
                Text(stringResource(R.string.action_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

private val PRESET_AVATARS = listOf("🦊", "🐻", "🐼", "🦁", "🐸", "🚀", "💎", "🌟", "🔥", "🤑")

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ProfileDialog(
    initialName: String,
    initialNickname: String,
    photoPath: String?,
    defaultName: String,
    onPickPhoto: (android.net.Uri) -> Unit,
    onPickEmoji: (String) -> Unit,
    onRemovePhoto: () -> Unit,
    onSave: (name: String, nickname: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var nickname by remember { mutableStateOf(initialNickname) }
    var confirmRemovePhoto by remember { mutableStateOf(false) }
    val photoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) onPickPhoto(uri) }

    if (confirmRemovePhoto) {
        AlertDialog(
            onDismissRequest = { confirmRemovePhoto = false },
            title = { Text(stringResource(R.string.photo_remove)) },
            text = { Text(stringResource(R.string.photo_remove_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemovePhoto()
                        confirmRemovePhoto = false
                    }
                ) {
                    Text(
                        stringResource(R.string.editor_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemovePhoto = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_profile)) },
        text = {
            Column(
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
            ) {
                // No explicit delete button: press and hold removes the
                // photo, the hint below spells that out.
                Avatar(
                    photoPath = photoPath,
                    fallbackText = nickname.ifBlank { name.ifBlank { defaultName } },
                    size = 72.dp,
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            photoLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                        onLongClick = { if (photoPath != null) confirmRemovePhoto = true }
                    )
                )
                Text(
                    text = stringResource(R.string.profile_photo_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement =
                        androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    items(PRESET_AVATARS.size) { index ->
                        val emoji = PRESET_AVATARS[index]
                        Avatar(
                            photoPath = by.mlastovsky.kosht.ui.components
                                .EMOJI_AVATAR_PREFIX + emoji,
                            fallbackText = emoji,
                            size = 44.dp,
                            modifier = Modifier.clickable { onPickEmoji(emoji) }
                        )
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text(stringResource(R.string.profile_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it.take(24) },
                    label = { Text(stringResource(R.string.profile_nickname)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, nickname) }) {
                Text(stringResource(R.string.editor_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun NotificationToggle(
    titleRes: Int,
    descRes: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(stringResource(titleRes)) },
        supportingContent = { Text(stringResource(descRes)) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        colors = transparentListColors(),
        modifier = Modifier.clickable { onChange(!checked) }
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 28.dp, end = 16.dp, top = 12.dp, bottom = 6.dp)
    )
}

/** Rounded grouped card for a block of related settings rows. */
@Composable
private fun SettingsCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer),
        content = content
    )
}

@Composable
private fun transparentListColors() =
    ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)

@Composable
private fun themeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    }
)

private fun currencyLabel(code: String): String {
    val name = runCatching {
        Currency.getInstance(code).getDisplayName(Locale.getDefault())
            .replaceFirstChar { it.titlecase(Locale.getDefault()) }
    }.getOrNull()
    return if (name != null) "$code · $name" else code
}

@Composable
private fun ThemeDialog(
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_theme)) },
        text = {
            Column {
                ThemeMode.entries.forEach { mode ->
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = current == mode,
                                onClick = { onSelect(mode) }
                            )
                            .padding(vertical = 10.dp)
                    ) {
                        RadioButton(selected = current == mode, onClick = null)
                        Text(
                            text = themeLabel(mode),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun CurrencyDialog(
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_currency)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.currency_convert_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                SettingsViewModel.SUPPORTED_CURRENCIES.forEach { code ->
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = current == code,
                                onClick = { onSelect(code) }
                            )
                            .padding(vertical = 10.dp)
                    ) {
                        RadioButton(selected = current == code, onClick = null)
                        Text(
                            text = currencyLabel(code),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

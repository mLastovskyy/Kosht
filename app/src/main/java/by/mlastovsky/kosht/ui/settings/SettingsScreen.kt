package by.mlastovsky.kosht.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PersonAddAlt
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Summarize
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WavingHand
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import by.mlastovsky.kosht.R
import by.mlastovsky.kosht.data.UpdateInstaller
import by.mlastovsky.kosht.data.UpdateStatus
import by.mlastovsky.kosht.data.lock.Biometrics
import by.mlastovsky.kosht.model.AppLanguage
import by.mlastovsky.kosht.model.LockTimeout
import by.mlastovsky.kosht.model.ReportField
import by.mlastovsky.kosht.model.ThemeMode
import by.mlastovsky.kosht.ui.AppViewModelProvider
import by.mlastovsky.kosht.ui.account.AccountViewModel
import by.mlastovsky.kosht.ui.account.AuthDialog
import by.mlastovsky.kosht.ui.account.SyncReport
import by.mlastovsky.kosht.ui.account.fullSyncTime
import by.mlastovsky.kosht.ui.account.lastSyncLabel
import by.mlastovsky.kosht.ui.components.Avatar
import by.mlastovsky.kosht.ui.components.LegalDocs
import by.mlastovsky.kosht.ui.components.TextInput
import by.mlastovsky.kosht.ui.components.rememberDocumentOpener
import by.mlastovsky.kosht.ui.lock.AppLockViewModel
import by.mlastovsky.kosht.ui.lock.PinSetupSheet
import by.mlastovsky.kosht.ui.profile.ProfileDialog
import by.mlastovsky.kosht.ui.stats.ReportPeriod
import by.mlastovsky.kosht.util.Money
import java.util.Currency
import java.util.Locale

@Composable
fun SettingsScreen(
    onOpenGuide: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
    accountViewModel: AccountViewModel =
        viewModel(factory = AppViewModelProvider.Factory)
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
                        Money.format(
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

        SectionHeader(stringResource(R.string.settings_security))

        SettingsCard { SecuritySettings() }

        SectionHeader(stringResource(R.string.settings_display))

        SettingsCard {
            NotificationToggle(
                titleRes = R.string.show_greeting,
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
                icon = Icons.Rounded.Calculate,
                checked = current.autoCalculator,
                onChange = viewModel::setAutoCalculator
            )
            NotificationToggle(
                titleRes = R.string.transfer_fee_setting,
                descRes = R.string.transfer_fee_setting_desc,
                icon = Icons.Rounded.SwapHoriz,
                checked = current.transferFee,
                onChange = viewModel::setTransferFee
            )
        }

        SectionHeader(stringResource(R.string.settings_report))

        SettingsCard {
            val activePeriod = remember(current.reportPeriod) {
                ReportPeriod.entries
                    .firstOrNull { it.name == current.reportPeriod }
                    ?: ReportPeriod.MONTH
            }
            val activeFields = remember(current.reportFields) {
                current.reportFields.mapNotNull { name ->
                    ReportField.entries.firstOrNull { it.name == name }
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
                            ReportField.entries.size
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
        NotificationToggle(
            titleRes = R.string.notif_setting_awards,
            icon = Icons.Rounded.EmojiEvents,
            checked = current.notifyAwards,
            onChange = { enabled ->
                if (enabled) requestPermissionIfNeeded()
                viewModel.setNotifyAwards(enabled)
            }
        )

        if (accountViewModel.isConfigured) MarketingToggle(accountViewModel)
        }

        if (accountViewModel.isConfigured) {
            SectionHeader(stringResource(R.string.account_title))
            SettingsCard { AccountSettings(accountViewModel, current.syncPhotos) }
        }

        SectionHeader(stringResource(R.string.settings_about))

        SettingsCard {
        ListItem(
            headlineContent = { Text(stringResource(R.string.guide_title)) },
            leadingContent = { Icon(Icons.AutoMirrored.Rounded.HelpOutline, contentDescription = null) },
            colors = transparentListColors(),
            modifier = Modifier.clickable(onClick = onOpenGuide)
        )

        ListItem(
            headlineContent = { Text(stringResource(R.string.tour_replay)) },
            supportingContent = { Text(stringResource(R.string.tour_replay_desc)) },
            leadingContent = { Icon(Icons.Rounded.Explore, contentDescription = null) },
            colors = transparentListColors(),
            modifier = Modifier.clickable(onClick = viewModel::replayTour)
        )

        DocumentRow(
            titleRes = R.string.legal_terms,
            icon = Icons.AutoMirrored.Rounded.Article,
            asset = LegalDocs.TERMS_ASSET,
            fileName = LegalDocs.TERMS_FILE
        )
        DocumentRow(
            titleRes = R.string.legal_privacy,
            icon = Icons.Rounded.PrivacyTip,
            asset = LegalDocs.PRIVACY_ASSET,
            fileName = LegalDocs.PRIVACY_FILE
        )
        DocumentRow(
            titleRes = R.string.guide_pdf_title,
            icon = Icons.AutoMirrored.Rounded.MenuBook,
            asset = "manual.pdf",
            fileName = "kosht-manual.pdf"
        )

        val packageInfo = remember {
            runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0)
            }.getOrNull()
        }
        val versionName = packageInfo?.versionName ?: "—"
        val versionCode = remember(packageInfo) {
            packageInfo?.let { PackageInfoCompat.getLongVersionCode(it) }
                ?: 0L
        }
        val updateCheck by viewModel.updateCheck.collectAsStateWithLifecycle()
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_version)) },
            supportingContent = {
                Text(
                    if (updateCheck is UpdateCheckState.Checking) {
                        stringResource(R.string.update_checking)
                    } else {
                        versionName + " · " + stringResource(R.string.update_check_hint)
                    }
                )
            },
            leadingContent = { Icon(Icons.Rounded.Info, contentDescription = null) },
            trailingContent = {
                if (updateCheck is UpdateCheckState.Checking) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            },
            colors = transparentListColors(),
            modifier = Modifier.clickable { viewModel.checkForUpdate(versionCode) }
        )

        val offlineUpdateMessage = stringResource(R.string.update_offline)

        val installPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            (updateCheck as? UpdateCheckState.NeedsInstallPermission)?.let {
                viewModel.installUpdate(it.available)
            }
        }
        when (val state = updateCheck) {
            is UpdateCheckState.Done -> {
                val status = state.status
                if (status is UpdateStatus.Failed) {

                    LaunchedEffect(state) {
                        android.widget.Toast.makeText(
                            context,
                            offlineUpdateMessage,
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        viewModel.dismissUpdateCheck()
                    }
                } else {
                    UpdateResultDialog(
                        available = status as? UpdateStatus.Available,
                        onInstall = viewModel::installUpdate,
                        onDismiss = viewModel::dismissUpdateCheck
                    )
                }
            }

            is UpdateCheckState.Downloading -> UpdateProgressDialog(
                title = stringResource(R.string.update_downloading_title),
                text = stringResource(
                    R.string.update_downloading_text,
                    state.available.versionName
                ),
                percent = state.percent,
                onCancel = viewModel::dismissUpdateCheck
            )

            is UpdateCheckState.Installing -> UpdateProgressDialog(
                title = stringResource(R.string.update_installing_title),
                text = stringResource(
                    R.string.update_installing_text,
                    state.available.versionName
                ),
                percent = UpdateInstaller.UNKNOWN_PROGRESS,
                onCancel = null
            )

            is UpdateCheckState.UpdateFailed -> UpdateMessageDialog(
                title = stringResource(R.string.update_failed_title),
                text = stringResource(R.string.update_failed_text),
                confirmLabel = stringResource(R.string.update_retry),
                onConfirm = { viewModel.installUpdate(state.available) },
                onDismiss = viewModel::dismissUpdateCheck
            )

            UpdateCheckState.SignatureMismatch -> UpdateMessageDialog(
                title = stringResource(R.string.update_signature_title),
                text = stringResource(R.string.update_signature_text),
                confirmLabel = stringResource(R.string.action_close),
                onConfirm = viewModel::dismissUpdateCheck,
                onDismiss = viewModel::dismissUpdateCheck
            )

            is UpdateCheckState.NeedsInstallPermission -> UpdateMessageDialog(
                title = stringResource(R.string.update_permission_title),
                text = stringResource(R.string.update_permission_text),
                confirmLabel = stringResource(R.string.update_permission_open),
                onConfirm = {
                    runCatching {
                        installPermissionLauncher.launch(viewModel.unknownSourcesIntent())
                    }
                },
                onDismiss = viewModel::dismissUpdateCheck
            )

            UpdateCheckState.Idle, UpdateCheckState.Checking -> Unit
        }
        }

        if (accountViewModel.isConfigured) PointOfNoReturn(accountViewModel)

        Spacer(Modifier.padding(bottom = 24.dp))
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

        ProfileDialog(
            onDismiss = { showProfileDialog = false }
        )
    }

    if (showLanguageDialog) {
        LanguageDialog(
            current = language,
            onSelect = { lang ->
                showLanguageDialog = false
                if (lang != language) {
                    viewModel.setLanguage(lang)

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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
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
    val minor = Money.parseToMinor(amountText, currencyCode) ?: 0L

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

@Composable
private fun SecuritySettings(
    viewModel: AppLockViewModel =
        viewModel(factory = AppViewModelProvider.Factory)
) {
    val lock by viewModel.settings.collectAsStateWithLifecycle()
    val setup by viewModel.setup.collectAsStateWithLifecycle()
    val current = lock ?: return
    val context = LocalContext.current
    val activity = LocalActivity.current as? FragmentActivity
    var showTimeout by remember { mutableStateOf(false) }

    val fingerAvailable = remember {
        Biometrics.enrolled(context)
    }
    val promptTitle = stringResource(R.string.lock_prompt_title)
    val promptSubtitle = stringResource(R.string.lock_prompt_subtitle)
    val cancelLabel = stringResource(R.string.action_cancel)

    ListItem(
        headlineContent = { Text(stringResource(R.string.lock_setting)) },
        supportingContent = { Text(stringResource(R.string.lock_setting_desc)) },
        leadingContent = { Icon(Icons.Rounded.Lock, contentDescription = null) },
        trailingContent = {
            Switch(
                checked = current.enabled,
                onCheckedChange = { on ->
                    if (on) viewModel.startCreate() else viewModel.startDisable()
                }
            )
        },
        colors = transparentListColors(),
        modifier = Modifier.clickable {
            if (current.enabled) viewModel.startDisable() else viewModel.startCreate()
        }
    )

    if (current.enabled) {

        ListItem(
            headlineContent = { Text(stringResource(R.string.lock_change)) },
            leadingContent = { Icon(Icons.Rounded.Dialpad, contentDescription = null) },
            colors = transparentListColors(),
            modifier = Modifier.clickable { viewModel.startChange() }
        )

        if (fingerAvailable) {

            val toggleFinger = { enabled: Boolean ->
                if (!enabled || activity == null) {
                    viewModel.setBiometrics(false)
                } else {
                    Biometrics.prompt(
                        activity = activity,
                        title = promptTitle,
                        subtitle = promptSubtitle,
                        negativeButton = cancelLabel,
                        onSuccess = { viewModel.setBiometrics(true) },
                        onError = { message ->
                            message?.let {
                                android.widget.Toast
                                    .makeText(context, it, android.widget.Toast.LENGTH_LONG)
                                    .show()
                            }
                        }
                    )
                }
            }

            ListItem(
                headlineContent = { Text(stringResource(R.string.lock_biometrics)) },
                leadingContent = { Icon(Icons.Rounded.Fingerprint, contentDescription = null) },
                trailingContent = {
                    Switch(checked = current.biometrics, onCheckedChange = toggleFinger)
                },
                colors = transparentListColors(),
                modifier = Modifier.clickable { toggleFinger(!current.biometrics) }
            )
        }
        ListItem(
            headlineContent = { Text(stringResource(R.string.lock_timeout)) },
            supportingContent = { Text(lockTimeoutLabel(current.timeoutMinutes)) },
            leadingContent = { Icon(Icons.Rounded.Timer, contentDescription = null) },
            colors = transparentListColors(),
            modifier = Modifier.clickable { showTimeout = true }
        )
    }

    if (showTimeout) {
        LockTimeoutDialog(
            minutes = current.timeoutMinutes,
            onSet = { chosen ->
                viewModel.setTimeoutMinutes(chosen)
                showTimeout = false
            },
            onDismiss = { showTimeout = false }
        )
    }

    setup?.let { flow ->
        PinSetupSheet(
            setup = flow,
            storedLength = current.pinLength,
            viewModel = viewModel
        )
    }
}

@Composable
private fun lockTimeoutLabel(minutes: Int): String =
    if (minutes <= LockTimeout.AT_ONCE) {
        stringResource(R.string.lock_timeout_immediately)
    } else {
        pluralStringResource(
            R.plurals.lock_timeout_minutes,
            minutes,
            minutes
        )
    }

@Composable
private fun LockTimeoutDialog(
    minutes: Int,
    onSet: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember {
        mutableStateOf(if (minutes > 0) minutes.toString() else "")
    }
    val typed = text.toIntOrNull()
    val valid = typed != null &&
        typed in 1..LockTimeout.MAX_MINUTES

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lock_timeout)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { input ->
                        text = input.filter { it.isDigit() }.take(4)
                    },
                    label = { Text(stringResource(R.string.lock_timeout_field)) },
                    suffix = { Text(stringResource(R.string.lock_timeout_unit)) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(R.string.lock_timeout_immediately),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSet(LockTimeout.AT_ONCE) }
                        .padding(vertical = 12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { typed?.let(onSet) }
            ) { Text(stringResource(R.string.editor_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun AccountSettings(
    viewModel: AccountViewModel,
    photoSync: Boolean
) {
    val context = LocalContext.current
    val account by viewModel.account.collectAsStateWithLifecycle()
    val lastSyncAt by viewModel.lastSyncAt.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()
    val report by viewModel.report.collectAsStateWithLifecycle()
    var showDetails by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    val purgedMessage = stringResource(R.string.account_photos_purged)

    val doneMessage = stringResource(R.string.account_sync_done)
    val offlineMessage = stringResource(R.string.account_sync_offline)
    val receivedTemplate = stringResource(R.string.account_sync_received)
    val failedTemplate = stringResource(R.string.account_sync_failed)
    report?.let { outcome ->
        LaunchedEffect(outcome) {
            val text = when (outcome) {
                is SyncReport.Done ->
                    if (outcome.received > 0) {
                        String.format(receivedTemplate, outcome.received)
                    } else {
                        doneMessage
                    }

                SyncReport.Offline -> offlineMessage
                is SyncReport.Failed ->
                    String.format(failedTemplate, outcome.message)
            }
            android.widget.Toast
                .makeText(context, text, android.widget.Toast.LENGTH_LONG)
                .show()
            viewModel.clearReport()
        }
    }

    fun togglePhotoSync(enabled: Boolean) {
        viewModel.setPhotoSync(enabled) { purged ->
            if (purged) {
                android.widget.Toast
                    .makeText(context, purgedMessage, android.widget.Toast.LENGTH_LONG)
                    .show()
            }
        }
    }

    val current = account
    if (current == null || !current.signedIn) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.account_sign_in)) },
            supportingContent = { Text(stringResource(R.string.account_signed_out)) },
            leadingContent = {
                Icon(Icons.Rounded.CloudOff, contentDescription = null)
            },
            colors = transparentListColors(),
            modifier = Modifier.clickable { viewModel.startSignIn() }
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.account_sign_up)) },
            leadingContent = {
                Icon(Icons.Rounded.PersonAddAlt, contentDescription = null)
            },
            colors = transparentListColors(),
            modifier = Modifier.clickable { viewModel.startSignUp() }
        )
    } else {

        ListItem(
            headlineContent = {
                Text(
                    text = current.email.orEmpty(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = {
                Text(
                    text = if (syncing) {
                        stringResource(R.string.account_syncing)
                    } else {
                        lastSyncLabel(lastSyncAt)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingContent = {
                Icon(
                    imageVector = if (lastSyncAt > 0) {
                        Icons.Rounded.CloudDone
                    } else {
                        Icons.Rounded.CloudQueue
                    },
                    contentDescription = null
                )
            },
            trailingContent = {
                if (syncing) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = viewModel::syncNow) {
                        Icon(
                            imageVector = Icons.Rounded.Sync,
                            contentDescription = stringResource(R.string.account_sync_now),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            colors = transparentListColors(),
            modifier = Modifier.clickable { showDetails = true }
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.account_auto_sync)) },
            supportingContent = { Text(stringResource(R.string.account_auto_sync_hint)) },
            leadingContent = { Icon(Icons.Rounded.CloudSync, contentDescription = null) },
            trailingContent = {
                Switch(checked = current.autoSync, onCheckedChange = viewModel::setAutoSync)
            },
            colors = transparentListColors(),
            modifier = Modifier.clickable { viewModel.setAutoSync(!current.autoSync) }
        )

        ListItem(
            headlineContent = { Text(stringResource(R.string.account_sync_photos)) },
            supportingContent = { Text(stringResource(R.string.account_sync_photos_hint)) },
            leadingContent = { Icon(Icons.Rounded.PhotoLibrary, contentDescription = null) },
            trailingContent = {
                Switch(
                    checked = photoSync,
                    onCheckedChange = { enabled -> togglePhotoSync(enabled) }
                )
            },
            colors = transparentListColors(),
            modifier = Modifier.clickable { togglePhotoSync(!photoSync) }
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.account_sign_out)) },
            leadingContent = {
                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
            },
            colors = transparentListColors(),
            modifier = Modifier.clickable { confirmSignOut = true }
        )
        if (showDetails) {
            SyncDetailsDialog(
                email = current.email.orEmpty(),
                lastSyncAt = lastSyncAt,
                autoSync = current.autoSync,
                syncing = syncing,
                onSyncNow = {
                    showDetails = false
                    viewModel.syncNow()
                },
                onDismiss = { showDetails = false }
            )
        }
        if (confirmSignOut) {

            AlertDialog(
                onDismissRequest = { confirmSignOut = false },
                title = { Text(stringResource(R.string.account_sign_out_title)) },
                text = { Text(stringResource(R.string.account_sign_out_text)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmSignOut = false
                            viewModel.signOut()
                        }
                    ) { Text(stringResource(R.string.account_sign_out)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmSignOut = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }
    }

    AuthDialog(viewModel)
}

private fun toastDeletion(context: android.content.Context, deleted: Boolean) {
    android.widget.Toast.makeText(
        context,
        context.getString(if (deleted) R.string.legal_deleted else R.string.legal_delete_failed),
        android.widget.Toast.LENGTH_LONG
    ).show()
}

@Composable
private fun SyncDetailsDialog(
    email: String,
    lastSyncAt: Long,
    autoSync: Boolean,
    syncing: Boolean,
    onSyncNow: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.CloudDone, contentDescription = null) },
        title = { Text(stringResource(R.string.account_sync)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailLine(stringResource(R.string.account_email), email)
                DetailLine(
                    label = stringResource(R.string.account_last_sync_label),
                    value = fullSyncTime(lastSyncAt)
                )
                DetailLine(
                    label = stringResource(R.string.account_auto_sync),
                    value = stringResource(
                        if (autoSync) R.string.state_on else R.string.state_off
                    )
                )
            }
        },
        confirmButton = {
            TextButton(enabled = !syncing, onClick = onSyncNow) {
                Text(stringResource(R.string.account_sync_now))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun DeleteAccountDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.legal_delete_title)) },
        text = { Text(stringResource(R.string.legal_delete_text)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.legal_delete_confirm),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun MarketingToggle(viewModel: AccountViewModel) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    val marketing by viewModel.marketingConsent.collectAsStateWithLifecycle()
    val signedIn = account?.signedIn == true

    LaunchedEffect(signedIn) {
        if (signedIn) viewModel.loadMarketingConsent()
    }
    if (!signedIn) return

    ListItem(
        headlineContent = { Text(stringResource(R.string.legal_marketing)) },
        leadingContent = { Icon(Icons.Rounded.MarkEmailRead, contentDescription = null) },
        trailingContent = {
            Switch(
                checked = marketing == true,
                onCheckedChange = viewModel::setMarketingConsent
            )
        },
        colors = transparentListColors(),
        modifier = Modifier.clickable { viewModel.setMarketingConsent(marketing != true) }
    )
}

@Composable
private fun DocumentRow(
    titleRes: Int,
    icon: ImageVector,
    asset: String,
    fileName: String
) {
    val openDocument = rememberDocumentOpener()
    ListItem(
        headlineContent = {
            Text(
                text = stringResource(titleRes),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Icon(Icons.Rounded.Download, contentDescription = null) },
        colors = transparentListColors(),
        modifier = Modifier.clickable { openDocument(asset, fileName) }
    )
}

@Composable
private fun PointOfNoReturn(
    accountViewModel: AccountViewModel
) {
    val account by accountViewModel.account.collectAsStateWithLifecycle()
    if (account?.signedIn != true) return
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }

    SectionHeader(stringResource(R.string.settings_danger))
    SettingsCard {
        ListItem(
            headlineContent = {
                Text(
                    text = stringResource(R.string.legal_delete),
                    color = MaterialTheme.colorScheme.error
                )
            },
            supportingContent = { Text(stringResource(R.string.legal_delete_hint)) },
            leadingContent = {
                Icon(
                    imageVector = Icons.Rounded.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            colors = transparentListColors(),
            modifier = Modifier.clickable { confirmDelete = true }
        )
    }

    if (confirmDelete) {
        DeleteAccountDialog(
            onConfirm = {
                confirmDelete = false
                accountViewModel.deleteAccount { ok -> toastDeletion(context, ok) }
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

@Composable
private fun UpdateProgressDialog(
    title: String,
    text: String,
    percent: Int,
    onCancel: (() -> Unit)?
) {
    AlertDialog(

        onDismissRequest = { onCancel?.invoke() },
        title = { Text(title) },
        text = {
            Column {
                Text(text)
                Spacer(Modifier.padding(top = 12.dp))
                if (percent >= 0) {

                    val progress by animateFloatAsState(
                        targetValue = (percent / 100f).coerceIn(0f, 1f),
                        animationSpec = tween(400),
                        label = "updateProgress"
                    )
                    LinearProgressIndicator(
                        progress = { progress },

                        drawStopIndicator = {},
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            if (onCancel != null) {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    )
}

@Composable
private fun UpdateMessageDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    )
}

@Composable
private fun UpdateResultDialog(
    available: UpdateStatus.Available?,
    onInstall: (UpdateStatus.Available) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (available != null) {
                        R.string.update_available_title
                    } else {
                        R.string.update_latest_title
                    }
                )
            )
        },
        text = {
            Text(
                if (available != null) {
                    stringResource(R.string.update_available_text, available.versionName)
                } else {
                    stringResource(R.string.update_latest_text)
                }
            )
        },
        confirmButton = {
            if (available != null) {
                TextButton(onClick = { onInstall(available) }) {
                    Text(stringResource(R.string.update_download))
                }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
            }
        },
        dismissButton = {
            if (available != null) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_later)) }
            }
        }
    )
}

private fun reportPeriodLabel(period: ReportPeriod): Int =
    when (period) {
        ReportPeriod.WEEK -> R.string.report_period_week
        ReportPeriod.MONTH -> R.string.report_period_month
        ReportPeriod.QUARTER -> R.string.report_period_quarter
        ReportPeriod.YEAR -> R.string.report_period_year
    }

@Composable
private fun ReportPeriodDialog(
    selected: ReportPeriod,
    onConfirm: (ReportPeriod) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_report_period)) },
        text = {
            Column {
                ReportPeriod.entries.forEach { period ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
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

@Composable
private fun ReportFieldsDialog(
    selected: Set<ReportField>,
    onConfirm: (Set<ReportField>) -> Unit,
    onDismiss: () -> Unit
) {
    var fields by remember { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.report_fields_title)) },
        text = {
            Column {
                ReportField.entries.forEach { field ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
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
                                    ReportField.SPENT ->
                                        R.string.report_spent
                                    ReportField.INCOME ->
                                        R.string.report_income
                                    ReportField.NET ->
                                        R.string.report_net
                                    ReportField.AVG_DAY ->
                                        R.string.report_avg_day
                                    ReportField.FREE_DAYS ->
                                        R.string.report_free_days
                                    ReportField.TOP_CATEGORY ->
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

@Composable
private fun NotificationToggle(
    titleRes: Int,
    descRes: Int? = null,
    icon: ImageVector,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(stringResource(titleRes)) },
        supportingContent = descRes?.let { { Text(stringResource(it)) } },
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

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
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
    ListItemDefaults.colors(containerColor = Color.Transparent)

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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
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

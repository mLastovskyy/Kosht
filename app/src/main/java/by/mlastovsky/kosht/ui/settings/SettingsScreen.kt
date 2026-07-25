package by.mlastovsky.kosht.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Summarize
import androidx.compose.material3.AlertDialog
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
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
        }

        SectionHeader(stringResource(R.string.settings_appearance))

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

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        SectionHeader(stringResource(R.string.settings_general))

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

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        SectionHeader(stringResource(R.string.settings_notifications))

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

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        SectionHeader(stringResource(R.string.settings_about))

        val versionName = remember {
            runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull() ?: "1.0"
        }
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_version)) },
            supportingContent = { Text(versionName) },
            leadingContent = { Icon(Icons.Rounded.Info, contentDescription = null) },
            colors = transparentListColors()
        )
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

@Composable
private fun ProfileDialog(
    initialName: String,
    initialNickname: String,
    photoPath: String?,
    defaultName: String,
    onPickPhoto: (android.net.Uri) -> Unit,
    onSave: (name: String, nickname: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var nickname by remember { mutableStateOf(initialNickname) }
    val photoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) onPickPhoto(uri) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_profile)) },
        text = {
            Column(
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
            ) {
                Avatar(
                    photoPath = photoPath,
                    fallbackText = nickname.ifBlank { name.ifBlank { defaultName } },
                    size = 72.dp,
                    modifier = Modifier.clickable {
                        photoLauncher.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    }
                )
                Text(
                    text = stringResource(R.string.profile_photo_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
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

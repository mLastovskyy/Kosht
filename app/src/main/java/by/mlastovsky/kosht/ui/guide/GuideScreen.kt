package by.mlastovsky.kosht.ui.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import by.mlastovsky.kosht.R

private data class GuideSection(
    val icon: ImageVector,
    val titleRes: Int,
    val bodyRes: Int
)

private val sections = listOf(
    GuideSection(Icons.Rounded.Home, R.string.guide_home_title, R.string.guide_home_body),
    GuideSection(Icons.Rounded.Add, R.string.guide_editor_title, R.string.guide_editor_body),
    GuideSection(
        Icons.Rounded.DocumentScanner,
        R.string.guide_scan_title,
        R.string.guide_scan_body
    ),
    GuideSection(
        Icons.AutoMirrored.Rounded.ReceiptLong,
        R.string.guide_history_title,
        R.string.guide_history_body
    ),
    GuideSection(
        Icons.Rounded.Calculate,
        R.string.guide_calc_title,
        R.string.guide_calc_body
    ),
    GuideSection(Icons.Rounded.PieChart, R.string.guide_stats_title, R.string.guide_stats_body),
    GuideSection(
        Icons.Rounded.CreditCard,
        R.string.guide_accounts_title,
        R.string.guide_accounts_body
    ),
    GuideSection(
        Icons.Rounded.AccountBalanceWallet,
        R.string.guide_wallet_title,
        R.string.guide_wallet_body
    ),
    GuideSection(Icons.Rounded.Savings, R.string.guide_savings_title, R.string.guide_savings_body),
    GuideSection(
        Icons.Rounded.EmojiEvents,
        R.string.guide_achievements_title,
        R.string.guide_achievements_body
    ),
    GuideSection(
        Icons.Rounded.CloudSync,
        R.string.guide_sync_title,
        R.string.guide_sync_body
    ),
    GuideSection(
        Icons.Rounded.Settings,
        R.string.guide_settings_title,
        R.string.guide_settings_body
    )
)

/** Copies the bundled PDF to cache and opens it in a viewer. */
@Composable
private fun PdfManualCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val errorText = stringResource(R.string.guide_pdf_error)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.guide_pdf_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = stringResource(R.string.guide_pdf_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
            )
        }
        androidx.compose.material3.Button(
            onClick = {
                runCatching {
                    val dir = java.io.File(context.cacheDir, "docs").apply { mkdirs() }
                    val file = java.io.File(dir, "kosht-manual.pdf")
                    context.assets.open("manual.pdf").use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        context.packageName + ".fileprovider",
                        file
                    )
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                        .setDataAndType(uri, "application/pdf")
                        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(intent)
                }.onFailure {
                    android.widget.Toast
                        .makeText(context, errorText, android.widget.Toast.LENGTH_SHORT)
                        .show()
                }
            }
        ) {
            Text(stringResource(R.string.guide_pdf_open))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        TopAppBar(
            title = { Text(stringResource(R.string.guide_title)) },
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
            item {
                Text(
                    text = stringResource(R.string.guide_intro),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            item(key = "pdf") {
                PdfManualCard()
            }
            sections.forEach { section ->
                item(key = section.titleRes) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clip(MaterialTheme.shapes.large)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    section.icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = stringResource(section.titleRes),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                        Text(
                            text = stringResource(section.bodyRes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }
            }
        }
    }
}

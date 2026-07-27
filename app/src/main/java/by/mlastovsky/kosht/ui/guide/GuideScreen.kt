package by.mlastovsky.kosht.ui.guide

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import by.mlastovsky.kosht.R
import by.mlastovsky.kosht.ui.tour.SmallMock
import by.mlastovsky.kosht.ui.tour.ScreenArt
import by.mlastovsky.kosht.ui.tour.TourArt
import by.mlastovsky.kosht.util.PdfDocs

private data class GuideCard(
    val art: TourArt,
    val titleRes: Int,
    val bodyRes: Int,
    val screen: Int? = null
)

private data class GuideGroup(val titleRes: Int, val cards: List<GuideCard>)

private val groups = listOf(
    GuideGroup(
        R.string.guide_group_screens,
        listOf(
            GuideCard(TourArt.HOME, R.string.nav_home, R.string.guide_home_lines, screen = 1),
            GuideCard(
                TourArt.HISTORY,
                R.string.nav_history,
                R.string.guide_history_lines,
                screen = 2
            ),
            GuideCard(TourArt.STATS, R.string.nav_stats, R.string.guide_stats_lines, screen = 3),
            GuideCard(
                TourArt.WALLET,
                R.string.nav_wallet,
                R.string.guide_wallet_lines,
                screen = 4
            ),
            GuideCard(
                TourArt.SETTINGS,
                R.string.nav_settings,
                R.string.guide_settings_lines,
                screen = 5
            )
        )
    ),
    GuideGroup(
        R.string.guide_group_record,
        listOf(
            GuideCard(TourArt.ADD, R.string.guide_add_head, R.string.guide_add_lines),
            GuideCard(TourArt.CALC, R.string.guide_calc_head, R.string.guide_calc_lines),
            GuideCard(TourArt.ITEMS, R.string.guide_items_head, R.string.guide_items_lines),
            GuideCard(TourArt.SCAN, R.string.guide_scan_head, R.string.guide_scan_lines)
        )
    ),
    GuideGroup(
        R.string.guide_group_more,
        listOf(
            GuideCard(TourArt.STREAK, R.string.guide_streak_head, R.string.guide_streak_lines),
            GuideCard(TourArt.LOCK, R.string.guide_lock_head, R.string.guide_lock_lines),
            GuideCard(TourArt.SYNC, R.string.guide_sync_head, R.string.guide_sync_lines),
            GuideCard(TourArt.UPDATE, R.string.guide_update_head, R.string.guide_update_lines)
        )
    )
)

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
            item(key = "pdf") { PdfManualCard() }
            groups.forEach { group ->
                item(key = group.titleRes) {
                    Text(
                        text = stringResource(group.titleRes),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 4.dp)
                    )
                }
                group.cards.forEach { card ->
                    item(key = card.bodyRes) { GuideRow(card) }
                }
            }
        }
    }
}

@Composable
private fun GuideRow(card: GuideCard) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        ScreenArt(card.art, SmallMock)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            card.screen?.let { number ->
                Text(
                    text = stringResource(R.string.guide_screen_of, number),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
            }
            Text(
                text = stringResource(card.titleRes),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(card.bodyRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PdfManualCard() {
    val context = LocalContext.current
    val errorText = stringResource(R.string.guide_pdf_error)
    val savedTemplate = stringResource(R.string.doc_saved)
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
        Button(
            onClick = {
                val message = when (
                    val outcome = PdfDocs
                        .download(context, "manual.pdf", "kosht-manual.pdf")
                ) {
                    is PdfDocs.Outcome.Saved -> String.format(savedTemplate, outcome.fileName)
                    PdfDocs.Outcome.Opened -> null
                    PdfDocs.Outcome.Failed -> errorText
                }
                message?.let {
                    Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                }
            }
        ) {
            Text(stringResource(R.string.guide_pdf_open))
        }
    }
}

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

private data class GuideCard(val art: TourArt, val titleRes: Int, val bodyRes: Int)

private val cards = listOf(
    GuideCard(TourArt.TABS, R.string.tour_tabs_title, R.string.tour_tabs_body),
    GuideCard(TourArt.ADD, R.string.tour_add_title, R.string.tour_add_body),
    GuideCard(TourArt.SCAN, R.string.tour_scan_title, R.string.tour_scan_body),
    GuideCard(TourArt.ITEMS, R.string.guide_items_title, R.string.guide_items_short),
    GuideCard(TourArt.HISTORY, R.string.guide_history_title, R.string.guide_history_short),
    GuideCard(TourArt.STATS, R.string.guide_stats_title, R.string.guide_stats_short),
    GuideCard(TourArt.WALLET, R.string.tour_wallet_title, R.string.tour_wallet_body),
    GuideCard(TourArt.STREAK, R.string.tour_streak_title, R.string.tour_streak_body)
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
            cards.forEach { card ->
                item(key = card.titleRes) { GuideRow(card) }
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
        verticalAlignment = Alignment.CenterVertically
    ) {
        ScreenArt(card.art, SmallMock)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
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

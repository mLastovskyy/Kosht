package by.mlastovsky.kosht.ui.tour

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import by.mlastovsky.kosht.R
import by.mlastovsky.kosht.ui.AppViewModelProvider
import kotlinx.coroutines.launch

private data class TourPage(val art: TourArt, val titleRes: Int, val bodyRes: Int)

private val pages = listOf(
    TourPage(TourArt.TABS, R.string.tour_tabs_title, R.string.tour_tabs_body),
    TourPage(TourArt.ADD, R.string.tour_add_title, R.string.tour_add_body),
    TourPage(TourArt.SCAN, R.string.tour_scan_title, R.string.tour_scan_body),
    TourPage(TourArt.WALLET, R.string.tour_wallet_title, R.string.tour_wallet_body),
    TourPage(TourArt.STREAK, R.string.tour_streak_title, R.string.tour_streak_body),
    TourPage(TourArt.MANUAL, R.string.tour_manual_title, R.string.tour_manual_body)
)

@Composable
fun TourScreen(viewModel: TourViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val last = pagerState.currentPage == pages.lastIndex

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = viewModel::finish) {
                Text(stringResource(R.string.tour_skip))
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { page ->
            Slide(pages[page])
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            pages.indices.forEach { index ->
                val here = index == pagerState.currentPage
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (here) 10.dp else 7.dp)
                        .clip(CircleShape)
                        .background(
                            if (here) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            }
                        )
                )
            }
        }
        Button(
            onClick = {
                if (last) {
                    viewModel.finish()
                } else {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .height(52.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Text(stringResource(if (last) R.string.tour_start else R.string.tour_next))
        }
    }
}

@Composable
private fun Slide(page: TourPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(FullMock.height),
            contentAlignment = Alignment.Center
        ) {
            ScreenArt(page.art)
        }
        Spacer(Modifier.height(28.dp))
        Text(
            text = stringResource(page.titleRes),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(page.bodyRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

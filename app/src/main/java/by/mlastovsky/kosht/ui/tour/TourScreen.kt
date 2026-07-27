package by.mlastovsky.kosht.ui.tour

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import by.mlastovsky.kosht.R
import by.mlastovsky.kosht.ui.AppViewModelProvider
import by.mlastovsky.kosht.ui.navigation.MainTabs
import kotlinx.coroutines.launch

private enum class TourArt { TABS, ADD, SCAN, WALLET, STREAK, MANUAL }

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
                .height(ART_HEIGHT),
            contentAlignment = Alignment.Center
        ) {
            when (page.art) {
                TourArt.TABS -> TabsArt()
                TourArt.ADD -> AddArt()
                TourArt.SCAN -> ScanArt()
                TourArt.WALLET -> WalletArt()
                TourArt.STREAK -> StreakArt()
                TourArt.MANUAL -> ManualArt()
            }
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

@Composable
private fun TabsArt() {
    Phone {
        MainTabs.forEach { tab ->
            MockRow(tab.selectedIcon, stringResource(tab.labelRes))
        }
        Spacer(Modifier.weight(1f))
        MockBar(selected = 0)
    }
}

@Composable
private fun AddArt() {
    Phone {
        Placeholder(0.45f, 9.dp)
        Spacer(Modifier.height(10.dp))
        Block(46.dp)
        Spacer(Modifier.height(10.dp))
        Placeholder(0.9f)
        Spacer(Modifier.height(7.dp))
        Placeholder(0.75f)
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Spotlight {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        MockBar(selected = 0)
    }
}

@Composable
private fun ScanArt() {
    Phone {
        Placeholder(0.4f, 9.dp)
        Spacer(Modifier.height(10.dp))
        Block(46.dp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(4) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            )
            Spotlight(shape = MaterialTheme.shapes.small) {
                Icon(
                    imageVector = Icons.Rounded.DocumentScanner,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        MockBar(selected = 0)
    }
}

@Composable
private fun WalletArt() {
    Phone {
        listOf(
            Icons.Rounded.CreditCard to R.string.settings_accounts,
            Icons.Rounded.Autorenew to R.string.tour_wallet_payments,
            Icons.Rounded.Handshake to R.string.section_debts,
            Icons.Rounded.Savings to R.string.section_savings
        ).forEach { (icon, labelRes) ->
            MockRow(icon, stringResource(labelRes))
        }
        Spacer(Modifier.weight(1f))
        MockBar(selected = 3)
    }
}

@Composable
private fun MockRow(icon: ImageVector, label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            fontSize = MOCK_TEXT,
            lineHeight = MOCK_TEXT,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StreakArt() {
    Phone {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Placeholder(0.4f, 9.dp)
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.LocalFireDepartment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = "7",
                    fontSize = MOCK_TEXT,
                    lineHeight = MOCK_TEXT,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Block(46.dp)
        Spacer(Modifier.height(10.dp))
        Placeholder(0.9f)
        Spacer(Modifier.height(7.dp))
        Placeholder(0.75f)
        Spacer(Modifier.weight(1f))
        MockBar(selected = 0)
    }
}

@Composable
private fun ManualArt() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PathChip(Icons.Rounded.Settings, stringResource(R.string.nav_settings), accent = false)
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(vertical = 6.dp)
                .size(22.dp)
        )
        PathChip(
            Icons.AutoMirrored.Rounded.MenuBook,
            stringResource(R.string.guide_pdf_title),
            accent = true
        )
    }
}

@Composable
private fun PathChip(icon: ImageVector, label: String, accent: Boolean) {
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.large)
            .background(
                if (accent) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (accent) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (accent) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun Phone(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .width(PHONE_WIDTH)
            .height(ART_HEIGHT)
            .clip(PHONE_SHAPE)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, PHONE_SHAPE)
            .padding(12.dp),
        content = content
    )
}

@Composable
private fun Placeholder(fraction: Float, height: Dp = 7.dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth(fraction)
            .height(height)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    )
}

@Composable
private fun Block(height: Dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    )
}

@Composable
private fun Spotlight(
    shape: Shape = CircleShape,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun MockBar(selected: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        MainTabs.forEachIndexed { index, tab ->
            val here = index == selected
            Icon(
                imageVector = if (here) tab.selectedIcon else tab.unselectedIcon,
                contentDescription = null,
                tint = if (here) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(if (here) 17.dp else 15.dp)
            )
        }
    }
}

private val ART_HEIGHT = 248.dp

private val PHONE_WIDTH = 148.dp

private val PHONE_SHAPE = RoundedCornerShape(20.dp)

private val MOCK_TEXT = 9.sp

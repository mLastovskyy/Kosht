package by.mlastovsky.kosht.ui.tour

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ShoppingBasket
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.mlastovsky.kosht.R
import by.mlastovsky.kosht.ui.navigation.MainTabs

internal enum class TourArt { TABS, ADD, SCAN, ITEMS, HISTORY, STATS, WALLET, STREAK, MANUAL }

internal data class MockSize(val width: Dp, val height: Dp)

internal val FullMock = MockSize(148.dp, 248.dp)

internal val SmallMock = MockSize(132.dp, 196.dp)

@Composable
internal fun ScreenArt(art: TourArt, size: MockSize = FullMock) {
    when (art) {
        TourArt.TABS -> TabsArt(size)
        TourArt.ADD -> AddArt(size)
        TourArt.SCAN -> ScanArt(size)
        TourArt.ITEMS -> ItemsArt(size)
        TourArt.HISTORY -> HistoryArt(size)
        TourArt.STATS -> StatsArt(size)
        TourArt.WALLET -> WalletArt(size)
        TourArt.STREAK -> StreakArt(size)
        TourArt.MANUAL -> ManualArt()
    }
}

@Composable
private fun TabsArt(size: MockSize) {
    Phone(size) {
        MainTabs.forEach { tab ->
            MockRow(tab.selectedIcon, stringResource(tab.labelRes))
        }
        Spacer(Modifier.weight(1f))
        MockBar(selected = 0)
    }
}

@Composable
private fun AddArt(size: MockSize) {
    Phone(size) {
        Placeholder(0.45f, 9.dp)
        Spacer(Modifier.height(10.dp))
        Block(42.dp)
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
private fun ScanArt(size: MockSize) {
    Phone(size) {
        Placeholder(0.4f, 9.dp)
        Spacer(Modifier.height(10.dp))
        Block(42.dp)
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
private fun ItemsArt(size: MockSize) {
    Phone(size) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Placeholder(0.35f, 9.dp)
            Spacer(Modifier.weight(1f))
            Spotlight(size = 26.dp, shape = CircleShape) {
                Icon(
                    imageVector = Icons.Rounded.ShoppingBasket,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        repeat(3) { index ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Placeholder(0.5f - index * 0.08f)
                Spacer(Modifier.weight(1f))
                Placeholder(0.22f)
            }
        }
        Spacer(Modifier.weight(1f))
        MockBar(selected = 0)
    }
}

@Composable
private fun HistoryArt(size: MockSize) {
    Phone(size) {
        Placeholder(0.35f, 9.dp)
        Spacer(Modifier.height(10.dp))
        MockRow(Icons.Rounded.CreditCard, "")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(24.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            )
            Box(
                modifier = Modifier
                    .size(width = 30.dp, height = 24.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        MockRow(Icons.Rounded.Savings, "")
        Spacer(Modifier.weight(1f))
        MockBar(selected = 1)
    }
}

@Composable
private fun StatsArt(size: MockSize) {
    Phone(size) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .border(11.dp, MaterialTheme.colorScheme.primary, CircleShape)
            )
        }
        Spacer(Modifier.height(12.dp))
        Bar(0.8f, MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Bar(0.55f, MaterialTheme.colorScheme.tertiary)
        Spacer(Modifier.height(8.dp))
        Bar(0.3f, MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.weight(1f))
        MockBar(selected = 2)
    }
}

@Composable
private fun WalletArt(size: MockSize) {
    Phone(size) {
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
private fun StreakArt(size: MockSize) {
    Phone(size) {
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
        Block(42.dp)
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
private fun Phone(size: MockSize, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .width(size.width)
            .height(size.height)
            .clip(PHONE_SHAPE)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, PHONE_SHAPE)
            .padding(12.dp),
        content = content
    )
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
        if (label.isEmpty()) {
            Placeholder(0.7f)
        } else {
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
private fun Bar(fraction: Float, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(9.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(9.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
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
    size: Dp = 38.dp,
    shape: Shape = CircleShape,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
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

private val PHONE_SHAPE = RoundedCornerShape(20.dp)

private val MOCK_TEXT = 9.sp

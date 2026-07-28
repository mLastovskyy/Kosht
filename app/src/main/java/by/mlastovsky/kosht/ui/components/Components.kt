package by.mlastovsky.kosht.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import by.mlastovsky.kosht.ui.CategoryVisuals
import by.mlastovsky.kosht.ui.settings.SettingsViewModel

@Composable
fun CategoryBadge(
    iconKey: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    selected: Boolean = false,
    iconPath: String? = null
) {
    val picture = rememberBitmapFromPath(iconPath, maxDimension = PICTURE_PIXELS)
    if (picture != null) {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .then(
                    if (selected) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = picture,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }
    val background = if (selected) color else color.copy(alpha = 0.16f)
    val tint = if (selected) Color.White else color
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = CategoryVisuals.icon(iconKey),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size * 0.52f)
        )
    }
}

private const val PICTURE_PIXELS = 256

@Composable
fun CurrencyChips(
    selected: String,
    onSelect: (String) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(SettingsViewModel.SUPPORTED_CURRENCIES) { code ->
            FilterChip(
                selected = code == selected,
                onClick = { onSelect(code) },
                label = { Text(code) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimatedAmountText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = 1,
    reveal: FullTextReveal? = null
) {
    val amount: @Composable () -> Unit = {
        AnimatedContent(
            targetState = text,
            transitionSpec = {
                (slideInVertically { height -> -height / 2 } + fadeIn()) togetherWith
                    (slideOutVertically { height -> height / 2 } + fadeOut())
            },
            label = "amount"
        ) { value ->
            Text(
                text = value,
                style = style,
                color = color,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { layout ->
                    if (value == text) reveal?.truncated = layout.hasVisualOverflow
                },
                modifier = modifier
            )
        }
    }
    if (reveal == null) {
        amount()
    } else {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = { PlainTooltip { Text(text) } },
            state = reveal.tooltipState(),
            enableUserInput = false,
            content = { amount() }
        )
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun AccountBadge(
    iconKey: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    iconPath: String? = null
) {
    val picture = rememberBitmapFromPath(iconPath, maxDimension = PICTURE_PIXELS)
    if (picture != null) {
        Image(
            bitmap = picture,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
        )
        return
    }
    Icon(
        imageVector = CategoryVisuals.icon(iconKey),
        contentDescription = null,
        tint = color,
        modifier = modifier.size(size)
    )
}

package by.mlastovsky.kosht.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ShoppingBasket
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import by.mlastovsky.kosht.R

@Composable
fun ItemsChip(
    count: Int,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val turn by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "items-chip"
    )
    val container = if (expanded) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val ink = if (expanded) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(CircleShape)
            .background(container)
            .clickable(onClick = onClick)
            .padding(start = 7.dp, end = 3.dp, top = 3.dp, bottom = 3.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.ShoppingBasket,
            contentDescription = pluralStringResource(R.plurals.items_count, count, count),
            tint = ink,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = ink,
            modifier = Modifier.padding(start = 4.dp)
        )
        Icon(
            imageVector = Icons.Rounded.ExpandMore,
            contentDescription = null,
            tint = ink,
            modifier = Modifier
                .size(14.dp)
                .rotate(turn)
        )
    }
}

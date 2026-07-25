package by.mlastovsky.kosht.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Prefix marking a built-in emoji avatar instead of a photo file. */
const val EMOJI_AVATAR_PREFIX = "emoji:"

/** Profile photo, built-in emoji avatar or initials in a circle. */
@Composable
fun Avatar(
    photoPath: String?,
    fallbackText: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    val emoji = photoPath?.takeIf { it.startsWith(EMOJI_AVATAR_PREFIX) }
        ?.removePrefix(EMOJI_AVATAR_PREFIX)
    val bitmap = rememberBitmapFromPath(
        photoPath?.takeIf { emoji == null },
        maxDimension = 256
    )
    val density = LocalDensity.current
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        when {
            emoji != null -> Text(
                text = emoji,
                fontSize = with(density) { (size * 0.52f).toSp() }
            )
            bitmap != null -> Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            else -> Text(
                text = fallbackText.trim().take(2).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

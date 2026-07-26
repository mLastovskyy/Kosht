package by.mlastovsky.kosht.ui.awards

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import by.mlastovsky.kosht.R
import by.mlastovsky.kosht.ui.AppViewModelProvider

/**
 * Says so, there and then, when an award is earned.
 *
 * Sits above the whole app rather than on the achievements screen: awards are
 * earned while adding a record or confirming a payment, and being told a week
 * later — the next time that screen happens to be opened — is not being told.
 */
@Composable
fun AwardCelebration(
    viewModel: AwardsViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val key = queue.firstOrNull() ?: return

    AlertDialog(
        onDismissRequest = viewModel::dismissFirst,
        icon = { PulsingBadge(key) },
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.award_unlocked_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(AwardVisuals.titleRes(key)),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Text(
                text = stringResource(AwardVisuals.descRes(key)),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = viewModel::dismissFirst) {
                Text(stringResource(R.string.award_unlocked_confirm))
            }
        }
    )
}

/** The award's own icon, breathing gently so the eye goes to it. */
@Composable
private fun PulsingBadge(key: String) {
    val pulse by rememberInfiniteTransition(label = "award").animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(900), repeatMode = RepeatMode.Reverse),
        label = "scale"
    )
    Box(
        modifier = Modifier
            .size(64.dp)
            .scale(pulse)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = AwardVisuals.icon(key),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(34.dp)
        )
    }
}

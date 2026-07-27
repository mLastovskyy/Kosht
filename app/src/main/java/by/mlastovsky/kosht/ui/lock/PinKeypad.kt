package by.mlastovsky.kosht.ui.lock

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import by.mlastovsky.kosht.R

@Composable
fun PinDots(
    filled: Int,
    total: Int,
    error: Boolean,
    modifier: Modifier = Modifier
) {
    val shake = remember { Animatable(0f) }
    LaunchedEffect(error) {
        if (!error) return@LaunchedEffect
        shake.animateTo(
            targetValue = 0f,
            animationSpec = keyframes {
                durationMillis = 380
                0f at 0
                -14f at 60
                14f at 130
                -9f at 210
                6f at 290
                0f at 380
            }
        )
    }
    Row(
        modifier = modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) {
                placeable.placeRelative(IntOffset(shake.value.dp.roundToPx(), 0))
            }
        },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(total) { index ->
            val on = index < filled
            val color by animateColorAsState(
                targetValue = when {
                    error -> MaterialTheme.colorScheme.error
                    on -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
                },
                animationSpec = tween(150),
                label = "pinDotColor"
            )
            val size by animateDpAsState(
                targetValue = if (on) 15.dp else 11.dp,
                animationSpec = tween(150),
                label = "pinDotSize"
            )
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Composable
fun PinKeypad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    enabled: Boolean = true,
    onBiometric: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf("123", "456", "789").forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { digit ->
                    PinKey(
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                        background = MaterialTheme.colorScheme.surfaceContainer,
                        onClick = { onDigit(digit) }
                    ) {
                        Text(
                            text = digit.toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (onBiometric != null) {
                PinKey(
                    modifier = Modifier.weight(1f),

                    enabled = true,
                    background = MaterialTheme.colorScheme.primaryContainer,
                    onClick = onBiometric
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Fingerprint,
                        contentDescription = stringResource(R.string.lock_biometrics),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            PinKey(
                modifier = Modifier.weight(1f),
                enabled = enabled,
                background = MaterialTheme.colorScheme.surfaceContainer,
                onClick = { onDigit('0') }
            ) {
                Text(
                    text = "0",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
            }
            PinKey(
                modifier = Modifier.weight(1f),
                enabled = enabled,
                background = Color.Transparent,
                onClick = onBackspace
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Backspace,
                    contentDescription = stringResource(R.string.cd_backspace),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PinKey(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    background: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    val presses = remember(enabled) { MutableInteractionSource() }
    Box(
        modifier = modifier
            .height(64.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(if (enabled) background else background.copy(alpha = 0.4f))
            .clickable(
                interactionSource = presses,
                indication = ripple(),
                enabled = enabled
            ) {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

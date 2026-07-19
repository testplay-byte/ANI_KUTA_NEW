package app.anikuta.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.anikuta.ui.theme.AnikutaSprings

/**
 * A Material 3 Expressive card with spring-based press feedback.
 *
 * On press, the card:
 *  - scales from 1.0 → [pressedScale] (default 0.96) using [AnikutaSprings.press]
 *  - morphs corner radius from [cornerRadius] → [pressedCornerRadius] (default
 *    16dp → 20dp) using [AnikutaSprings.effects]
 *
 * This is the shared version of the ExpressiveAnimeCard pattern from
 * HomeScreen. Each screen provides its own [content] layout (cover image,
 * title, metadata, etc.) — the press feedback is shared.
 *
 * Related files:
 *   - Expressive.kt (AnikutaSprings) — the spring specs
 *   - HomeScreen.kt ExpressiveAnimeCard — the original (still used for Home)
 *   - LibraryScreen.kt LibraryCard — should use this
 *   - SearchScreen.kt SearchAnimeCard — should use this
 *   - HistoryScreen.kt ContinueWatchingCard — should use this
 *
 * @param onClick called when the card is tapped
 * @param modifier applied to the Card
 * @param cornerRadius default corner radius (16dp)
 * @param pressedCornerRadius corner radius when pressed (20dp)
 * @param pressedScale scale when pressed (0.96)
 * @param containerColor card background (defaults to surfaceContainerLow)
 * @param elevation default card elevation (2dp)
 * @param content the card's content (ColumnScope so callers can use Arrangement)
 */
@Composable
fun ExpressiveCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    pressedCornerRadius: Dp = 20.dp,
    pressedScale: Float = 0.96f,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    elevation: Dp = 2.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = AnikutaSprings.press,
        label = "expressive_card_scale",
    )

    val cornerRadiusPx by animateFloatAsState(
        targetValue = if (isPressed) pressedCornerRadius.value else cornerRadius.value,
        animationSpec = AnikutaSprings.effects,
        label = "expressive_card_corner",
    )

    Card(
        modifier = modifier.scale(scale),
        shape = RoundedCornerShape(cornerRadiusPx.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = elevation,
            pressedElevation = elevation + 2.dp,
        ),
        interactionSource = interactionSource,
        onClick = onClick,
        content = content,
    )
}

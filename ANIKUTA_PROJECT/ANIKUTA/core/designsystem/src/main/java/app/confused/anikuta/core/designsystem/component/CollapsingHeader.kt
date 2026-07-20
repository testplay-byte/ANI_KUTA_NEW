package app.confused.anikuta.core.designsystem.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.theme.Motion

/**
 * A collapsing header — a title that shrinks when content scrolls.
 *
 * Per `DESIGN_LANGUAGE/` (from the prototype's `CollapsingHeader.kt`):
 * - **Expanded:** 32sp, bold, letterSpacing -0.02sp. When scrollState is at top (≤ 20px).
 * - **Collapsed:** 22sp, bold. When scrolled past 20px.
 * - **Pinned:** Always visible (sits OUTSIDE the scroll Column). Never scrolls away.
 * - **Animation:** animateFloatAsState, tween 300ms, FastOutSlowInEasing.
 * - **Actions slot:** `actions: @Composable RowScope.() -> Unit` for trailing buttons.
 * - **Status bar:** Uses `.statusBarsPadding()` (the header respects the status bar;
 *   content behind it is edge-to-edge).
 *
 * Usage:
 * ```kotlin
 * val scrollState = rememberScrollState()
 * Column {
 *     CollapsingHeader(title = "Library", scrollState = scrollState)
 *     Column(Modifier.verticalScroll(scrollState)) { /* content */ }
 * }
 * ```
 */
@Composable
fun CollapsingHeader(
    title: String,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val collapsed = scrollState.value > 20

    val targetFontSize = if (collapsed) 22f else 32f
    val fontSize by animateFloatAsState(
        targetValue = targetFontSize,
        animationSpec = tween(Motion.DurationStandard, easing = FastOutSlowInEasing),
        label = "headerFontSize",
    )

    val targetPaddingTop = if (collapsed) 4f else 8f
    val paddingTop by animateFloatAsState(
        targetValue = targetPaddingTop,
        animationSpec = tween(Motion.DurationStandard, easing = FastOutSlowInEasing),
        label = "headerPaddingTop",
    )
    val targetPaddingBottom = if (collapsed) 2f else 4f
    val paddingBottom by animateFloatAsState(
        targetValue = targetPaddingBottom,
        animationSpec = tween(Motion.DurationStandard, easing = FastOutSlowInEasing),
        label = "headerPaddingBottom",
    )

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = paddingTop.dp,
                    bottom = paddingBottom.dp,
                )
                .statusBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                fontSize = fontSize.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.02).sp,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
            )
            actions()
        }
    }
}

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
import app.confused.anikuta.core.designsystem.theme.RobotoFamily

/**
 * A collapsing header — a title that shrinks when content scrolls.
 *
 * Per the updated prototype (`PROTOTYPE_REFERENCE/Anime_App/.../CollapsingHeader.kt`):
 * - **Expanded:** 36sp, ExtraBold (800), letterSpacing -0.02sp. When scrollState is at top (≤ 20px).
 * - **Collapsed:** 26sp, ExtraBold (800). When scrolled past 20px.
 * - **Pinned:** Always visible (sits OUTSIDE the scroll Column). Never scrolls away.
 * - **Animation:** animateFloatAsState, tween 300ms, FastOutSlowInEasing.
 * - **Font:** Uses [RobotoFamily] (bundled) so ExtraBold renders on ALL devices.
 * - **Actions slot:** `actions: @Composable RowScope.() -> Unit` for trailing buttons.
 * - **Status bar:** Uses `.statusBarsPadding()`.
 *
 * Usage with Column + verticalScroll:
 * ```kotlin
 * val scrollState = rememberScrollState()
 * Column {
 *     CollapsingHeader(title = "Library", scrollState = scrollState)
 *     Column(Modifier.verticalScroll(scrollState)) { /* content */ }
 * }
 * ```
 *
 * Usage with LazyVerticalGrid / LazyColumn:
 * ```kotlin
 * val gridState = rememberLazyGridState()
 * val collapsed = gridState.firstVisibleItemScrollOffset > 20 || gridState.firstVisibleItemIndex > 0
 * CollapsingHeader(title = "Browse", collapsed = collapsed)
 * LazyVerticalGrid(state = gridState) { /* content */ }
 * ```
 */
@Composable
fun CollapsingHeader(
    title: String,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    CollapsingHeader(
        title = title,
        collapsed = scrollState.value > 20,
        modifier = modifier,
        actions = actions,
    )
}

/**
 * Overload that accepts a [collapsed] boolean directly — for use with Lazy lists/grids
 * that don't share a ScrollState with the header.
 */
@Composable
fun CollapsingHeader(
    title: String,
    collapsed: Boolean,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {

    // Animate the font size smoothly between expanded (36sp) and collapsed (26sp)
    val targetFontSize = if (collapsed) 26f else 36f
    val fontSize by animateFloatAsState(
        targetValue = targetFontSize,
        animationSpec = tween(Motion.DurationStandard, easing = FastOutSlowInEasing),
        label = "headerFontSize",
    )

    // Animate padding — collapse more aggressively so content moves up
    val targetPaddingTop = if (collapsed) 2f else 8f
    val paddingTop by animateFloatAsState(
        targetValue = targetPaddingTop,
        animationSpec = tween(Motion.DurationStandard, easing = FastOutSlowInEasing),
        label = "headerPaddingTop",
    )
    val targetPaddingBottom = if (collapsed) 0f else 4f
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
                fontFamily = RobotoFamily,
                fontSize = fontSize.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.02).sp,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
            )
            actions()
        }
    }
}

package com.testplaybyte.animeapp.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import androidx.compose.foundation.ScrollState

/**
 * CollapsingHeader — a title that shrinks when the content is scrolled.
 *
 * Matches the web prototype behavior:
 * - At scroll top: title is large (displayLarge = 32sp, bold).
 * - When scrolled past 20px: title shrinks to headlineMedium (22sp, bold).
 * - The title ALWAYS stays visible (pinned at top) — it never scrolls away.
 * - Smooth animation between the two states.
 *
 * Usage:
 * ```kotlin
 * val scrollState = rememberScrollState()
 * CollapsingHeader(title = "Anime", scrollState = scrollState)
 * // content below with verticalScroll(scrollState)
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

    // Animate the font size smoothly between expanded (32sp) and collapsed (22sp)
    val targetFontSize = if (collapsed) 22f else 32f
    val fontSize by animateFloatAsState(
        targetValue = targetFontSize,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "headerFontSize",
    )

    // Animate padding
    val targetPaddingTop = if (collapsed) 4f else 8f
    val paddingTop by animateFloatAsState(
        targetValue = targetPaddingTop,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "headerPaddingTop",
    )
    val targetPaddingBottom = if (collapsed) 2f else 4f
    val paddingBottom by animateFloatAsState(
        targetValue = targetPaddingBottom,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
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

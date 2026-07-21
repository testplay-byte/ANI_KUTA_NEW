package app.confused.anikuta.feature.videoresolver

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.theme.RobotoFamily

/**
 * The video resolver bottom sheet — appears after tapping an episode.
 *
 * Per `DESIGN_LANGUAGE/04-screens/video-resolver.md`:
 * - **No drag handle** (design principle #2).
 * - **Partial height** (design principle #3) — doesn't cover the full screen.
 * - **3-tier hierarchy**: Server (expandable) → Audio → Quality.
 *
 * States:
 * - [VideoResolverState.Resolving] — spinner + "Resolving video sources...".
 * - [VideoResolverState.Show] — expandable server accordion with audio/quality.
 * - [VideoResolverState.NoSources] — "No video sources available" + install hint.
 * - [VideoResolverState.Error] — error message + retry button.
 *
 * @param state the current resolver state.
 * @param onDismiss called when the user dismisses the sheet.
 * @param onVideoSelected called when the user picks a video.
 * @param onRetry called when the user taps the retry button in the Error state.
 */
@Composable
fun VideoResolverSheet(
    state: VideoResolverState,
    onDismiss: () -> Unit,
    onVideoSelected: (ResolverVideo) -> Unit,
    onRetry: () -> Unit = {},
) {
    if (state is VideoResolverState.Hidden) return

    // Height: max 70% of the screen height (per user request — "increase by a
    // small amount but not more than 70% of the device's screen height").
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maxHeight = screenHeight * 0.7f

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .padding(16.dp),
        ) {
            // ── Header: "Episode N" + close button ──
            ResolverHeader(state, onDismiss)

            Spacer(modifier = Modifier.height(16.dp))

            // ── Body: state-dependent content ──
            when (state) {
                is VideoResolverState.Resolving -> ResolvingContent()
                is VideoResolverState.NoSources -> NoSourcesContent()
                is VideoResolverState.Error -> ErrorContent(state.message, onRetry)
                is VideoResolverState.Show -> ShowContent(state.servers, onVideoSelected)
                VideoResolverState.Hidden -> {}
            }
        }
    }
}

/** Header row: "Episode N" (or "Resolving...") + close (X) button. */
@Composable
private fun ResolverHeader(state: VideoResolverState, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = when (state) {
                is VideoResolverState.Resolving -> "Resolving\u2026"
                is VideoResolverState.Show -> "Episode ${state.episodeNumber}"
                is VideoResolverState.NoSources -> "Episode ${state.episodeNumber}"
                is VideoResolverState.Error -> "Episode ${state.episodeNumber}"
                VideoResolverState.Hidden -> ""
            },
            fontFamily = RobotoFamily,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(50),
            modifier = Modifier.size(32.dp).clickable(onClick = onDismiss),
        ) {
            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

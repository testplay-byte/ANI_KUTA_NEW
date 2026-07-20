package app.confused.anikuta.feature.videoresolver

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.theme.RobotoFamily

/**
 * Video resolver bottom sheet — appears when the user taps an episode.
 *
 * Per `DESIGN_LANGUAGE/04-screens/video-resolver.md` + OLD_ANIKUTA's VideoPickerSheet:
 * - NO drag handle (design language principle #2).
 * - Partial height (design language principle #3).
 * - 3-tier hierarchy: Server → Audio → Quality.
 * - Loading/resolving state with spinner.
 * - No-sources state (since no extensions are loaded yet).
 *
 * When extensions are loaded (Phase 4B), this will show real server/audio/quality
 * options from the source's hoster list.
 *
 * @param state The current [VideoResolverState].
 * @param onDismiss Called when the user dismisses the sheet.
 * @param onVideoSelected Called when the user picks a video (with the [ResolverVideo]).
 */
@Composable
fun VideoResolverSheet(
    state: VideoResolverState,
    onDismiss: () -> Unit,
    onVideoSelected: (ResolverVideo) -> Unit,
) {
    if (state is VideoResolverState.Hidden) return

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Header row: title + close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when (state) {
                        is VideoResolverState.Resolving -> "Resolving..."
                        is VideoResolverState.Cached -> "Episode ${state.episodeNumber}"
                        is VideoResolverState.Show -> "Episode ${state.episodeNumber}"
                        is VideoResolverState.NoSources -> "Episode ${state.episodeNumber}"
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
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(onClick = onDismiss),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (state) {
                is VideoResolverState.Resolving -> ResolvingContent()
                is VideoResolverState.NoSources -> NoSourcesContent()
                is VideoResolverState.Cached -> {
                    // Cached state — would show cached results + refreshing badge
                    NoSourcesContent() // For now, same as no sources
                }
                is VideoResolverState.Show -> ShowContent(
                    servers = state.servers,
                    onVideoSelected = onVideoSelected,
                )
                VideoResolverState.Hidden -> {}
            }
        }
    }
}

@Composable
private fun ResolvingContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp,
            modifier = Modifier.size(32.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Resolving video sources...",
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NoSourcesContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "No video sources available",
            fontFamily = RobotoFamily,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Install an anime extension from Settings → Extensions to stream episodes.",
            fontFamily = RobotoFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ShowContent(
    servers: List<ResolverServer>,
    onVideoSelected: (ResolverVideo) -> Unit,
) {
    // 3-tier hierarchy: Server → Audio → Quality
    // Each server is an expandable section with audio versions, each with quality options.
    servers.forEach { server ->
        Text(
            text = server.name,
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        server.audioVersions.forEach { audio ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 4.dp, end = 0.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = audio.label,
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(50.dp),
                )
                audio.videos.forEach { video ->
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .clickable { onVideoSelected(video) },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = video.quality,
                                fontFamily = RobotoFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }
        }
    }
}

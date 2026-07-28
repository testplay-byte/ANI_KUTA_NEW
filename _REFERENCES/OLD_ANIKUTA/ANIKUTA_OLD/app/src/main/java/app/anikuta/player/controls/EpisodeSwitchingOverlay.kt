package app.anikuta.player.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * Episode switching overlay — shown over the video area while the player
 * resolves and loads a new episode.
 *
 * Design:
 *  - Background: the new episode's thumbnail (if available) with a dark
 *    gradient scrim for readability. Falls back to a themed dark surface.
 *  - Center: a CircularProgressIndicator + "Loading episode..." text.
 *  - No video controls are shown — the user can't interact with the video
 *    during the switch.
 *
 * This overlay replaces [MinimizedControls] while [PlayerViewModel.isSwitchingEpisode]
 * is true. When the video finishes loading (MPV_EVENT_FILE_LOADED), the
 * overlay is dismissed and [MinimizedControls] returns.
 *
 * @param episodeThumbnailUrl URL of the new episode's thumbnail (nullable)
 * @param episodeTitle Title of the new episode (nullable, shown under spinner)
 */
@Composable
fun EpisodeSwitchingOverlay(
    episodeThumbnailUrl: String?,
    episodeTitle: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // Episode thumbnail as background (if available)
        if (!episodeThumbnailUrl.isNullOrBlank()) {
            AsyncImage(
                model = episodeThumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            // Dark gradient scrim over the thumbnail for readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.7f),
                            0.5f to Color.Black.copy(alpha = 0.85f),
                            1f to Color.Black.copy(alpha = 0.7f),
                        ),
                    ),
            )
        }

        // Loading indicator + text (centered)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 3.dp,
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Loading episode...",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
            if (!episodeTitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = episodeTitle,
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

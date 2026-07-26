package app.anikuta.ui.detail.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.anikuta.data.anilist.model.AniListNextAiring

/**
 * A pill that shows the next episode's airing time.
 *
 * Click to toggle between two display modes:
 *
 * 1. **Text mode** (default): "Ep 1016 in 2d 5h" — static, concise.
 * 2. **Countdown mode**: "2d 05:23:45" — live-updating every second.
 *
 * The countdown mode uses a [LaunchedEffect] that updates `currentTime`
 * every second, causing the composable to recompose and display the
 * updated remaining time.
 *
 * @param airing The next airing info from AniList.
 */
@Composable
internal fun AiringPill(airing: AniListNextAiring) {
    var showCountdown by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        onClick = { showCountdown = !showCountdown },
        modifier = Modifier.padding(vertical = 2.dp),
    ) {
        if (showCountdown) {
            // Live countdown mode — updates every second
            var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(Unit) {
                while (true) {
                    currentTime = System.currentTimeMillis()
                    kotlinx.coroutines.delay(1000)
                }
            }
            val remainingMs = (airing.airingAt?.toLong() ?: 0L) * 1000 - currentTime
            val text = if (remainingMs > 0) {
                val days = remainingMs / 86400000
                val hours = (remainingMs % 86400000) / 3600000
                val mins = (remainingMs % 3600000) / 60000
                val secs = (remainingMs % 60000) / 1000
                val timePart = "${String.format("%02d", hours)}:${String.format("%02d", mins)}:${String.format("%02d", secs)}"
                if (days > 0) "Ep ${airing.episode} in ${days}d $timePart"
                else "Ep ${airing.episode} in $timePart"
            } else {
                "Ep ${airing.episode} airing now!"
            }
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        } else {
            // Text mode — static "Ep N in Xd Yh"
            val text = "Ep ${airing.episode} in ${formatTimeRemaining(airing.timeUntilAiring ?: 0)}"
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

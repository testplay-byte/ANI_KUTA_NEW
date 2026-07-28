package app.confused.anikuta.feature.animedetails

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.common.model.details.UnifiedAnime
import app.confused.anikuta.core.common.model.details.UnifiedStatus
import app.confused.anikuta.core.designsystem.theme.RobotoFamily

/**
 * Horizontal scrollable row of genre chips.
 *
 * Hidden if [UnifiedAnime.genres] is empty (doc 04 Table 3 row 8).
 */
@Composable
fun GenresRow(anime: UnifiedAnime) {
    if (anime.genres.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        anime.genres.forEach { genre ->
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                shape = RoundedCornerShape(50),
            ) {
                Text(
                    text = genre,
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/**
 * Collapsible synopsis section with "Show more / Show less" toggle.
 *
 * Hidden if [UnifiedAnime.description] is null/blank (doc 04 Table 3 row 9).
 * The provider already normalized HTML → plain text, so we just strip any
 * leftover tags defensively.
 */
@Composable
fun SynopsisSection(description: String) {
    var expanded by remember { mutableStateOf(false) }
    val cleanDesc = description.replace(Regex("<[^>]*>"), "")
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = "Synopsis",
            fontFamily = RobotoFamily,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = cleanDesc,
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (cleanDesc.length > 100) {
            Text(
                text = if (expanded) "Show less" else "Show more",
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable { expanded = !expanded },
            )
        }
    }
}

/**
 * Key/value information section — conditional per doc 04 Table 3 rows 13–15.
 *
 * AniList-mode rows (hidden in pure-extension mode): Format, Season, Score,
 * Studio, Aired, Source. Extension-mode bonus row: Author/Artist.
 *
 * Status + Episodes are shown in both modes when available.
 */
@Composable
fun InfoSection(anime: UnifiedAnime) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = "Information",
            fontFamily = RobotoFamily,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Format (AniList-only — null in extension mode)
        anime.format?.let { InfoRow("Format", it) }

        // Status (both sources — UnifiedStatus)
        statusLabel(anime.status)?.let { InfoRow("Status", it) }

        // Season (AniList-only)
        if (anime.season != null && anime.seasonYear != null) {
            InfoRow("Season", "${anime.season!!.lowercase().replaceFirstChar { it.uppercase() }} ${anime.seasonYear}")
        } else if (anime.seasonYear != null) {
            InfoRow("Year", anime.seasonYear.toString())
        }

        // Episodes (both — AniList total or extension fetched count)
        anime.episodeCount?.let { InfoRow("Episodes", it.toString()) }

        // Score (AniList-only)
        anime.averageScore?.let { InfoRow("Score", "$it / 100") }

        // Studio (AniList-only)
        if (anime.studios.isNotEmpty()) {
            InfoRow("Studio", anime.studios.joinToString(", "))
        }

        // Aired (AniList-only)
        anime.startDate?.let { InfoRow("Aired", it) }

        // Source (AniList-only — original work source)
        anime.source?.let { InfoRow("Source", it.lowercase().replaceFirstChar { it.uppercase() }) }

        // Author / Artist (extension-only bonus)
        anime.author?.let { InfoRow("Author", it) }
        anime.artist?.let { InfoRow("Artist", it) }

        // Data source indicator (which provider produced this view)
        InfoRow("Data", anime.sourceName)
    }
}

/** Display label for [UnifiedStatus] — null for UNKNOWN (the row is hidden). */
private fun statusLabel(status: UnifiedStatus): String? = when (status) {
    UnifiedStatus.FINISHED -> "Finished"
    UnifiedStatus.RELEASING -> "Releasing"
    UnifiedStatus.NOT_YET_RELEASED -> "Not yet released"
    UnifiedStatus.CANCELLED -> "Cancelled"
    UnifiedStatus.HIATUS -> "On hiatus"
    UnifiedStatus.UNKNOWN -> null
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp,
            modifier = Modifier.height(32.dp),
        )
    }
}

@Composable
fun ErrorState(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Couldn't load anime",
            fontFamily = RobotoFamily,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

package app.confused.anikuta.feature.search.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.anilist.model.AniListAnime
import app.confused.anikuta.core.anilist.model.coverUrl
import app.confused.anikuta.core.anilist.model.displayTitle
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import app.confused.anikuta.feature.search.viewmodel.SearchResult
import coil3.compose.AsyncImage
import eu.kanade.tachiyomi.animesource.model.SAnime

/**
 * One result tile — renders an AniList OR an extension result in the same shape.
 *
 * Ported from the prototype's `AnimeCard`. Visual rules:
 * - Cover: 2:3 aspect, `RoundedCornerShape(12.dp)`, `surfaceVariant` placeholder bg.
 * - Title: 12sp SemiBold, 1-2 lines, ellipsize.
 * - Meta row: format · episodes/year (AniList) OR source name (extension).
 *
 * For AniList results: shows a score badge (top-end, `primary` bg) when
 * `averageScore != null`.
 *
 * For extension results: shows the source name under the title (so the user
 * knows which extension the result came from). No score badge (extensions
 * don't provide scores).
 *
 * @param result the unified result (AniList or Extension).
 * @param onClick tapped — caller routes to detail (AniList ID) or linking flow.
 */
@Composable
fun ResultAnimeCard(
    result: SearchResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clickable { onClick() }
            .padding(horizontal = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = result.coverUrl(),
                contentDescription = result.displayTitle(),
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Crop,
            )
            // Score badge (AniList only — extensions don't provide scores).
            // Dark translucent pill with a lime star + white score.
            // Size-matched to the library badge style: tight lineHeight, 9sp font,
            // 6dp corners, 4dp outer offset. Per user: "the size of the badges
            // needs to be adjusted... just like how it is now on the library page."
            val score = result.scoreBadge()
            if (score != null) {
                Surface(
                    color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "★",
                            fontSize = 9.sp,
                            lineHeight = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = RobotoFamily,
                        )
                        Spacer(Modifier.padding(horizontal = 2.dp))
                        Text(
                            text = score,
                            fontSize = 9.sp,
                            lineHeight = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = androidx.compose.ui.graphics.Color.White,
                            fontFamily = RobotoFamily,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = result.displayTitle(),
            fontFamily = RobotoFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp,
        )
        val meta = result.metaLine()
        if (meta.isNotEmpty()) {
            Text(
                text = meta,
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Helpers to read display fields from the sealed SearchResult ─────────────

private fun SearchResult.coverUrl(): String? = when (this) {
    is SearchResult.AniList -> anime.coverUrl
    is SearchResult.Extension -> sAnime.thumbnail_url
}

private fun SearchResult.displayTitle(): String = when (this) {
    is SearchResult.AniList -> anime.displayTitle
    is SearchResult.Extension -> sAnime.title
}

private fun SearchResult.scoreBadge(): String? = when (this) {
    is SearchResult.AniList -> anime.averageScore?.let { "%.1f".format(it / 10.0) }
    is SearchResult.Extension -> null
}

private fun SearchResult.metaLine(): String = when (this) {
    is SearchResult.AniList -> {
        val parts = mutableListOf<String>()
        anime.format?.let { parts.add(it) }
        anime.episodes?.let { parts.add("$it ep") } ?: anime.seasonYear?.let { parts.add(it.toString()) }
        parts.joinToString(" · ")
    }
    // Extension results: no meta line (per owner request — don't show the
    // extension name below the cover; the active source is shown in the toggle).
    is SearchResult.Extension -> ""
}

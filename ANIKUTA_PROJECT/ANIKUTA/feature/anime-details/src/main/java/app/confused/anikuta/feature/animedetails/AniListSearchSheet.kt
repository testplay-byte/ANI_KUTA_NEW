package app.confused.anikuta.feature.animedetails

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.anilist.api.AniListApi
import app.confused.anikuta.core.anilist.model.AniListAnime
import app.confused.anikuta.core.anilist.model.coverUrl
import app.confused.anikuta.core.anilist.model.displayTitle
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import app.confused.anikuta.core.designsystem.component.AnikutaBottomSheet
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A bottom sheet for searching AniList and picking the correct anime.
 *
 * Used by the three-dot menu's "Switch anime" / "Wrong anime?" option in
 * **AniList mode** — when the user is viewing an AniList anime that's the
 * wrong one (e.g., wrong season, wrong adaptation) and wants to navigate to
 * the correct entry.
 *
 * (Extension mode's "Switch anime" / "Link to AniList" uses the existing
 * [app.confused.anikuta.feature.search.ui.ExtensionLinkingSheet] via
 * `AppController.startLinking` — that flow re-links the extension anime to
 * a different AniList entry. This sheet is only for AniList-mode, where
 * there's no extension to re-link; it just navigates to the picked anime.)
 *
 * Per design language: `dragHandle = null`, partial height, RobotoFamily.
 *
 * @param anilistApi the AniList API client (for the search query).
 * @param initialQuery pre-filled search text (usually the current anime's title).
 * @param onPicked called when the user picks a result — the callback receives
 *   the picked anime's AniList ID. The caller navigates to the new details page.
 * @param onDismiss called when the sheet is dismissed (back, scrim tap, or pick).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AniListSearchSheet(
    anilistApi: AniListApi,
    initialQuery: String,
    onPicked: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf(initialQuery) }
    var results by remember { mutableStateOf<List<AniListAnime>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }

    // Debounced search — re-runs when the query changes.
    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = emptyList()
            hasSearched = false
            return@LaunchedEffect
        }
        isLoading = true
        hasSearched = true
        try {
            results = withContext(Dispatchers.IO) { anilistApi.searchAnime(query, perPage = 20) }
        } catch (e: Exception) {
            results = emptyList()
        } finally {
            isLoading = false
        }
    }

    AnikutaBottomSheet(
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            // ── Header ──
            Text(
                text = "Switch anime",
                fontFamily = RobotoFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Text(
                text = "Search AniList for the correct anime",
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            // ── Search field ──
            app.confused.anikuta.core.designsystem.component.SearchField(
                query = query,
                onQueryChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── Results ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp, max = 480.dp),
            ) {
                when {
                    isLoading -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                    !hasSearched || query.isBlank() -> {
                        Text(
                            text = "Type to search AniList",
                            fontFamily = RobotoFamily,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                    results.isEmpty() -> {
                        Text(
                            text = "No results for \"$query\"",
                            fontFamily = RobotoFamily,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                    else -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(results) { anime ->
                                AniListSearchResultRow(
                                    anime = anime,
                                    onClick = {
                                        onPicked(anime.id)
                                        onDismiss()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AniListSearchResultRow(
    anime: AniListAnime,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Cover thumbnail
            if (anime.coverUrl != null) {
                AsyncImage(
                    model = anime.coverUrl,
                    contentDescription = anime.displayTitle,
                    modifier = Modifier
                        .size(width = 56.dp, height = 80.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.size(width = 56.dp, height = 80.dp),
                ) {}
            }
            // Title + score
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = anime.displayTitle,
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                anime.averageScore?.let { score ->
                    Text(
                        text = "\u2605 $score%",
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                anime.seasonYear?.let { year ->
                    Text(
                        text = year.toString(),
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

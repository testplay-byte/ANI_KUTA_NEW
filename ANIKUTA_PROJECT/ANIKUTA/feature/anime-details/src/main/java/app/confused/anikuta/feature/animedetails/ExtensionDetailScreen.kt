package app.confused.anikuta.feature.animedetails

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import app.confused.anikuta.core.common.repository.AnimeRepository
import app.confused.anikuta.core.common.repository.CategoryRepository
import app.confused.anikuta.core.common.repository.EpisodeRepository
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import app.confused.anikuta.data.extension.matcher.SourceMatcher
import coil3.compose.AsyncImage
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode

/**
 * Extension-only details page — for anime not on AniList.
 *
 * Uses the EXACT SAME UI layout as the normal [AnimeDetailScreen] / [DetailContent]:
 * - [ExtensionDetailBanner] (same as [DetailBanner] — blurred cover, gradient,
 *   action buttons, cover thumbnail + title at bottom)
 * - Genres row (from SAnime.genre)
 * - [SynopsisSection] (same composable — 2 lines + Show more)
 * - [EpisodesSection] (same composable — full two-section episode list)
 *
 * The ONLY differences from the normal details page:
 * - Data comes from SAnime (not AniListAnime)
 * - No AniList score / status / next airing episode (these fields are null)
 * - An extra "A" (AniList link) button next to the save button — lets the user
 *   re-link to AniList if they find the anime's AniList ID later.
 * - No metadata enrichment (episode metadata requires AniList ID)
 *
 * Per user: "Nothing should change in it. Everything should look perfect and
 * exactly the same as the normal details page but the things which are not
 * available will not show."
 */
@Composable
fun ExtensionDetailScreen(
    source: AnimeCatalogueSource,
    sAnime: SAnime,
    onBack: () -> Unit,
    onOpenEpisode: (SEpisode, AnimeSource, List<SEpisode>) -> Unit,
    onRelinkAnilist: () -> Unit = {},
) {
    val context = LocalContext.current

    val animeRepository: AnimeRepository = remember { org.koin.core.context.GlobalContext.get().get() }
    val categoryRepository: CategoryRepository = remember { org.koin.core.context.GlobalContext.get().get() }
    val episodeRepository: EpisodeRepository = remember { org.koin.core.context.GlobalContext.get().get() }

    @Suppress("UNCHECKED_CAST")
    val vm: ExtensionDetailViewModel = viewModel(
        key = "ext_detail_${source.id}_${sAnime.url}",
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ExtensionDetailViewModel(
                    source = source,
                    sAnime = sAnime,
                    animeRepository = animeRepository,
                    categoryRepository = categoryRepository,
                    episodeRepository = episodeRepository,
                    appContext = context.applicationContext,
                ) as T
        },
    )

    val animeState by vm.animeState.collectAsState()
    val episodeState by vm.episodeState.collectAsState()
    val isSaved by vm.isSaved.collectAsState()
    val isRefreshing by vm.isRefreshing.collectAsState()
    val currentMatch by vm.currentMatch.collectAsState()
    val watchedEpisodes by vm.watchedEpisodes.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when (val state = animeState) {
            is ExtensionDetailState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            is ExtensionDetailState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                }
            }
            is ExtensionDetailState.Success -> {
                ExtensionDetailContent(
                    anime = state.anime,
                    episodeState = episodeState,
                    currentMatch = currentMatch,
                    watchedEpisodes = watchedEpisodes,
                    isRefreshing = isRefreshing,
                    saved = isSaved,
                    onBack = onBack,
                    onToggleSave = { vm.toggleSave() },
                    onRelinkAnilist = onRelinkAnilist,
                    onOpenEpisode = onOpenEpisode,
                    onRefresh = { vm.refresh() },
                )
            }
        }
    }
}

/**
 * The main scrollable content — mirrors [DetailContent] exactly but uses
 * [ExtensionAnime] instead of [AniListAnime].
 */
@Composable
private fun ExtensionDetailContent(
    anime: ExtensionAnime,
    episodeState: EpisodeState,
    currentMatch: SourceMatcher.SourceMatch?,
    watchedEpisodes: Set<String>,
    isRefreshing: Boolean,
    saved: Boolean,
    onBack: () -> Unit,
    onToggleSave: () -> Unit,
    onRelinkAnilist: () -> Unit,
    onOpenEpisode: (SEpisode, AnimeSource, List<SEpisode>) -> Unit,
    onRefresh: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        // ── Banner (exact same layout as DetailBanner) ──
        item {
            ExtensionDetailBanner(
                anime = anime,
                saved = saved,
                onBack = onBack,
                onToggleSave = onToggleSave,
                onRelinkAnilist = onRelinkAnilist,
            )
        }

        // ── Genres ──
        if (anime.genre.isNotEmpty()) {
            item { ExtensionGenresRow(anime.genre) }
        }

        // ── Synopsis ── (reuse the same SynopsisSection composable)
        if (!anime.description.isNullOrBlank()) {
            item { SynopsisSection(anime.description!!) }
        }

        // ── Episodes ── (reuse the same EpisodesSection composable)
        item {
            EpisodesSection(
                episodeState = episodeState,
                currentMatch = currentMatch,
                allMatches = listOfNotNull(currentMatch),
                watchedEpisodes = watchedEpisodes,
                episodeMetadata = emptyMap(), // No metadata for extension-only anime
                isSearching = false,
                manualSearchResults = emptyList(),
                manualSearchErrors = emptyList(),
                autoMatchErrors = null,
                hasSearched = false,
                availableSources = emptyList(),
                initialSearchQuery = anime.title,
                onOpenEpisode = { episode, src, episodes ->
                    onOpenEpisode(episode, src, episodes)
                },
                onToggleWatched = {},
                onSwitchSource = {},
                onManualSearch = { _, _ -> },
                onLinkManual = { _, _ -> },
                onClearManualSearch = {},
                showMetadataLoading = false, // Extension-only anime has no metadata
            )
        }
    }
}

/**
 * Banner for the extension-only details page — EXACT copy of [DetailBanner]
 * but uses [ExtensionAnime] instead of [AniListAnime].
 *
 * Same layout: 360dp blurred cover + gradient overlay + action buttons at top +
 * cover thumbnail + title + meta at the bottom.
 *
 * Extra: an "A" (AniList link) button next to the save button.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExtensionDetailBanner(
    anime: ExtensionAnime,
    saved: Boolean,
    onBack: () -> Unit,
    onToggleSave: () -> Unit,
    onRelinkAnilist: () -> Unit,
) {
    val coverColor = MaterialTheme.colorScheme.surfaceVariant // fallback color (no AniList cover color)

    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp),
        ) {
            if (!anime.coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = anime.coverUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().blur(8.dp),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
            }
            Box(
                modifier = Modifier.fillMaxSize().background(coverColor.copy(alpha = 0.2f)),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.2f),
                                Color.Transparent,
                                MaterialTheme.colorScheme.background,
                            ),
                        ),
                    ),
            )
        }

        // ── Action buttons row (same as DetailBanner) ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionButton(icon = Icons.Filled.ArrowBack, contentDescription = "Back", onClick = onBack)
            Row {
                // Save button (same as normal)
                ActionButton(
                    icon = if (saved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    contentDescription = if (saved) "Remove from library" else "Add to library",
                    onClick = onToggleSave,
                )
                // "A" button — relink to AniList
                ActionButton(
                    icon = Icons.Filled.MoreHoriz,
                    contentDescription = "Link to AniList",
                    onClick = onRelinkAnilist,
                    text = "A",
                )
            }
        }

        // ── Cover thumbnail + title (same as DetailBanner) ──
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!anime.coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = anime.coverUrl,
                    contentDescription = anime.title,
                    modifier = Modifier
                        .size(width = 100.dp, height = 150.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = anime.title,
                    fontFamily = RobotoFamily,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(6.dp))
                // Meta info (only what's available from the extension)
                val metaParts = buildList {
                    // Status (0=unknown, 1=ongoing, 2=completed)
                    when (anime.status) {
                        1 -> add("ongoing")
                        2 -> add("completed")
                    }
                    add(anime.sourceName)
                }
                if (metaParts.isNotEmpty()) {
                    Text(
                        text = metaParts.joinToString(" \u00b7 "),
                        fontFamily = RobotoFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Genres row — same style as GenresRow but takes a List<String>. */
@Composable
private fun ExtensionGenresRow(genres: List<String>) {
    androidx.compose.foundation.lazy.LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(genres.size) { index ->
            val genre = genres[index]
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(50),
            ) {
                Text(
                    text = genre,
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** Action button — same as in DetailBanner. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    text: String? = null,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.3f))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        if (text != null) {
            Text(
                text = text,
                fontFamily = RobotoFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.items(count: Int, itemContent: @Composable (Int) -> Unit) {
    items(count, key = { it }, itemContent = { itemContent(it) })
}

package app.confused.anikuta.feature.animedetails

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import app.confused.anikuta.core.anilist.api.AniListApi
import app.confused.anikuta.data.extension.AnimeExtensionManager
import app.confused.anikuta.data.extension.matcher.SourceMatcher
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.SEpisode

/**
 * The anime detail screen — shows AniList metadata + real episodes from a
 * matched extension source.
 *
 * Three-stage load (per design spec §6.1):
 * 1. AniList → anime metadata (title, cover, description, score).
 * 2. Extension source match → [SourceMatcher] searches trusted sources.
 * 3. Episode list → `source.getEpisodeList(sAnime)` on the matched source.
 *
 * The screen creates an [AnimeDetailViewModel] scoped to `animeId` (survives
 * configuration changes) and observes its state flows.
 *
 * @param animeId the AniList anime ID.
 * @param api the AniList API client.
 * @param extensionManager provides installed + trusted sources.
 * @param sourceMatcher searches sources by title.
 * @param onBack called when the user navigates back.
 * @param onOpenEpisode called when an episode is tapped (episode + source).
 */
@Composable
fun AnimeDetailScreen(
    animeId: Int,
    api: AniListApi,
    extensionManager: AnimeExtensionManager,
    sourceMatcher: SourceMatcher,
    onBack: () -> Unit,
    onOpenEpisode: (SEpisode, AnimeSource) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current

    @Suppress("UNCHECKED_CAST")
    val vm: AnimeDetailViewModel = viewModel(
        key = "detail_$animeId",
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AnimeDetailViewModel(
                    anilistId = animeId,
                    api = api,
                    extensionManager = extensionManager,
                    sourceMatcher = sourceMatcher,
                    appContext = context.applicationContext,
                ) as T
        },
    )

    val animeState by vm.animeState.collectAsState()
    val episodeState by vm.episodeState.collectAsState()
    val currentMatch by vm.currentMatch.collectAsState()
    val allMatches by vm.allMatches.collectAsState()
    val watchedEpisodes by vm.watchedEpisodes.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when (val state = animeState) {
            is DetailState.Loading -> LoadingState()
            is DetailState.Error -> ErrorState(message = state.message)
            is DetailState.Success -> DetailContent(
                anime = state.anime,
                episodeState = episodeState,
                currentMatch = currentMatch,
                allMatches = allMatches,
                watchedEpisodes = watchedEpisodes,
                onBack = onBack,
                onOpenEpisode = onOpenEpisode,
                onToggleWatched = vm::toggleWatched,
                onSwitchSource = vm::switchSource,
            )
        }
    }
}

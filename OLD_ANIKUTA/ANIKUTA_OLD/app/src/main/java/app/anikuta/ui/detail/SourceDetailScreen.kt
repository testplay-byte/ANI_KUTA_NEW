package app.anikuta.ui.detail

import android.content.Intent
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import app.anikuta.domain.source.anime.service.AnimeSourceManager
import app.anikuta.player.PlayerActivity
import app.anikuta.source.api.AnimeCatalogueSource
import app.anikuta.source.api.model.SAnime
import app.anikuta.source.api.model.SEpisode
import app.anikuta.source.api.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Phase I — Source-based detail screen (enhanced with episodes + playback).
 *
 * Shown when:
 *   - An extension anime is NOT found on AniList (fallback from SourceLinkingScreen)
 *
 * Shows:
 *   - Anime cover, title, genres, status, description (from getAnimeDetails)
 *   - Episode list (from getEpisodeList)
 *   - Tapping an episode resolves videos and launches the player
 *
 * Related files:
 *   - SourceLinkingScreen.kt — navigates here when AniList search fails
 *   - AnikutaNavGraph.kt — route: source-detail-fallback/{sourceId}/{animeUrl}/{title}/{thumbnailUrl}
 *   - ExtensionLinkStore.kt — caches AniList links (if linked later, this screen is skipped)
 *
 * @param sourceId the extension source ID
 * @param animeUrl the source-specific anime URL
 * @param initialTitle the title from the search result (shown while details load)
 * @param initialThumbnailUrl the thumbnail from the search result (shown while details load)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceDetailScreen(
    sourceId: Long,
    animeUrl: String,
    onBack: () -> Unit,
    initialTitle: String = "",
    initialThumbnailUrl: String? = null,
) {
    val viewModel: SourceDetailViewModel = viewModel(key = "source_detail_${sourceId}_$animeUrl") {
        SourceDetailViewModel(sourceId, animeUrl)
    }
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = (state as? SourceDetailState.Success)?.anime?.title ?: initialTitle.ifBlank { "Loading…" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when (val s = state) {
            is SourceDetailState.Loading -> {
                // Show initial info while loading (cover + title from the search result)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (!initialThumbnailUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = initialThumbnailUrl,
                            contentDescription = initialTitle,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Text(
                        text = initialTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(16.dp),
                    )
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
            is SourceDetailState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(s.message, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onBack) { Text("Go back") }
                    }
                }
            }
            is SourceDetailState.Success -> {
                val anime = s.anime
                val episodes = s.episodes
                val resolvingEpisode = s.resolvingEpisodeUrl

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    // Cover
                    item {
                        val thumbUrl = anime.thumbnail_url ?: initialThumbnailUrl
                        if (!thumbUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = thumbUrl,
                                contentDescription = anime.title,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(280.dp),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                    // Title
                    item {
                        Text(
                            text = anime.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                    // Genres
                    val genres = anime.getGenres()
                    if (!genres.isNullOrEmpty()) {
                        item {
                            Text(
                                text = genres.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                    // Status
                    item {
                        val statusLabel = when (anime.status) {
                            SAnime.ONGOING -> "Ongoing"
                            SAnime.COMPLETED -> "Completed"
                            SAnime.LICENSED -> "Licensed"
                            SAnime.PUBLISHING_FINISHED -> "Publishing Finished"
                            SAnime.CANCELLED -> "Cancelled"
                            SAnime.ON_HIATUS -> "On Hiatus"
                            else -> "Unknown"
                        }
                        Text(
                            text = "Status: $statusLabel",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    // Description
                    val desc = anime.description
                    if (!desc.isNullOrBlank()) {
                        item {
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                    // Episodes header
                    if (episodes.isNotEmpty()) {
                        item {
                            Text(
                                text = "Episodes (${episodes.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                    }
                    // Episode list — alternating bg like the AniList DetailScreen
                    items(episodes.size) { index ->
                        val episode = episodes[index]
                        val cardColor = if (index % 2 == 0) {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.playEpisode(episode, context) },
                            shape = RoundedCornerShape(12.dp),
                            color = cardColor,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Episode thumbnail (if available)
                                if (!episode.preview_url.isNullOrBlank()) {
                                    AsyncImage(
                                        model = episode.preview_url,
                                        contentDescription = episode.name,
                                        modifier = Modifier
                                            .width(100.dp)
                                            .height(56.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop,
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                }
                                // Episode info
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = episode.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (episode.episode_number > 0) {
                                        Text(
                                            text = "Episode ${episode.episode_number.toInt()}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                // Loading indicator while resolving
                                if (resolvingEpisode == episode.url) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                }
                            }
                        }
                    }
                    // No episodes
                    if (episodes.isEmpty() && !s.isLoadingEpisodes) {
                        item {
                            Text(
                                text = "No episodes available",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                    item { Spacer(modifier = Modifier.height(32.dp)) }
                }
            }
        }
    }
}

/** ViewModel for [SourceDetailScreen]. */
class SourceDetailViewModel(
    private val sourceId: Long,
    private val animeUrl: String,
) : ViewModel() {

    companion object {
        private const val TAG = "SourceDetailVM"
    }

    private val sourceManager: AnimeSourceManager? = try { Injekt.get() } catch (e: Exception) { null }

    private val _state = MutableStateFlow<SourceDetailState>(SourceDetailState.Loading)
    val state: StateFlow<SourceDetailState> = _state.asStateFlow()

    init {
        loadDetails()
    }

    private fun loadDetails() {
        val manager = sourceManager
        if (manager == null) {
            _state.value = SourceDetailState.Error("Source manager not available")
            return
        }
        viewModelScope.launch {
            _state.value = SourceDetailState.Loading
            try {
                val source = withContext(Dispatchers.IO) {
                    manager.getCatalogueSources().find { it.id == sourceId }
                }
                if (source == null) {
                    _state.value = SourceDetailState.Error("Source not found (id=$sourceId)")
                    return@launch
                }
                // Fetch anime details
                val sAnime = SAnime.create().apply {
                    url = animeUrl
                    title = ""
                }
                val details = withContext(Dispatchers.IO) { source.getAnimeDetails(sAnime) }
                Log.d(TAG, "Loaded source details: ${details.title}")

                // Show details immediately, then load episodes
                _state.value = SourceDetailState.Success(
                    anime = details,
                    episodes = emptyList(),
                    isLoadingEpisodes = true,
                )

                // Fetch episodes
                try {
                    val episodes = withContext(Dispatchers.IO) { source.getEpisodeList(details) }
                    Log.d(TAG, "Loaded ${episodes.size} episodes")
                    _state.value = SourceDetailState.Success(
                        anime = details,
                        episodes = episodes.sortedByDescending { it.episode_number },
                        isLoadingEpisodes = false,
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load episodes", e)
                    _state.value = SourceDetailState.Success(
                        anime = details,
                        episodes = emptyList(),
                        isLoadingEpisodes = false,
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load source details", e)
                _state.value = SourceDetailState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /** Resolve videos for an episode and launch the player. */
    fun playEpisode(episode: SEpisode, context: android.content.Context) {
        val manager = sourceManager ?: return
        val current = _state.value as? SourceDetailState.Success ?: return

        // Mark as resolving
        _state.value = current.copy(resolvingEpisodeUrl = episode.url)

        viewModelScope.launch {
            try {
                val source = withContext(Dispatchers.IO) {
                    manager.getCatalogueSources().find { it.id == sourceId }
                }
                if (source == null) {
                    _state.value = current.copy(resolvingEpisodeUrl = null)
                    return@launch
                }

                // Resolve videos
                val videos = withContext(Dispatchers.IO) {
                    try {
                        source.getVideoList(episode)
                    } catch (e: Exception) {
                        // Try hoster list fallback
                        try {
                            val hosters = source.getHosterList(episode)
                            hosters.flatMap { source.getVideoList(it) }
                        } catch (e2: Exception) {
                            emptyList()
                        }
                    }
                }

                if (videos.isEmpty()) {
                    Log.e(TAG, "No videos found for episode ${episode.name}")
                    _state.value = current.copy(resolvingEpisodeUrl = null)
                    return@launch
                }

                // Pick the first video (best quality is usually first)
                val video = videos.first()
                Log.d(TAG, "Launching player: ${video.videoUrl}")

                // Launch PlayerActivity
                val intent = PlayerActivity.newIntent(
                    context = context,
                    videoUrl = video.videoUrl,
                    title = episode.name,
                )
                context.startActivity(intent)

                _state.value = current.copy(resolvingEpisodeUrl = null)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to play episode", e)
                _state.value = current.copy(resolvingEpisodeUrl = null)
            }
        }
    }
}

sealed class SourceDetailState {
    data object Loading : SourceDetailState()
    data class Success(
        val anime: SAnime,
        val episodes: List<SEpisode>,
        val isLoadingEpisodes: Boolean = false,
        val resolvingEpisodeUrl: String? = null,
    ) : SourceDetailState()
    data class Error(val message: String) : SourceDetailState()
}

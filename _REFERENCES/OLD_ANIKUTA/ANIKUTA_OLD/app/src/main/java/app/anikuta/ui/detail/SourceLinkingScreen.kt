package app.anikuta.ui.detail

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import app.anikuta.data.anilist.model.AniListAnime
import app.anikuta.data.anilist.repository.AniListRepository
import app.anikuta.data.cache.ExtensionLinkStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Phase I — Source linking screen (revised).
 *
 * When the user taps an extension anime result:
 *   1. Shows a loading card (cover + name + "Processing...")
 *   2. Searches AniList by title — first WITHOUT adult filter, then WITH adult filter
 *   3. If results found → auto-selects the first match → links it → opens DetailScreen
 *   4. If NO results found → shows the search results list (if any) + manual search field
 *      - User can tap any result to link + open
 *      - User can type a different title and search manually
 *
 * Related files:
 *   - ExtensionLinkStore.kt — caches the link
 *   - AnikutaNavGraph.kt — route + cache check
 *   - AniListRepository.kt — searchAnime() + searchAnimeWithAdult()
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceLinkingScreen(
    sourceId: Long,
    animeUrl: String,
    title: String,
    thumbnailUrl: String?,
    sourceName: String,
    onLinked: (anilistId: Int) -> Unit,
    onAniListNotFound: () -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: SourceLinkingViewModel = viewModel(key = "source_linking_${sourceId}_$animeUrl") {
        SourceLinkingViewModel(sourceId, animeUrl, title)
    }
    val state by viewModel.state.collectAsState()
    var manualSearchQuery by remember { mutableStateOf("") }

    // Auto-navigate when linked
    LaunchedEffect(state) {
        if (state is SourceLinkingState.Linked) {
            onLinked((state as SourceLinkingState.Linked).anilistId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Cover + title (always visible)
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (!thumbnailUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = thumbnailUrl,
                            contentDescription = title,
                            modifier = Modifier
                                .width(160.dp)
                                .height(240.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    if (sourceName.isNotBlank()) {
                        Text(
                            text = "From: $sourceName",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            when (val s = state) {
                is SourceLinkingState.Searching -> {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Processing... Just wait a moment",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "Searching AniList for \"$title\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
                is SourceLinkingState.Linked -> {
                    item {
                        Text(
                            "Found! Opening details...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                is SourceLinkingState.NotFound -> {
                    // Show search results (if any) + manual search
                    if (s.results.isNotEmpty()) {
                        item {
                            Text(
                                "Did you mean one of these?",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        items(s.results) { result ->
                            SearchResultRow(
                                anime = result,
                                onTap = { viewModel.selectResult(result) },
                            )
                        }
                    } else {
                        item {
                            Text(
                                "No matches found on AniList.\nTry searching manually below.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    // Manual search field
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = manualSearchQuery,
                            onValueChange = { manualSearchQuery = it },
                            label = { Text("Search AniList manually") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = {
                                    if (manualSearchQuery.isNotBlank()) {
                                        viewModel.manualSearch(manualSearchQuery.trim())
                                    }
                                }) {
                                    Icon(Icons.Filled.Search, contentDescription = "Search")
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(anime: AniListAnime, onTap: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTap() },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (anime.coverImage.best() != null) {
                AsyncImage(
                    model = anime.coverImage.best(),
                    contentDescription = anime.title.preferred(),
                    modifier = Modifier
                        .width(50.dp)
                        .height(70.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = anime.title.preferred(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val bits = buildList {
                    anime.seasonYear?.let { add(it.toString()) }
                    anime.format?.let { add(it) }
                    anime.episodes?.let { add("$it eps") }
                }
                if (bits.isNotEmpty()) {
                    Text(
                        bits.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** ViewModel for [SourceLinkingScreen]. */
class SourceLinkingViewModel(
    private val sourceId: Long,
    private val animeUrl: String,
    private val animeTitle: String,
) : ViewModel() {

    companion object {
        private const val TAG = "SourceLinkingVM"
    }

    private val anilistRepo: AniListRepository? = try { Injekt.get() } catch (e: Exception) { null }
    private val linkStore: ExtensionLinkStore? = try { Injekt.get() } catch (e: Exception) { null }

    private val _state = MutableStateFlow<SourceLinkingState>(SourceLinkingState.Searching)
    val state: StateFlow<SourceLinkingState> = _state.asStateFlow()

    init {
        searchAndLink()
    }

    private fun searchAndLink() {
        val repo = anilistRepo
        if (repo == null) {
            _state.value = SourceLinkingState.NotFound(emptyList())
            return
        }
        viewModelScope.launch {
            _state.value = SourceLinkingState.Searching
            try {
                // Step 1: Search WITHOUT adult filter
                val resultsNormal = withContext(Dispatchers.IO) {
                    repo.searchAnime(animeTitle, page = 1, perPage = 10)
                }
                if (resultsNormal.isNotEmpty()) {
                    autoLink(resultsNormal)
                    return@launch
                }

                // Step 2: No results without adult → search WITH adult filter
                Log.d(TAG, "No results without adult filter, retrying with adult filter")
                val resultsAdult = withContext(Dispatchers.IO) {
                    repo.searchAnimeWithAdult(animeTitle, page = 1, perPage = 10)
                }
                if (resultsAdult.isNotEmpty()) {
                    autoLink(resultsAdult)
                    return@launch
                }

                // Step 3: No results at all → show NotFound with empty list
                Log.d(TAG, "No AniList results for \"$animeTitle\" (tried both filters)")
                _state.value = SourceLinkingState.NotFound(emptyList())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to search AniList for \"$animeTitle\"", e)
                _state.value = SourceLinkingState.NotFound(emptyList())
            }
        }
    }

    /** Auto-link the first result. */
    private fun autoLink(results: List<AniListAnime>) {
        val bestMatch = results.first()
        Log.d(TAG, "Auto-linked: \"$animeTitle\" → AniList ID ${bestMatch.id} (${bestMatch.title.preferred()})")
        linkStore?.link(sourceId, animeUrl, bestMatch.id)
        _state.value = SourceLinkingState.Linked(bestMatch.id)
    }

    /** Manual search — user types a different title. */
    fun manualSearch(query: String) {
        val repo = anilistRepo ?: return
        viewModelScope.launch {
            _state.value = SourceLinkingState.Searching
            try {
                // Try both filters for manual search too
                val results = withContext(Dispatchers.IO) {
                    val normal = repo.searchAnime(query, page = 1, perPage = 10)
                    if (normal.isNotEmpty()) normal else repo.searchAnimeWithAdult(query, page = 1, perPage = 10)
                }
                _state.value = SourceLinkingState.NotFound(results)
            } catch (e: Exception) {
                _state.value = SourceLinkingState.NotFound(emptyList())
            }
        }
    }

    /** User selected a specific result from the list. */
    fun selectResult(anime: AniListAnime) {
        Log.d(TAG, "Manual link: \"$animeTitle\" → AniList ID ${anime.id} (${anime.title.preferred()})")
        linkStore?.link(sourceId, animeUrl, anime.id)
        _state.value = SourceLinkingState.Linked(anime.id)
    }
}

sealed class SourceLinkingState {
    data object Searching : SourceLinkingState()
    data class Linked(val anilistId: Int) : SourceLinkingState()
    /** Auto-match failed. Show [results] for manual selection + manual search field. */
    data class NotFound(val results: List<AniListAnime>) : SourceLinkingState()
}

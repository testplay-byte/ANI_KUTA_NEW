package app.anikuta.ui.detail

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import app.anikuta.data.anilist.model.AniListAnime
import app.anikuta.ui.detail.components.AiringPill
import app.anikuta.ui.detail.components.DownloadButtonTall
import app.anikuta.ui.detail.components.EpisodeDisplaySettings
import app.anikuta.ui.detail.components.EpisodeOptionsSheet
import app.anikuta.ui.detail.components.EpisodeRow
import app.anikuta.ui.detail.components.EpisodeRowContent
import app.anikuta.ui.detail.components.cleanHtmlTags
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    anilistId: Int,
    autoPlayUrl: String = "",
    onBack: () -> Unit,
) {
    val viewModel: DetailViewModel = viewModel(key = "detail_$anilistId") {
        DetailViewModel(anilistId)
    }
    val detailState by viewModel.anime.collectAsState()
    val isSaved by viewModel.isSaved.collectAsState()
    val episodeState by viewModel.episodes.collectAsState()
    val playRequest by viewModel.playRequest.collectAsState()
    val resolvingEpisode by viewModel.resolvingEpisode.collectAsState()
    val videoPicker by viewModel.videoPicker.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isEnrichingMetadata by viewModel.isEnrichingMetadata.collectAsState()
    val downloadStatus by viewModel.downloadStatus.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val downloadedOnDisk by viewModel.downloadedOnDisk.collectAsState()
    val context = LocalContext.current
    var expandedDescription by remember { mutableStateOf(false) }

    // Episode seen (watched) status — collected reactively from EpisodeSeenStore
    val episodeSeenStore = remember {
        try { uy.kohesive.injekt.Injekt.get<app.anikuta.data.cache.EpisodeSeenStore>() }
        catch (e: Exception) { null }
    }
    // Use a mutable state that can be refreshed on resume (when returning from player)
    var seenEpisodes by remember { mutableStateOf(episodeSeenStore?.getAll() ?: emptySet()) }
    // Collect changes reactively
    LaunchedEffect(episodeSeenStore) {
        episodeSeenStore?.changes?.collect { newSet ->
            seenEpisodes = newSet
        }
    }
    // Re-read on resume (when returning from PlayerActivity)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                seenEpisodes = episodeSeenStore?.getAll() ?: emptySet()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Long-press download menu state (Q4)
    var longPressEpisode by remember { mutableStateOf<app.anikuta.source.api.model.SEpisode?>(null) }

    // Phase 7.5: Episode display settings
    // FIX (D.8): Use stateIn().collectAsState() so preferences update reactively
    // when the user changes them in Settings. Previously used remember { .get() }
    // which captured the value once and never updated.
    val playerPrefs = remember {
        try { uy.kohesive.injekt.Injekt.get<app.anikuta.player.PlayerPreferences>() }
        catch (e: Exception) { null }
    }
    val detailScope = androidx.compose.runtime.rememberCoroutineScope()
    val showTitles by (playerPrefs?.showEpisodeTitles()?.stateIn(detailScope) ?: kotlinx.coroutines.flow.MutableStateFlow(true)).collectAsState()
    val showSummaries by (playerPrefs?.showEpisodeSummaries()?.stateIn(detailScope) ?: kotlinx.coroutines.flow.MutableStateFlow(true)).collectAsState()
    val showThumbnails by (playerPrefs?.showEpisodeThumbnails()?.stateIn(detailScope) ?: kotlinx.coroutines.flow.MutableStateFlow(true)).collectAsState()
    val showDates by (playerPrefs?.showEpisodeDates()?.stateIn(detailScope) ?: kotlinx.coroutines.flow.MutableStateFlow(true)).collectAsState()
    val showEpisodeNumber by (playerPrefs?.showEpisodeNumber()?.stateIn(detailScope) ?: kotlinx.coroutines.flow.MutableStateFlow(true)).collectAsState()
    val showAudioPills by (playerPrefs?.showAudioPills()?.stateIn(detailScope) ?: kotlinx.coroutines.flow.MutableStateFlow(true)).collectAsState()
    val synopsisPosition by (playerPrefs?.synopsisPosition()?.stateIn(detailScope) ?: kotlinx.coroutines.flow.MutableStateFlow("right")).collectAsState()
    val datePosition by (playerPrefs?.datePosition()?.stateIn(detailScope) ?: kotlinx.coroutines.flow.MutableStateFlow("right_below_synopsis")).collectAsState()
    val thumbnailSize by (playerPrefs?.thumbnailSize()?.stateIn(detailScope) ?: kotlinx.coroutines.flow.MutableStateFlow("medium")).collectAsState()
    val titlePosition by (playerPrefs?.titlePosition()?.stateIn(detailScope) ?: kotlinx.coroutines.flow.MutableStateFlow("right")).collectAsState()
    val episodeNumberPosition by (playerPrefs?.episodeNumberPosition()?.stateIn(detailScope) ?: kotlinx.coroutines.flow.MutableStateFlow("overlay")).collectAsState()
    val thumbnailPosition by (playerPrefs?.thumbnailPosition()?.stateIn(detailScope) ?: kotlinx.coroutines.flow.MutableStateFlow("left")).collectAsState()
    val animeInfoPosition by (playerPrefs?.animeInfoPosition()?.stateIn(detailScope) ?: kotlinx.coroutines.flow.MutableStateFlow("below")).collectAsState()
    val dynamicThemingEnabled by (playerPrefs?.dynamicDetailTheming()?.stateIn(detailScope) ?: kotlinx.coroutines.flow.MutableStateFlow(true)).collectAsState()
    val downloadButtonPlacement by (playerPrefs?.downloadButtonPlacement()?.stateIn(detailScope) ?: kotlinx.coroutines.flow.MutableStateFlow("episode_row")).collectAsState()

    // Watched episode appearance (grayscale / blur / both / none) — configurable in Settings
    val watchedAppearancePref by (playerPrefs?.watchedEpisodeAppearance()?.stateIn(detailScope) ?: kotlinx.coroutines.flow.MutableStateFlow("grayscale")).collectAsState()
    val watchedBlurRadius by (playerPrefs?.watchedEpisodeBlurRadius()?.stateIn(detailScope) ?: kotlinx.coroutines.flow.MutableStateFlow(2f)).collectAsState()
    val watchedAlpha by (playerPrefs?.watchedEpisodeAlpha()?.stateIn(detailScope) ?: kotlinx.coroutines.flow.MutableStateFlow(0.55f)).collectAsState()
    val watchedAppearance = remember(watchedAppearancePref) {
        app.anikuta.ui.detail.components.WatchedEpisodeAppearance.fromPref(watchedAppearancePref)
    }

    // Aggregate display settings into a single object for EpisodeRowContent.
    // This avoids passing 12+ individual parameters through the composable tree.
    val displaySettings = EpisodeDisplaySettings(
        showThumbnails = showThumbnails,
        showSummaries = showSummaries,
        showTitles = showTitles,
        showDates = showDates,
        showEpisodeNumber = showEpisodeNumber,
        showAudioPills = showAudioPills,
        synopsisPosition = synopsisPosition,
        datePosition = datePosition,
        thumbnailSize = thumbnailSize,
        titlePosition = titlePosition,
        episodeNumberPosition = episodeNumberPosition,
        thumbnailPosition = thumbnailPosition,
        downloadButtonPlacement = downloadButtonPlacement,
    )

    // Issue A: Scan filesystem for downloaded episodes when the detail page is entered.
    // This ensures the green checkmark appears for episodes that were downloaded
    // in a previous session or after the auto-remove countdown removed them from the queue.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.refreshDownloadedOnDisk()
    }

    // Phase 2: Auto-play support for history resume.
    // When autoPlayUrl is set (from History tap) and episodes finish loading,
    // find the matching episode and trigger playEpisode() automatically.
    // This launches the player at the saved position.
    var autoPlayAttempted by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(episodeState, autoPlayUrl) {
        if (autoPlayUrl.isBlank() || autoPlayAttempted) return@LaunchedEffect
        val loaded = episodeState as? EpisodeState.Loaded ?: return@LaunchedEffect
        val matchingEpisode = loaded.episodeList.find { it.url == autoPlayUrl }
        if (matchingEpisode != null) {
            autoPlayAttempted = true
            viewModel.playEpisode(matchingEpisode)
        }
    }

    // Observe play requests from the ViewModel → launch the player.
    androidx.compose.runtime.LaunchedEffect(playRequest) {
        val req = playRequest ?: return@LaunchedEffect
        when (req) {
            is PlayRequest.Play -> {
                // Get cover color + cover URL + anime title from the current anime (if loaded)
                val anime = (detailState as? DetailState.Success)?.anime
                val coverColorInt = anime?.coverImage?.color?.let {
                    try { android.graphics.Color.parseColor(it) } catch (e: Exception) { 0 }
                } ?: 0
                val coverUrlStr = anime?.coverImage?.extraLarge ?: anime?.coverImage?.large ?: ""
                val animeTitleStr = anime?.title?.preferred() ?: ""
                val intent = app.anikuta.player.PlayerActivity.newIntent(
                    context = context,
                    videoUrl = req.url,
                    title = req.title,
                    anilistId = req.anilistId,
                    episodeUrl = req.episodeUrl,
                    episodeNumber = req.episodeNumber,
                    videoHeaders = req.videoHeaders,
                    coverColor = coverColorInt,
                    coverUrl = coverUrlStr,
                    animeTitle = animeTitleStr,
                    sourceId = req.sourceId,
                    videoServer = req.videoServer,
                    videoAudio = req.videoAudio,
                    videoQuality = req.videoQuality,
                )
                context.startActivity(intent)
                viewModel.consumePlayRequest()
            }
            is PlayRequest.Error -> {
                android.widget.Toast.makeText(context, req.message, android.widget.Toast.LENGTH_SHORT).show()
                viewModel.consumePlayRequest()
            }
        }
    }

    when (detailState) {
        is DetailState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        is DetailState.Error -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp).statusBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "Couldn't load: ${(detailState as DetailState.Error).message}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = { viewModel.refreshEverything() }) { Text("Retry") }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onBack) { Text("Go back") }
            }
        }
        is DetailState.Success -> {
            val anime = (detailState as DetailState.Success).anime
            // Parse the cover color from AniList (format: "#RRGGBB")
            val fallbackColor = MaterialTheme.colorScheme.primary
            val coverColor = remember(anime.coverImage.color, fallbackColor) {
                try {
                    anime.coverImage.color?.let { Color(android.graphics.Color.parseColor(it)) }
                        ?: fallbackColor
                } catch (e: Exception) {
                    fallbackColor
                }
            }

            // Dynamic theming: generate a color scheme from the AniList cover color.
            // AniList provides coverImage.color as a hex string — we use that single
            // color and generate variants (darker/lighter/desaturated) for the whole
            // page: background, episode card alternating colors, accent, etc.
            // No Palette API / image loading needed — fast and reliable.
            //
            // CRITICAL: both generateDynamicScheme() AND toM3ColorScheme() must be
            // remembered. If toM3ColorScheme() is called on every recomposition, it
            // creates a new ColorScheme object each frame → MaterialTheme sees a
            // different object → forces ALL children to recompose on every scroll
            // frame → massive jank.
            val defaultScheme = MaterialTheme.colorScheme
            val themedColorScheme = remember(coverColor, dynamicThemingEnabled, defaultScheme) {
                if (dynamicThemingEnabled) {
                    generateDynamicScheme(coverColor).toM3ColorScheme()
                } else {
                    defaultScheme
                }
            }
            val pageBgColor = themedColorScheme.background

            MaterialTheme(colorScheme = themedColorScheme) {
            Box(modifier = Modifier.fillMaxSize().background(pageBgColor)) {
            ThreeStagePullRefresh(
                isRefreshing = isRefreshing,
                onRefresh = { stage -> viewModel.onRefreshStage(stage) },
            ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                // Edge-to-edge banner with cover image + theme color tint
                item(key = "header") {
                    DetailHeader(
                        anilistId = anilistId,
                        anime = anime,
                        coverColor = coverColor,
                        isSaved = isSaved,
                        episodeState = episodeState,
                        onBack = onBack,
                        onSave = {
                            viewModel.toggleSaved()
                            // Confirmation toast — the bookmark icon change is
                            // subtle; this gives clear feedback (user request).
                            android.widget.Toast.makeText(
                                context,
                                if (isSaved) "Removed from library"
                                else "Saved to library",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        },
                        onShare = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "https://anilist.co/anime/$anilistId")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share anime"))
                        },
                    )
                }

                // Genres
                anime.genres?.let { genres ->
                    if (genres.isNotEmpty()) {
                        item(key = "genres") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(vertical = 8.dp),
                            ) {
                                items(genres, key = { it }) { genre ->
                                    AssistChip(
                                        onClick = { },
                                        label = { Text(genre) },
                                        shape = RoundedCornerShape(8.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // Description
                anime.description?.let { rawDesc ->
                    val desc = cleanHtmlTags(rawDesc)
                    if (desc.isNotBlank()) {
                        item(key = "description") {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Text("Synopsis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = if (expandedDescription) Int.MAX_VALUE else 4,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                TextButton(onClick = { expandedDescription = !expandedDescription }) {
                                    Text(if (expandedDescription) "Show less" else "Show more")
                                }
                            }
                        }
                    }
                }

                // Anime info — position is configurable (above or below episodes)
                // Info content composable
                val infoContent: @Composable () -> Unit = {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("Information", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        InfoRow("Format", anime.format ?: "Unknown")
                        InfoRow("Status", anime.status ?: "Unknown")
                        InfoRow("Season", "${anime.season ?: "?"} ${anime.seasonYear ?: ""}")
                        InfoRow("Episodes", anime.episodes?.toString() ?: "Unknown")
                        InfoRow("Score", anime.averageScore?.let { "$it / 100" } ?: "Not scored")
                    }
                }

                // Info above episodes (if position is 'above')
                if (animeInfoPosition == "above") {
                    item(key = "info") { infoContent() }
                }

                // Episodes section
                // When anime info is ABOVE episodes: episodes render directly as items
                // in the outer LazyColumn (no inner scrollable container) so the whole
                // screen scrolls as one long list — the user can scroll from the header
                // all the way to the last episode without a nested scroll area.
                // When anime info is BELOW episodes: episodes stay in an inner
                // scrollable LazyColumn with a max height so the info section below
                // remains reachable without scrolling through the entire episode list.
                val isInfoAbove = animeInfoPosition == "above"
                val loadedEpisodes = episodeState as? EpisodeState.Loaded

                if (isInfoAbove && loadedEpisodes != null) {
                    // Episodes header (title + enrichment indicator + count + source)
                    item(key = "episodes_header") {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Episodes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                // Metadata enrichment indicator
                                if (isEnrichingMetadata) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(12.dp),
                                                strokeWidth = 1.5.dp,
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                "Fetching metadata…",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "${loadedEpisodes.episodeList.size} episodes",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                ) {
                                    Text(
                                        loadedEpisodes.sourceName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                    // Episodes directly as items — full page scroll, no inner container.
                    // Each episode gets 16dp horizontal padding (to align with the
                    // header) and 4dp vertical padding (so 8dp total between episodes).
                    itemsIndexed(loadedEpisodes.episodeList, key = { _, it -> it.url }) { index, episode ->
                        val isEpisodeSeen = seenEpisodes.contains("$anilistId:${episode.url}")
                        Box(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Whether the download button is rendered OUTSIDE the episode
                                // row (compact icon). For "synopsis" placement WITH a summary,
                                // the button is rendered INSIDE the episode container (in the
                                // synopsis area) by EpisodeRow itself, so we don't add it here.
                                val hasSummary = showSummaries && !episode.summary.isNullOrBlank()
                                val showDownloadOutside = downloadButtonPlacement == "episode_row" ||
                                    (downloadButtonPlacement == "synopsis" && !hasSummary)
                                Box(modifier = Modifier.weight(1f).zIndex(1f)) {
                                    EpisodeRow(
                                        episode = episode,
                                        isSeen = isEpisodeSeen,
                                        onClick = { viewModel.playEpisode(episode) },
                                        onLongClick = { longPressEpisode = episode },
                                        onSwipeRight = {
                                            episodeSeenStore?.toggleSeen(anilistId, episode.url)
                                        },
                                        onSwipeLeft = {
                                            viewModel.onDownloadButtonClick(episode)
                                        },
                                        index = index,
                                        dynamicColors = null,
                                        appearance = watchedAppearance,
                                        grayscaleAlpha = watchedAlpha,
                                        blurRadiusDp = watchedBlurRadius,
                                    ) {
                                        EpisodeRowContent(
                                            episode = episode,
                                            settings = displaySettings,
                                            index = index,
                                            downloadStatus = downloadStatus,
                                            downloadProgress = downloadProgress,
                                            downloadedOnDisk = downloadedOnDisk,
                                            onDownloadClick = { viewModel.onDownloadButtonClick(episode) },
                                            onDownloadLongClick = { longPressEpisode = episode },
                                        )
                                    }
                                }
                                // Download button outside the episode container — only for
                                // "episode_row" placement, or "synopsis" with no summary (fallback)
                                if (showDownloadOutside) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    DownloadButtonTall(
                                        episodeUrl = episode.url,
                                        downloadStatus = downloadStatus,
                                        downloadProgress = downloadProgress,
                                        downloadedOnDisk = downloadedOnDisk,
                                        onDownload = { viewModel.onDownloadButtonClick(episode) },
                                        onLongClick = { longPressEpisode = episode },
                                        index = index,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Below mode OR not-yet-loaded: episodes in a section with an
                    // inner scrollable container (max height) when loaded.
                    item(key = "episodes") {
                        Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Episodes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                // Metadata enrichment indicator
                                if (isEnrichingMetadata) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(12.dp),
                                                strokeWidth = 1.5.dp,
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                "Fetching metadata…",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            when (val es = episodeState) {
                                is EpisodeState.Idle, is EpisodeState.Searching -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Searching extensions…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                is EpisodeState.LoadingEpisodes -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Loading episodes…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                is EpisodeState.NotAired -> {
                                    InfoCard(
                                        title = "Not yet available",
                                        body = "This anime hasn't aired yet. It will be available to stream after it airs." +
                                            (es.seasonYear?.let { " Expected: ${es.season ?: ""} $it".trim() } ?: ""),
                                    )
                                }
                                is EpisodeState.NoMatch -> {
                                    InfoCard(
                                        title = "No streaming source available",
                                        body = "No installed extension has '${es.searchedTitle}'. Install more extensions to stream this anime.",
                                    )
                                }
                                is EpisodeState.Loaded -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            "${es.episodeList.size} episodes",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                        ) {
                                            Text(
                                                es.sourceName,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    // Episode list — inner scrollable container (max height 1.5x)
                                    androidx.compose.foundation.lazy.LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 600.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        itemsIndexed(es.episodeList, key = { _, it -> it.url }) { index, episode ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                // Whether the download button is rendered OUTSIDE
                                                // the episode row. For "synopsis" placement WITH a
                                                // summary, the button is rendered INSIDE the episode
                                                // container by EpisodeRow itself.
                                                val hasSummary = showSummaries && !episode.summary.isNullOrBlank()
                                                val showDownloadOutside = downloadButtonPlacement == "episode_row" ||
                                                    (downloadButtonPlacement == "synopsis" && !hasSummary)
                                                Box(modifier = Modifier.weight(1f).zIndex(1f)) {
                                                    EpisodeRow(
                                                        episode = episode,
                                                        isSeen = seenEpisodes.contains("$anilistId:${episode.url}"),
                                                        onClick = { viewModel.playEpisode(episode) },
                                                        onLongClick = { longPressEpisode = episode },
                                                        onSwipeRight = {
                                                            episodeSeenStore?.toggleSeen(anilistId, episode.url)
                                                        },
                                                        onSwipeLeft = {
                                                            viewModel.onDownloadButtonClick(episode)
                                                        },
                                                        index = index,
                                                        dynamicColors = null,
                                                        appearance = watchedAppearance,
                                                        grayscaleAlpha = watchedAlpha,
                                                        blurRadiusDp = watchedBlurRadius,
                                                    ) {
                                                        EpisodeRowContent(
                                                            episode = episode,
                                                            settings = displaySettings,
                                                            index = index,
                                                            downloadStatus = downloadStatus,
                                                            downloadProgress = downloadProgress,
                                                            downloadedOnDisk = downloadedOnDisk,
                                                            onDownloadClick = { viewModel.onDownloadButtonClick(episode) },
                                                            onDownloadLongClick = { longPressEpisode = episode },
                                                        )
                                                    }
                                                }
                                                // Download button outside the episode container —
                                                // only for "episode_row", or "synopsis" with no summary
                                                if (showDownloadOutside) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    DownloadButtonTall(
                                                        episodeUrl = episode.url,
                                                        downloadStatus = downloadStatus,
                                                        downloadProgress = downloadProgress,
                                                        downloadedOnDisk = downloadedOnDisk,
                                                        onDownload = { viewModel.onDownloadButtonClick(episode) },
                                                        onLongClick = { longPressEpisode = episode },
                                                        index = index,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                is EpisodeState.Error -> {
                                    InfoCard(
                                        title = "Couldn't load episodes",
                                        body = es.message,
                                    )
                                }
                            }
                        }
                    }
                }

                // Info below episodes (if position is 'below' — default)
                if (animeInfoPosition != "above") {
                    item(key = "info_below") { infoContent() }
                }
            }

            // ---- Phase 7: Video quality picker ----
            // Handles Resolving (first-time), Cached (instant + background refresh),
            // and Show (fresh) states. Uses LazyColumn (scrollable), collapsible
            // servers, audio-version grouping (SUB/DUB/HSUB), quality-desc sort.
            val pickerState = videoPicker
            val expandedServersState by viewModel.expandedServers.collectAsState()
            if (pickerState !is VideoPickerState.Hidden) {
                VideoPickerSheet(
                    state = pickerState,
                    expandedServers = expandedServersState,
                    onToggleServer = { viewModel.toggleServer(it) },
                    onPickVideo = { video, ep -> viewModel.playSpecificVideo(video, ep) },
                    onDismiss = { viewModel.dismissVideoPicker() },
                )
            }

            // Long-press bottom sheet moved to TOP LEVEL of DetailScreen (see below).
            // It was buried inside ThreeStagePullRefresh > Box > MaterialTheme > when(Success)
            // which prevented Compose from recomposing it when longPressEpisode changed.

            } // end ThreeStagePullRefresh
            } // end Box
            } // end MaterialTheme
        } // end DetailState.Success branch
        } // end when(detailState)

        // Long-press options sheet.
        //
        // Extracted into a dedicated composable [EpisodeOptionsSheet] that receives
        // `longPressEpisode` as a parameter. This ensures reliable recomposition:
        // when `longPressEpisode` transitions from null to non-null, Compose
        // recomposes this composable because the parameter changed.
        //
        // Previous approach used `longPressEpisode?.let { ModalBottomSheet(...) }`
        // inline in this 800-line function. Despite the state being mutableStateOf,
        // Compose did not reliably recompose the let-block — likely due to the
        // function's complex control flow and recomposition scope boundaries.
        EpisodeOptionsSheet(
            episode = longPressEpisode,
            isSeen = longPressEpisode?.let { seenEpisodes.contains("$anilistId:${it.url}") } ?: false,
            downloadState = longPressEpisode?.let { downloadStatus[it.url] },
            isOnDisk = longPressEpisode?.let { downloadedOnDisk.contains(it.url) } ?: false,
            onDismiss = { longPressEpisode = null },
            onPlay = {
                val ep = longPressEpisode
                longPressEpisode = null
                ep?.let { viewModel.playEpisode(it) }
            },
            onDownload = {
                val ep = longPressEpisode
                longPressEpisode = null
                ep?.let { viewModel.onDownloadButtonClick(it) }
            },
            onDelete = {
                val ep = longPressEpisode
                longPressEpisode = null
                ep?.let { viewModel.deleteDownloadedEpisode(it) }
            },
            onCancel = {
                val ep = longPressEpisode
                longPressEpisode = null
                ep?.let { viewModel.cancelDownloadForEpisode(it) }
            },
            onMarkSeen = {
                val ep = longPressEpisode
                longPressEpisode = null
                ep?.let { episodeSeenStore?.markSeen(anilistId, it.url) }
            },
            onMarkUnseen = {
                val ep = longPressEpisode
                longPressEpisode = null
                ep?.let { episodeSeenStore?.markUnseen(anilistId, it.url) }
            },
        )
    }

@Composable
private fun DetailHeader(
    anilistId: Int,
    anime: AniListAnime,
    coverColor: Color,
    isSaved: Boolean,
    episodeState: EpisodeState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        // Edge-to-edge blurred banner with theme color tint
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp),  // Taller to truly cover behind status bar
        ) {
            // Blurred cover image as background
            AsyncImage(
                model = anime.coverImage.extraLarge ?: anime.coverImage.best(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(radius = 8.dp),  // Very subtle blur — just enough to make text readable
                contentScale = ContentScale.Crop,
            )
            // Theme color tint overlay — very subtle
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(coverColor.copy(alpha = 0.2f)),
            )
            // Gradient overlay for text readability (bottom fade to background)
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

        // Top bar buttons (back + save + share + three-dot menu) — over the banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Row {
                IconButton(onClick = onSave) {
                    Icon(
                        if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = if (isSaved) "Saved" else "Save",
                        tint = if (isSaved) MaterialTheme.colorScheme.primary else Color.White,
                    )
                }
                IconButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                }
                // Three-dot menu: Notification settings + Auto Download
                // Both are hidden for fully-released anime (no new episodes to notify about or download).
                // If both are hidden, shows "Nothing to configure".
                var showNotificationSettings by remember { mutableStateOf(false) }
                var showDownloadSettings by remember { mutableStateOf(false) }
                var showMenu by remember { mutableStateOf(false) }

                // Check if all episodes are released. Uses multiple signals:
                // 1. AniList status == FINISHED
                // 2. No nextAiringEpisode (no upcoming episode scheduled)
                // 3. The loaded extension episode count >= AniList's total episode count
                val loadedEpisodeCount = (episodeState as? EpisodeState.Loaded)?.episodeList?.size ?: 0
                val isFullyReleased = remember(anime, loadedEpisodeCount) {
                    anime.status?.uppercase() == "FINISHED" &&
                    anime.nextAiringEpisode == null &&
                    anime.episodes != null &&
                    anime.episodes > 0 &&
                    loadedEpisodeCount >= anime.episodes
                }

                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = Color.White)
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    if (isFullyReleased) {
                        // Both options hidden — show "Nothing to configure"
                        DropdownMenuItem(
                            text = { Text("Nothing to configure", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = { /* no-op — disabled */ },
                            enabled = false,
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Notification settings") },
                            onClick = {
                                showMenu = false
                                showNotificationSettings = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Auto Download") },
                            onClick = {
                                showMenu = false
                                showDownloadSettings = true
                            },
                        )
                    }
                }
                if (showNotificationSettings) {
                    AnimeSettingsSheet(
                        anilistId = anilistId,
                        mode = SettingsMode.NOTIFICATIONS,
                        onDismiss = { showNotificationSettings = false },
                    )
                }
                if (showDownloadSettings) {
                    AnimeSettingsSheet(
                        anilistId = anilistId,
                        mode = SettingsMode.DOWNLOADS,
                        onDismiss = { showDownloadSettings = false },
                    )
                }
            }
        }

        // Cover + title overlapping the banner
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AsyncImage(
                    model = anime.coverImage.best(),
                    contentDescription = anime.title.preferred(),
                    modifier = Modifier
                        .width(100.dp)
                        .height(150.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    Text(
                        anime.title.preferred(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (anime.averageScore != null) {
                            Text("★ ${anime.averageScore}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (anime.status != null) {
                            Text("· ${anime.status}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (anime.episodes != null) {
                            Text("· ${anime.episodes} eps", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    // Next episode airing pill — click to toggle between text and countdown
                    anime.nextAiringEpisode?.let { airing ->
                        Spacer(modifier = Modifier.height(6.dp))
                        AiringPill(airing)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


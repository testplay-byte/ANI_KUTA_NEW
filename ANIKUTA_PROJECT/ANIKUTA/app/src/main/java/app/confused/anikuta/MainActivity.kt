package app.confused.anikuta

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.anilist.api.AniListApi
import app.confused.anikuta.core.designsystem.component.AnikutaBottomNavBar
import app.confused.anikuta.core.designsystem.component.CollapsingHeader
import app.confused.anikuta.core.designsystem.component.NavIcons
import app.confused.anikuta.core.designsystem.component.NavItem
import app.confused.anikuta.core.designsystem.theme.AnikutaTheme
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import app.confused.anikuta.data.extension.AnimeExtensionManager
import app.confused.anikuta.data.extension.cache.ExtensionLinkStore
import app.confused.anikuta.data.extension.matcher.SourceMatcher
import app.confused.anikuta.data.extension.repo.ExtensionRepoApi
import app.confused.anikuta.data.extension.repo.ExtensionRepoRepository
import app.confused.anikuta.feature.animedetails.AnimeDetailScreen
import app.confused.anikuta.feature.browse.BrowseScreen
import app.confused.anikuta.feature.library.LibraryScreen
import app.confused.anikuta.feature.extensionssettings.ExtensionRepoSettingsScreen
import app.confused.anikuta.feature.extensionssettings.ExtensionsSettingsScreen
import app.confused.anikuta.feature.search.data.RecentSearchesStore
import app.confused.anikuta.feature.search.ui.ExtensionLinkingSheet
import app.confused.anikuta.feature.search.ui.SearchScreen
import app.confused.anikuta.feature.search.viewmodel.SearchResult
import app.confused.anikuta.feature.videoresolver.ResolverResult
import app.confused.anikuta.feature.videoresolver.ResolverService
import app.confused.anikuta.feature.videoresolver.VideoResolverSheet
import app.confused.anikuta.feature.videoresolver.VideoResolverState
import app.confused.anikuta.feature.watch.WatchRequest
import app.confused.anikuta.feature.watch.WatchScreen
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AnikutaTheme(darkTheme = true) {
                AnikutaApp()
            }
        }
        // ── Agent 2: Handle OAuth callback intent (initial launch via redirect) ──
        handleOAuthIntent(intent)
    }

    // ── Agent 2: Handle OAuth callback when app is already running (singleTask) ──
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleOAuthIntent(intent)
    }

    private fun handleOAuthIntent(intent: android.content.Intent?) {
        if (intent?.action == android.content.Intent.ACTION_VIEW) {
            val data = intent.data?.toString() ?: return
            pendingOAuthCallback.value = data
        }
    }

    companion object {
        // Holds the OAuth callback URL for the composable to process.
        val pendingOAuthCallback = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    }
}

// Nav items: Settings is now under More (per owner feedback)
private val navItems = listOf(
    NavItem("home", "Home", NavIcons.Home),
    NavItem("library", "Library", NavIcons.Library),
    NavItem("search", "Search", NavIcons.Search),
    NavItem("more", "More", NavIcons.More),
)

@Composable
private fun AnikutaApp() {
    var currentRoute by remember { mutableStateOf("home") }
    var detailAnimeId by remember { mutableStateOf<Int?>(null) }
    var showExtensions by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showRepoSettings by remember { mutableStateOf(false) }
    var resolverState by remember { mutableStateOf<VideoResolverState>(VideoResolverState.Hidden) }
    var watchTarget by remember { mutableStateOf<WatchRequest?>(null) }
    // ── Agent 2: Profile + Trackers ──
    var showProfile by remember { mutableStateOf(false) }
    var showTrackers by remember { mutableStateOf(false) }
    // Holds the tracker ID when an OAuth login is in progress (for the callback).
    var pendingTrackerAuth by remember { mutableStateOf<Int?>(null) }
    // Episode settings sub-page (Hub / Display / Layout / Metadata). Null = not in the flow.
    // The app uses a hand-rolled state-machine for navigation (NOT Voyager / Compose Nav).
    var episodeSettingsPage by remember {
        mutableStateOf<app.confused.anikuta.feature.episodesettings.EpisodeSettingsPage?>(null)
    }
    val anilistApi = remember { AniListApi() }
    val extensionManager: AnimeExtensionManager = koinInject()
    val sourceMatcher: SourceMatcher = koinInject()
    val resolverService = remember { ResolverService() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Search-page stores (registered in searchModule + extensionModule)
    val recentsStore: RecentSearchesStore = koinInject()
    val extensionLinkStore: ExtensionLinkStore = koinInject()
    val searchUiPreferences: app.confused.anikuta.feature.search.data.SearchUiPreferences = koinInject()

    // Repo layer (for the ExtensionsSettings + RepoSettings screens)
    val repoRepository: ExtensionRepoRepository = koinInject()
    val repoApi: ExtensionRepoApi = koinInject()

    // Tracks the episode+source+episodeList+watchCtx being resolved (for retry + watch page)
    var resolveTarget by remember {
        mutableStateOf<ResolveTarget?>(null)
    }

    // The extension result currently being linked to AniList (search page → linking sheet).
    // Null when no linking is in progress.
    var linkingTarget by remember {
        mutableStateOf<Pair<AnimeCatalogueSource, SAnime>?>(null)
    }

    // Handle back gesture for sub-screens + resolver sheet + linking sheet + episode-settings sub-pages
    BackHandler(enabled = watchTarget != null || detailAnimeId != null || showExtensions || showSettings || showRepoSettings || resolverState !is VideoResolverState.Hidden || linkingTarget != null || episodeSettingsPage != null || showProfile || showTrackers) {
        when {
            watchTarget != null -> watchTarget = null
            resolverState !is VideoResolverState.Hidden -> resolverState = VideoResolverState.Hidden
            linkingTarget != null -> linkingTarget = null
            episodeSettingsPage != null -> {
                // Pop sub-page → Hub; Hub → exit the flow entirely.
                episodeSettingsPage =
                    if (episodeSettingsPage == app.confused.anikuta.feature.episodesettings.EpisodeSettingsPage.Hub) null
                    else app.confused.anikuta.feature.episodesettings.EpisodeSettingsPage.Hub
            }
            // ── Agent 2: Profile + Trackers ──
            showTrackers -> showTrackers = false
            showProfile -> showProfile = false
            detailAnimeId != null -> {
                detailAnimeId = null
                resolverState = VideoResolverState.Hidden
            }
            showRepoSettings -> showRepoSettings = false
            showExtensions -> showExtensions = false
            showSettings -> showSettings = false
        }
    }

    // ── Agent 2: Process OAuth callback when a redirect is received ──
    val trackerManager: app.confused.anikuta.core.tracker.TrackerManager = koinInject()
    androidx.compose.runtime.LaunchedEffect(Unit) {
        MainActivity.pendingOAuthCallback.collect { callbackUrl ->
            if (callbackUrl != null) {
                // Determine which tracker to use based on the callback URL host.
                // AniList redirects to aniyomi://anilist-auth#access_token=...
                // MAL redirects to aniyomi://myanimelist-auth?code=...
                val uri = android.net.Uri.parse(callbackUrl)
                val trackerId = when (uri.host) {
                    "anilist-auth" -> app.confused.anikuta.core.tracker.Tracker.ANILIST_ID
                    "myanimelist-auth" -> app.confused.anikuta.core.tracker.Tracker.MAL_ID
                    else -> pendingTrackerAuth // fallback to the pending tracker
                }
                val tracker = trackerId?.let { trackerManager.getTracker(it) }
                if (tracker != null) {
                    scope.launch {
                        val success = tracker.handleAuthCallback(callbackUrl)
                        if (success) {
                            Toast.makeText(context, "${tracker.name} connected", Toast.LENGTH_SHORT).show()
                            Log.i("AnikutaTracker", "${tracker.name} login successful")
                        } else {
                            Toast.makeText(context, "${tracker.name} login failed", Toast.LENGTH_SHORT).show()
                            Log.e("AnikutaTracker", "${tracker.name} login failed")
                        }
                        pendingTrackerAuth = null
                        MainActivity.pendingOAuthCallback.value = null
                    }
                } else {
                    pendingTrackerAuth = null
                    MainActivity.pendingOAuthCallback.value = null
                }
            }
        }
    }

    /**
     * Resolves videos from [source] for [episode] and updates [resolverState].
     * Called when the user taps an episode on the detail screen.
     * Stores the full [episodeList] + [watchCtx] so the watch page can switch
     * episodes + render rich metadata.
     */
    fun resolveEpisode(
        episode: SEpisode,
        source: AnimeSource,
        episodeList: List<SEpisode>,
        watchCtx: app.confused.anikuta.feature.animedetails.WatchEpisodeContext,
    ) {
        val epNum = episode.episode_number.toInt().let { if (it > 0) it else 0 }
        resolveTarget = ResolveTarget(episode, source, episodeList, watchCtx)
        resolverState = VideoResolverState.Resolving(epNum)
        Log.i("AnikutaResolver", "Resolving: ${episode.name} from ${source.name} (${episodeList.size} episodes)")

        scope.launch {
            when (val result = resolverService.resolve(source, episode)) {
                is ResolverResult.Success -> {
                    resolverState = VideoResolverState.Show(epNum, result.servers)
                }
                is ResolverResult.NoSources -> {
                    resolverState = VideoResolverState.NoSources(epNum)
                    Toast.makeText(context, "No video sources available", Toast.LENGTH_SHORT).show()
                }
                is ResolverResult.Error -> {
                    resolverState = VideoResolverState.Error(epNum, result.message)
                    Toast.makeText(context, "Failed to resolve: ${result.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when {
            // Watch page (full screen, no bottom nav) — MUST come before detailAnimeId
            watchTarget != null -> {
                WatchScreen(
                    watchRequest = watchTarget!!,
                    onBack = { watchTarget = null },
                )
            }
            // Detail screen (full screen, no bottom nav)
            detailAnimeId != null -> {
                AnimeDetailScreen(
                    animeId = detailAnimeId!!,
                    api = anilistApi,
                    extensionManager = extensionManager,
                    sourceMatcher = sourceMatcher,
                    extensionLinkStore = extensionLinkStore,
                    onBack = {
                        detailAnimeId = null
                        resolverState = VideoResolverState.Hidden
                    },
                    onOpenEpisode = { episode, source, episodeList, watchCtx ->
                        resolveEpisode(episode, source, episodeList, watchCtx)
                    },
                )
            }
            // Repo settings sub-screen (from Extensions) — MUST come before showExtensions
            showRepoSettings -> {
                ExtensionRepoSettingsScreen(
                    repoRepository = repoRepository,
                    repoApi = repoApi,
                    onBack = { showRepoSettings = false },
                )
            }
            // Extensions sub-screen (from Settings)
            showExtensions -> {
                ExtensionsSettingsScreen(
                    extensionManager = extensionManager,
                    repoRepository = repoRepository,
                    onBack = { showExtensions = false },
                    onOpenRepoSettings = { showRepoSettings = true },
                )
            }
            // Episode settings sub-page (full screen — Hub / Display / Layout / Metadata)
            episodeSettingsPage != null -> {
                when (val page = episodeSettingsPage!!) {
                    app.confused.anikuta.feature.episodesettings.EpisodeSettingsPage.Hub -> {
                        app.confused.anikuta.feature.episodesettings.EpisodeSettingsHubScreen(
                            onBack = { episodeSettingsPage = null },
                            onOpenDisplay = { episodeSettingsPage = app.confused.anikuta.feature.episodesettings.EpisodeSettingsPage.Display },
                            onOpenLayout = { episodeSettingsPage = app.confused.anikuta.feature.episodesettings.EpisodeSettingsPage.Layout },
                            onOpenMetadata = { episodeSettingsPage = app.confused.anikuta.feature.episodesettings.EpisodeSettingsPage.Metadata },
                        )
                    }
                    app.confused.anikuta.feature.episodesettings.EpisodeSettingsPage.Display -> {
                        app.confused.anikuta.feature.episodesettings.EpisodeDisplaySettingsScreen(
                            onBack = { episodeSettingsPage = app.confused.anikuta.feature.episodesettings.EpisodeSettingsPage.Hub },
                        )
                    }
                    app.confused.anikuta.feature.episodesettings.EpisodeSettingsPage.Layout -> {
                        app.confused.anikuta.feature.episodesettings.EpisodeLayoutSettingsScreen(
                            onBack = { episodeSettingsPage = app.confused.anikuta.feature.episodesettings.EpisodeSettingsPage.Hub },
                        )
                    }
                    app.confused.anikuta.feature.episodesettings.EpisodeSettingsPage.Metadata -> {
                        app.confused.anikuta.feature.episodesettings.EpisodeMetadataSettingsScreen(
                            onBack = { episodeSettingsPage = app.confused.anikuta.feature.episodesettings.EpisodeSettingsPage.Hub },
                        )
                    }
                }
            }
            // Settings sub-screen (from More)
            showSettings -> {
                SettingsScreen(
                    onOpenExtensions = { showExtensions = true },
                    onOpenEpisodeSettings = { episodeSettingsPage = app.confused.anikuta.feature.episodesettings.EpisodeSettingsPage.Hub },
                    onBack = { showSettings = false },
                )
            }
            // ── Agent 2: Profile + Trackers — full-screen pages ──
            showProfile -> {
                app.confused.anikuta.feature.my.ProfileScreen(
                    onOpenAnime = { id -> detailAnimeId = id },
                    onOpenTrackers = {
                        showProfile = false
                        showTrackers = true
                    },
                )
            }
            showTrackers -> {
                app.confused.anikuta.feature.trackers.TrackersSettingsScreen(
                    onBack = { showTrackers = false },
                    onLoginTracker = { trackerId ->
                        pendingTrackerAuth = trackerId
                        val tracker = trackerManager.getTracker(trackerId)
                        val authUrl = tracker?.getAuthUrl()
                        if (authUrl != null) {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(authUrl))
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        }
                    },
                )
            }
            // Tab content
            else -> {
                when (currentRoute) {
                    "home" -> BrowseScreen(
                        api = anilistApi,
                        onOpenAnime = { id -> detailAnimeId = id },
                    )
                    "library" -> LibraryScreen(
                        onOpenAnime = { id -> detailAnimeId = id },
                        onOpenContinueWatching = { item -> detailAnimeId = item.anilistId },
                    )
                    "search" -> SearchScreen(
                        anilistApi = anilistApi,
                        extensionManager = extensionManager,
                        sourceMatcher = sourceMatcher,
                        recentsStore = recentsStore,
                        uiPreferences = searchUiPreferences,
                        onOpenAnime = { id -> detailAnimeId = id },
                        onOpenExtensionResult = { result ->
                            // Start the extension→AniList linking flow.
                            // The linking sheet auto-searches AniList by the SAnime title;
                            // on success it calls onLinked (opens detail). The sheet is
                            // delayed 400ms so fast resolves skip it entirely (smoother UX).
                            linkingTarget = result.source to result.sAnime
                        },
                    )
                    "more" -> MoreScreen(
                        onOpenSettings = { showSettings = true },
                        onOpenProfile = { showProfile = true },
                        onOpenTrackers = { showTrackers = true },
                    )
                    else -> PlaceholderScreen(title = currentRoute.replaceFirstChar { it.uppercase() })
                }

                // Floating bottom nav
                AnikutaBottomNavBar(
                    items = navItems,
                    currentRoute = currentRoute,
                    onSelect = { route -> currentRoute = route },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }

        // Video resolver overlay — renders on top of any screen (detail, browse, etc.)
        if (resolverState !is VideoResolverState.Hidden) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
                    .clickable { resolverState = VideoResolverState.Hidden },
                contentAlignment = Alignment.BottomCenter,
            ) {
                VideoResolverSheet(
                    state = resolverState,
                    onDismiss = { resolverState = VideoResolverState.Hidden },
                    onVideoSelected = { video ->
                        Log.i("AnikutaResolver", "Video selected: ${video.quality} (${video.url})")
                        // CRITICAL: Capture servers BEFORE clearing resolverState.
                        // Otherwise the servers are lost and the quality sheet
                        // will be empty in the watch page.
                        val servers = (resolverState as? VideoResolverState.Show)?.servers ?: emptyList()
                        resolverState = VideoResolverState.Hidden
                        val target = resolveTarget
                        if (target != null) {
                            watchTarget = WatchRequest(
                                videoUrl = video.url,
                                videoHeaders = video.videoHeaders,
                                videoTitle = video.videoTitle.ifBlank { target.episode.name },
                                anilistId = detailAnimeId ?: 0,
                                animeTitle = target.watchCtx.animeTitle,
                                coverUrl = target.watchCtx.coverUrl,
                                coverColor = null, // TODO: extract from coverUrl via Palette
                                episodeUrl = target.episode.url,
                                episodeNumber = target.episode.episode_number,
                                sourceId = target.source.id,
                                source = target.source,
                                videoServer = "",
                                videoAudio = "",
                                videoQuality = 0,
                                episodeList = target.episodeList,
                                episodeMetadata = target.watchCtx.episodeMetadata,
                                subtitleTracks = video.subtitleTracks,
                                audioTracks = video.audioTracks,
                                resolvedServers = servers,
                            )
                        }
                    },
                    onRetry = {
                        resolveTarget?.let { rt ->
                            resolveEpisode(rt.episode, rt.source, rt.episodeList, rt.watchCtx)
                        }
                    },
                )
            }
        }

        // Extension→AniList linking sheet overlay (Phase D).
        // Shown when the user taps an extension result on the Search page.
        // The sheet auto-searches AniList by the SAnime title:
        //  - On link success → opens the existing AnimeDetailScreen (by AniList ID).
        //    A "Linked to AniList" toast is shown ONLY on fresh links (auto or
        //    manual) — NOT on cache hits (per owner: "it should not show that
        //    always"). The [wasCached] flag from the VM drives this.
        //  - On "go without linking" → shows a toast (extension-only detail page
        //    is a future enhancement; the existing AnimeDetailScreen needs an ID).
        if (linkingTarget != null) {
            val (source, sAnime) = linkingTarget!!
            ExtensionLinkingSheet(
                source = source,
                sAnime = sAnime,
                anilistApi = anilistApi,
                linkStore = extensionLinkStore,
                onLinked = { anilistId, wasCached ->
                    linkingTarget = null
                    if (!wasCached) {
                        Toast.makeText(context, "Linked to AniList", Toast.LENGTH_SHORT).show()
                        Log.i("AnikutaSearch", "Linked (fresh): ${sAnime.title} → AniList $anilistId")
                    } else {
                        Log.i("AnikutaSearch", "Linked (cached): ${sAnime.title} → AniList $anilistId (no toast)")
                    }
                    detailAnimeId = anilistId
                },
                onGoWithoutLinking = { extSource, extSAnime ->
                    linkingTarget = null
                    Toast.makeText(
                        context,
                        "Extension-only detail page is a future enhancement. " +
                            "Install the AniList-linked version to watch.",
                        Toast.LENGTH_LONG,
                    ).show()
                    Log.i("AnikutaSearch", "Go-without-linking: ${extSAnime.title} from ${extSource.name}")
                },
                onDismiss = { linkingTarget = null },
            )
        }
    }
}

/**
 * More screen — a list with Settings and other options.
 */
@Composable
private fun MoreScreen(
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit = {},
    onOpenTrackers: () -> Unit = {},
) {
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize()) {
        CollapsingHeader(title = "More", scrollState = scrollState)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 110.dp),
        ) {
            // ── Agent 2: Profile + Trackers entries (at top, per design) ──
            item {
                app.confused.anikuta.feature.my.ProfileTrackersMoreEntries(
                    onOpenProfile = onOpenProfile,
                    onOpenTrackers = onOpenTrackers,
                )
            }
            item {
                SettingsSectionLabel("General")
                MoreRow(
                    icon = Icons.Filled.Settings,
                    title = "Settings",
                    subtitle = "Theme, display, data management",
                    onClick = onOpenSettings,
                )
            }
        }
    }
}

/**
 * Settings screen — a sub-screen from More.
 *
 * Per user requirement: the episode settings are reached via a SINGLE "Episode
 * settings" row that navigates to a full-page hub (NOT a bottom sheet). The hub
 * then links to Display / Layout / Metadata sub-pages. See
 * `:feature:episode-settings` for the screens.
 */
@Composable
private fun SettingsScreen(
    onOpenExtensions: () -> Unit,
    onOpenEpisodeSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize()) {
        CollapsingHeader(title = "Settings", scrollState = scrollState)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 110.dp),
        ) {
            item {
                SettingsSectionLabel("General")
                MoreRow(
                    icon = Icons.Filled.Extension,
                    title = "Extensions",
                    subtitle = "Manage anime and manga extensions",
                    onClick = onOpenExtensions,
                )
            }
            item {
                SettingsSectionLabel("Episode List")
                MoreRow(
                    icon = Icons.Filled.Tune,
                    title = "Episode settings",
                    subtitle = "Display, layout, and metadata fetching for the episode list",
                    onClick = onOpenEpisodeSettings,
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text = text,
        fontFamily = RobotoFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun MoreRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = RobotoFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    fontFamily = RobotoFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize()) {
        CollapsingHeader(title = title, scrollState = scrollState)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$title — coming in a future phase",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Holds the episode being resolved + its source + the full episode list + the
 * watch context (anime title, cover, metadata map). Used for retry-on-error
 * and for constructing the [WatchRequest] when the user picks a video.
 */
private data class ResolveTarget(
    val episode: SEpisode,
    val source: AnimeSource,
    val episodeList: List<SEpisode>,
    val watchCtx: app.confused.anikuta.feature.animedetails.WatchEpisodeContext,
)

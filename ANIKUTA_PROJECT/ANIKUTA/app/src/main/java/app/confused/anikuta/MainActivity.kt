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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Settings
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
import app.confused.anikuta.data.extension.matcher.SourceMatcher
import app.confused.anikuta.data.extension.repo.ExtensionRepoApi
import app.confused.anikuta.data.extension.repo.ExtensionRepoRepository
import app.confused.anikuta.feature.animedetails.AnimeDetailScreen
import app.confused.anikuta.feature.browse.BrowseScreen
import app.confused.anikuta.feature.extensionssettings.ExtensionRepoSettingsScreen
import app.confused.anikuta.feature.extensionssettings.ExtensionsSettingsScreen
import app.confused.anikuta.feature.videoresolver.ResolverResult
import app.confused.anikuta.feature.videoresolver.ResolverService
import app.confused.anikuta.feature.videoresolver.VideoResolverSheet
import app.confused.anikuta.feature.videoresolver.VideoResolverState
import app.confused.anikuta.feature.watch.WatchRequest
import app.confused.anikuta.feature.watch.WatchScreen
import eu.kanade.tachiyomi.animesource.AnimeSource
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
    val anilistApi = remember { AniListApi() }
    val extensionManager: AnimeExtensionManager = koinInject()
    val sourceMatcher: SourceMatcher = koinInject()
    val resolverService = remember { ResolverService() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Repo layer (for the ExtensionsSettings + RepoSettings screens)
    val repoRepository: ExtensionRepoRepository = koinInject()
    val repoApi: ExtensionRepoApi = koinInject()

    // Tracks the episode+source being resolved (for retry on Error)
    var resolveTarget by remember {
        mutableStateOf<Pair<SEpisode, AnimeSource>?>(null)
    }

    // Handle back gesture for sub-screens + resolver sheet
    BackHandler(enabled = watchTarget != null || detailAnimeId != null || showExtensions || showSettings || showRepoSettings || resolverState !is VideoResolverState.Hidden) {
        when {
            watchTarget != null -> watchTarget = null
            resolverState !is VideoResolverState.Hidden -> resolverState = VideoResolverState.Hidden
            detailAnimeId != null -> {
                detailAnimeId = null
                resolverState = VideoResolverState.Hidden
            }
            showRepoSettings -> showRepoSettings = false
            showExtensions -> showExtensions = false
            showSettings -> showSettings = false
        }
    }

    /**
     * Resolves videos from [source] for [episode] and updates [resolverState].
     * Called when the user taps an episode on the detail screen.
     */
    fun resolveEpisode(episode: SEpisode, source: AnimeSource) {
        val epNum = episode.episode_number.toInt().let { if (it > 0) it else 0 }
        resolveTarget = episode to source
        resolverState = VideoResolverState.Resolving(epNum)
        Log.i("AnikutaResolver", "Resolving: ${episode.name} from ${source.name}")

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
                    onBack = {
                        detailAnimeId = null
                        resolverState = VideoResolverState.Hidden
                    },
                    onOpenEpisode = { episode, source ->
                        resolveEpisode(episode, source)
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
            // Settings sub-screen (from More)
            showSettings -> {
                SettingsScreen(
                    onOpenExtensions = { showExtensions = true },
                    onBack = { showSettings = false },
                )
            }
            // Tab content
            else -> {
                when (currentRoute) {
                    "home" -> BrowseScreen(
                        api = anilistApi,
                        onOpenAnime = { id -> detailAnimeId = id },
                    )
                    "more" -> MoreScreen(
                        onOpenSettings = { showSettings = true },
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
                        resolverState = VideoResolverState.Hidden
                        val episode = resolveTarget?.first
                        val source = resolveTarget?.second
                        if (episode != null && source != null) {
                            watchTarget = WatchRequest(
                                videoUrl = video.url,
                                videoHeaders = video.videoHeaders,
                                videoTitle = video.videoTitle.ifBlank { episode.name },
                                anilistId = detailAnimeId ?: 0,
                                animeTitle = "",
                                coverUrl = null,
                                coverColor = null,
                                episodeUrl = episode.url,
                                episodeNumber = episode.episode_number,
                                sourceId = source.id,
                                source = source,
                                videoServer = "",
                                videoAudio = "",
                                videoQuality = 0,
                                episodeList = listOf(episode),
                            )
                        }
                    },
                    onRetry = {
                        resolveTarget?.let { (episode, source) ->
                            resolveEpisode(episode, source)
                        }
                    },
                )
            }
        }
    }
}

/**
 * More screen — a list with Settings and other options.
 */
@Composable
private fun MoreScreen(
    onOpenSettings: () -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize()) {
        CollapsingHeader(title = "More", scrollState = scrollState)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 110.dp),
        ) {
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
 */
@Composable
private fun SettingsScreen(
    onOpenExtensions: () -> Unit,
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

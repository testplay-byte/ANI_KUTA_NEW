package app.confused.anikuta

import android.os.Bundle
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import app.confused.anikuta.feature.animedetails.AnimeDetailScreen
import app.confused.anikuta.feature.browse.BrowseScreen
import app.confused.anikuta.feature.extensionssettings.ExtensionsSettingsScreen
import app.confused.anikuta.feature.videoresolver.VideoResolverSheet
import app.confused.anikuta.feature.videoresolver.VideoResolverState

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
    var resolverState by remember { mutableStateOf<VideoResolverState>(VideoResolverState.Hidden) }
    val anilistApi = remember { AniListApi() }

    // Handle back gesture for sub-screens + resolver sheet
    BackHandler(enabled = detailAnimeId != null || showExtensions || showSettings || resolverState !is VideoResolverState.Hidden) {
        when {
            resolverState !is VideoResolverState.Hidden -> resolverState = VideoResolverState.Hidden
            detailAnimeId != null -> detailAnimeId = null
            showExtensions -> showExtensions = false
            showSettings -> showSettings = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when {
            // Detail screen (full screen, no bottom nav)
            detailAnimeId != null -> {
                AnimeDetailScreen(
                    animeId = detailAnimeId!!,
                    api = anilistApi,
                    onBack = { detailAnimeId = null },
                    onOpenEpisode = { epNum ->
                        // No extensions loaded yet — show "No sources" state
                        resolverState = VideoResolverState.NoSources(episodeNumber = epNum)
                    },
                )
            }
            // Extensions sub-screen (from Settings)
            showExtensions -> {
                ExtensionsSettingsScreen(
                    onBack = { showExtensions = false },
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
                // Video resolver sheet overlay (shows on top of the detail screen)
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
                                resolverState = VideoResolverState.Hidden
                                // Phase 6: open the watch page / player with this video
                            },
                        )
                    }
                }
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

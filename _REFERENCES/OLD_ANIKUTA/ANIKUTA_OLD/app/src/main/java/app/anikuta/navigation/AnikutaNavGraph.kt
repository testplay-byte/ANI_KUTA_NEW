package app.anikuta.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.anikuta.ui.detail.DetailScreen
import app.anikuta.ui.home.HomeScreen
import app.anikuta.ui.library.LibraryScreen
import app.anikuta.ui.history.HistoryScreen
import app.anikuta.ui.search.SearchScreen
import app.anikuta.ui.settings.MoreScreen
import app.anikuta.player.PlaybackStateStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

sealed class Screen(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    data object Home : Screen("home", "Home", Icons.Filled.Home, Icons.Outlined.Home)
    data object Library : Screen("library", "Library", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic)
    data object History : Screen("history", "History", Icons.Filled.History, Icons.Outlined.History)
    data object Search : Screen("search", "Search", Icons.Filled.Search, Icons.Outlined.Search)
    data object More : Screen("more", "More", Icons.Filled.MoreHoriz, Icons.Outlined.MoreHoriz)
    data object Detail : Screen("detail/{anilistId}", "Detail", Icons.Filled.Home, Icons.Outlined.Home)
}

private val bottomNavScreens = listOf(Screen.Home, Screen.Library, Screen.History, Screen.Search, Screen.More)

@Composable
fun AnikutaNavGraph() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = currentDestination?.route in bottomNavScreens.map { it.route }

    // Phase N-3: Check for pending deep-link navigation from a notification tap.
    // NotificationDispatcher sets NotificationDeepLink.pendingDetailId before
    // launching MainActivity; this LaunchedEffect reads + clears it on start.
    LaunchedEffect(Unit) {
        app.anikuta.notification.NotificationDeepLink.consumePendingNavigation()?.let { (anilistId, autoPlayUrl) ->
            if (autoPlayUrl != null) {
                navController.navigate("detail/$anilistId?autoPlayUrl=$autoPlayUrl")
            } else {
                navController.navigate("detail/$anilistId")
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                // Floating pill-style bottom nav — the area behind the pill
                // is completely transparent so the content scrolls under it.
                // Only the pill Surface itself has a background.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 0.dp,
                        shadowElevation = 8.dp,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            bottomNavScreens.forEach { screen ->
                                val isSelected = currentDestination?.hierarchy?.any { it.route == screen.route } == true

                                val iconScale by animateFloatAsState(
                                    targetValue = if (isSelected) 1.15f else 1f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium,
                                    ),
                                    label = "nav_icon_scale",
                                )

                                // Each nav item is a clickable Surface that gets
                                // a secondaryContainer background when selected.
                                Surface(
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                                    else androidx.compose.ui.graphics.Color.Transparent,
                                    onClick = {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                    ) {
                                        Icon(
                                            if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                                            contentDescription = screen.label,
                                            modifier = Modifier
                                                .size(22.dp)
                                                .scale(iconScale),
                                            tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        if (isSelected) {
                                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(4.dp))
                                            Text(
                                                screen.label,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            // Only apply bottom padding (for nav bar height) — NOT top, so home + detail can be edge-to-edge
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onAnimeClick = { anilistId ->
                        navController.navigate("detail/$anilistId")
                    },
                )
            }
            composable(Screen.Library.route) {
                LibraryScreen(
                    onAnimeClick = { anilistId ->
                        navController.navigate("detail/$anilistId")
                    },
                )
            }
            composable(Screen.History.route) {
                HistoryScreen(
                    onResume = { anilistId, episodeUrl, title ->
                        // Phase F — direct resume: check PlaybackStateStore for a saved
                        // video URL. If found, launch the player directly (skips the
                        // "Resolving video" step on the detail page). If not found,
                        // fall back to the detail page with autoPlayUrl.
                        val context = navController.context
                        try {
                            val pbStore: PlaybackStateStore = Injekt.get()
                            val saved = pbStore.get(anilistId, episodeUrl)
                            // Also fetch watch progress for cover URL + anime title + episode number
                            val wpStore: app.anikuta.player.WatchProgressStore = Injekt.get()
                            val progress = wpStore.get(anilistId, episodeUrl)
                            if (saved != null && saved.videoUrl.isNotBlank()) {
                                // Direct launch with the saved video URL + server + audio + quality
                                val intent = app.anikuta.player.PlayerActivity.newIntent(
                                    context = context,
                                    videoUrl = saved.videoUrl,
                                    title = title,
                                    anilistId = anilistId,
                                    episodeUrl = episodeUrl,
                                    episodeNumber = progress?.episodeNumber ?: -1f,
                                    videoHeaders = saved.videoHeaders,
                                    coverUrl = progress?.coverUrl ?: "",
                                    animeTitle = progress?.animeTitle ?: "",
                                    sourceId = saved.sourceId,
                                    videoServer = saved.videoServer,
                                    videoAudio = saved.videoAudio,
                                    videoQuality = saved.videoQuality,
                                )
                                context.startActivity(intent)
                            } else {
                                // No saved state — fall back to detail with autoPlayUrl
                                val encoded = java.net.URLEncoder.encode(episodeUrl, "UTF-8")
                                navController.navigate("detail/$anilistId?autoPlayUrl=$encoded")
                            }
                        } catch (e: Exception) {
                            // PlaybackStateStore not available — fall back to detail
                            val encoded = java.net.URLEncoder.encode(episodeUrl, "UTF-8")
                            navController.navigate("detail/$anilistId?autoPlayUrl=$encoded")
                        }
                    },
                    onOpenDetail = { anilistId ->
                        navController.navigate("detail/$anilistId")
                    },
                )
            }
            composable(Screen.Search.route) {
                SearchScreen(
                    onAnimeClick = { anilistId ->
                        navController.navigate("detail/$anilistId")
                    },
                    onSourceResultClick = { route ->
                        navController.navigate(route)
                    },
                )
            }
            composable(Screen.More.route) {
                MoreScreen(
                    onOpenDebug = { navController.navigate("debug") },
                    onNavigate = { route -> navController.navigate(route) },
                )
            }
            composable(
                route = "detail/{anilistId}?autoPlayUrl={autoPlayUrl}",
                arguments = listOf(
                    navArgument("anilistId") { type = NavType.IntType },
                    navArgument("autoPlayUrl") {
                        type = NavType.StringType
                        defaultValue = ""
                        nullable = true
                    },
                ),
            ) { backStackEntry ->
                val anilistId = backStackEntry.arguments?.getInt("anilistId") ?: 0
                val autoPlayUrl = backStackEntry.arguments?.getString("autoPlayUrl")?.let {
                    java.net.URLDecoder.decode(it, "UTF-8")
                } ?: ""
                DetailScreen(
                    anilistId = anilistId,
                    autoPlayUrl = autoPlayUrl,
                    onBack = { navController.popBackStack() },
                )
            }
            // Phase I — Source linking (from extension search results)
            // Checks ExtensionLinkStore first; if linked, goes to DetailScreen;
            // if not, shows SourceLinkingScreen which searches AniList + links.
            composable(
                route = "source-link/{sourceId}/{animeUrl}/{title}/{thumbnailUrl}",
                arguments = listOf(
                    navArgument("sourceId") { type = NavType.LongType },
                    navArgument("animeUrl") { type = NavType.StringType },
                    navArgument("title") { type = NavType.StringType },
                    navArgument("thumbnailUrl") { type = NavType.StringType },
                ),
            ) { backStackEntry ->
                val sourceId = backStackEntry.arguments?.getLong("sourceId") ?: -1L
                val animeUrl = backStackEntry.arguments?.getString("animeUrl")?.let {
                    java.net.URLDecoder.decode(it, "UTF-8")
                } ?: ""
                val title = backStackEntry.arguments?.getString("title")?.let {
                    java.net.URLDecoder.decode(it, "UTF-8")
                } ?: ""
                val thumbnailUrl = backStackEntry.arguments?.getString("thumbnailUrl")?.let {
                    java.net.URLDecoder.decode(it, "UTF-8")
                } ?: ""

                // Check if already linked
                val linkStore: app.anikuta.data.cache.ExtensionLinkStore = try {
                    Injekt.get()
                } catch (e: Exception) { null!! }

                val linkedId = linkStore?.getAniListId(sourceId, animeUrl)
                if (linkedId != null && linkedId > 0) {
                    // Already linked → go directly to DetailScreen
                    LaunchedEffect(Unit) {
                        navController.navigate("detail/$linkedId") {
                            popUpTo("source-link/$sourceId/${java.net.URLEncoder.encode(animeUrl, "UTF-8")}/${java.net.URLEncoder.encode(title, "UTF-8")}/${java.net.URLEncoder.encode(thumbnailUrl, "UTF-8")}") {
                                inclusive = true
                            }
                        }
                    }
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    // Not linked → show the linking screen
                    app.anikuta.ui.detail.SourceLinkingScreen(
                        sourceId = sourceId,
                        animeUrl = animeUrl,
                        title = title,
                        thumbnailUrl = thumbnailUrl.ifBlank { null },
                        sourceName = "",
                        onLinked = { anilistId ->
                            navController.navigate("detail/$anilistId") {
                                popUpTo("source-link/$sourceId/${java.net.URLEncoder.encode(animeUrl, "UTF-8")}/${java.net.URLEncoder.encode(title, "UTF-8")}/${java.net.URLEncoder.encode(thumbnailUrl, "UTF-8")}") {
                                    inclusive = true
                                }
                            }
                        },
                        onAniListNotFound = {
                            // No longer used — the linking screen now handles everything
                            // (shows search results + manual search). This callback is kept
                            // for API compatibility but does nothing.
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            // Hidden debug screen (Phase 5 task 5.1). Accessible via long-press
            // on the version number in About settings. Easily removable: delete this
            // composable + the DebugScreen file + the long-press handler.
            composable(route = "debug") {
                app.anikuta.ui.debug.DebugScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            // --- Settings subpages (Phase 6 task 6.17-6.24) ---
            composable("settings/general") {
                app.anikuta.ui.settings.GeneralSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable("settings/library") {
                app.anikuta.ui.settings.LibrarySettingsScreen(onBack = { navController.popBackStack() })
            }
            composable("settings/history") {
                app.anikuta.ui.settings.HistorySettingsScreen(onBack = { navController.popBackStack() })
            }
            composable("settings/search") {
                app.anikuta.ui.settings.SearchSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable("settings/notifications") {
                app.anikuta.ui.settings.NotificationSettingsScreen(
                    onBack = { navController.popBackStack() },
                    onNavigate = { route -> navController.navigate(route) },
                )
            }
            composable("settings/notifications/tracked") {
                app.anikuta.ui.settings.TrackedAnimeScreen(
                    mode = app.anikuta.ui.detail.SettingsMode.NOTIFICATIONS,
                    onBack = { navController.popBackStack() },
                )
            }
            composable("settings/data") {
                app.anikuta.ui.settings.StorageSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable("settings/player") {
                app.anikuta.ui.settings.PlayerSettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenEpisodeDisplay = { navController.navigate("settings/player/episodes") },
                    onOpenSubtitleSettings = { navController.navigate("settings/player/subtitles") },
                    onOpenGeneral = { navController.navigate("settings/player/general") },
                    onOpenPlayback = { navController.navigate("settings/player/playback") },
                    onOpenDisplayBehavior = { navController.navigate("settings/player/display-behavior") },
                )
            }
            composable("settings/player/episodes") {
                app.anikuta.ui.settings.PlayerEpisodeDisplayScreen(onBack = { navController.popBackStack() })
            }
            composable("settings/player/subtitles") {
                app.anikuta.ui.settings.SubtitleSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable("settings/player/general") {
                app.anikuta.ui.settings.PlayerGeneralSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable("settings/player/playback") {
                app.anikuta.ui.settings.PlayerPlaybackSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable("settings/player/display-behavior") {
                app.anikuta.ui.settings.PlayerDisplayBehaviorSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable("settings/details") {
                app.anikuta.ui.settings.DetailsSettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDisplay = { navController.navigate("settings/details/display") },
                    onOpenLayout = { navController.navigate("settings/details/layout") },
                    onOpenMetadata = { navController.navigate("settings/details/metadata") },
                )
            }
            composable("settings/details/display") {
                app.anikuta.ui.settings.DisplaySettingsScreen(onBack = { navController.popBackStack() })
            }
            composable("settings/details/layout") {
                app.anikuta.ui.settings.LayoutSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable("settings/details/metadata") {
                app.anikuta.ui.settings.MetadataSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable("settings/extensions") {
                app.anikuta.ui.settings.ExtensionsSettingsScreen(
                    onBack = { navController.popBackStack() },
                    onManageRepos = { navController.navigate("extension_repos") },
                    onOpenExtensionDetails = { pkgName ->
                        navController.navigate("extension_details/$pkgName")
                    },
                )
            }
            composable("settings/downloads") {
                app.anikuta.ui.download.DownloadQueueScreen(
                    onBack = { navController.popBackStack() },
                    onOpenSettings = { navController.navigate("settings/downloads/settings") },
                )
            }
            composable("settings/downloads/settings") {
                app.anikuta.ui.settings.DownloadsSettingsScreen(
                    onBack = { navController.popBackStack() },
                    onNavigate = { route -> navController.navigate(route) },
                )
            }
            composable("settings/downloads/tracked") {
                app.anikuta.ui.settings.TrackedAnimeScreen(
                    mode = app.anikuta.ui.detail.SettingsMode.DOWNLOADS,
                    onBack = { navController.popBackStack() },
                )
            }
            composable("settings/tracking") {
                app.anikuta.ui.settings.TrackingSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable("settings/backup") {
                app.anikuta.ui.settings.BackupSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable("settings/about") {
                app.anikuta.ui.settings.AboutSettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDebug = { navController.navigate("debug") },
                )
            }
            // --- Phase 7: Extension management routes ---
            composable("extension_repos") {
                app.anikuta.ui.settings.ExtensionReposScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = "extension_details/{pkgName}",
                arguments = listOf(navArgument("pkgName") { type = NavType.StringType }),
            ) { backStackEntry ->
                val pkgName = backStackEntry.arguments?.getString("pkgName") ?: ""
                app.anikuta.ui.settings.ExtensionDetailsScreen(
                    pkgName = pkgName,
                    onBack = { navController.popBackStack() },
                    onOpenSourcePreferences = { sourceId ->
                        navController.navigate("source_preferences/$sourceId")
                    },
                )
            }
            composable(
                route = "source_preferences/{sourceId}",
                arguments = listOf(navArgument("sourceId") { type = NavType.LongType }),
            ) { backStackEntry ->
                val sourceId = backStackEntry.arguments?.getLong("sourceId") ?: 0L
                app.anikuta.ui.settings.SourcePreferencesScreen(
                    sourceId = sourceId,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

package app.confused.anikuta.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.confused.anikuta.core.anilist.api.AniListApi
import app.confused.anikuta.data.extension.AnimeExtensionManager
import app.confused.anikuta.data.extension.matcher.SourceMatcher
import app.confused.anikuta.data.extension.repo.ExtensionRepoApi
import app.confused.anikuta.data.extension.repo.ExtensionRepoRepository
import app.confused.anikuta.feature.animedetails.AnimeDetailScreen
import app.confused.anikuta.feature.animedetails.ExtensionDetailScreen
import app.confused.anikuta.feature.animedetails.WatchEpisodeContext
import app.confused.anikuta.feature.backup.aniyomi.AniyomiRestoreFlow
import app.confused.anikuta.feature.backup.BackupSettingsScreen
import app.confused.anikuta.feature.browse.BrowseScreen
import app.confused.anikuta.feature.download.DownloadedFilesScreen
import app.confused.anikuta.feature.download.DownloadsScreen
import app.confused.anikuta.feature.download.DownloadSettingsScreen
import app.confused.anikuta.feature.download.ExtensionSourceInfo
import app.confused.anikuta.feature.episodesettings.EpisodeDisplaySettingsScreen
import app.confused.anikuta.feature.episodesettings.EpisodeLayoutSettingsScreen
import app.confused.anikuta.feature.episodesettings.EpisodeMetadataSettingsScreen
import app.confused.anikuta.feature.episodesettings.EpisodeSettingsHubScreen
import app.confused.anikuta.feature.extensionssettings.ExtensionRepoSettingsScreen
import app.confused.anikuta.feature.extensionssettings.ExtensionsSettingsScreen
import app.confused.anikuta.feature.history.HistoryScreen
import app.confused.anikuta.feature.library.LibraryScreen
import app.confused.anikuta.feature.my.ProfileScreen
import app.confused.anikuta.feature.search.ui.SearchScreen
import app.confused.anikuta.feature.trackers.TrackersSettingsScreen
import app.confused.anikuta.feature.updates.UpdatesScreen
import app.confused.anikuta.feature.watch.WatchRequest
import app.confused.anikuta.feature.watch.WatchScreen
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.SAnime
import org.koin.compose.koinInject

// ════════════════════════════════════════════════════════════════════════
//  Tab destinations (the 4 bottom-nav tabs)
// ════════════════════════════════════════════════════════════════════════

object BrowseTabDestination : Screen {
    @Composable
    override fun Content() {
        val appController = koinInject<AppController>()
        BrowseScreen(
            api = appController.anilistApi,
            onOpenAnime = { id -> appController.pushDetail(id) },
        )
    }
}

object LibraryTabDestination : Screen {
    @Composable
    override fun Content() {
        val appController = koinInject<AppController>()
        LibraryScreen(
            onOpenAnime = { id -> appController.pushDetail(id) },
            onOpenContinueWatching = { item -> appController.pushDetail(item.anilistId) },
        )
    }
}

object SearchTabDestination : Screen {
    @Composable
    override fun Content() {
        val appController = koinInject<AppController>()
        SearchScreen(
            anilistApi = appController.anilistApi,
            extensionManager = appController.extensionManager,
            sourceMatcher = appController.sourceMatcher,
            recentsStore = appController.recentsStore,
            uiPreferences = appController.searchUiPreferences,
            onOpenAnime = { id -> appController.pushDetail(id) },
            onOpenExtensionResult = { result ->
                // Start the extension→AniList linking flow.
                appController.startLinking(result.source, result.sAnime)
            },
        )
    }
}

object MoreTabDestination : Screen {
    @Composable
    override fun Content() {
        val appController = koinInject<AppController>()
        val navigator = LocalNavigator.currentOrThrow
        MoreScreen(
            onOpenSettings = { navigator.push(SettingsDestination) },
            onOpenHistory = { navigator.push(HistoryDestination) },
            onOpenUpdates = { navigator.push(UpdatesDestination) },
            onOpenProfile = { navigator.push(ProfileDestination) },
            onOpenTrackers = { navigator.push(TrackersDestination) },
            onOpenDownloads = { navigator.push(DownloadsDestination) },
        )
    }
}

// ════════════════════════════════════════════════════════════════════════
//  Anime detail + extension detail + watch
// ════════════════════════════════════════════════════════════════════════

data class AnimeDetailDestination(val animeId: Int) : Screen {
    @Composable
    override fun Content() {
        val appController = koinInject<AppController>()
        val navigator = LocalNavigator.currentOrThrow

        // Collect download tasks reactively for this anime's episode rows.
        val downloadTasksMap by appController.downloadTasksFlow
            .collectAsStateWithLifecycle(initialValue = emptyMap())
        val downloadStates = appController.getDownloadStates(animeId, downloadTasksMap)

        AnimeDetailScreen(
            animeId = animeId,
            api = appController.anilistApi,
            extensionManager = appController.extensionManager,
            sourceMatcher = appController.sourceMatcher,
            extensionLinkStore = appController.extensionLinkStore,
            onBack = { navigator.pop() },
            onOpenEpisode = { episode, source, episodeList, watchCtx ->
                appController.resolveEpisode(episode, source, episodeList, watchCtx, animeId)
            },
            onDownloadEpisode = { episode, source, watchCtx ->
                appController.downloadEpisode(episode, source, watchCtx, animeId)
            },
            downloadStates = downloadStates,
            onDownloadCancel = { episodeUrl -> appController.cancelDownload(animeId, episodeUrl) },
            onDownloadResume = { episodeUrl -> appController.resumeDownload(animeId, episodeUrl) },
            onDownloadRetry = { episodeUrl -> appController.retryDownload(animeId, episodeUrl) },
            onDownloadDelete = { episodeUrl -> appController.deleteDownload(animeId, episodeUrl) },
        )
    }
}

data class ExtensionDetailDestination(
    val source: AnimeCatalogueSource,
    val sAnime: SAnime,
) : Screen {
    @Composable
    override fun Content() {
        val appController = koinInject<AppController>()
        val navigator = LocalNavigator.currentOrThrow
        ExtensionDetailScreen(
            source = source,
            sAnime = sAnime,
            onBack = { navigator.pop() },
            onOpenEpisode = { episode, src, episodeList ->
                appController.resolveEpisode(
                    episode, src, episodeList,
                    WatchEpisodeContext(
                        animeTitle = sAnime.title,
                        coverUrl = sAnime.thumbnail_url,
                    ),
                    anilistId = 0,
                )
            },
            onRelinkAnilist = {
                // Open the linking sheet as an overlay — don't close the extension page.
                appController.startLinking(source, sAnime)
            },
        )
    }
}

data class WatchDestination(val watchRequest: WatchRequest) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        WatchScreen(
            watchRequest = watchRequest,
            onBack = { navigator.pop() },
        )
    }
}

// ════════════════════════════════════════════════════════════════════════
//  History + Updates
// ════════════════════════════════════════════════════════════════════════

object HistoryDestination : Screen {
    @Composable
    override fun Content() {
        val appController = koinInject<AppController>()
        val navigator = LocalNavigator.currentOrThrow
        HistoryScreen(
            onBack = { navigator.pop() },
            onOpenAnime = { anilistId -> appController.pushDetail(anilistId) },
        )
    }
}

object UpdatesDestination : Screen {
    @Composable
    override fun Content() {
        val appController = koinInject<AppController>()
        val navigator = LocalNavigator.currentOrThrow
        UpdatesScreen(
            onBack = { navigator.pop() },
            onOpenAnime = { anilistId -> appController.pushDetail(anilistId) },
        )
    }
}

// ════════════════════════════════════════════════════════════════════════
//  Profile + Trackers
// ════════════════════════════════════════════════════════════════════════

object ProfileDestination : Screen {
    @Composable
    override fun Content() {
        val appController = koinInject<AppController>()
        val navigator = LocalNavigator.currentOrThrow
        ProfileScreen(
            onOpenAnime = { id -> appController.pushDetail(id) },
            onOpenTrackers = { navigator.push(TrackersDestination) },
        )
    }
}

object TrackersDestination : Screen {
    @Composable
    override fun Content() {
        val appController = koinInject<AppController>()
        val navigator = LocalNavigator.currentOrThrow
        val context = androidx.compose.ui.platform.LocalContext.current
        TrackersSettingsScreen(
            onBack = { navigator.pop() },
            onLoginTracker = { trackerId ->
                appController.updatePendingTrackerAuth(trackerId)
                val tracker = appController.trackerManager.getTracker(trackerId)
                val authUrl = tracker?.getAuthUrl()
                if (authUrl != null) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            },
        )
    }
}

// ════════════════════════════════════════════════════════════════════════
//  Backup + Aniyomi restore
// ════════════════════════════════════════════════════════════════════════

object BackupDestination : Screen {
    @Composable
    override fun Content() {
        val appController = koinInject<AppController>()
        val navigator = LocalNavigator.currentOrThrow
        BackupSettingsScreen(
            onBack = { navigator.pop() },
            onRestoreComplete = {
                // After restore completes + user clicks OK → navigate to Library.
                appController.navigateToLibraryTab()
            },
            onAniyomiRestore = { uri ->
                navigator.push(AniyomiRestoreDestination(uri))
            },
        )
    }
}

data class AniyomiRestoreDestination(val fileUri: Uri?) : Screen {
    @Composable
    override fun Content() {
        val appController = koinInject<AppController>()
        val navigator = LocalNavigator.currentOrThrow
        AniyomiRestoreFlow(
            fileUri = fileUri,
            onCancel = { navigator.pop() },
            onComplete = { appController.navigateToLibraryTab() },
        )
    }
}

// ════════════════════════════════════════════════════════════════════════
//  Downloads + download settings + downloaded files
// ════════════════════════════════════════════════════════════════════════

object DownloadsDestination : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        DownloadsScreen(
            onBack = { navigator.pop() },
            onOpenSettings = { navigator.push(DownloadSettingsDestination) },
            onOpenDownloaded = { navigator.push(DownloadedFilesDestination) },
        )
    }
}

object DownloadSettingsDestination : Screen {
    @Composable
    override fun Content() {
        val appController = koinInject<AppController>()
        val navigator = LocalNavigator.currentOrThrow
        val extensionSources = appController.extensionManager.getTrustedExtensions().flatMap { ext ->
            ext.sources.map { source ->
                ExtensionSourceInfo(
                    sourceId = source.id,
                    sourceName = source.name,
                    extensionName = ext.name,
                )
            }
        }
        DownloadSettingsScreen(
            onBack = { navigator.pop() },
            extensionSources = extensionSources,
        )
    }
}

object DownloadedFilesDestination : Screen {
    @Composable
    override fun Content() {
        val appController = koinInject<AppController>()
        val navigator = LocalNavigator.currentOrThrow
        DownloadedFilesScreen(
            onBack = { navigator.pop() },
            onPlayEpisode = { anilistId, episodeUrl ->
                appController.pushDetail(anilistId)
            },
        )
    }
}

// ════════════════════════════════════════════════════════════════════════
//  Extensions settings + repo settings
// ════════════════════════════════════════════════════════════════════════

object ExtensionsDestination : Screen {
    @Composable
    override fun Content() {
        val appController = koinInject<AppController>()
        val navigator = LocalNavigator.currentOrThrow
        ExtensionsSettingsScreen(
            extensionManager = appController.extensionManager,
            repoRepository = appController.repoRepository,
            onBack = { navigator.pop() },
            onOpenRepoSettings = { navigator.push(ExtensionRepoDestination) },
        )
    }
}

object ExtensionRepoDestination : Screen {
    @Composable
    override fun Content() {
        val appController = koinInject<AppController>()
        val navigator = LocalNavigator.currentOrThrow
        ExtensionRepoSettingsScreen(
            repoRepository = appController.repoRepository,
            repoApi = appController.repoApi,
            onBack = { navigator.pop() },
        )
    }
}

// ════════════════════════════════════════════════════════════════════════
//  Settings + Episode settings
// ════════════════════════════════════════════════════════════════════════

object SettingsDestination : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        SettingsScreen(
            onOpenExtensions = { navigator.push(ExtensionsDestination) },
            onOpenEpisodeSettings = { navigator.push(EpisodeSettingsHubDestination) },
            onOpenBackup = { navigator.push(BackupDestination) },
            onBack = { navigator.pop() },
        )
    }
}

object EpisodeSettingsHubDestination : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        EpisodeSettingsHubScreen(
            onBack = { navigator.pop() },
            onOpenDisplay = { navigator.push(EpisodeDisplaySettingsDestination) },
            onOpenLayout = { navigator.push(EpisodeLayoutSettingsDestination) },
            onOpenMetadata = { navigator.push(EpisodeMetadataSettingsDestination) },
        )
    }
}

object EpisodeDisplaySettingsDestination : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        EpisodeDisplaySettingsScreen(onBack = { navigator.pop() })
    }
}

object EpisodeLayoutSettingsDestination : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        EpisodeLayoutSettingsScreen(onBack = { navigator.pop() })
    }
}

object EpisodeMetadataSettingsDestination : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        EpisodeMetadataSettingsScreen(onBack = { navigator.pop() })
    }
}

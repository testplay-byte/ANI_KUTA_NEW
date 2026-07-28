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
import cafe.adriel.voyager.core.screen.ScreenKey
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
            onOpenExtensionAnime = { anime -> appController.openLibraryAnime(anime) },
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
    /** Unique key per animeId — prevents Voyager SaveableStateHolder collision
     *  when two AnimeDetailDestination instances exist simultaneously (e.g. during
     *  a `navigator.replace` transition when "Switch anime" navigates to a different
     *  AniList entry). Without this, Voyager crashes with
     *  "Key AnimeDetailDestination:transition was used multiple times". */
    override val key: ScreenKey = "AnimeDetailDestination($animeId)"
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
            // "Switch anime" from the AniList page — resolve the source from the saved
            // link + start the linking flow (corrects a wrong auto-match).
            onLinkToAniList = { appController.startLinkingFromAnilist(animeId) },
            // "Switch anime" picked — update links + navigate to the new anime.
            onSwitchAnimePicked = { newId -> appController.switchAnilistAnime(animeId, newId) },
        )
    }
}

/**
 * Extension-entry variant of the unified details page. Replaces the old
 * `ExtensionDetailDestination` (which pushed the now-removed `ExtensionDetailScreen`).
 *
 * Renders the SAME `AnimeDetailScreen` in extension mode — the unified page
 * handles both data sources. The optional [anilistId] enables the AniList-merge
 * path in `ExtensionDetailsProvider` (linked extension anime get the best of both).
 */
data class ExtensionAnimeDetailDestination(
    val source: AnimeCatalogueSource,
    val sAnime: SAnime,
    val anilistId: Int? = null,
) : Screen {
    /** Unique key per source+url — prevents Voyager SaveableStateHolder collision. */
    override val key: ScreenKey = "ExtensionAnimeDetailDestination(${source.id}_${sAnime.url})"
    @Composable
    override fun Content() {
        val appController = koinInject<AppController>()
        val navigator = LocalNavigator.currentOrThrow

        // For unlinked extension anime, download states are keyed by sourceId+url
        // (not anilistId). Use 0 as the download-key fallback — the download
        // orchestrator resolves by episode URL regardless.
        val downloadKey = anilistId ?: 0
        val downloadTasksMap by appController.downloadTasksFlow
            .collectAsStateWithLifecycle(initialValue = emptyMap())
        val downloadStates = appController.getDownloadStates(downloadKey, downloadTasksMap)

        AnimeDetailScreen(
            extensionSource = source,
            extensionSAnime = sAnime,
            extensionAnilistId = anilistId,
            onBack = { navigator.pop() },
            onOpenEpisode = { episode, src, episodeList, watchCtx ->
                appController.resolveEpisode(
                    episode, src, episodeList, watchCtx,
                    anilistId = anilistId ?: 0,
                )
            },
            onDownloadEpisode = { episode, src, watchCtx ->
                appController.downloadEpisode(episode, src, watchCtx, downloadKey)
            },
            downloadStates = downloadStates,
            onDownloadCancel = { episodeUrl -> appController.cancelDownload(downloadKey, episodeUrl) },
            onDownloadResume = { episodeUrl -> appController.resumeDownload(downloadKey, episodeUrl) },
            onDownloadRetry = { episodeUrl -> appController.retryDownload(downloadKey, episodeUrl) },
            onDownloadDelete = { episodeUrl -> appController.deleteDownload(downloadKey, episodeUrl) },
            // Extension mode: "Link to AniList" opens the AniList linking sheet overlay.
            onLinkToAniList = { appController.startLinking(source, sAnime) },
            // "Switch anime" picked (linked only) — update links + navigate to the new anime.
            onSwitchAnimePicked = { newId ->
                if (anilistId != null) {
                    appController.switchAnilistAnime(anilistId, newId)
                }
            },
        )
    }
}

/**
 * Watch destination — uses an object (not a data class) so Voyager doesn't try
 * to serialize the WatchRequest (which contains non-Serializable fields like
 * AnimeSource). The WatchRequest is stored in AppController.pendingWatchRequest
 * and retrieved here via Koin injection.
 */
object WatchDestination : Screen {
    @Composable
    override fun Content() {
        val appController = koinInject<AppController>()
        val navigator = LocalNavigator.currentOrThrow
        val watchRequest = appController.pendingWatchRequest
        if (watchRequest != null) {
            WatchScreen(
                watchRequest = watchRequest,
                onBack = { navigator.pop() },
            )
        }
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
                appController.pendingAniyomiFileUri = uri
                navigator.push(AniyomiRestoreDestination)
            },
        )
    }
}

object AniyomiRestoreDestination : Screen {
    @Composable
    override fun Content() {
        val appController = koinInject<AppController>()
        val navigator = LocalNavigator.currentOrThrow
        AniyomiRestoreFlow(
            fileUri = appController.pendingAniyomiFileUri,
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
            onOpenAppearance = { navigator.push(AppearanceDestination) },
            onOpenPlayer = { navigator.push(PlayerSettingsDestination) },
            onOpenBackup = { navigator.push(BackupDestination) },
            onBack = { navigator.pop() },
        )
    }
}

/**
 * The Player settings page — auto-play toggle + future player preferences.
 */
object PlayerSettingsDestination : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        app.confused.anikuta.feature.settings.PlayerGeneralScreen(
            onBack = { navigator.pop() },
        )
    }
}

/**
 * The Appearance page — a list of option rows (General, Episode settings).
 * Per owner spec (Session 1): this screen is JUST a list of buttons. Tapping
 * General navigates to [AppearanceGeneralDestination] (the actual settings).
 */
object AppearanceDestination : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        app.confused.anikuta.feature.settings.AppearanceScreen(
            onOpenGeneral = { navigator.push(AppearanceGeneralDestination) },
            onOpenEpisodeSettings = { navigator.push(EpisodeSettingsHubDestination) },
            onBack = { navigator.pop() },
        )
    }
}

/**
 * The Appearance → General page — the actual theme settings (theme mode,
 * palettes, AMOLED). Per owner spec (Session 1 + feedback): no episode settings
 * link here (it's on the Appearance list screen).
 */
object AppearanceGeneralDestination : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        app.confused.anikuta.feature.settings.AppearanceGeneralScreen(
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

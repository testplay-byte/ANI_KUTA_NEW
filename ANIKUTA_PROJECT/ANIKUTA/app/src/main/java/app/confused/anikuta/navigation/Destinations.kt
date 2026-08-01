package app.confused.anikuta.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.confused.anikuta.core.anilist.api.AniListApi
import app.confused.anikuta.core.providerapi.MetadataProviderRegistry
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
        // Phase 7 (ADR-041): BrowseScreen resolves the active HomeFeedProvider
        // through the registry instead of calling AniListApi directly.
        val registry = koinInject<MetadataProviderRegistry>()
        BrowseScreen(
            registry = registry,
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
        // Phase 7 (ADR-041): the AniList tab routes through the registry →
        // SearchProvider / HomeFeedProvider. anilistApi is still passed because
        // the ExtensionLinkingViewModel (separate VM, opened from this screen's
        // extension result tap) uses it directly for the linking-flow search.
        val registry = koinInject<MetadataProviderRegistry>()
        SearchScreen(
            anilistApi = appController.anilistApi,
            extensionManager = appController.extensionManager,
            sourceMatcher = appController.sourceMatcher,
            recentsStore = appController.recentsStore,
            uiPreferences = appController.searchUiPreferences,
            registry = registry,
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

        // Phase 6 (ADR-050): download identity is content_id. AnimeDetailDestination
        // is the AniList-linked entry point — contentId is always "al:$animeId".
        val contentId = "al:$animeId"

        // Collect download tasks reactively for this anime's episode rows.
        val downloadTasksMap by appController.downloadTasksFlow
            .collectAsStateWithLifecycle(initialValue = emptyMap())
        val downloadStates = appController.getDownloadStates(contentId, downloadTasksMap)

        AnimeDetailScreen(
            animeId = animeId,
            onBack = { navigator.pop() },
            onOpenEpisode = { episode, source, episodeList, watchCtx ->
                appController.resolveEpisode(episode, source, episodeList, watchCtx, contentId)
            },
            onDownloadEpisode = { episode, source, watchCtx ->
                appController.downloadEpisode(episode, source, watchCtx, contentId)
            },
            downloadStates = downloadStates,
            onDownloadCancel = { episodeUrl -> appController.cancelDownload(contentId, episodeUrl) },
            onDownloadResume = { episodeUrl -> appController.resumeDownload(contentId, episodeUrl) },
            onDownloadRetry = { episodeUrl -> appController.retryDownload(contentId, episodeUrl) },
            onDownloadDelete = { episodeUrl -> appController.deleteDownload(contentId, episodeUrl) },
            // "Switch anime" from the AniList page — resolve the source from the saved
            // link + start the linking flow (corrects a wrong auto-match).
            onLinkToAniList = { appController.startLinkingFromAnilist(animeId) },
            // "Switch anime" picked — update links + navigate to the new anime.
            onSwitchAnimePicked = { newId -> appController.switchAnilistAnime(animeId, newId) },
            // "Unlink from AniList" — remove both directional links + navigate to
            // the extension-mode details page (or DB-first if source uninstalled).
            onUnlinkFromAniList = { appController.unlinkFromAniList(animeId) },
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
 *
 * # Parcelable / Serialization Safety (CRITICAL FIX)
 *
 * This destination stores **only serializable primitive fields** ([sourceId],
 * [animeUrl], [animeTitle], [thumbnailUrl], [anilistId], [forceInitialRefresh]).
 * The live `AnimeCatalogueSource` + `SAnime` objects are **NOT** stored — they
 * are resolved in [Content] via `SourceMatcher.getSourceById(sourceId)` +
 * `SAnimeImpl` reconstruction.
 *
 * **Why:** Voyager saves the back stack to an Android `Bundle` when the Activity
 * stops (`onStop` → `PendingTransactionActions$StopInfo`). The `Bundle` path
 * Java-serializes each `Screen` data class. Extension classes (e.g.
 * `eu.kanade.tachiyomi.animeextension.en.anikoto.Anikoto`) are NOT `Serializable`,
 * so storing them directly caused `BadParcelableException` →
 * `NotSerializableException` crashes on background / screen rotation.
 *
 * This mirrors the `LibraryExtensionDetailDestination` pattern (which already
 * stored only primitives for the same reason). The `WatchDestination` uses an
 * `object` + `AppController.pendingWatchRequest` for the same issue.
 *
 * @param sourceId the `AnimeCatalogueSource.id` — used to look up the live source
 *   in [Content]. If the source was uninstalled while the app was dead, the page
 *   opens in DB-first mode (`extensionSource = null`, like
 *   [LibraryExtensionDetailDestination]).
 * @param animeUrl the `SAnime.url` (source-relative).
 * @param animeTitle the `SAnime.title` (for display + AniList reverse-search).
 * @param thumbnailUrl the `SAnime.thumbnail_url` (optional — for cover display
 *   before the fresh fetch completes).
 * @param anilistId optional — when non-null, the ExtensionDetailsProvider merges
 *   AniList metadata into the view (linked extension anime).
 * @param forceInitialRefresh Fix 2 (SOURCE-SWITCH-FIXES): when `true`, the
 *   `AnimeDetailViewModel.init { loadInternal(forceRefresh = forceInitialRefresh) }`
 *   bypasses the DB-first short-circuit + forces a fresh fetch from the extension
 *   (calling `updateMetadataFromExtension` in `ExtensionDetailsProvider.persistEpisodes`
 *   so stale AniList metadata is overwritten). Used by `AppController.unlinkFromAniList`
 *   so the post-unlink page shows fresh extension data instead of stale AniList data.
 */
data class ExtensionAnimeDetailDestination(
    val sourceId: Long,
    val animeUrl: String,
    val animeTitle: String,
    val thumbnailUrl: String? = null,
    val anilistId: Int? = null,
    val forceInitialRefresh: Boolean = false,
) : Screen {
    /** Unique key per source+url+anilistId — prevents Voyager SaveableStateHolder collision.
     *  Includes anilistId + forceInitialRefresh so that navigating from a linked entry
     *  (anilistId=12345) to an unlinked entry (anilistId=null) doesn't reuse the same key
     *  (which would crash with "Key was used multiple times"). */
    override val key: ScreenKey = "ExtensionAnimeDetailDestination(${sourceId}_${animeUrl}_${anilistId ?: "none"}_$forceInitialRefresh)"

    @Composable
    override fun Content() {
        val appController = koinInject<AppController>()
        val navigator = LocalNavigator.currentOrThrow

        // Resolve the live source from the stored sourceId. If the extension was
        // uninstalled while the app was dead, source will be null — the page opens
        // in DB-first mode (same as LibraryExtensionDetailDestination).
        val source = remember(sourceId) {
            appController.sourceMatcher.getSourceById(sourceId)
        }

        // Reconstruct the SAnime from the stored primitives. This is safe because
        // SAnimeImpl is a simple data holder; the details page's fresh fetch will
        // overwrite these fields with live data from the extension anyway.
        val sAnime = remember(sourceId, animeUrl, animeTitle, thumbnailUrl) {
            eu.kanade.tachiyomi.animesource.model.SAnimeImpl().apply {
                url = animeUrl
                title = animeTitle
                thumbnail_url = thumbnailUrl
            }
        }

        // Phase 6 (ADR-050): compute the content_id for this extension anime.
        //  - Linked:   "al:$anilistId" (matches the AniList-linked grouping).
        //  - Unlinked: "aniyomi:${sourceId}:${animeUrl}" — the per-source
        //    local_id fallback (matches LocalId format from Phase 1). This is
        //    the gate-removal fix: unlinked extension anime are now downloadable
        //    (their content_id = local_id), so the user can download without
        //    first linking to AniList.
        //
        //  Note: we use sourceId (the stored primitive) instead of source?.id
        //  because source may be null (extension uninstalled). The contentId
        //  must be stable regardless of whether the source is currently installed.
        val contentId = if (anilistId != null) "al:$anilistId"
            else "aniyomi:$sourceId:$animeUrl"

        val downloadTasksMap by appController.downloadTasksFlow
            .collectAsStateWithLifecycle(initialValue = emptyMap())
        val downloadStates = appController.getDownloadStates(contentId, downloadTasksMap)

        AnimeDetailScreen(
            extensionSource = source,
            extensionSAnime = sAnime,
            extensionAnilistId = anilistId,
            extensionSourceId = sourceId,
            forceInitialRefresh = forceInitialRefresh,
            onBack = { navigator.pop() },
            onOpenEpisode = { episode, src, episodeList, watchCtx ->
                appController.resolveEpisode(
                    episode, src, episodeList, watchCtx,
                    contentId = contentId,
                )
            },
            onDownloadEpisode = { episode, src, watchCtx ->
                appController.downloadEpisode(episode, src, watchCtx, contentId)
            },
            downloadStates = downloadStates,
            onDownloadCancel = { episodeUrl -> appController.cancelDownload(contentId, episodeUrl) },
            onDownloadResume = { episodeUrl -> appController.resumeDownload(contentId, episodeUrl) },
            onDownloadRetry = { episodeUrl -> appController.retryDownload(contentId, episodeUrl) },
            onDownloadDelete = { episodeUrl -> appController.deleteDownload(contentId, episodeUrl) },
            // Extension mode: "Link to AniList" opens the AniList linking sheet overlay.
            // Only available when the source is installed (can't link without a live source).
            onLinkToAniList = {
                if (source != null) {
                    appController.startLinking(source, sAnime)
                }
            },
            // "Switch anime" picked (linked only) — update links + navigate to the new anime.
            onSwitchAnimePicked = { newId ->
                if (anilistId != null) {
                    appController.switchAnilistAnime(anilistId, newId)
                }
            },
            // "Unlink from AniList" (linked only) — pass the stored sourceId + animeUrl
            // so AppController doesn't have to re-resolve from SourceLinkStore.
            onUnlinkFromAniList = {
                if (anilistId != null) {
                    appController.unlinkFromAniList(anilistId, sourceId, animeUrl)
                }
            },
        )
    }
}

/**
 * Library anime with a missing source extension. Opens the unified details page
 * using [extensionSourceId] (no live source object) — the provider's DB-first
 * path loads saved data. The user can see saved episodes but can't play/download
 * (the source is gone). They can use the "Source unavailable" chip on the
 * details page to switch to another extension.
 *
 * This is the library-no-source destination from the scroll-blur branch. Pushed
 * by `AppController.openLibraryAnime` when the user taps a library anime whose
 * source extension was uninstalled (instead of bailing with a toast).
 *
 * @param forceInitialRefresh Fix 2 (SOURCE-SWITCH-FIXES): when `true`, the
 *   `AnimeDetailViewModel.init { loadInternal(forceRefresh = forceInitialRefresh) }`
 *   bypasses the DB-first short-circuit + forces a fresh fetch. Used by
 *   `AppController.unlinkFromAniList` (when the source is no longer installed)
 *   so the post-unlink page shows fresh data.
 */
data class LibraryExtensionDetailDestination(
    val sourceId: Long,
    val animeUrl: String,
    val animeTitle: String,
    val forceInitialRefresh: Boolean = false,
) : Screen {
    override val key: ScreenKey = "LibraryExtDetail($sourceId:$animeUrl:$forceInitialRefresh)"

    @Composable
    override fun Content() {
        val appController = koinInject<AppController>()
        val navigator = LocalNavigator.currentOrThrow
        val context = androidx.compose.ui.platform.LocalContext.current

        val sAnime = remember {
            eu.kanade.tachiyomi.animesource.model.SAnimeImpl().apply {
                url = animeUrl
                title = animeTitle
            }
        }

        AnimeDetailScreen(
            extensionSource = null, // Source not installed — the screen uses extensionSourceId.
            extensionSAnime = sAnime,
            extensionAnilistId = null,
            extensionSourceId = sourceId,
            forceInitialRefresh = forceInitialRefresh,
            onBack = { navigator.pop() },
            onOpenEpisode = { _, _, _, _ ->
                // Source not installed — no live source to resolve against. The
                // user can still see saved episodes (rendered from the DB) but
                // can't play. Toast + log so the no-op is observable.
                android.widget.Toast.makeText(
                    context,
                    "Source not installed — cannot play this episode",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            },
            onDownloadEpisode = { _, _, _ ->
                android.widget.Toast.makeText(
                    context,
                    "Source not installed — cannot download",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            },
            onLinkToAniList = { appController.startLinkingFromAnilist(0) },
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
            // Phase 3: openLibraryAnime handles BOTH linked (anilistId != null → AniList
            // details) and unlinked (anilistId == null → extension details) anime.
            // This is the bug fix for the previously-unopenable unlinked history rows.
            onOpenAnime = { anime -> appController.openLibraryAnime(anime) },
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
            // Phase 6 (ADR-050): the callback now receives a content_id (String,
            // e.g. "al:154587" or "aniyomi:123:url") instead of an anilistId (Int).
            // openDownloadedAnimeByContentId handles both flavors.
            onPlayEpisode = { contentId, episodeUrl ->
                appController.openDownloadedAnimeByContentId(contentId)
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
            onOpenGeneral = { navigator.push(GeneralSettingsDestination) },
            onOpenPlayer = { navigator.push(PlayerSettingsDestination) },
            onOpenBackup = { navigator.push(BackupDestination) },
            onOpenAbout = { navigator.push(AboutDestination) },
            onBack = { navigator.pop() },
        )
    }
}

/**
 * The General settings page — auto-link toggle, extension linking behavior,
 * future per-extension config + provider selection.
 */
object GeneralSettingsDestination : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        app.confused.anikuta.feature.settings.GeneralSettingsScreen(
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

// ════════════════════════════════════════════════════════════════════════
//  About + Updates
// ════════════════════════════════════════════════════════════════════════

/**
 * The About & Updates screen — shows app version, lets the user manually check
 * for updates, download available updates, and re-open previously downloaded
 * APK versions.
 */
object AboutDestination : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val appController = koinInject<AppController>()
        app.confused.anikuta.feature.settings.AboutScreen(
            onBack = { navigator.pop() },
            hideUpdates = false,
            onUpdateFound = {
                // Clear the dismiss cooldown so the sheet shows, then display it
                org.koin.core.context.GlobalContext.get()
                    .get<app.confused.anikuta.core.appupdate.AppUpdatePreferences>()
                    .clearDismissCooldown()
                appController.showUpdateSheet()
            },
        )
    }
}

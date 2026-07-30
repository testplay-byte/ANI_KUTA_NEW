package app.confused.anikuta.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.confused.anikuta.MainActivity
import app.confused.anikuta.core.designsystem.component.AnikutaBottomNavBar
import app.confused.anikuta.core.designsystem.component.NavIcons
import app.confused.anikuta.core.designsystem.component.NavItem
import app.confused.anikuta.feature.download.DownloadVideoPickerSheet
import app.confused.anikuta.feature.search.ui.ExtensionLinkingSheet
import app.confused.anikuta.core.videoresolver.VideoResolverState
import app.confused.anikuta.feature.videoresolver.VideoResolverSheet
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.FadeTransition
import org.koin.compose.koinInject

/**
 * The bottom-nav items (4 tabs: Home, Library, Search, More).
 *
 * "More" is always last and cannot be removed (per ADR-017).
 */
private val navItems = listOf(
    NavItem("home", "Home", NavIcons.Home),
    NavItem("library", "Library", NavIcons.Library),
    NavItem("search", "Search", NavIcons.Search),
    NavItem("more", "More", NavIcons.More),
)

/**
 * The Voyager-based app root. Replaces the old hand-rolled state-machine
 * `AnikutaApp()` composable that was in `MainActivity.kt`.
 *
 * **Architecture:**
 * - A single root [Navigator] holds the back stack. The 4 bottom-nav tabs are
 *   the root screens; switching tabs calls `navigator.replace(newTab)`.
 *   Pushed screens (detail, watch, settings, etc.) push on top.
 * - The floating bottom nav is shown only when the stack is at a tab (depth ≤ 1).
 *   Pushed screens hide it — matching the previous behavior.
 * - Overlay sheets (resolver, linking, download picker) render on top of
 *   everything, driven by [AppController]'s state. They are NOT navigated
 *   screens — they're modal bottom sheets (per DESIGN_LANGUAGE §1: no drag
 *   handle, partial height).
 * - OAuth callbacks + download-error toasts are observed via [LaunchedEffect].
 *
 * **Why a single Navigator (not per-tab Navigators):** This matches the
 * previous behavior exactly — the old state machine had a single "active
 * screen" (the first matching `when` branch won), not per-tab back stacks.
 * Per-tab Navigators could be added later if the owner wants tab state
 * preservation (flagged as a future enhancement).
 */
@Composable
fun AnikutaRoot() {
    val appController = koinInject<AppController>()

    // Clear stale overlay state on recomposition (e.g., after Activity recreate).
    // If the app was killed + reopened while a linking sheet or resolver was
    // visible, the state would be stale (the Voyager back stack is lost on
    // Activity recreation). Clearing these prevents crashes from stale state.
    LaunchedEffect(Unit) {
        appController.linkingTarget = null
        appController.dismissDownloadPicker()
        if (appController.resolverState !is VideoResolverState.Hidden) {
            appController.hideResolver()
        }
    }

    // TODO(owner): Voyager 1.0.1 doesn't have rememberNavigator(). The app
    // loses the back stack when the Activity is recreated (e.g. switching apps).
    // This will be addressed in a future session with a custom Saver or by
    // upgrading to a Voyager version that supports state restoration.
    Navigator(BrowseTabDestination) { navigator ->
        // Wire the navigator into the AppController so it can push/pop.
        SideEffect {
            appController.navigator = navigator
        }

        // ── Download error toast observer ──
        val downloadTasksMap by appController.downloadTasksFlow
            .collectAsStateWithLifecycle(initialValue = emptyMap())
        LaunchedEffect(downloadTasksMap) {
            appController.checkForDownloadErrors(downloadTasksMap)
        }

        // ── OAuth callback observer ──
        LaunchedEffect(Unit) {
            MainActivity.pendingOAuthCallback.collect { callbackUrl ->
                if (callbackUrl != null) {
                    appController.handleOAuthCallback(callbackUrl)
                    MainActivity.pendingOAuthCallback.value = null
                }
            }
        }

        // Bottom nav is visible only at the tab level (stack depth ≤ 1).
        val showBottomNav = navigator.items.size <= 1

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            // ── Screen content (simple fade transition — no slide) ──
            // Per owner preference: a clean cross-fade between screens rather
            // than a slide animation. Smooth, simple, and good-looking.
            FadeTransition(navigator = navigator)

            // ── Floating bottom nav (on top of content, below overlays) ──
            if (showBottomNav) {
                AnikutaBottomNavBar(
                    items = navItems,
                    currentRoute = appController.currentTab,
                    onSelect = { route -> appController.switchTab(route) },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }

            // ── Overlay sheets (rendered on top of everything) ──
            AppOverlays(appController)
        }
    }
}

/**
 * Renders the three modal overlay sheets driven by [AppController] state:
 * 1. Video resolver sheet (when resolving/picking a video to watch).
 * 2. Extension → AniList linking sheet (when tapping an extension search result).
 * 3. Download video picker sheet (when auto-download is OFF or fallback=ASK).
 *
 * Each is a [ModalBottomSheet] with `dragHandle = null` (per DESIGN_LANGUAGE §2).
 * They are NOT navigated screens — they float above the current screen.
 */
@Composable
private fun AppOverlays(appController: AppController) {
    // ── Back gesture for the resolver overlay ──
    // The VideoResolverSheet is a custom Box (NOT a ModalBottomSheet), so it
    // doesn't have built-in back handling. This BackHandler is composed AFTER
    // the screen content (via FadeTransition), so it takes priority when the
    // resolver is visible. The other two overlays (linking sheet, download
    // picker) are ModalBottomSheets and handle back themselves.
    val resolverVisible = appController.resolverState !is VideoResolverState.Hidden
    BackHandler(enabled = resolverVisible) {
        appController.hideResolver()
    }

    // ── 1. Video resolver overlay ──
    // Only themed when adaptiveColorsPlayer is ON (per owner feedback).
    val resolverState = appController.resolverState
    if (resolverState !is VideoResolverState.Hidden) {
        val themePrefs = appController.themePrefs
        val adaptivePlayer = themePrefs.adaptiveColorsPlayer.get()
        val coverColorArgb = appController.resolveTarget?.watchCtx?.coverColorArgb ?: 0
        val resolverScheme = if (adaptivePlayer && coverColorArgb != 0) {
            app.confused.anikuta.core.designsystem.theme.generateDynamicScheme(
                coverColorArgb, darkTheme = true, amoled = false,
            )
        } else null

        val resolverContent: @Composable () -> Unit = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
                    .clickable { appController.hideResolver() },
                contentAlignment = Alignment.BottomCenter,
            ) {
                VideoResolverSheet(
                    state = resolverState,
                    onDismiss = { appController.hideResolver() },
                    onVideoSelected = { video -> appController.onVideoSelected(video) },
                    onRetry = { appController.retryResolve() },
                )
            }
        }

        if (resolverScheme != null) {
            MaterialTheme(colorScheme = resolverScheme, content = resolverContent)
        } else {
            resolverContent()
        }
    }

    // ── 2. Extension → AniList linking sheet ──
    val linkingTarget = appController.linkingTarget
    if (linkingTarget != null) {
        val (source, sAnime) = linkingTarget
        ExtensionLinkingSheet(
            source = source,
            sAnime = sAnime,
            anilistApi = appController.anilistApi,
            linkStore = appController.extensionLinkStore,
            linkingPreferences = appController.linkingPreferences,
            // Fix 1 (SOURCE-SWITCH-FIXES): forward the original extension source + sAnime
            // to AppController.onLinked so the navigation target is
            // ExtensionAnimeDetailDestination (Extension mode + Stage-D AniList merge),
            // NOT AnimeDetailDestination (which loses the source + re-matches via
            // SourceMatcher).
            onLinked = { anilistId, wasCached ->
                appController.onLinked(anilistId, wasCached, source, sAnime)
            },
            onGoWithoutLinking = { extSource, extSAnime ->
                appController.onGoWithoutLinking(extSource, extSAnime)
            },
            onDismiss = { appController.dismissLinking() },
        )
    }

    // ── 3. Download video picker sheet ──
    val downloadPickerTarget = appController.downloadPickerTarget
    if (downloadPickerTarget != null) {
        DownloadVideoPickerSheet(
            servers = downloadPickerTarget.servers,
            animeTitle = downloadPickerTarget.anime.title,
            episodeName = downloadPickerTarget.episode.name,
            onVideoSelected = { video, serverName, audioLabel ->
                appController.enqueuePickedVideo(video, serverName, audioLabel)
            },
            onDismiss = { appController.dismissDownloadPicker() },
        )
    }
}

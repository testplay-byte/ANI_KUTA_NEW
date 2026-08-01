package app.confused.anikuta.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
    val setupPrefs = koinInject<app.confused.anikuta.core.preferences.SetupWizardPreferences>()
    val isSetupCompleted by setupPrefs.observeCompleted()
        .collectAsStateWithLifecycle(initialValue = setupPrefs.isCompleted())

    // ── Setup Wizard gate ──
    // On first launch (or when the user re-runs the wizard from Settings),
    // show the SetupWizardApp instead of the main Navigator.
    if (!isSetupCompleted) {
        app.confused.anikuta.feature.setupwizard.SetupWizardApp(
            onComplete = {
                // The wizard calls setCompleted(true) internally.
                // The state flow will recompose this composable → main UI shows.
            },
        )
        return
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

        // ── Ad return lifecycle observer ──
        // When the ad system is awaiting the user's return from the browser,
        // observe the Activity lifecycle. On ON_RESUME, call appController.onAdReturn()
        // so the ad manager can check if the user stayed long enough.
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME && appController.adAwaitingReturn) {
                    appController.onAdReturn()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        // ── App update check on startup ──
        // Checks for updates immediately on app open. If an update is available,
        // shows the update bottom sheet right away (no 6-hour cooldown during
        // testing — the user wants to see the update dialog every time they
        // open the app so they can test the download/install flow repeatedly).
        //
        // Also cleans up old downloaded APKs (versions <= current) to free storage.
        //
        // **Post-install popup:** if the user just installed an update (recorded
        // via AppUpdatePreferences.pendingPostInstall before the system installer
        // launched), show the post-install success popup instead of the update
        // sheet — and skip the update check (we already know we're on the latest).
        LaunchedEffect(Unit) {
            try {
                val updatePrefs = org.koin.core.context.GlobalContext.get()
                    .get<app.confused.anikuta.core.appupdate.AppUpdatePreferences>()

                // ── Post-install success popup check ──
                // If the user just installed an update, show the popup + skip
                // the update check (we just installed the latest, no point).
                val pendingPostInstall = updatePrefs.getPendingPostInstall()
                if (pendingPostInstall.isNotEmpty()) {
                    android.util.Log.d("AnikutaUpdate",
                        "Startup: pending post-install v$pendingPostInstall — showing popup, skipping update check")
                    updatePrefs.clearPendingPostInstall()
                    // Clean up the just-installed APK file too (the popup also
                    // does this, but cleaning here handles the case where the
                    // popup is dismissed before its cleanup runs).
                    appController.updateManager.cleanupOldDownloads()
                    appController.updateManager.clearUpdateState()
                    appController.showPostInstallPopup()
                    return@LaunchedEffect
                }

                // ── Update check ──
                // Both beta and non-beta builds check for updates. The update source
                // (GitHubUpdateSource) is configured in AppUpdateModule to point at
                // the beta repo (Confused-Creature-180/APP_BETA), so beta builds
                // check against beta releases — not the old test repo.

                android.util.Log.d("AnikutaUpdate", "Startup: cleaning up old downloads + state...")
                appController.updateManager.cleanupOldDownloads()
                appController.updateManager.clearUpdateState()

                android.util.Log.d("AnikutaUpdate", "Startup: beginning update check...")
                val update = appController.updateManager.checkForUpdate()
                android.util.Log.d("AnikutaUpdate", "Startup: check complete, update=${update?.versionName}")

                if (update != null) {
                    // Clear the dismiss cooldown so the dialog ALWAYS shows on
                    // startup (for testing the update flow repeatedly). Once the
                    // system is fully tested, we can re-enable the 6-hour cooldown.
                    updatePrefs.clearDismissCooldown()
                    appController.showUpdateSheet()
                }
            } catch (e: Exception) {
                android.util.Log.w("AnikutaUpdate", "Startup update check failed", e)
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

    // ── 4. Unlink download action dialog ──
    // Shows when the user taps "Unlink from AniList" and the anime has downloaded
    // episodes. Prompts: "Transfer downloads" or "Delete downloads".
    val unlinkAction = appController.pendingUnlinkDownloadAction
    if (unlinkAction != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { appController.cancelUnlinkDownloadAction() },
            title = { androidx.compose.material3.Text("Downloaded Episodes Found") },
            text = {
                androidx.compose.material3.Text(
                    "'${unlinkAction.animeTitle}' has downloaded episodes.\n\n" +
                        "Transfer: keeps the downloaded files (they'll be re-keyed to the " +
                        "extension-only entry).\n\n" +
                        "Delete: permanently removes all downloaded episodes for this anime.",
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    appController.confirmUnlinkWithDownloadAction(deleteDownloads = false)
                }) { androidx.compose.material3.Text("Transfer") }
            },
            dismissButton = {
                androidx.compose.foundation.layout.Row {
                    androidx.compose.material3.TextButton(onClick = {
                        appController.confirmUnlinkWithDownloadAction(deleteDownloads = true)
                    }) { androidx.compose.material3.Text("Delete") }
                    androidx.compose.material3.TextButton(onClick = {
                        appController.cancelUnlinkDownloadAction()
                    }) { androidx.compose.material3.Text("Cancel") }
                }
            },
        )
    }

    // ── 5. Ad interstitial dialog ──
    // Shown when AppController.pendingAdNavigation is non-null (set by withAdGate).
    // The user must accept or cancel before the deferred navigation proceeds.
    if (appController.pendingAdNavigation != null) {
        AdDialog(appController)
    }

    // ── 6. App update bottom sheet ──
    // Shown when AppController.showUpdateDialog is true (set on startup if an
    // update is available + not in the dismiss cooldown). The user can cancel
    // (6-hour cooldown) or download (progress bar → auto-install).
    if (appController.showUpdateDialog) {
        UpdateBottomSheet(appController)
    }

    // ── 7. Post-install success popup ──
    // Shown when AppController.showPostInstallSuccess is true (set on startup
    // if AppUpdatePreferences.getPendingPostInstall() was non-empty). Animates
    // a "Cleaning up downloaded APK…" → "APK deleted" sequence + auto-dismisses
    // after ~2 seconds.
    if (appController.showPostInstallSuccess) {
        PostInstallSuccessSheet(appController)
    }
}

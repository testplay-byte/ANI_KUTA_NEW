# Phase 7 — Backend Improvements (Detailed Plan)

> **Status: PLANNING — awaiting user verification before implementation.**
> Built from 4 parallel research passes over the aniyomi reference + current ANI-KUTA code.
> Last updated: Session 21.

---

## 0. Executive summary

Phase 7 has **four workstreams** (A–D). Each is independently shippable, but A should
land first because B/D depend on the "trusted sources" concept it introduces.

| ID | Workstream | What it delivers | Builds on aniyomi? |
|----|------------|------------------|--------------------|
| A  | Extension system overhaul | Repo setup UI, trust system, sources-vs-installed split, extension details + settings pages, max-2-trusted limit | **Partly** — repo mgmt + trust + settings screens exist in aniyomi; ANI-KUTA has the domain/data code copied but unwired. Max-2 + popup is novel. |
| B  | Downloads preferences redesign | Drag-and-drop priority lists for quality / audio / server, quality-vs-audio priority toggle, fallback workflow, a real video resolver | **Mostly novel** — aniyomi has no global quality/audio/server prefs (delegates to extensions). Must build from scratch. Reuses `sh.calvin.reorderable` drag lib. |
| C  | Episode list + detail caching & refresh | DB-backed episode cache (replaces in-memory Map), soft background refresh, 3-stage pull-to-refresh | **Partly** — aniyomi's `SyncEpisodesWithSource` + DB Flow pattern is the template. 3-stage pull-to-refresh is novel (custom `nestedScroll`). |
| D  | Episode video caching + picker redesign | Short-TTL video cache, smooth soft-refresh, scrollable + collapsible picker, audio-version grouping, quality-desc sort | **Partly** — aniyomi's `QualitySheet` (LazyColumn + collapse) is the UI template. Video cache + audio grouping + quality sort are novel. |

**Recommended order:** A → C → D → B.
- A first: establishes the "trusted sources" gate that B's server enumeration and D's resolution depend on.
- C before D: C fixes the episode-list cache; D fixes the per-episode video cache. C's DB wiring is simpler and unblocks D's Flow-based smooth updates.
- B last: it's the most novel and benefits from D's `VideoTitleParser` (quality/audio extraction) already existing.

---

## Workstream A — Extension system overhaul

### A.0 Current state (from research)

- **Repo management:** aniyomi has a full stack — `extension_repos` SQLDelight table, `AnimeExtensionRepoRepository`, `Create/Delete/Replace/Update/Get` interactors, `ExtensionRepoService` (fetches `/repo.json`), `AnimeExtensionApi.findExtensions()` (fetches `/index.min.json`). **ANI-KUTA has ALL of this copied** in `domain/` + `data/` but **NOT wired into DI**. Instead, `app/` has STUB classes (`GetAnimeExtensionRepo` returns one hardcoded repo; `TrustAnimeExtension` always returns `true`) that SHADOW the real ones. This is why the available-extensions list is empty/broken.
- **Trust system:** aniyomi has `AnimeExtension.Untrusted` subtype + `TrustAnimeExtension` interactor + `trustedExtensions()` StringSet pref + `ExtensionTrustDialog` UI. **ANI-KUTA has a stub** that always trusts everything — no untrusted extensions ever surface.
- **Extension settings:** aniyomi has `AnimeExtensionDetailsScreen` (metadata + per-source enable switches + settings-gear) → `AnimeSourcePreferencesScreen` (wraps `PreferenceFragmentCompat`, calls `source.setupPreferenceScreen(screen)`). `ConfigurableAnimeSource` interface already exists in ANI-KUTA's source-api. **ANI-KUTA has NONE of these screens.**
- **Installer:** aniyomi has `AnimeExtensionInstaller` (DownloadManager + 3 install strategies: LEGACY/PRIVATE/SHIZUKU) + `AnimeExtensionInstallReceiver` (live-refresh on package changes). **ANI-KUTA has a hand-rolled OkHttp+FileProvider flow** in `ExtensionsViewModel` that doesn't auto-refresh the list.

### A.1 Tasks

#### A.1.1 Resolve the shadow-class conflict + wire real domain layer into DI
**Delete** the stubs in `app/src/main/java/app/anikuta/domain/`:
- `app/.../domain/mihon/extensionrepo/anime/interactor/GetAnimeExtensionRepo.kt` (stub → use `domain/` version)
- `app/.../domain/mihon/extensionrepo/anime/interactor/UpdateAnimeExtensionRepo.kt` (stub → use `domain/` version)
- `app/.../domain/mihon/extensionrepo/model/ExtensionRepo.kt` (3-field stub → use `domain/` 5-field version)
- `app/.../domain/extension/anime/interactor/TrustAnimeExtension.kt` (always-true stub → use `domain/` version)

**Register in `AppModule.kt`:**
- `AnimeExtensionRepoRepository` / `AnimeExtensionRepoRepositoryImpl` (needs SQLDelight `extension_reposQueries`)
- `ExtensionRepoService` (needs `NetworkHelper` + `Json`)
- `CreateAnimeExtensionRepo`, `DeleteAnimeExtensionRepo`, `ReplaceAnimeExtensionRepo`, `GetAnimeExtensionRepo` (real), `GetAnimeExtensionRepoCount`, `UpdateAnimeExtensionRepo` (real)
- `TrustAnimeExtension` (real — needs `AnimeExtensionRepoRepository` + `SourcePreferences`)

**Verify** the SQLDelight `extension_repos.sq` is in the `data/` module's sqldelight source set and generates queries (it's already copied; confirm the gradle config points at it).

#### A.1.2 Port the real installer + install receiver
Port from aniyomi (selective copy, per D1):
- `AnimeExtensionInstaller` (the `LEGACY` install path is enough for ANI-KUTA — `PRIVATE`/`SHIZUKU` are optional, defer).
- `AnimeExtensionInstallService` + `AnimeExtensionInstallActivity` + `AnimeExtensionInstallReceiver`.
- Register the receiver in `AndroidManifest.xml` with `<action android:name="android.intent.action.PACKAGE_ADDED"/>` etc.
- Replace `ExtensionsViewModel.installExtension()`'s hand-rolled flow with `extensionManager.installExtension(ext)` (which calls `installer.downloadAndInstall`).
- Replace `ExtensionsViewModel.uninstallExtension()`'s 3-tier fallback with `extensionManager.uninstallExtension(ext)`.
- The `AnimeExtensionInstallReceiver` auto-refreshes the installed/untrusted lists on package changes — no more manual pull-to-refresh after install.

**Add permissions** (most already exist, verify): `REQUEST_INSTALL_PACKAGES`, `REQUEST_DELETE_PACKAGES`, `QUERY_ALL_PACKAGES`.

#### A.1.3 Extension repo management screen
Build a new settings subpage: **"Extension repositories"** (accessed from Settings → Extensions → "Manage repositories", AND from the empty-state of the Extensions screen).

Port from aniyomi's `AnimeExtensionReposScreen` + `AnimeExtensionReposScreenModel` + shared `ExtensionReposScreen` content composable + 4 dialogs (Create / Delete / Conflict / Confirm).
- Lists all repos (baseUrl, name, website, signingKeyFingerprint).
- FAB "+" → `RepoDialog.Create` (enter index URL `https://…/index.min.json`).
- Per-repo: "Open website" + "Delete" (with confirm dialog).
- Conflict dialog: if a new repo's signing fingerprint matches an existing repo, prompt to replace.
- Empty state: "No repositories added yet" + button to add one.

This screen writes to the `extension_repos` SQLDelight table via `CreateAnimeExtensionRepo` / `DeleteAnimeExtensionRepo`. Once a repo exists, `AnimeExtensionApi.findExtensions()` fetches `/index.min.json` and the "Available" section populates.

**NavGraph:** add route `extension_repos`.

#### A.1.4 Trust system + "Sources" vs "Installed" split
Restructure the Extensions screen into **three sections** (currently two):

1. **Sources** (new name for the trusted installed extensions) — these are the only extensions used for app functionality. Capped at **2** (see A.1.6).
2. **Installed** (installed-but-untrusted extensions) — each row shows two actions: **Trust** (→ moves to Sources, if under the cap) and **Delete** (uninstall).
3. **Available** (from repos, not yet installed) — each row shows **Install**.

This replaces aniyomi's model (where "Installed" includes untrusted). In ANI-KUTA, "Sources" = trusted, "Installed" = untrusted. Only "Sources" extensions are registered in `AnimeSourceManager` as active catalogue sources.

**Implementation:**
- `AnimeExtensionLoader.loadAll()` already partitions into `Success` vs `Untrusted` based on `TrustAnimeExtension.isTrusted()`. Once A.1.1 wires the real `TrustAnimeExtension`, untrusted extensions will correctly surface.
- `AnimeExtensionManager` already has `_untrustedExtensions` StateFlow (currently always empty due to the stub). It will populate.
- `ExtensionsViewModel` exposes three lists: `sources` (trusted), `installed` (untrusted), `available`.
- `AndroidAnimeSourceManager.getCatalogueSources()` must be gated to only return sources from TRUSTED extensions (currently it returns all installed). Add a filter: `source.extensionPackage in trustedSet`.

#### A.1.5 Extension details + extension settings pages
**Extension details page** (new route `extension_details/{pkgName}`):
Port from aniyomi's `AnimeExtensionDetailsScreen` + `AnimeExtensionDetailsScreenModel` + composable.
- Header: icon (112dp), name, package name, version, language, NSFW badge.
- Actions: Uninstall, App Info (system settings), (if trusted) "Remove from Sources" / (if untrusted) "Add to Sources".
- Per-source rows (an extension can contain multiple sources): source name + enable/disable Switch + (if `ConfigurableAnimeSource`) a Settings gear → opens `source_preferences/{sourceId}`.

**Extension settings page** (new route `source_preferences/{sourceId}`):
Port from aniyomi's `AnimeSourcePreferencesScreen` + `SourcePreferencesFragment`.
- Wraps `PreferenceFragmentCompat` inside a Compose `FragmentContainerView` (legacy interop — aniyomi does this because extensions build their settings UI with `androidx.preference` widgets).
- `SourcePreferencesFragment.populateScreen()`:
  ```kotlin
  val source = Injekt.get<AnimeSourceManager>().getOrStub(sourceId)
  if (source is ConfigurableAnimeSource) {
      val dataStore = SharedPreferencesDataStore(source.sourcePreferences())
      preferenceManager.preferenceDataStore = dataStore
      source.setupPreferenceScreen(sourceScreen)
  }
  ```
- Port the `SharedPreferencesDataStore` adapter from aniyomi.
- Settings persist to `"source_$id"` SharedPreferences — the extension reads them on-demand during network work, so they apply throughout automatically.

**Click flow:** Extensions screen → tap a **Sources** (trusted) extension → extension details page → tap Settings gear on a source → extension settings page. (Untrusted/installed extensions don't get a details page — only Trust/Delete actions, per the user's spec.)

#### A.1.6 Max-2-trusted limit + popup (NOVEL — no aniyomi precedent)
This is brand-new. Implementation:

- Add a `SourcePreferences.trustedSources()` preference — a `StringSet` capped at 2 entries (the package names of trusted extensions). This is SEPARATE from aniyomi's `trustedExtensions()` (which stores `"pkg:versionCode:sigHash"` and is unlimited). We use aniyomi's `trustedExtensions()` for the cryptographic trust check (is the signing key trusted?), and `trustedSources()` for the app-policy cap (is this extension one of the user's 2 active sources?).

  Actually — simpler: reuse `trustedExtensions()` for crypto trust, and add `trustedSources()` as the policy set. An extension is "in Sources" iff it passes BOTH checks. This keeps the security model (crypto trust) separate from the UX policy (max 2).

- `AnimeExtensionManager.trust(extension)`:
  ```kotlin
  suspend fun trust(extension: AnimeExtension.Untrusted) {
      val current = sourcePreferences.trustedSources().get()
      if (current.size >= 2) {
          // Reject — caller must show the popup
          throw TrustLimitExceeded(current)  // sealed result instead of exception, TBD
      }
      trustExtension.trust(...)  // aniyomi's crypto trust
      sourcePreferences.trustedSources().set(current + extension.pkgName)
      // reload as Success...
  }
  ```

- **The popup** (new `MaxTrustedSourcesDialog`): when the user taps "Trust" on a 3rd extension, show an AlertDialog:
  > "You can only have 2 extensions in Sources. Remove one of these to continue:"
  > • [Source 1 name] — Remove
  > • [Source 2 name] — Remove
  > [Cancel]
  
  Tapping "Remove" on one revokes its trust (`trustedSources().set(current - pkg)`, reload as Untrusted), then trusts the new one.

- The ViewModel exposes a `trustResult: StateFlow<TrustResult>` where `TrustResult` is sealed: `Success | LimitExceeded(currentTrusted: List<String>) | Error(message)`. The screen observes it and shows the popup on `LimitExceeded`.

#### A.1.7 Gate app functionality on trusted sources only
- `AniyomiSourceBridge.findMatch()` and `searchExtensions()` must only search across the 2 trusted sources (not all installed). `AndroidAnimeSourceManager.getCatalogueSources()` filters by `trustedSources()`.
- This is the single most important behavioral change: **untrusted installed extensions are NOT used for search/resolve/play.** They sit in "Installed" until trusted.

### A.2 Files touched (Workstream A)

**Delete (stubs):**
- `app/.../domain/mihon/extensionrepo/anime/interactor/GetAnimeExtensionRepo.kt`
- `app/.../domain/mihon/extensionrepo/anime/interactor/UpdateAnimeExtensionRepo.kt`
- `app/.../domain/mihon/extensionrepo/model/ExtensionRepo.kt`
- `app/.../domain/extension/anime/interactor/TrustAnimeExtension.kt`

**Create:**
- `app/.../ui/settings/extensionrepos/ExtensionReposScreen.kt` + `ExtensionReposScreenModel.kt` + `components/RepoDialogs.kt`
- `app/.../ui/extension/details/ExtensionDetailsScreen.kt` + `ExtensionDetailsScreenModel.kt`
- `app/.../ui/extension/details/SourcePreferencesScreen.kt` + `SourcePreferencesFragment.kt` + `SharedPreferencesDataStore.kt`
- `app/.../ui/extension/components/MaxTrustedSourcesDialog.kt`
- `app/.../extension/anime/util/AnimeExtensionInstaller.kt` + `AnimeExtensionInstallService.kt` + `AnimeExtensionInstallActivity.kt` + `AnimeExtensionInstallReceiver.kt`

**Modify:**
- `app/.../di/AppModule.kt` — register repo repository + service + interactors + real `TrustAnimeExtension`.
- `app/.../extension/anime/util/AnimeExtensionLoader.kt` — `isExtensionTrusted()` calls the real `TrustAnimeExtension`.
- `app/.../extension/anime/AnimeExtensionManager.kt` — add `installExtension()`, `uninstallExtension()`, `trust()` (with cap), `revokeTrust()`.
- `app/.../extension/anime/AndroidAnimeSourceManager.kt` — `getCatalogueSources()` filters by `trustedSources()`.
- `app/.../ui/settings/ExtensionsSettingsScreen.kt` — restructure into Sources / Installed / Available sections; add row-click → details page; add Trust/Delete actions.
- `app/.../ui/settings/ExtensionsViewModel.kt` — expose 3 lists + `trustResult`; replace hand-rolled install with `extensionManager.installExtension`.
- `app/src/main/AndroidManifest.xml` — register install receiver + permissions.
- NavGraph: add `extension_repos`, `extension_details/{pkgName}`, `source_preferences/{sourceId}` routes.

### A.3 Verification (Workstream A)
- Add a repo from the Repos screen → "Available" section populates with extensions from `/index.min.json`.
- Install an extension from "Available" → it moves to "Installed" (untrusted). It does NOT appear in search/resolve.
- Tap "Trust" on an installed extension → it moves to "Sources". Now it's used for search/resolve.
- Trust a 2nd extension → works.
- Try to trust a 3rd → popup shows the 2 current Sources + "Remove" buttons.
- Tap a Sources extension → details page opens → tap Settings gear → extension's own preference screen renders → change a setting → it persists to `source_$id` SharedPreferences → extension reads it on next network call.
- Untrust a Sources extension → it moves back to "Installed".
- Uninstall an installed extension → it disappears.

---

## Workstream B — Downloads preferences redesign

### B.0 Current state (from research)

- ANI-KUTA's `DownloadPreferences` has 3 single-value prefs: `preferredQuality` (String, default "720p"), `preferredAudioVersion` (String, default "sub"), `preferredServer` (String, default "" = auto). All three are **single-select** in the UI (dropdowns + a free-text field).
- **None of these prefs are actually consumed** — `DownloadManager.enqueueDownload()` takes a pre-resolved `Video` and is never called anywhere. The download pipeline is not wired to any UI action.
- aniyomi has NO global quality/audio/server prefs — it delegates entirely to extensions via `sortHosters()`/`sortVideos()` + the `Video.preferred` flag. ANI-KUTA must build the entire priority-list system from scratch.
- The drag-drop library `sh.calvin.reorderable:reorderable:2.4.3` is used by aniyomi (for category reordering) but is NOT a dependency in ANI-KUTA yet. The usage pattern (`rememberReorderableLazyListState` + `ReorderableItem` + `Modifier.draggableHandle()`) is well-documented in aniyomi's `AnimeCategoryScreen.kt`.

### B.1 Tasks

#### B.1.1 Add the reorderable dependency
Add to `gradle/libs.versions.toml`:
```toml
reorderable = { module = "sh.calvin.reorderable:reorderable", version = "2.4.3" }
```
Add to `app/build.gradle.kts`:
```kotlin
implementation(libs.reorderable)
```

#### B.1.2 Redesign `DownloadPreferences` — ordered lists + priority toggle + fallback mode
Replace the 3 single-value prefs with ordered-list prefs (serialized to JSON strings via `kotlinx.serialization`):

```kotlin
// Ordered priority lists (highest priority first)
fun preferredQualityOrder(): Preference<List<String>>   // e.g. ["360p","720p","1080p"]
fun preferredAudioOrder(): Preference<List<String>>     // e.g. ["dub","sub"]
fun preferredServerOrder(): Preference<List<String>>    // e.g. ["VidPlay-1","HD-1",...]

// Which dimension wins when both can't be satisfied
fun qualityVsAudioPriority(): Preference<PriorityMode>  // QUALITY_FIRST or AUDIO_FIRST

// What to do when the preferred audio version is unavailable
fun audioFallbackMode(): Preference<AudioFallback>      // FAIL (don't download) or NEXT (fall back to next audio version)
fun qualityFallbackMode(): Preference<QualityFallback>  // always NEXT (fall back to next quality) — no "fail" option per spec
```

Enums:
```kotlin
enum class PriorityMode { QUALITY_FIRST, AUDIO_FIRST }
enum class AudioFallback { FAIL, NEXT }
enum class QualityFallback { NEXT }  // only one mode, but enum for extensibility
```

**Migration:** on first run of Phase 7, convert the old single-value prefs (`download_quality`, `download_audio_version`, `download_preferred_server`) to single-element ordered lists, so existing users keep their Phase 6 choice as the top priority.

#### B.1.3 Redesign `DownloadsSettingsScreen` — 3 drag-and-drop lists + 2 toggles
Replace the 2 dropdowns + text field with:

1. **Preferred quality** — a `ReorderableLazyColumn` of quality chips (`1080p`, `720p`, `360p`, `480p`, `2160p`, + "Add custom"). Each item has a drag handle. User drags to reorder. Top = highest priority.
2. **Preferred audio version** — a `ReorderableLazyColumn` of audio chips (`SUB`, `DUB`, `HSUB`, + "Add custom"). Same drag-to-reorder.
3. **Quality-vs-audio priority** — a 2-item reorderable list (just `[Quality, Audio]` or `[Audio, Quality]`). Top wins. This is the "does quality get priority or audio version" control the user asked for.
4. **Preferred server** — a `ReorderableLazyColumn` populated DYNAMICALLY from the trusted sources' hoster names (calls a new `GetAvailableServers` interactor that queries `AnimeExtensionManager` for each trusted source's known server names). User drags to reorder. Servers not in the list are deprioritized (used only if no listed server is available).
5. **Audio fallback mode** — a radio: "Show error (don't download)" vs "Download next available audio version".

Copy the drag pattern from aniyomi's `AnimeCategoryScreen.kt`:
```kotlin
val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
    val item = qualitiesState.removeAt(from.index)
    qualitiesState.add(to.index, item)
    saveOrder()
}
LazyColumn(state = lazyListState) {
    items(qualitiesState, key = { it }) { quality ->
        ReorderableItem(reorderableState, quality) {
            QualityRow(quality, modifier = Modifier.draggableHandle())
        }
    }
}
```

#### B.1.4 Build `DownloadVideoResolver` — the real priority resolver
New file: `app/.../download/DownloadVideoResolver.kt`.

Modeled on aniyomi's `HosterLoader.getBestVideo()` (concurrent hoster resolution + retry loop) but applying ANI-KUTA's priority lists instead of the `preferred` flag.

**Algorithm:**
```
1. Fetch raw hosters: source.getHosterList(episode) — catch ALL → fallback source.getVideoList(episode)
2. Flatten all videos across hosters.
3. Parse each video: VideoTitleParser.parse(video) → ParsedVideo(server, audio, quality)
4. Apply priority:
   a. If PriorityMode == QUALITY_FIRST:
      - For each quality in preferredQualityOrder (top first):
        - For each audio in preferredAudioOrder (top first):
          - Find videos matching (quality, audio)
          - For each server in preferredServerOrder (top first): pick first matching video
          - If found and resolves → return it
        - If no audio match AND audioFallback == FAIL → continue to next quality
        - (if audioFallback == NEXT → already tried all audios above)
      - If exhausted all qualities → return QualityNotAvailable
   b. If PriorityMode == AUDIO_FIRST: swap the outer/inner loops (audio outer, quality inner)
5. If nothing found → return NoVideo
```

**Returns a sealed `ResolveResult`:**
```kotlin
sealed class ResolveResult {
    data class Success(val video: Video) : ResolveResult()
    data object NoVideo : ResolveResult()           // extension returned no playable videos
    data object QualityNotAvailable : ResolveResult()
    data object AudioNotAvailable : ResolveResult()  // only if audioFallback == FAIL
}
```

The UI (download button on episode) shows a snackbar with the appropriate message on failure.

**Important:** the resolver calls `source.getHosterList()`/`getVideoList()` WITHOUT relying on the extension's `sortHosters()`/`sortVideos()` — it applies ANI-KUTA's ordering to the raw returned lists. This satisfies "extension's own settings will NOT be applied for downloads — these download preferences will be applied."

#### B.1.5 Wire `enqueueDownload` to a real trigger + route through resolver
Currently `DownloadManager.enqueueDownload(anilistId, episode, video, animeTitle)` takes a pre-resolved `Video` but is never called. Change to:
- `DownloadManager.enqueueDownload(anilistId, episode, animeTitle, source)` — takes the source, calls `DownloadVideoResolver` internally, then enqueues the `DownloadWorker` with the resolved `videoUrl` + `videoHeaders`.
- Add a download trigger in the UI: long-press an episode → "Download" (or a download icon on the episode row). This calls `downloadManager.enqueueDownload(...)`.
- `DownloadWorker` needs no change (it already downloads a given URL + headers) — but pass `videoHeaders` as work data so MPV/HTTP gets the right Referer.

### B.2 Files touched (Workstream B)

**Create:**
- `app/.../download/DownloadVideoResolver.kt`
- `app/.../ui/settings/downloads/components/ReorderablePriorityList.kt` (reusable drag-list component)
- `app/.../ui/settings/downloads/components/QualityPriorityList.kt`
- `app/.../ui/settings/downloads/components/AudioPriorityList.kt`
- `app/.../ui/settings/downloads/components/ServerPriorityList.kt`
- `app/.../domain/download/GetAvailableServers.kt` (interactor)

**Modify:**
- `gradle/libs.versions.toml` + `app/build.gradle.kts` — add `reorderable` dep.
- `app/.../download/DownloadPreferences.kt` — replace 3 single prefs with ordered-list prefs + 2 enums + migration.
- `app/.../ui/settings/downloads/DownloadsSettingsScreen.kt` — full rewrite of the 3 preference sections.
- `app/.../download/DownloadManager.kt` — `enqueueDownload` takes source + calls resolver.
- `app/.../ui/detail/DetailScreen.kt` + `DetailViewModel.kt` — add episode long-press → Download action.

### B.3 Verification (Workstream B)
- Open Downloads settings → see 3 drag lists (quality, audio, server) + 1 quality-vs-audio toggle + 1 audio-fallback radio.
- Drag 360p to top of quality list → it's now preferred.
- Drag Dub to top of audio list → dub preferred.
- Set quality-vs-audio = AUDIO_FIRST.
- Download an episode where dub-360p doesn't exist but dub-720p does → downloads dub-720p (audio first, then next quality).
- Set audio-fallback = FAIL. Download an episode with no dub at all → shows "Audio not available" error, doesn't download.
- Set audio-fallback = NEXT. Same episode → falls back to sub.

---

## Workstream C — Episode list + detail caching & refresh

### C.0 Current state (from research)

- ANI-KUTA's `DetailViewModel` uses an **in-memory `episodeCache: Map<Int, Pair<List<SEpisode>, String>>`** (companion object — survives navigation, dies on app restart) + a **persistent PreferenceStore cache for ONLY the source→anime mapping** (`ext_match_$id`, `ext_sanime_url_$id`). The episode LIST itself is not persistently cached.
- On cache hit, the function returns early — **no background refresh.**
- The SQLDelight `episodes` table + `EpisodeRepositoryImpl` (with `getEpisodeByAnimeIdAsFlow`) **already exist but are dormant** — `DetailViewModel` doesn't use them.
- **No pull-to-refresh at all** — only a "Retry" TextButton on the Error state.
- aniyomi's pattern: DB-backed episodes + `SyncEpisodesWithSource` (diff new/updated/removed) + `Flow` subscription for smooth UI updates + single-stage `PullRefresh`. The "show cached, refresh in background" pattern is in `AnimeScreenModel.init`.

### C.1 Tasks

#### C.1.1 Wire `DetailViewModel` to the DB (persistent episode cache)
- Register `EpisodeRepository` in `AppModule.kt` (the impl already exists in `data/`).
- In `DetailViewModel.loadEpisodes()`:
  - First, subscribe to `episodeRepository.getEpisodeByAnimeIdAsFlow(anilistId.toLong())` — this Flow emits the current DB-cached episode list immediately and re-emits whenever the DB is written.
  - Show the cached list instantly (`_episodes.value = EpisodeState.Loaded(cachedEps, sourceName)`).
  - Then launch a background coroutine to `fetchEpisodesFromSource()` (see C.1.2).
- This replaces the in-memory `episodeCache` Map. The DB IS the cache. (Keep the Map as an L0 if needed for instant display before the DB Flow's first emission, but it's likely unnecessary.)

**Map AniList anime → DB anime:** the `episodes` table is keyed by `anime_id` (a Long). ANI-KUTA's anime come from AniList (id is an Int). Need a stable mapping: use `anilistId.toLong()` as the `anime_id` in the `episodes` table. (The `anime` table in ANI-KUTA's DB may need a row per AniList anime — check if `AnimeRepositoryImpl` already handles this; if not, insert a minimal anime row on first detail-page visit.)

#### C.1.2 Port `SyncEpisodesWithSource` (smooth diff + DB write)
Port from aniyomi's `SyncEpisodesWithSource.kt` (selective copy):
- Input: raw `List<SEpisode>` from the extension, the anime, the source, `manualFetch: Boolean`.
- Diff against DB episodes (by `episode.url`):
  - New episodes (URL not in DB) → insert.
  - Removed episodes (DB URL not in source) → delete.
  - Updated episodes (name/number changed) → update.
- Write to DB via `EpisodeRepository`.
- The DB write triggers the Flow (C.1.1) → UI updates smoothly (new episodes appear in sorted position, removed ones disappear).

**Background refresh policy (NOVEL — stricter than aniyomi):**
- aniyomi only fetches from source if DB is empty. ANI-KUTA's requirement: **always soft-refresh in background** to check for new episodes, even on a cache hit.
- Implementation: after showing the cached list, always launch `viewModelScope.launch { fetchEpisodesFromSource(manualFetch = false) }`. This hits the extension, diffs, writes to DB → Flow emits → UI updates. If nothing changed, no DB write, no UI flicker.
- Guard: don't run background refresh more than once per N minutes (e.g. 5 min) per anime, to avoid hammering the extension on every navigation. Store `lastRefreshTime_$anilistId` in the PreferenceStore.

#### C.1.3 Build the 3-stage pull-to-refresh (NOVEL — custom `nestedScroll`)
aniyomi's `PullRefresh` is single-stage Material3. ANI-KUTA needs 3 stages. Build a custom composable: `ThreeStagePullRefresh.kt`.

**Design:**
- A `Modifier.nestedScroll` connection that tracks the pull-down distance.
- Two thresholds: `threshold1` (e.g. 120dp), `threshold2` (e.g. 240dp), `threshold3` (e.g. 360dp).
- Exposes `StateFlow<RefreshStage>`:
  ```kotlin
  enum class RefreshStage { IDLE, EPISODES, DETAILS, EVERYTHING }
  ```
- As the user pulls:
  - Past `threshold1` → stage = `EPISODES` → indicator shows "Release to refresh episodes".
  - Past `threshold2` → stage = `DETAILS` → indicator shows "Release to refresh details".
  - Past `threshold3` → stage = `EVERYTHING` → indicator shows "Release to refresh everything".
  - On release, execute the action for the current stage and reset to IDLE.
- Custom indicator overlay (a `Surface` at top center with the stage label + a progress arc).

**`DetailViewModel` refresh methods:**
- `refreshEpisodesOnly()` — calls `fetchEpisodesFromSource(manualFetch = true)` (bypasses the 5-min guard). Updates only the episode list.
- `refreshDetailsOnly()` — bypasses `CacheManager.getOrFetch` cache, calls `anilistRepo.getAnimeDetails(anilistId)` directly, updates `_anime` (title, description, cover, etc.).
- `refreshEverything()` — both of the above + cover re-fetch + force re-resolve source match (clear `ext_match_$id` cache, re-run `findMatch`).

**Wrap `DetailScreen`'s `LazyColumn`** in the `ThreeStagePullRefresh` composable.

### C.2 Files touched (Workstream C)

**Create:**
- `app/.../ui/components/ThreeStagePullRefresh.kt` (custom nestedScroll + indicator)
- `domain/.../items/episode/interactor/SyncEpisodesWithSource.kt` (port from aniyomi)

**Modify:**
- `app/.../di/AppModule.kt` — register `EpisodeRepository` + `SyncEpisodesWithSource`.
- `app/.../ui/detail/DetailViewModel.kt` — replace in-memory `episodeCache` with DB Flow subscription + background refresh + 3 refresh methods.
- `app/.../ui/detail/DetailScreen.kt` — wrap `LazyColumn` in `ThreeStagePullRefresh`.

### C.3 Verification (Workstream C)
- Open a detail page you've visited before → episodes appear INSTANTLY (from DB) → a few seconds later, new episodes (if any) appear smoothly without a flicker.
- Pull down slightly → "Release to refresh episodes" → release → episode list refreshes.
- Pull down further → "Release to refresh details" → release → AniList metadata (title/description/cover) refreshes.
- Pull down all the way → "Release to refresh everything" → release → full refresh (episodes + details + cover + source re-match).
- Kill the app, reopen → episodes still there (DB persistent).

---

## Workstream D — Episode video caching + picker redesign

### D.0 Current state (from research)

- ANI-KUTA's `playEpisode()` **always re-resolves** videos from the extension on every tap. No cache. Shows a full-screen "Resolving video…" overlay every time.
- `VideoPickerState` is a sealed class with only `Hidden` and `Show` — no `Cached` state.
- The picker is a `ModalBottomSheet` with a plain `Column` (NO scroll — the bug), NO collapse state, NO sort, NO title parsing.
- The `Video` model has `videoTitle: String` + `resolution: Int?` + `audioTracks: List<Track>`. The AniKoto extension produces titles like `"VidPlay-1 - SUB - 360p"`.
- aniyomi's `QualitySheet.kt` uses `LazyColumn` (scrolls) + `List<Boolean>` for collapse + structural `Hoster` grouping — but does NOT group by audio version or sort by quality.
- aniyomi does NOT cache videos (re-fetches every time — signed URLs expire).

### D.1 Tasks

#### D.1.1 Build `VideoTitleParser` (reused by B's resolver + D's picker)
New file: `app/.../ui/detail/VideoTitleParser.kt`.

```kotlin
data class ParsedVideo(
    val video: Video,
    val server: String,          // "VidPlay-1" or fallback to full title
    val audio: AudioVersion,     // SUB / DUB / HSUB / ANY
    val quality: Int?,           // 1080, 720, 360, or null
)

object VideoTitleParser {
    private val QUALITY_REGEX = Regex("""\b(\d{3,4})p\b""", RegexOption.IGNORE_CASE)
    private val AUDIO_REGEX = Regex("""\b(SUB|DUB|HSUB|HARDSUB|SUBBED|DUBBED)\b""", RegexOption.IGNORE_CASE)

    fun parse(video: Video): ParsedVideo {
        val title = video.videoTitle
        val quality = video.resolution ?: QUALITY_REGEX.find(title)?.groupValues?.get(1)?.toIntOrNull()
        val audioToken = AUDIO_REGEX.find(title)?.value?.uppercase()
        val audio = when {
            audioToken == null -> AudioVersion.ANY
            audioToken.startsWith("DUB") -> AudioVersion.DUB
            audioToken.startsWith("SUB") && audioToken != "HSUB" -> AudioVersion.SUB
            audioToken == "HSUB" || audioToken == "HARDSUB" -> AudioVersion.HARDSUB
            else -> AudioVersion.ANY
        }
        val server = title.substringBefore(" - ").trim().ifBlank { title }
        return ParsedVideo(video, server, audio, quality)
    }
}
```

Graceful degradation: if the title doesn't match the `"{server} - {audio} - {quality}"` format (other extensions), `server` falls back to the full title, `audio` to `ANY`, `quality` to `resolution` or null. The picker still works — it just groups under "All" / "Unknown".

#### D.1.2 Short-TTL video cache + `VideoPickerState.Cached`
**Cache design** (NOVEL — aniyomi doesn't cache videos):
- `videoCache: Map<String, CachedVideos>` keyed by `episode.url` (stable across navigations).
- `CachedVideos(serverGroups: List<ServerGroup>, timestamp: Long)`.
- TTL: 10 minutes (configurable). Signed URLs from some extensions expire; 10 min is a safe upper bound. On cache hit, show cached list instantly + launch background re-resolve.
- Also persist to PreferenceStore? No — signed URLs make persistent caching unsafe. In-memory only (survives navigation, dies on app restart). The episode-list DB cache (Workstream C) already survives restart; re-resolving videos on a fresh app start is acceptable.

**`VideoPickerState` redesign:**
```kotlin
sealed class VideoPickerState {
    data object Hidden : VideoPickerState()
    data class Resolving(val episode: SEpisode) : VideoPickerState()  // first-time resolve (no cache)
    data class Cached(val episode: SEpisode, val serverGroups: List<ServerGroup>, val isRefreshing: Boolean) : VideoPickerState()
    data class Show(val episode: SEpisode, val serverGroups: List<ServerGroup>) : VideoPickerState()
}
```

**`playEpisode()` flow:**
```
1. Check videoCache[episode.url]:
   a. HIT (not expired): _videoPicker.value = Cached(episode, cachedGroups, isRefreshing=true)
      Launch background re-resolve. On completion:
        - If new groups differ from cached → _videoPicker.update { Show(episode, newGroups) } (smooth swap)
        - If same → _videoPicker.update { Show(episode, cachedGroups) } (no visible change)
        - videoCache[episode.url] = CachedVideos(newGroups, now)
   b. MISS: _videoPicker.value = Resolving(episode)
      Resolve. On completion: _videoPicker.value = Show(episode, groups)
      videoCache[episode.url] = CachedVideos(groups, now)
2. If exactly 1 video → skip picker, auto-play.
```

The `Cached` state shows the picker INSTANTLY with a subtle "Refreshing…" badge in the corner. When the background re-resolve completes, the badge disappears and any changes (new qualities, removed servers) update smoothly via `MutableStateFlow.update`.

#### D.1.3 Redesign the picker UI — scroll + collapse + audio grouping + quality sort
Rewrite the picker in `DetailScreen.kt` (or extract to `VideoPickerSheet.kt`).

**New grouping structure** (per the user's spec — audio version is the top-level section):
```kotlin
data class AudioSection(
    val audio: AudioVersion,          // SUB / DUB / HSUB / ANY
    val servers: List<ServerSection>,  // collapsible
)
data class ServerSection(
    val serverName: String,
    val videos: List<Video>,          // sorted by quality DESC (1080p top, 360p bottom)
)
```

Build from `List<ServerGroup>` → parse each video → group by audio → within each audio, group by server → within each server, sort by quality desc.

**UI structure:**
```
ModalBottomSheet
  LazyColumn (FIXES the scroll bug)
    for each AudioSection:
      item { AudioHeader(audio.label, videoCount) }   // "SUB · 12 videos"
      for each ServerSection in AudioSection:
        item {
          ServerHeader(serverName, isExpanded, onClick = toggle)
        }
        if (isExpanded):
          items(videos) { video ->
            VideoRow(video)   // "1080p" chip + play button
          }
```

**Collapse state:** `expandedServers: Set<String>` (set of `"${audio}_${serverName}"` keys) in the ViewModel, toggled by `toggleServer(audio, serverName)`. Default: all expanded (matches aniyomi).

**Quality sort:** `videos.sortedByDescending { VideoTitleParser.parse(it).quality ?: 0 }`. Highest quality (1080p) at top, lowest (360p) at bottom — exactly as the user requested.

**Scroll:** `LazyColumn` inside `ModalBottomSheet` natively scrolls. Set `Modifier.fillMaxHeight(0.85f)` on the sheet so it takes most of the screen and the list has room to scroll.

### D.2 Files touched (Workstream D)

**Create:**
- `app/.../ui/detail/VideoTitleParser.kt`
- `app/.../ui/detail/VideoPickerSheet.kt` (extracted from DetailScreen, rewritten)

**Modify:**
- `app/.../ui/detail/DetailViewModel.kt` — add `videoCache` + `VideoPickerState.Resolving`/`Cached` + background re-resolve + `AudioSection`/`ServerSection` grouping + `expandedServers` state + `toggleServer()`.
- `app/.../ui/detail/DetailScreen.kt` — replace the picker `Column` with `VideoPickerSheet` (LazyColumn + collapsible + grouped).

### D.3 Verification (Workstream D)
- Tap an episode → "Resolving video…" overlay → picker appears.
- Tap the SAME episode again → picker appears INSTANTLY (cached) with a small "Refreshing…" badge → a moment later the badge disappears (no visible change if same).
- If the background refresh finds a new quality (e.g. a 480p appears) → it appears smoothly in the right sorted position without the picker flickering.
- Picker shows 3 audio sections: SUB, DUB, HSUB (if present). Within SUB: servers (VidPlay-1, HD-1, Vidstream-2, VidCloud-1). Within each server: 1080p, 720p, 360p (descending).
- Tap a server header → its videos collapse/expand with an animation.
- With 27 videos (the log's example), scroll to the bottom → all videos reachable. No truncation.

---

## Cross-cutting concerns

### X.1 Signed URL expiry (risk)
Some extensions return time-limited signed URLs (15-min expiry). The video cache (D.1.2) uses a 10-min TTL to stay safe. If a cached URL has expired by the time the user taps play, MPV will fail with a 403/network error. Mitigation: on play failure, clear the cache entry for that episode and re-resolve. The existing error overlay (Session 19) already shows the error + "Go Back"; we add a "Retry" button that re-resolves.

### X.2 Extension diversity
The AniKoto extension uses `"{server} - {audio} - {quality}"` format. Other extensions (the user will share more logs later) may use different formats. `VideoTitleParser` degrades gracefully (falls back to `ANY` audio, full-title server, `resolution` field for quality). As the user shares more extension logs, we extend the parser with additional format patterns. **Phase 7 ships the AniKoto format + graceful fallback; other formats are added incrementally.**

### X.3 DB schema check
The `episodes` table + `EpisodeRepositoryImpl` already exist (copied from aniyomi). Verify the `anime` table has a row per AniList anime (keyed by `anilistId.toLong()`) before C.1.1 can subscribe to `getEpisodeByAnimeIdAsFlow`. If the `anime` table is empty, insert a minimal row on first detail-page visit.

### X.4 Backward compatibility
- Old single-value download prefs → migrated to single-element ordered lists (B.1.2).
- Old `ext_match_$id` / `ext_sanime_url_$id` persistent cache → kept (still useful for source-match skip). The episode list moves to the DB.
- `mpvInitialized` flag → kept (belt-and-suspenders, per Session 19).

### X.5 Build verification
After each workstream, push to GitHub → GitHub Actions builds arm64-v8a APK → user installs → verifies on-device → we fix. No parallel tasks during the 3-min build wait (per user's rule).

---

## Open questions for the user (to confirm before implementation)

1. **Trust model confirmation:** An extension must pass BOTH (a) cryptographic trust (signing key matches a repo's fingerprint OR user explicitly trusted it) AND (b) be in the `trustedSources()` policy set (max 2). Is that the right model? Or should "Trust" be a single action that does both at once (which is simpler but less flexible)?

2. **Max-2-trusted popup UX:** When the user tries to trust a 3rd, the popup lists the 2 current Sources with "Remove" buttons. After removing one, should the new extension be trusted AUTOMATICALLY, or should the popup close and the user has to tap "Trust" again? (Auto-trust is fewer taps; manual is safer.)

3. **Video picker grouping depth:** The plan proposes a 3-level hierarchy: Audio Version → Server (collapsible) → Quality (sorted desc). Alternative: Audio Version → Quality (sorted desc) → Server (flat list within each quality). Which does the user prefer? (The plan defaults to the first, matching "dedicated sections for each audio version" + "collapse the servers".)

4. **3-stage pull-to-refresh thresholds:** 120dp / 240dp / 360dp. Are these comfortable? (Too small = accidental triggers; too large = hard to reach stage 3.) We can tune after first on-device test.

5. **Video cache TTL:** 10 minutes (safe for signed URLs). Is that acceptable, or should it be shorter/longer?

6. **Background episode refresh guard:** 5 minutes per anime (don't re-fetch more often than every 5 min). Acceptable?

7. **Implementation order:** A → C → D → B (recommended), or a different order?

8. **Other extensions:** The user said they'll share more extension logs later. Should Phase 7 ship with only the AniKoto format parser + graceful fallback, and add other formats as logs arrive? (Plan assumes yes.)

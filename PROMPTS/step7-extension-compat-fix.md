# Prompt — Extension Binary Compatibility Fix (Step 7)

You are a new AI agent joining the ANIKUTA Android project. This prompt gives
you the full context of a critical extension-compatibility fix that was applied
on 2026-07-21. Read this BEFORE touching any file in `core/source-api/` or
`app/.../App.kt`.

## Background — what broke

The user tested the previous APK and reported:
1. **First anime open → app crashed** (one-time, then worked on reopen)
2. **Second anime open → "no sources have this anime"** despite having 2 trusted extensions
3. No pull-to-refresh on the details page
4. No manual search/linking button next to the Episodes header

The Logcat (`NEW_LOGCAT.MD`) showed:
```
java.lang.IncompatibleClassChangeError: Found interface eu.kanade.tachiyomi.network.NetworkHelper,
but class was expected
```

## Root causes

### Cause 1 — NetworkHelper was an interface (CRASH)

The new project declared `NetworkHelper` as an `interface`:
```kotlin
interface NetworkHelper {
    val client: OkHttpClient
    val cloudflareClient: OkHttpClient
    ...
}
```

But Keiyoushi/Aniyomi extensions are compiled against the **reference**
`NetworkHelper` which is a **class**. Extension bytecode uses
`invokevirtual NetworkHelper.getClient()` — this bytecode instruction is
**illegal** when `NetworkHelper` is an interface (interfaces require
`invokeinterface`). The JVM throws `IncompatibleClassChangeError` and kills
the app.

### Cause 2 — Lazy flow race (NO SOURCES ON 2ND OPEN)

`SourceMatcher.getCatalogueSources()` read:
```kotlin
extensionManager.installedExtensionsFlow.value  // ← BUG
```

`installedExtensionsFlow` is built with `stateIn(SharingStarted.Lazily, emptyList())`.
Its `.value` returns the **empty initial list** until the first subscriber
arrives and the `map` operator runs. On a fresh app start where the user
navigates directly to a detail page (without first visiting the Extensions
screen), no subscriber had collected the flow yet → `.value` was `emptyList()`
→ "no catalogue sources available" even though 2 extensions were installed.

## The fix (already applied — do NOT revert)

### 1. NetworkHelper is a CLASS (not interface)

`core/source-api/.../network/NetworkHelper.kt`:
```kotlin
class NetworkHelper(
    private val context: Context? = null,
) {
    val client: OkHttpClient = ...
    val cloudflareClient: OkHttpClient = client
    val defaultUserAgent: String = "Mozilla/5.0 ..."
    fun defaultUserAgentProvider(): String = defaultUserAgent
}
```

**NEVER change this back to an interface.** It would re-introduce the crash.

### 2. AnimeHttpSource uses `by injectLazy()`

`core/source-api/.../online/AnimeHttpSource.kt`:
```kotlin
protected val network: NetworkHelper by injectLazy()
```

This matches the reference exactly. Extensions expect the shared singleton
(registered in Injekt by `App.kt`), not a per-source instance.

### 3. source-api depends on Injekt

`core/source-api/build.gradle.kts`:
```kotlin
api("com.github.mihonapp:injekt:91edab2317")
```

Must be `api` (not `implementation`) so extensions loaded at runtime can
resolve injekt types from the host classpath.

### 4. App.kt registers Injekt singletons

`app/.../App.kt` registers FOUR singletons in Injekt before any extension loads:
- `Application` — Keiyoushi extensions call `Injekt.get<Application>()`
- `Context` — same
- `NetworkHelper` — `AnimeHttpSource` resolves it via `injectLazy()`
- `Json` — Keiyoushi extensions call `Injekt.get<Json>()` in static initializers

If any of these is missing, extensions crash with `InjektionException` or
`ExceptionInInitializerError`.

### 5. SourceMatcher reads synchronously

`data/extension/.../matcher/SourceMatcher.kt`:
```kotlin
private fun getCatalogueSources(): List<AnimeCatalogueSource> {
    return extensionManager.getInstalledExtensions()  // ← synchronous
        .flatMap { it.sources }
        .filterIsInstance<AnimeCatalogueSource>()
}
```

`getInstalledExtensions()` reads `installedMap.value` directly (the underlying
StateFlow), which always has the current value. Never use
`installedExtensionsFlow.value` for synchronous reads.

### 6. All extension-facing catches use `Throwable`

```kotlin
} catch (e: Throwable) {  // ← NOT Exception
    Log.e(TAG, "...", e)
    null
}
```

Extension errors throw `Error` subclasses (`IncompatibleClassChangeError`,
`NoClassDefFoundError`, `ExceptionInInitializerError`). Catching only
`Exception` lets these propagate and crash the app.

## New UI features (already implemented)

### Pull-to-refresh

`DetailContent.kt` wraps the LazyColumn in Material3 `PullToRefreshBox`:
```kotlin
PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh) {
    LazyColumn { ... }
}
```

The ViewModel's `refresh()` re-runs all three stages (AniList + source match +
episodes) and drives `isRefreshing` for the indicator.

### Manual search sheet

`ManualSearchSheet.kt` is a `ModalBottomSheet` with:
- A search field (pre-filled with the anime's display title)
- A search button that calls `sourceMatcher.searchAllSources(query)`
- A results list (thumbnail + title + source name per row)
- Tapping a result calls `onLinkManual(source, sAnime)` which links it +
  loads episodes

### Source indicator + search button

`EpisodesSection.kt` header (next to "Episodes"):
- **Matched, multiple sources** → tappable source-name chip + search icon
- **Matched, single source** → source name text + search icon
- **No match** → prominent "Search manually" CTA button
- **Searching/loading** → search icon only

## Rules for future agents

1. **NEVER change `NetworkHelper` back to an interface.** It MUST be a class
   for binary compatibility with Keiyoushi extensions.

2. **NEVER use `= DefaultNetworkHelper()` in `AnimeHttpSource`.** Use
   `by injectLazy()` so extensions get the shared singleton.

3. **NEVER remove the Injekt registrations in `App.kt`.** All four
   (Application, Context, NetworkHelper, Json) are required by extensions.

4. **NEVER read `installedExtensionsFlow.value` for synchronous logic.** Use
   `getInstalledExtensions()` which reads the underlying map directly.

5. **ALWAYS catch `Throwable` (not `Exception`) around extension calls.**
   Extension errors are `Error` subclasses.

6. **ALWAYS compare with the reference (`OLD_ANIKUTA` or `ANIYOMI_REFRENCE`)
   before changing any file in `core/source-api/`.** That package is a
   binary-compatibility boundary.

## Files to read for full context

1. `ANIKUTA_PROJECT/ANIKUTA/core/source-api/src/main/kotlin/eu/kanade/tachiyomi/network/NetworkHelper.kt`
2. `ANIKUTA_PROJECT/ANIKUTA/core/source-api/src/main/kotlin/eu/kanade/tachiyomi/animesource/online/AnimeHttpSource.kt`
3. `ANIKUTA_PROJECT/ANIKUTA/app/src/main/java/app/confused/anikuta/App.kt`
4. `ANIKUTA_PROJECT/ANIKUTA/data/extension/src/main/java/app/confused/anikuta/data/extension/matcher/SourceMatcher.kt`
5. `ANIKUTA_PROJECT/ANIKUTA/feature/anime-details/src/main/java/app/confused/anikuta/feature/animedetails/AnimeDetailViewModel.kt`
6. `RULES/sessions/2026-07-21-0210-extension-compat-fix.md` — the full session handoff

## Verification

After any change to the extension-compat layer:
1. Commit + push to `main` (triggers CI per ADR-003).
2. Monitor the GitHub Actions run.
3. Download the APK artifact and install it.
4. Trust an extension, open an anime detail page — it should NOT crash.
5. Sources should be found on the FIRST open (no need to visit Extensions screen first).
6. Pull down on the detail page → refresh indicator should appear.
7. Tap the search icon next to "Episodes" → ManualSearchSheet should open.

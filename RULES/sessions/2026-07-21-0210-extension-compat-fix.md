# Session handoff — 2026-07-21 02:10 UTC

**Agent:** Z.ai Code (main session)
**Task ID:** 2
**Session started:** 2026-07-21 01:50 UTC
**Session ended:**   2026-07-21 02:10 UTC

## Goal of this session

Fix the ANIKUTA source-matching crash + "no sources on 2nd open" bug reported by
the user after testing the previous APK. Add pull-to-refresh, manual source
search/linking UI, and a source indicator next to the Episodes header. Then
commit, push to GitHub, trigger the CI build, and verify the APK is produced.

## What I did

- Downloaded and analyzed the user-provided Logcat (538 lines) from
  `https://github.com/testplay-byte/kuta/blob/main/NEW_LOGCAT.MD`
- Delegated full Logcat analysis to a general-purpose subagent (Task ID: 1)
  which produced a 342-line structured diagnostic report.
- Identified ROOT CAUSE #1 (crash): `IncompatibleClassChangeError` —
  `NetworkHelper` was declared as an `interface` but Keiyoushi extensions
  compile against it as a `class`.
- Identified ROOT CAUSE #2 (no sources on 2nd open): `SourceMatcher
  .getCatalogueSources()` read `installedExtensionsFlow.value` which uses
  `SharingStarted.Lazily` — returns empty initial list until first subscriber.
- Studied OLD_ANIKUTA's working implementation as the reference: `NetworkHelper`
  is a class, `AnimeHttpSource.network` uses `by injectLazy()`, `AppModule`
  registers `NetworkHelper` in Injekt.
- Applied 8 fixes across 11 files (see "What I changed" below).
- Committed with a detailed message, pushed to `main` (commit `8d8b8d9`).
- Verified the CI workflow run started (run ID `29794949081`, status
  `in_progress`).

## What I changed

**Source fixes (11 files, commit `8d8b8d9`):**
- `core/source-api/.../network/NetworkHelper.kt` — interface → class
- `core/source-api/build.gradle.kts` — added `api(injekt)`
- `core/source-api/.../online/AnimeHttpSource.kt` — `network by injectLazy()`
- `app/.../App.kt` — registered Application, Context, NetworkHelper, Json in Injekt
- `data/extension/.../matcher/SourceMatcher.kt` — `getInstalledExtensions()` + Throwable catches
- `feature/anime-details/.../AnimeDetailViewModel.kt` — isRefreshing/isSearching states + Throwable catches
- `feature/anime-details/.../AnimeDetailScreen.kt` — wires new states + callbacks
- `feature/anime-details/.../DetailContent.kt` — PullToRefreshBox wrapper
- `feature/anime-details/.../EpisodesSection.kt` — search icon + source indicator + CTA
- `feature/anime-details/.../ManualSearchSheet.kt` (NEW) — bottom sheet for manual search
- `feature/anime-details/.../EpisodeStates.kt` — NoSourcesState shows "Search manually" button

**Documentation (this session):**
- `RULES/sessions/2026-07-21-0210-extension-compat-fix.md` — this handoff
- `PROMPTS/step7-extension-compat-fix.md` — new agent prompt for the fix context

## Decisions made (and where they're recorded)

- **NetworkHelper MUST be a class (not interface)** — binary compatibility
  with Keiyoushi extension bytecode. Recorded in `NetworkHelper.kt` KDoc +
  this handoff. Future ADR candidate (ADR-030: Extension Binary Compatibility).
- **AnimeHttpSource.network uses `by injectLazy()`** — matches the reference
  exactly. Extensions expect the shared singleton, not a per-source instance.
- **Injekt MUST register Application, Context, NetworkHelper, Json** — all
  four are called by `Injekt.get<>()` in extension static initializers.
  Recorded in `App.kt` with detailed comments.
- **SourceMatcher reads `getInstalledExtensions()` not `installedExtensionsFlow.value`**
  — the lazy flow's `.value` returns empty until first subscriber. This was
  the "no sources on 2nd open" bug.
- **All extension-facing catches use `Throwable` not `Exception`** — binary
  incompat throws `Error` subclasses (IncompatibleClassChangeError,
  NoClassDefFoundError) which must be caught for extension isolation.

## What is DONE (exit criteria met)

- [x] Root cause of crash identified and fixed (NetworkHelper interface → class)
- [x] Root cause of "no sources" identified and fixed (lazy flow → sync read)
- [x] Pull-to-refresh added (Material3 PullToRefreshBox)
- [x] Manual search/linking UI added (ManualSearchSheet bottom sheet)
- [x] Source indicator added next to Episodes header
- [x] Error resilience hardened (Throwable catches, no more crashes)
- [x] Changes committed and pushed to GitHub
- [x] CI build triggered (run ID 29794949081)

## What is NOT done / in progress

- **CI build verification** — the build was `in_progress` at session end.
  Check run `29794949081` status. If it fails, read the logs and fix.
- **APK artifact verification** — once the build succeeds, the APK is
  uploaded as artifact `anikuta-debug-arm64-v8a`. Download from the Actions
  tab to verify.
- **ADR-030 (Extension Binary Compatibility)** — not yet formalized in
  `DOCS/04-design-decisions.md`. A future agent should add it documenting
  the NetworkHelper-must-be-a-class rule.

## Key files for the next agent to read

1. `ANIKUTA_PROJECT/ANIKUTA/core/source-api/src/main/kotlin/eu/kanade/tachiyomi/network/NetworkHelper.kt` — the class declaration + binary compat comments
2. `ANIKUTA_PROJECT/ANIKUTA/core/source-api/src/main/kotlin/eu/kanade/tachiyomi/animesource/online/AnimeHttpSource.kt` — the `by injectLazy()` pattern
3. `ANIKUTA_PROJECT/ANIKUTA/app/src/main/java/app/confused/anikuta/App.kt` — Injekt registration
4. `ANIKUTA_PROJECT/ANIKUTA/data/extension/src/main/java/app/confused/anikuta/data/extension/matcher/SourceMatcher.kt` — the sync read fix
5. `PROMPTS/step7-extension-compat-fix.md` — the full context of this fix

## Lessons learned (for future agents)

1. **Always compare with the reference before changing the source-api package.**
   The source-api package (`eu.kanade.tachiyomi.*`) is a binary-compatibility
   boundary — extensions are compiled against the Aniyomi reference, so any
   structural change (interface↔class, field layout, method signatures) will
   cause `IncompatibleClassChangeError` at runtime.

2. **`SharingStarted.Lazily` flows return empty `.value` until first subscriber.**
   When reading a StateFlow's current value for a synchronous operation (like
   source matching), use the underlying map's `.value` directly, or use
   `SharingStarted.Eagerly`, or read from the source map synchronously.

3. **Extension errors throw `Error` subclasses, not `Exception`.** Always catch
   `Throwable` around extension calls so one broken extension doesn't crash
   the app.

# Session Handoff — Search Page Implementation

**Date:** 2026-07-22 (UTC)
**Agent:** ANIKUTA Search Page Implementation Agent
**Branch:** `feature/search-page`
**PR:** https://github.com/testplay-byte/ANI_KUTA_NEW/pull/1 (DRAFT — do not merge)
**Latest commit:** `ad37410` — CI ✅ PASSED (run #115), APK artifact uploaded
**APK:** `anikuta-debug-arm64-v8a` (~39 MB) —
  https://github.com/testplay-byte/ANI_KUTA_NEW/actions/runs/29886635919

## Round 1 UX improvements (commit `ad37410`, after owner test feedback)

The owner tested the initial build + provided detailed feedback. 20 fixes
across 9 groups were implemented in one coherent round:

1. **Spacing** — reduced the vertical gap between the quick row (filters/sort)
   and the content below (topbar trailing spacer 4dp→2dp; content top padding
   tightened).
2. **Recent searches** — (a) collapse state now persists across screen changes
   + app restart via a new `SearchUiPreferences` store; (b) AniList and
   Extension now have SEPARATE recents lists (per-source `RecentSearchesStore`);
   (c) Extension mode now shows its own recents (was AniList-only).
3. **Filter sheet** — (a) filters are now BUFFERED (pending vs applied) — the
   sheet edits a pending copy; only "Apply filters" syncs + re-fetches (fixes
   "processing results before I clicked apply"); (b) Flat view content panel
   uses `animateContentSize()` — no sudden height jumps when switching tabs;
   (c) all AniList filter options present (genres/year/season/format/status/
   score/sort).
4. **Pagination** — AniList results now paginate on scroll-near-bottom
   (`currentPage++`, append); a "Loading more…" footer shows while fetching;
   `canLoadMore` stops when a page returns < 30 results.
5. **Sort dropdown** — redesigned: rounded surface, RobotoFamily, primary-
   colored active row + check icon.
6. **Score badge** — redesigned: dark translucent pill + lime star + white
   score (better contrast on any cover).
7. **Extension results UI** — (a) row titles now single-line; (b) removed the
   extension name from below extension cards (rows + search grid); (c) removed
   the "count · extensionName" from the Popular/Latest section header.
8. **Source toggle + picker** — (a) toggle widened 180dp→200dp + tighter
   padding (fixes "Extensi..." truncation); (b) new `ExtensionSourcePickerSheet`
   — a styled bottom sheet (dragHandle=null) with primaryContainer highlight +
   check on the selected source (replaces the ugly DropdownMenu).
9. **Linking flow UX** — (a) "Linked to AniList" toast only on FRESH links, not
   cache hits (`wasCached` flag); (b) linking sheet delayed 400ms — fast
   resolves skip the sheet entirely (no split-second flash); (c) "No matches on
   AniList" message now centered vertically in the sheet body.
10. **Preferred source for episodes** — `ExtensionLinkStore` gained a reverse
    lookup (`getPreferredSourceForAnilist`); `AnimeDetailViewModel` now prefers
    the source the user came from (via the link store) when no explicit source-
    switcher preference exists — fixes "it sometimes picks a completely
    different extension" for episode loading.

New files: `SearchUiPreferences.kt`, `ExtensionSourcePickerSheet.kt`.
Modified: `RecentSearchesStore.kt` (per-source), `SearchViewModel.kt` (pending
filters + pagination + per-source recents), `SearchScreen.kt`, `FilterSheet.kt`,
`ResultsCard.kt`, `ResultAnimeCard.kt`, `ExtensionResultsView.kt`,
`RecentSearchesCard.kt`, `SearchTopBar.kt`, `SourceToggle.kt`,
`ExtensionLinkingSheet.kt`, `ExtensionLinkingViewModel.kt`,
`ExtensionLinkStore.kt`, `AnimeDetailViewModel.kt`, `AnimeDetailScreen.kt`,
`MainActivity.kt`, `SearchModule.kt`.

---

## What was built (initial — commit bde381a)

A new `:feature:search` module implementing the dual-source Search page
(AniList + Extension), ported from the prototype at
`PROTOTYPE_REFERENCE/Anime_App/.../SearchScreen.kt` + `FilterSheet.kt`.

### Files created (15)

**`:feature:search` module scaffold:**
- `feature/search/build.gradle.kts` — module gradle (anikuta.library.compose + serialization)
- `feature/search/src/main/AndroidManifest.xml` — minimal manifest

**ViewModel layer:**
- `viewmodel/SearchViewModel.kt` — StateFlows for query/source/sort/filters/results/
  recents/extensionRows, debounced AniList + extension search, Popular/Latest row loading
  on Dispatchers.IO
- `viewmodel/ExtensionLinkingViewModel.kt` — drives the extension→AniList linking flow
  (cache check → auto-search → manual link / go-without-linking)

**UI layer (all animations copy the prototype EXACTLY):**
- `ui/SearchScreen.kt` — main screen, collapsing topbar + quick row + scrollable content,
  routes AniList taps to onOpenAnime + extension taps to onOpenExtensionResult
- `ui/SearchTopBar.kt` — title shrink (36→26sp), source toggle fade+shrink (180→0dp),
  search bar position swap, quick row slide-out (all tween(300, FastOutSlowInEasing))
- `ui/SearchBar.kt` — two sizes (52dp full / 44dp compact), RoundedCornerShape(50),
  BasicTextField + clear button, imeAction=Search
- `ui/SourceToggle.kt` — AniList/Extension pill (primaryContainer active)
- `ui/RecentSearchesCard.kt` — collapsible, individual delete, show more/less, clear all
- `ui/ResultsCard.kt` — surfaceVariant card, section header + count, 3-column chunked grid
- `ui/ResultAnimeCard.kt` — unified AniList/Extension card (cover + title + meta + score badge)
- `ui/ExtensionResultsView.kt` — Popular + Latest LazyRows in dedicated cards (extension default view)
- `ui/FilterSheet.kt` — full port of the prototype's 964-line sheet (Accordion + Flat modes,
  5 sections, dragHandle=null per design principle #2)
- `ui/ExtensionLinkingSheet.kt` — ModalBottomSheet (dragHandle=null) with cover + manual search
  + AniList results + "go without linking"

**Data layer:**
- `data/RecentSearchesStore.kt` — persisted recent query strings (max 12, dedup, ordered)

**`:data:extension` (shared with future ext-only detail page):**
- `data/extension/.../cache/ExtensionLinkStore.kt` — caches "$sourceId:$animeUrl" → anilistId
  via PreferenceStore.getObject (JSON-serialized Map<String,Int>)

### Files modified (5)
- `settings.gradle.kts` — added `include(":feature:search")`
- `app/build.gradle.kts` — added `implementation(projects.feature.search)`
- `app/.../MainActivity.kt` — wired the "search" nav route (was PlaceholderScreen),
  injects ExtensionLinkStore + RecentSearchesStore, renders ExtensionLinkingSheet overlay,
  "Linked to AniList" toast on link success
- `app/.../di/ExtensionModule.kt` — registered ExtensionLinkStore
- `app/.../di/SearchModule.kt` (NEW) — registered RecentSearchesStore
- `app/.../App.kt` — added searchModule to startKoin
- `core/anilist/.../api/AniListApi.kt` — added `searchAnimeWithFilters()` (dynamic GraphQL
  with genres/year/season/format/status/sort/minScore)
- `data/extension/build.gradle.kts` — added `core.preferences` dependency (for ExtensionLinkStore)
- `ARCHITECTURE.md` — documented the new `:feature:search` module

---

## Status: what works

✅ **Compiles on CI** (run #105, arm64-v8a APK built).
✅ **AniList search** — debounced plain search + filtered search (all FilterSheet options wired).
✅ **AniList default view** — popular anime when query is blank.
✅ **Extension search** — one source at a time via `SourceMatcher.searchOneSource`.
✅ **Extension default view** — Popular + Latest rows from the selected source.
✅ **Extension source picker** — tap Extension toggle while already selected → DropdownMenu
  of all trusted sources (per Q2).
✅ **Extension→AniList linking** — cache check → auto-search → manual link. "Linked to AniList"
  toast on success. Links persisted in ExtensionLinkStore.
✅ **Recent searches** — persisted across app restarts (SharedPreferences), individual delete,
  clear all, show more/less, collapsible.
✅ **FilterSheet** — full Accordion + Flat modes, all 5 sections, dragHandle=null.
✅ **Design language** — #B1F256 via MaterialTheme.colorScheme, RobotoFamily for headings,
  FastOutSlowInEasing tween(300) animations, no drag handles, edge-to-edge (statusBarsPadding),
  no hardcoded blue/indigo (audit passed).
✅ **Bottom nav clearance** — 110dp bottom padding on the scrollable content.

## Status: known limitations / NOT done

⚠️ **"Go without linking" path** — when the user picks "go without linking" on the linking
  sheet, the app shows a toast: "Extension-only detail page is a future enhancement."
  The existing `AnimeDetailScreen` requires an AniList ID; building an extension-only
  variant (D7/D8 in the task list) would require extending `:feature:anime-details` with
  a new entry point that takes `(source, SAnime)` instead of `anilistId: Int`. This is
  a meaningful separate task — flagged for a follow-up.
⚠️ **No runtime/visual verification** — I could not run the APK (no Android emulator in
  this environment; ADR-003 forbids local builds). CI confirms it compiles + the APK is
  produced, but I have NOT visually verified the animations, the filter sheet behavior,
  or the extension search end-to-end. The owner should install the APK and test the
  golden paths listed below.
⚠️ **RobotoFamily on some ExtraBold Text** — a few `Text(fontWeight = ExtraBold)` calls
  don't set `fontFamily = RobotoFamily` explicitly. They rely on the default
  `AnikutaTypography` (which sets RobotoFamily globally). Matches the prototype's
  behavior. If any heading renders non-bold on a device, add `fontFamily = RobotoFamily`.
⚠️ **`concurrency` in ci.yml** — pushes cancel in-progress runs. I worked around this by
  waiting for each run to finish before pushing the next fix. The repo's `ci.yml` has a
  pre-existing broken `branches: ain]` line (should be `[main]`) — NOT my bug, but it means
  push-to-branch doesn't trigger CI; only PR + workflow_dispatch do. Left untouched
  (out of scope).

---

## What to test (golden paths)

1. **Open the app → bottom nav → Search tab.** Should show "Search" title (36sp),
   AniList/Extension toggle (AniList active), search bar, "Popular anime" grid of 30.
2. **Type a query** (e.g. "frieren") → 500ms debounce → results grid updates, query
   added to recent searches.
3. **Scroll down** → title shrinks to 26sp, toggle fades+shrinks to 0 width, compact
   search bar appears beside the title, quick row slides out.
4. **Tap "Filters"** → FilterSheet opens (no drag handle). Try Accordion + Flat modes.
   Pick a genre + year → "Apply filters" → results re-fetch with filters.
5. **Tap "Clear all" in the FilterSheet** → filters reset.
6. **Tap a recent search** → re-searches that query.
7. **Tap the X on a recent** → deletes it. **Tap "Clear all"** → clears all recents.
8. **Switch to "Extension" toggle** → Popular + Latest rows from the top trusted source.
   (If no extensions installed → "No trusted extension installed" error message.)
9. **Tap the Extension toggle AGAIN while already selected** → source picker dropdown
   shows all trusted sources. Pick one → rows reload from that source.
10. **Type a query with Extension selected** → searches the selected source only.
11. **Tap an extension result** → linking sheet opens, auto-searches AniList by title.
    - If found → "Linked to AniList" toast → detail page opens.
    - If not found → manual search field + "Go without linking" button.
12. **Re-tap the same extension result** → goes straight to the detail page (cache hit).

---

## Next steps for the owner

1. **Review PR #1** — https://github.com/testplay-byte/ANI_KUTA_NEW/pull/1
2. **Download the APK** from the CI run artifacts (or trigger a new build).
3. **Install + test the golden paths** above.
4. **Decide on the extension-only detail page** (D7/D8) — build it as a follow-up, or
   remove the "go without linking" button for now.
5. **Merge to main** only after review — the agent did NOT merge (per instructions).

---

## Key files for the next agent

- **Entry point:** `app/.../MainActivity.kt` (search route + linking sheet overlay)
- **ViewModel:** `feature/search/.../viewmodel/SearchViewModel.kt`
- **Linking flow:** `feature/search/.../viewmodel/ExtensionLinkingViewModel.kt` +
  `feature/search/.../ui/ExtensionLinkingSheet.kt`
- **Link cache:** `data/extension/.../cache/ExtensionLinkStore.kt`
- **AniList filtered search:** `core/anilist/.../api/AniListApi.kt::searchAnimeWithFilters`
- **Prototype reference (copy-paste source):** `PROTOTYPE_REFERENCE/Anime_App/.../SearchScreen.kt`
  + `FilterSheet.kt`

## Design-decision notes

- **Q2 (one extension at a time)** — implemented via `selectedExtensionSourceId` in
  SearchViewModel + a DropdownMenu picker shown on Extension toggle re-tap.
- **Q5 (ExtensionLinkStore placement)** — in `:data:extension` (shared with future
  ext-only detail page), NOT in `:feature:search` (per ai-agent-rules.md §4).
- **Q6 (results grid)** — kept the prototype's `chunked(3)` Column approach (visual
  fidelity over `LazyVerticalGrid` perf).

— end of handoff —

# Prompt 2 — Search Page: Full Implementation

You are the ANIKUTA search page implementation agent. You have completed Prompt 1 (setup + analysis). Now implement the full search page.

**This is a large task.** Follow the to-do list strictly. Build via GitHub Actions after each major milestone.

---

## 0. GitHub Access + Branch Workflow

```
Repo: https://github.com/testplay-byte/ANI_KUTA_NEW
```

The GitHub PAT will be provided to you separately by the user. Do NOT hardcode it in any file.

**CRITICAL — Branch workflow:**
1. Create a new branch: `git checkout -b feature/search-page`
2. Do ALL your work on this branch — do NOT push to `main` directly
3. Push the branch: `git push -u origin feature/search-page`
4. **DO NOT merge to `main`** unless the user explicitly tells you to. The user will review the work and decide when to merge.
5. CI builds on your branch will work — the workflow triggers on `pull_request` to main and `workflow_dispatch`.

**ntfy notifications**: Send to `https://ntfy.sh/TASKISDONE` after each milestone.

---

## 1. Architecture Overview

```
┌──────────────────────────────────────────────────────────────────┐
│ :feature:search (NEW — the search screen + components)           │
│  • SearchScreen.kt — main screen with collapsing topbar          │
│  • SearchViewModel.kt — state holder                             │
│  • components/ — SearchTopBar, SearchBar, SourceToggle,          │
│    ResultsGrid, ResultsCard, RecentSearchesCard,                 │
│    ExtensionResultsView (popular + latest rows)                  │
│  • FilterSheet.kt — ported from prototype                        │
│  • ExtensionLinkingSheet.kt — extension→AniList linking flow     │
│  • ExtensionLinkStore.kt — persists sourceId+url → anilistId     │
├──────────────────────────────────────────────────────────────────┤
│ :core:anilist (existing — extend with filtered search)           │
│  • AniListApi.kt — add searchAnimeWithFilters(query, genres,     │
│    year, season, format, status, sort)                           │
├──────────────────────────────────────────────────────────────────┤
│ :data:extension (existing — use SourceMatcher for ext search)    │
│  • SourceMatcher.searchOneSource() — already exists              │
│  • AnimeExtensionManager.getInstalledExtensions() — already exists│
├──────────────────────────────────────────────────────────────────┤
│ :app (wiring)                                                    │
│  • MainActivity.kt — wire SearchScreen to a nav route/tab        │
└──────────────────────────────────────────────────────────────────┘
```

---

## 2. Dual-Source Search (CRITICAL — understand this fully)

The search page has TWO search sources, toggled at the top:

### 2.1 AniList Search (default)
- When "AniList" is selected: searches via `AniListApi.searchAnime(query)` (existing method)
- Also supports filtered search: `searchAnimeWithFilters(query, genres, year, season, format, status, sort)` (NEW method you'll add to AniListApi)
- Results are `AniListAnime` objects — tapping opens the existing detail page directly
- When no query: shows "Popular anime" (via `AniListApi.fetchPopular()`)
- Sort options: Popularity, Score, Newest, Title A-Z, Trending, Favourites

### 2.2 Extension Search
- When "Extension" is selected: the default view (no query) shows TWO sections:
  1. **Popular** — `source.getPopularAnime(page=1)` from each trusted extension
  2. **Latest** — `source.getLatestUpdates(page=1)` from each trusted extension
  - Both are horizontal scroll rows, each in a dedicated background card
  - If multiple extensions are trusted, show rows per extension (or merge — your call, but merge is simpler)
- When the user types a query: searches via `SourceMatcher.searchOneSource(sourceId, query)` for each trusted extension
  - Results are `SAnime` objects from the extension — these are NOT AniList anime
  - Results show: cover thumbnail, title, source name
  - Tapping an extension result triggers the **Extension-to-AniList Linking Flow** (see §3)

### 2.3 Extension Popular/Latest Implementation

For the extension default view (no query):
```kotlin
// For each trusted source:
val popularPage = withContext(Dispatchers.IO) { source.getPopularAnime(1) }
val latestPage = withContext(Dispatchers.IO) { source.getLatestUpdates(1) }
// popularPage.animes = List<SAnime>
// latestPage.animes = List<SAnime>
```

Display:
- "Popular" section: horizontal LazyRow of SAnime cards (cover + title)
- "Latest" section: horizontal LazyRow of SAnime cards (cover + title)
- Each section in a Surface card with RoundedCornerShape(20dp), surfaceVariant background
- Tapping any card triggers the linking flow (§3)

---

## 3. Extension-to-AniList Linking Flow (CRITICAL — complex)

When the user taps an extension anime result, two things happen:

### 3.1 Auto-Link (happy path)
1. Take the SAnime title from the extension result
2. Search AniList: `AniListApi.searchAnime(sAnime.title)`
3. If results found → auto-select the first result (exact title match preferred)
4. Link: store `sourceId + animeUrl → anilistId` in `ExtensionLinkStore`
5. Open the detail page for the linked AniList anime
6. Show a brief "Linked to AniList" toast

### 3.2 Not Found (manual link path)
If AniList search returns no results (or no good match):
1. Show an `ExtensionLinkingSheet` (bottom-up, `dragHandle = null`) with:
   - The extension anime's cover + title + source name
   - "This anime was not found on AniList" message
   - A search field pre-filled with the anime title
   - AniList search results (if any) — user can tap to manually link
   - "Go without linking" button — opens a minimal detail page using only extension data
2. If the user picks an AniList result → link it → open detail page
3. If the user taps "Go without linking" → open a detail page that works with extension-only data

### 3.3 ExtensionLinkStore
Persist the link so next time the user opens the same extension anime, it goes straight to the AniList detail page:
```kotlin
class ExtensionLinkStore(context: Context) {
    // Key: "$sourceId:$animeUrl" → Value: anilistId
    fun getAniListId(sourceId: Long, animeUrl: String): Int?
    fun link(sourceId: Long, animeUrl: String, anilistId: Int)
    fun unlink(sourceId: Long, animeUrl: String)
}
```
Use SharedPreferences with JSON serialization (like the old project's `ExtensionLinkStore.kt`).

### 3.4 Extension-Only Detail Page (for "Go without linking")
If the user goes without linking, open a detail page that uses ONLY the extension's `SAnime` data:
- Cover image (`SAnime.thumbnail_url`)
- Title (`SAnime.title`)
- Description (`SAnime.description` — may be null)
- Episode list (`source.getEpisodeList(sAnime)`)
- This works exactly like the normal detail page but without AniList metadata (no score, no genres, no AniList ID)
- The user can still save it to the library, watch episodes, etc.

---

## 4. Search Page UI (copy-paste from prototype — adapt to ANIKUTA)

### 4.1 Collapsing Top Bar (EXACT animation from prototype)

```
Expanded state:
┌──────────────────────────────────────────────────────────┐
│  Search                              [AniList | Extension]│  ← title 36sp + toggle
│  ┌──────────────────────────────────────────────────────┐│
│  │ 🔍  Search anime...                            ✕     ││  ← search bar 52dp
│  └──────────────────────────────────────────────────────┘│
├──────────────────────────────────────────────────────────┤
│  [Filters (2)]                            [Popularity ▼] │  ← quick row
└──────────────────────────────────────────────────────────┘

Collapsed state (scrolled down):
┌──────────────────────────────────────────────────────────┐
│  Search    ┌──────────────────────────────────────────┐  │  ← title 26sp + compact search
│            │ 🔍  current query                   ✕     │  │
│            └──────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────┘
(quick row hidden — fadeOut + shrinkVertically)
```

Animations (copy from prototype EXACTLY):
- `titleFontSize`: 36f → 26f, `tween(300, FastOutSlowInEasing)`
- `sourceAlpha`: 1f → 0f, `tween(300, FastOutSlowInEasing)`
- `sourceWidth`: 180dp → 0dp, `tween(300, FastOutSlowInEasing)`
- Search bar: `AnimatedVisibility` with `fadeIn + expandVertically` / `fadeOut + shrinkVertically`
- Quick row: `AnimatedVisibility` with `fadeOut + shrinkVertically`
- `collapsed = scrollState.value > 20`

### 4.2 Source Toggle
- Pill-shaped (`RoundedCornerShape(50)`), `surfaceVariant.copy(alpha=0.3f)` background, 3dp padding
- Two buttons: "AniList" (Search icon) and "Extension" (Extension icon)
- Active: `primaryContainer` bg + `onPrimaryContainer` text
- Inactive: transparent bg + `onSurfaceVariant` text

### 4.3 Search Bar
- Two sizes: full (52dp) and compact (44dp)
- `RoundedCornerShape(50)`, `surfaceVariant.copy(alpha=0.4f)` background
- Search icon (left) + BasicTextField + clear button (right, only when text exists)
- `KeyboardOptions(imeAction = ImeAction.Search)`
- 500ms debounce on typing

### 4.4 Filter Sheet (port from prototype EXACTLY)

Port `PROTOTYPE_REFERENCE/Anime_App/.../FilterSheet.kt` (964 lines). Two view modes:
- **Accordion** (default): 5 expandable sections (Genres, Release, Type, Score, Sort), one open at a time
- **Flat**: tab row + content panel

Sections:
1. **Genres** — multi-select FlowRow of 16 genre chips
2. **Release** — Year cycle pill + Season cycle pill
3. **Type** — Format cycle pill + Status cycle pill
4. **Score** — Slider (0-100, step 5) + "Any" / "X.X+" label
5. **Sort** — single-select chip row of 6 sort options

Bottom actions: "Clear all" (outlined, left) + "Apply filters" (filled, right).
**`dragHandle = null`** (design principle #2).

### 4.5 Sort Dropdown
- Pill-shaped button showing current sort label
- DropdownMenu with 5-6 sort options
- Active sort has checkmark + ExtraBold

### 4.6 Recent Searches Card
- Collapsible (chevron down to collapse, "Show" button to expand)
- Each item: clock icon + text + individual delete (X) button
- "Show N more" / "Show less" if >3 items
- "Clear all" link
- In a `surfaceVariant.copy(alpha=0.3f)` card with `RoundedCornerShape(20dp)`
- Persisted in SharedPreferences (survives app restart)
- Only shown when: query is blank AND no filters active AND recents exist

### 4.7 Results Grid
- 3-column chunked rows (same as prototype)
- In a `surfaceVariant.copy(alpha=0.3f)` card with `RoundedCornerShape(20dp)`
- Section header: label + count ("Results for 'query'" / "Popular anime" / "Trending · Extension")
- Loading state: "Loading…"
- Error state: error message in `error` color
- Empty state: "No results found for 'query'"

### 4.8 Extension Results (Popular + Latest rows)
When source = Extension AND query is blank:
- **Popular row**: horizontal LazyRow of SAnime cards, in a dedicated card
- **Latest row**: horizontal LazyRow of SAnime cards, in a dedicated card
- Each card: cover (2:3 aspect) + title (1 line) + source name
- Tapping → linking flow (§3)

---

## 5. AniList API Extension

Add to `AniListApi.kt`:
```kotlin
suspend fun searchAnimeWithFilters(
    query: String?,
    page: Int = 1,
    perPage: Int = 20,
    genres: Set<String> = emptySet(),
    year: Int? = null,
    season: String? = null,
    format: String? = null,
    status: String? = null,
    sort: String = "POPULARITY_DESC",
): List<AniListAnime>
```

This requires a new GraphQL query that includes the filter parameters. Use AniList's `Page(media: ...)` query with the filter fields. See the prototype's `AniListClient.kt` for reference on how to construct the GraphQL query.

---

## 6. Design Language Rules (DO NOT BREAK)

1. **#B1F256** lime green theme — primary color
2. **RobotoFamily ExtraBold** (800) for all headings
3. **NO drag handle** on ANY `ModalBottomSheet` — always `dragHandle = null`
4. **Edge-to-edge** — `statusBarsPadding()` on the top bar
5. **RoundedCornerShape(50)** for pills, **20dp** for cards, **12dp** for small cards
6. **FastOutSlowInEasing** for all animations (300ms duration)
7. **No indigo or blue colors**
8. Copy the prototype's UI EXACTLY — same spacing, same colors (adapted to ANIKUTA's #B1F256), same animations

---

## 7. To-Do List (70+ tasks)

### Phase A: Data Layer — 10 tasks

- [ ] **A1**: Create `feature/search/build.gradle.kts` with deps on core:anilist, data:extension, core:designsystem, core:sourceApi
- [ ] **A2**: Add `searchAnimeWithFilters()` to `AniListApi.kt` (new GraphQL query with filter params)
- [ ] **A3**: Create `ExtensionLinkStore.kt` in `:feature:search` — SharedPreferences-backed, JSON-serialized map of `"$sourceId:$animeUrl" → anilistId`
- [ ] **A4**: Create `RecentSearchesStore.kt` — SharedPreferences-backed list of recent query strings (max 12)
- [ ] **A5**: Register stores in Koin
- [ ] **A6**: **BUILD CHECK** — commit + push to feature branch + verify CI compiles
- [ ] **A7**: Send ntfy: "Phase A (data layer) complete"

### Phase B: Search UI Core — 20 tasks

- [ ] **B1**: Create `SearchViewModel.kt` — StateFlows: query, source, results, loading, error, hasSearched, sort, filters, recents, collapsed
- [ ] **B2**: Create `SearchScreen.kt` — main screen layout (collapsing topbar + quick row + scrollable content)
- [ ] **B3**: Port `SearchTopBar` from prototype — collapsing title + source toggle + search bar, ALL animations exact
- [ ] **B4**: Port `SearchBar` from prototype — two sizes (full 52dp, compact 44dp), RoundedCornerShape(50), debounce
- [ ] **B5**: Port `SourceToggle` from prototype — AniList/Extension pill toggle
- [ ] **B6**: Port the quick row (Filters button + Sort dropdown) with slide-out animation
- [ ] **B7**: Create `ResultsCard.kt` — surfaceVariant card with section header + 3-column grid
- [ ] **B8**: Create `AnimeCard.kt` — cover (2:3) + title + score badge, tap → onOpenAnime
- [ ] **B9**: Create `RecentSearchesCard.kt` — collapsible, individual delete, show more/less, clear all
- [ ] **B10**: Port `FilterSheet.kt` from prototype — accordion + flat modes, 5 sections, `dragHandle = null`
- [ ] **B11**: Wire AniList search — debounced `AniListApi.searchAnime(query)` or `searchAnimeWithFilters()`
- [ ] **B12**: Wire sort dropdown → re-fetch with new sort
- [ ] **B13**: Wire filter sheet → re-fetch with filters
- [ ] **B14**: Wire recent searches — add on search, pick to re-search, delete individual, clear all
- [ ] **B15**: Verify all animations match the prototype EXACTLY (title shrink, toggle fade, search bar move, quick row slide)
- [ ] **B16**: Verify all ModalBottomSheets have `dragHandle = null`
- [ ] **B17**: Verify all text uses RobotoFamily ExtraBold for headings
- [ ] **B18**: **BUILD CHECK** — commit + push + verify CI
- [ ] **B19**: Send ntfy: "Phase B (search UI core) complete"

### Phase C: Extension Search — 15 tasks

- [ ] **C1**: Create `ExtensionResultsView.kt` — shows Popular + Latest rows when source=Extension and query is blank
- [ ] **C2**: Wire `source.getPopularAnime(1)` and `source.getLatestUpdates(1)` for each trusted source (on Dispatchers.IO)
- [ ] **C3**: Create extension result cards (SAnime cover + title + source name)
- [ ] **C4**: Wire extension search: `SourceMatcher.searchOneSource(sourceId, query)` when source=Extension and query is not blank
- [ ] **C5**: Show extension search results in the same ResultsCard layout (3-column grid)
- [ ] **C6**: Tapping extension result → start linking flow (§3)
- [ ] **C7**: Handle multiple trusted sources — merge results or show per-source sections
- [ ] **C8**: Verify all source calls are on `Dispatchers.IO` (NetworkOnMainThreadException guard)
- [ ] **C9**: **BUILD CHECK** — commit + push + verify CI
- [ ] **C10**: Send ntfy: "Phase C (extension search) complete"

### Phase D: Extension-to-AniList Linking — 15 tasks

- [ ] **D1**: Create `ExtensionLinkingSheet.kt` — bottom-up sheet (dragHandle=null) with:
  - Extension anime cover + title + source name
  - "Not found on AniList" message (when auto-search fails)
  - Search field pre-filled with anime title
  - AniList results list (user can tap to manually link)
  - "Go without linking" button
- [ ] **D2**: Implement auto-link flow: search AniList by SAnime.title → if found, link + open detail
- [ ] **D3**: Implement manual-link flow: user picks from AniList results → link + open detail
- [ ] **D4**: Implement "go without linking" flow: open extension-only detail page
- [ ] **D5**: Persist links in `ExtensionLinkStore` — next time, skip linking and go straight to detail
- [ ] **D6**: Show "Linked to AniList" toast on successful link
- [ ] **D7**: Create extension-only detail page (minimal — uses SAnime data, no AniList ID)
- [ ] **D8**: Wire extension-only detail page to the episode list + video resolver (same as normal detail)
- [ ] **D9**: **BUILD CHECK** — commit + push + verify CI
- [ ] **D10**: Send ntfy: "Phase D (extension linking) complete"

### Phase E: Navigation + Polish — 10 tasks

- [ ] **E1**: Wire `SearchScreen` into `MainActivity.kt` — add as a nav destination (accessible from a search icon in the top bar or bottom nav)
- [ ] **E2**: Wire AniList result tap → `onOpenAnime(anilistId)` → existing detail page
- [ ] **E3**: Add bottom padding for floating nav (110dp)
- [ ] **E4**: Verify edge-to-edge — `statusBarsPadding()` on the top bar
- [ ] **E5**: Add error handling — network errors, empty results, extension errors
- [ ] **E6**: Add loading states — shimmer/skeleton for results while loading
- [ ] **E7**: **BUILD CHECK** — commit + push + verify CI
- [ ] **E8**: Send ntfy: "Phase E (navigation + polish) complete"

### Phase F: Final Verification — 8 tasks

- [ ] **F1**: Verify the APK builds successfully on the feature branch
- [ ] **F2**: Download the APK artifact — verify it's valid
- [ ] **F3**: Verify no nested LazyColumn (use LazyVerticalGrid or chunked Column)
- [ ] **F4**: Verify all ModalBottomSheets have `dragHandle = null`
- [ ] **F5**: Verify the collapsing animation matches the prototype EXACTLY
- [ ] **F6**: Verify the filter sheet works (accordion + flat modes)
- [ ] **F7**: Verify recent searches persist across app restart
- [ ] **F8**: Send final ntfy with APK download URL + "DO NOT merge to main — awaiting user review"

---

## 8. Rules Summary (DO NOT BREAK)

1. **NEVER push to `main`** — work on `feature/search-page` branch only
2. **NEVER merge to `main`** unless the user explicitly tells you to
3. **NEVER nest a LazyColumn inside another LazyColumn**
4. **NEVER create a ModalBottomSheet without `dragHandle = null`**
5. **NEVER call source.getPopularAnime/getLatestUpdates/getSearchAnime on the main thread** — wrap in `withContext(Dispatchers.IO)`
6. **ALWAYS copy the prototype's animations EXACTLY** — same easing, same duration, same transitions
7. **ALWAYS use RobotoFamily ExtraBold** for headings
8. **ALWAYS use #B1F256** as the primary color
9. **ALWAYS send ntfy notifications** after each milestone
10. **ALWAYS verify the APK** after each build

---

## 9. After Completion

1. Do NOT merge to main — leave the PR open for the user to review
2. Update `RULES/sessions/` with a session handoff
3. Send a final ntfy notification with the APK download URL
4. Report: what was built, APK link, what to test, and "DO NOT merge — awaiting user review"

**Good luck. Take your time. Verify everything. Don't rush.**

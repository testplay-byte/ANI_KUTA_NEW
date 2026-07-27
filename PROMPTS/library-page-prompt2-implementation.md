# Prompt 2 — Library Page: Full Implementation

You are the ANIKUTA library page implementation agent. You have completed Prompt 1 (setup + analysis). Now implement the full library page.

**This is a large task.** Follow the to-do list at the end strictly. Build via GitHub Actions after each major milestone. Do NOT skip verification steps.

---

## 0. GitHub Access + Branch Workflow

```
Repo: https://github.com/testplay-byte/ANI_KUTA_NEW
```

The GitHub PAT will be provided to you separately by the user. Do NOT hardcode it in any file.

**CRITICAL — Branch workflow:**
1. Create a new branch: `git checkout -b feature/library-page`
2. Do ALL your work on this branch — do NOT push to `main` directly
3. Push the branch: `git push -u origin feature/library-page`
4. The CI workflow triggers on `pull_request` to main — but you should also push to your branch to trigger CI (the workflow also has `workflow_dispatch` so you can trigger it manually)
5. After the build succeeds and you've verified everything, create a Pull Request to merge `feature/library-page` into `main`
6. Only merge to `main` after the PR build passes

**CI workflow**: `.github/workflows/ci.yml` triggers on push to `main` and pull requests to `main`. It builds the debug APK and uploads it as an artifact. Monitor builds via:
```bash
curl -s -H "Authorization: token <PAT>" "https://api.github.com/repos/testplay-byte/ANI_KUTA_NEW/actions/runs?per_page=1"
```

**CRITICAL — CI concurrency**: The workflow has `cancel-in-progress: true` on `main`. Pushes to your feature branch will NOT cancel each other (different ref). But do NOT push to `main` while a build is running.

**ntfy notifications**: Send to `https://ntfy.sh/TASKISDONE` after each major milestone and after the final APK builds.

---

## 1. Architecture Overview

You are building the library feature across these modules:

```
┌──────────────────────────────────────────────────────────────────┐
│ :feature:library (the library screen + components)               │
│  • LibraryScreen.kt — main screen with tabs/grid/list            │
│  • LibraryViewModel.kt — state holder                            │
│  • components/ — LibraryGrid, LibraryList, LibraryCard,          │
│    CategoryTabs, ContinueWatchingSection, SelectionActionBar,    │
│    CustomizeSheet, CategorySheet, AddCategorySheet               │
│  • CategorySuggester.kt — keyword suggestion logic               │
├──────────────────────────────────────────────────────────────────┤
│ :core:database (SQLDelight — already has categories table)       │
│  • categories.sq — already exists, verify schema                │
│  • anime_category.sq — already exists, verify schema            │
├──────────────────────────────────────────────────────────────────┤
│ :data:anime (repositories — extend with library/category repos)  │
│  • LibraryRepository.kt — NEW: save/remove anime, get library    │
│  • CategoryRepository.kt — NEW: CRUD for categories              │
├──────────────────────────────────────────────────────────────────┤
│ :app (wiring — connect library to navigation + detail page)      │
│  • MainActivity.kt — wire LibraryScreen to the Library tab       │
│  • AnimeDetailScreen — wire save button to LibraryRepository     │
└──────────────────────────────────────────────────────────────────┘
```

### Modular design rules:

1. **`:feature:library`** owns the UI. It depends on `:core:database`, `:data:anime`, `:core:anilist`, `:core:designsystem`, `:core:player` (for WatchProgressStore).

2. **LibraryRepository** is the data layer. It persists saved anime (AniList ID + cached metadata) to SQLDelight. It also reads from WatchProgressStore for the continue-watching section.

3. **CategoryRepository** handles CRUD for categories. The "Default" category is created automatically on first launch and has a fixed ID (order=0).

4. **CategorySuggester** is a standalone utility object that takes the current text + cursor position and returns a suggestion (or null). It's pure Kotlin, no Compose dependency, so it can be unit-tested independently.

5. All bottom-up sheets (`ModalBottomSheet`) MUST set `dragHandle = null` (design principle #2).

---

## 2. Database Schema

Read the existing SQLDelight schema:
- `ANIKUTA_PROJECT/ANIKUTA/core/database/src/main/sqldelight/app/confused/anikuta/core/database/categories.sq`
- `ANIKUTA_PROJECT/ANIKUTA/core/database/src/main/sqldelight/app/confused/anikuta/core/database/anime_category.sq`
- `ANIKUTA_PROJECT/ANIKUTA/core/database/src/main/sqldelight/app/confused/anikuta/core/database/animes.sq`

If the schema needs modification (e.g., adding `status` column for watching/completed/paused/dropped/planning), modify the `.sq` files and run `./gradlew :core:database:generateAnikutaDebugInterface` (or the project's SQLDelight task).

**Category table should have:**
- `_id` (INTEGER PRIMARY KEY)
- `name` (TEXT NOT NULL)
- `order_index` (INTEGER NOT NULL DEFAULT 0) — for display order
- `flags` (INTEGER NOT NULL DEFAULT 0) — for sort/filter/display mode per category

**Anime-Category junction table:**
- `anime_id` (INTEGER)
- `category_id` (INTEGER)
- PRIMARY KEY (anime_id, category_id)

**Animes table should have (add if missing):**
- `_id`, `anilist_id`, `title`, `cover_url`, `cover_color`, `score`, `status` (watching/completed/paused/dropped/planning), `total_episodes`, `date_added`, `last_watched`

---

## 3. Category System (DETAILED — read carefully)

### 3.1 Default Category

- Created automatically on first launch (id=1, name="Default", order_index=0)
- If the user has NOT created any custom categories → Default is always shown
- If the user HAS created custom categories but Default has no anime → Default is HIDDEN (only categories with anime are shown)
- If the user HAS created custom categories AND Default has anime → Default is shown alongside custom categories

### 3.2 Category Suggestion System (CRITICAL — implement exactly as specified)

When the user is typing a new category name, the system suggests names based on 5 keywords:
- `watching`
- `completed`
- `paused`
- `dropped`
- `planning`

**Rules:**
1. Suggestions appear only after the user has typed **3 or more characters**
2. The suggestion matches if ANY 3-letter substring of the typed text matches ANY 3-letter prefix of ANY of the 5 keywords (case-insensitive)
3. The suggestion's case matches the user's typing:
   - All lowercase → suggestion is all lowercase (e.g., "watching")
   - First letter capital, rest lowercase → suggestion is capitalized (e.g., "Watching")
   - All caps → suggestion is all caps (e.g., "WATCHING")
   - Mixed case → suggestion matches the user's exact casing pattern
4. The suggestion appears as a tappable bubble (chip) below or next to the text field
5. Tapping the bubble auto-completes the text field with the suggested name

**Implementation:**
```kotlin
object CategorySuggester {
    private val KEYWORDS = listOf("watching", "completed", "paused", "dropped", "planning")

    fun suggest(typed: String): String? {
        if (typed.length < 3) return null
        val lowerTyped = typed.lowercase()
        // Check if any 3-char substring of typed matches any keyword's prefix
        for (keyword in KEYWORDS) {
            for (i in 0..lowerTyped.length - 3) {
                val substring = lowerTyped.substring(i, i + 3)
                if (keyword.startsWith(substring)) {
                    // Match found — apply the user's casing to the keyword
                    return applyCasing(keyword, typed)
                }
            }
        }
        return null
    }

    private fun applyCasing(keyword: String, typed: String): String {
        return when {
            typed.all { it.isLowerCase() } -> keyword.lowercase()
            typed.all { it.isUpperCase() } -> keyword.uppercase()
            typed[0].isUpperCase() && typed.drop(1).all { it.isLowerCase() } ->
                keyword.replaceFirstChar { it.uppercase() }
            else -> keyword // fallback: match the typed pattern as closely as possible
        }
    }
}
```

### 3.3 Save Flow

**From the anime detail page:**
- **Short press** the save/bookmark button → saves the anime to the **Default** category (if not already saved). If already saved, removes it (toggle).
- **Long press** the save/bookmark button → opens a **Save to Category** bottom-up sheet showing:
  - List of all categories (with checkmarks on categories the anime is already in)
  - "Add new category" option at the bottom (opens a text field + the suggestion system)
  - User can select multiple categories (or deselect)
  - Tapping "Done" saves the anime to the selected categories

---

## 4. Library Screen UI

### 4.1 Layout

```
┌──────────────────────────────────────────────────────────┐
│  Library                              [⚙ customize]      │  ← collapsing header
├──────────────────────────────────────────────────────────┤
│  [All] [Watching] [Completed] [Paused] [Dropped] [Plan]  │  ← category tabs (scrollable)
│  ────────────────                                        │
├──────────────────────────────────────────────────────────┤
│  Continue Watching                                       │  ← section (only if progress exists)
│  ┌──────┐  ┌──────┐  ┌──────┐                          │
│  │cover │  │cover │  │cover │                           │  ← horizontal scroll
│  │ ████ │  │ ████ │  │ ██   │                           │     (progress bar overlay)
│  │title │  │title │  │title │                           │
│  │Ep 3  │  │Ep 5  │  │Ep 1  │                           │
│  └──────┘  └──────┘  └──────┘                          │
├──────────────────────────────────────────────────────────┤
│  [grid icon] [list icon]    [sort] [filter]    🔍       │  ← toolbar row
├──────────────────────────────────────────────────────────┤
│  ┌──────┐  ┌──────┐  ┌──────┐                          │
│  │cover │  │cover │  │cover │                           │  ← grid view (3 columns)
│  │title │  │title │  │title │                           │
│  │ 12ep │  │ 24ep │  │ 0ep  │                           │
│  └──────┘  └──────┘  └──────┘                          │
│  ┌──────┐  ┌──────┐  ┌──────┐                          │
│  │cover │  │cover │  │cover │                           │
│  │title │  │title │  │title │                           │
│  └──────┘  └──────┘  └──────┘                          │
└──────────────────────────────────────────────────────────┘
```

### 4.2 Grid View

- Configurable columns (2-5, default 3)
- Each card: cover image (aspect ratio 2:3), title (1-2 lines, ExtraBold), episode count badge, status badge (if not default), progress bar (if watch progress exists)
- Long-press → selection mode (checkmark overlay)
- Tap → open anime detail page
- Card has rounded corners (12dp), tonal elevation, subtle shadow

### 4.3 List View

- Each row: small cover (52×74dp), title (ExtraBold), metadata (status, episode count, last watched), progress bar
- Same tap/long-press behavior as grid

### 4.4 Category Tabs

- Horizontal scrollable row of FilterChips
- "All" is always first (shows all anime regardless of category)
- Then each category name (Default, Watching, Completed, etc.)
- Active tab is highlighted with primary color
- Tabs scroll horizontally if they overflow

### 4.5 Continue Watching Section

- Only shown if WatchProgressStore has entries
- Horizontal scroll of cards, each showing:
  - Cover image
  - Progress bar overlay (position / duration)
  - Anime title
  - "Episode N" text
  - Tap → opens the watch page directly for that anime/episode

### 4.6 Selection Mode

- Long-press any card to enter selection mode
- Selected cards show a checkmark overlay
- Floating action bar appears at the bottom (above the floating nav):
  - Cancel (X icon) — exits selection mode
  - Category (folder icon) — opens the "Move to Category" sheet
  - Delete (trash icon) — opens a confirm-delete dialog
- In selection mode, the category tabs are replaced with a "Select All / Clear" bar

### 4.7 Customize Sheet (gear button)

- ModalBottomSheet with `dragHandle = null`
- Sections:
  - **Layout**: Grid / List (2-way toggle)
  - **Columns**: 2 / 3 / 4 / 5 (only for grid mode)
  - **Show badges**: Episode count, Status, Unread (switches)
  - **Sort**: Title A-Z, Title Z-A, Date added (newest), Date added (oldest), Last watched, Progress
  - **Cover style**: Rounded / Square (for grid cards)
- Changes apply immediately (reactive state)

### 4.8 Sort + Filter

- Sort button opens a dropdown or bottom-up sheet with sort options
- Filter button opens a bottom-up sheet with:
  - Status filter (watching, completed, paused, dropped, planning) — multi-select chips
  - Source filter (which extension source) — multi-select
  - Category filter (which category) — multi-select

### 4.9 Search Within Library

- Search icon in the toolbar
- Tapping it expands a search field
- Filters the library by anime title (case-insensitive, substring match)
- Real-time filtering as the user types

### 4.10 Empty State

- When the library is empty: large icon + "Your library is empty" + "Browse anime and add them to your library." description
- When a category/filter has no anime: "No anime in this category" + "Try a different category or add anime."

---

## 5. Wiring

### 5.1 Navigation (MainActivity.kt)

- The Library tab in the bottom nav already exists — wire it to `LibraryScreen`
- Add `LibraryScreen` to the `when` block in `AnikutaApp()`
- Tapping a library card calls `onOpenAnime(anilistId)` → opens the detail page (already exists)

### 5.2 Detail Page Save Button

- The detail page already has a save/bookmark button (the `saved` state in `DetailContent.kt`)
- Wire it to `LibraryRepository`:
  - Short press → toggle save to Default category
  - Long press → open Save to Category sheet
- The save button state should reflect whether the anime is in the library (check LibraryRepository)

### 5.3 Koin DI

- Register `LibraryRepository` and `CategoryRepository` in the app's Koin module
- Inject them into `LibraryViewModel` and `AnimeDetailViewModel`

---

## 6. Design Language Rules (DO NOT BREAK)

1. **#B1F256** lime green theme — primary color
2. **RobotoFamily ExtraBold** (800) for all headings
3. **NO drag handle** on ANY `ModalBottomSheet` — always `dragHandle = null`
4. **Edge-to-edge** — status bar area is part of the app canvas
5. **Floating bottom nav** — the library content has bottom padding so it doesn't go under the nav
6. **Rounded corners** — cards use 12dp, sheets use 24dp top corners
7. **Alternating backgrounds** — list items can have zebra-stripe backgrounds
8. **Accent-colored section headers** — left-aligned, ExtraBold, primary color
9. **No indigo or blue colors** unless explicitly requested

---

## 7. To-Do List (80+ tasks)

### Phase A: Data Layer — 15 tasks

- [ ] **A1**: Read the existing SQLDelight schema (categories.sq, anime_category.sq, animes.sq). Verify what columns exist.
- [ ] **A2**: Modify the schema if needed — add `status`, `date_added`, `last_watched` columns to animes table. Add `flags` to categories table if missing.
- [ ] **A3**: Run `./gradlew :core:database:generateAnikutaDebugInterface` to regenerate SQLDelight interfaces (or let the build do it)
- [ ] **A4**: Create `LibraryRepository.kt` in `:data:anime` — methods: `saveAnime(anime, categoryId)`, `removeAnime(anilistId)`, `getLibrary()`, `getLibraryByCategory(categoryId)`, `isInLibrary(anilistId)`, `getContinueWatching()`, `searchLibrary(query)`, `updateStatus(anilistId, status)`, `updateLastWatched(anilistId)`
- [ ] **A5**: Create `CategoryRepository.kt` in `:data:anime` — methods: `getCategories()`, `createCategory(name)`, `renameCategory(id, name)`, `deleteCategory(id)`, `reorderCategory(id, newIndex)`, `getAnimeCategories(anilistId)`, `setAnimeCategories(anilistId, categoryIds)`, `ensureDefaultCategoryExists()`
- [ ] **A6**: Register `LibraryRepository` + `CategoryRepository` in Koin (RepositoryModule.kt or a new LibraryModule.kt)
- [ ] **A7**: Create `CategorySuggester.kt` — the keyword suggestion logic (see §3.2). Pure Kotlin, no Compose.
- [ ] **A8**: Verify the Default category is created on first launch (call `ensureDefaultCategoryExists()` from App.kt or the repository init)
- [ ] **A9**: **BUILD CHECK** — commit + push to feature branch + verify CI compiles
- [ ] **A10**: Send ntfy notification: "Phase A (data layer) complete"

### Phase B: Library UI Core — 20 tasks

- [ ] **B1**: Create `feature/library/build.gradle.kts` with dependencies on core:database, data:anime, core:anilist, core:designsystem, core:player
- [ ] **B2**: Create `LibraryViewModel.kt` — StateFlows for: libraryItems, categories, activeCategory, displayMode (grid/list), sortMode, filterMode, searchQuery, selectionMode, selectedIds, continueWatching
- [ ] **B3**: Create `LibraryScreen.kt` — main screen with collapsing header + category tabs + content area
- [ ] **B4**: Create `CategoryTabs.kt` — horizontal scrollable FilterChips (All + categories)
- [ ] **B5**: Create `LibraryGrid.kt` — grid view with configurable columns, LazyVerticalGrid
- [ ] **B6**: Create `LibraryList.kt` — list view with covers + metadata
- [ ] **B7**: Create `LibraryCard.kt` — grid card (cover, title, badges, progress bar, long-press)
- [ ] **B8**: Create `LibraryRow.kt` — list row (small cover, title, metadata, progress bar, long-press)
- [ ] **B9**: Create `ContinueWatchingSection.kt` — horizontal scroll of cards with progress
- [ ] **B10**: Create `EmptyState.kt` — icon + title + description for empty library
- [ ] **B11**: Wire tap → `onOpenAnime(anilistId)`, long-press → selection mode
- [ ] **B12**: Verify all text uses RobotoFamily ExtraBold for headings
- [ ] **B13**: Verify all cards use #B1F256 theme colors
- [ ] **B14**: Verify no nested LazyColumn (grid uses LazyVerticalGrid, not LazyColumn in LazyColumn)
- [ ] **B15**: **BUILD CHECK** — commit + push + verify CI
- [ ] **B16**: Send ntfy notification: "Phase B (library UI core) complete"

### Phase C: Categories + Save Flow — 15 tasks

- [ ] **C1**: Create `SaveToCategorySheet.kt` — bottom-up sheet (dragHandle=null) with category list + checkboxes + "Add new category" option
- [ ] **C2**: Create `AddCategorySheet.kt` — text field + CategorySuggester suggestion bubble + create button
- [ ] **C3**: Wire the suggestion bubble — tap to auto-complete, case-matching
- [ ] **C4**: Wire `SaveToCategorySheet` to `CategoryRepository.setAnimeCategories()`
- [ ] **C5**: Wire the detail page save button — short press → Default category, long press → SaveToCategorySheet
- [ ] **C6**: Create `SelectionActionBar.kt` — floating bar with Cancel/Category/Delete
- [ ] **C7**: Create `MoveToCategorySheet.kt` — same as SaveToCategorySheet but for bulk move in selection mode
- [ ] **C8**: Wire selection mode — long-press enters, tap toggles, Cancel exits
- [ ] **C9**: Wire delete — confirm dialog → `LibraryRepository.removeAnime()`
- [ ] **C10**: Verify the Default category visibility logic (§3.1)
- [ ] **C11**: **BUILD CHECK** — commit + push + verify CI
- [ ] **C12**: Send ntfy notification: "Phase C (categories + save flow) complete"

### Phase D: Sort + Filter + Search + Customize — 15 tasks

- [ ] **D1**: Create `SortSheet.kt` — bottom-up sheet with sort options
- [ ] **D2**: Create `FilterSheet.kt` — bottom-up sheet with status/source/category filters
- [ ] **D3**: Create `CustomizeSheet.kt` — bottom-up sheet with layout/columns/badges settings
- [ ] **D4**: Wire sort → `LibraryViewModel.setSortMode()`
- [ ] **D5**: Wire filter → `LibraryViewModel.setFilterMode()`
- [ ] **D6**: Wire customize → `LibraryViewModel.setDisplayMode()` + `setColumns()` + `setShowBadges()`
- [ ] **D7**: Create search bar — expandable text field in the toolbar
- [ ] **D8**: Wire search → `LibraryViewModel.setSearchQuery()` → real-time filtering
- [ ] **D9**: Persist settings (display mode, columns, sort, filter) in SharedPreferences
- [ ] **D10**: **BUILD CHECK** — commit + push + verify CI
- [ ] **D11**: Send ntfy notification: "Phase D (sort/filter/search/customize) complete"

### Phase E: Navigation Wiring + Polish — 10 tasks

- [ ] **E1**: Wire `LibraryScreen` into `MainActivity.kt` — the Library tab
- [ ] **E2**: Wire library card tap → `onOpenAnime(anilistId)` → detail page
- [ ] **E3**: Wire continue-watching card tap → watch page (construct WatchRequest or navigate to detail first)
- [ ] **E4**: Add bottom padding for floating nav (110dp)
- [ ] **E5**: Add animations — fade in/out for selection mode, slide for sheets
- [ ] **E6**: Verify edge-to-edge — status bar area is part of the app canvas
- [ ] **E7**: Verify all ModalBottomSheets have `dragHandle = null`
- [ ] **E8**: **BUILD CHECK** — commit + push + verify CI
- [ ] **E9**: Send ntfy notification: "Phase E (navigation + polish) complete"

### Phase F: Final Verification — 10 tasks

- [ ] **F1**: Create a Pull Request from `feature/library-page` to `main`
- [ ] **F2**: Verify the PR CI build passes
- [ ] **F3**: Download the APK artifact — verify it's valid
- [ ] **F4**: Verify the APK contains the LibraryScreen class
- [ ] **F5**: Verify no nested LazyColumn anywhere in the library code
- [ ] **F6**: Verify all ModalBottomSheets have `dragHandle = null`
- [ ] **F7**: Verify the CategorySuggester logic matches the spec (3-char minimum, case-matching, 5 keywords)
- [ ] **F8**: Merge the PR to `main` (only after everything passes)
- [ ] **F9**: Verify the main branch CI build passes after merge
- [ ] **F10**: Send final ntfy notification with APK download URL

---

## 8. Rules Summary (DO NOT BREAK THESE)

1. **NEVER push directly to `main`** — work on `feature/library-page` branch, create a PR to merge
2. **NEVER nest a LazyColumn inside another LazyColumn** — use `LazyVerticalGrid` or `Column + forEach`
3. **NEVER create a ModalBottomSheet without `dragHandle = null`** — design principle #2
4. **NEVER use indigo or blue colors** — #B1F256 is the primary color
5. **ALWAYS use RobotoFamily ExtraBold** for headings
6. **ALWAYS catch `Throwable`** around database calls (SQLDelight can throw RuntimeException)
7. **ALWAYS send ntfy notifications** after each milestone
8. **ALWAYS verify the APK** after each build — download + check for key classes
9. **ALWAYS follow the 12 design principles** (especially: no drag handle, edge-to-edge, #B1F256 theme, floating bottom nav)
10. **ALWAYS implement the CategorySuggester exactly as specified** — 3-char minimum, 5 keywords, case-matching, suggestion bubble

---

## 9. After Completion

After all phases are done and the PR is merged:

1. Update `RULES/sessions/` with a detailed session handoff documenting:
   - What was built (list all files created/modified)
   - Architecture decisions made
   - Any gaps remaining
   - Lessons learned

2. Send a final ntfy notification with the APK download URL.

3. Report back to the user with:
   - Summary of what was built
   - APK download link
   - What to test
   - Recommended next phase

**Good luck. Take your time. Verify everything. Don't rush.**

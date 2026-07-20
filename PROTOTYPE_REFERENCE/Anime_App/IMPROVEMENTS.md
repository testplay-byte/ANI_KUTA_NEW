# Anime App — Native Android (Improvement Tracker)

> Tracks the gap between the native Android app and the web prototype.
> Future agents should use this to know what's implemented, what needs work, and what's missing.
>
> **Last updated:** Build #14 (commit 1606ac9)

---

## ✅ Implemented & Working

### Core
- **M3 dark purple theme** — matches the prototype's color palette (PrimaryDark #d0bcff, Surface tiers, etc.)
- **Light theme** — works, toggles live (reads from DataStore settings)
- **Background color** — proper M3 dark purple (#14111F) on all pages (was gray #303030)
- **7 screens** — Home, Library, History, Schedule, Search, Settings, Detail
- **Navigation** — Navigation Compose with floating bottom nav (Box overlay, not Scaffold)
- **Bottom nav** — floating pill, SVG Material icons, content-sized active item, animated label
- **Collapsing headers** — pinned title (36sp→26sp, ExtraBold), shrinks on scroll, never scrolls away
- **Bold text** — all bold text uses FontWeight.ExtraBold (800) for visibility on Android
- **Launcher icon** — adaptive icon (purple bg + play button)

### Home
- Hero carousel (180dp height)
- Continue Watching section (from history, with progress bars)
- Popular This Season grid (3-column)
- Top Rated grid (3-column)

### Search
- Collapsing topbar: title + source toggle + search bar
  - When scrolled: search bar moves beside title, source toggle fades out
  - Quick row (filters + sort) slides out
- Source toggle: AniList/Extension with SVG icons
  - AniList → Popular anime (30 results)
  - Extension → Trending now (30 results)
- Filters button (left) + Sort dropdown (right, 5 options)
- Filter sheet: accordion + flat views, 5 categories (Genres/Release/Type/Score/Sort)
- Recent searches: collapsible card, individual delete, "Show N more"
- Results grid: in a surface card, 3-column, minimal padding (8dp outer)

### Library
- Status tabs (All/Watching/Completed/Plan) with item counts
- Grid mode (2-5 columns, configurable) + List mode
- Long-press multi-select mode with checkmark circles
- Bottom action bar (Cancel/Category/Delete)
- Category menu (shows current categories + move options)
- Customize sheet (Layout/Columns/Text placement/Cover details/Episode badge position)
- Poster styles (Rounded/Soft/Sharp) + Card density (Compact/Default/Comfortable)

### History
- Continue Watching section
- Recently Viewed list with timestamps

### Schedule
- 7-day selector with airing counts
- Airing list with cover, title, EP badge, time, relative time
- Past entries dimmed, next-up highlighted

### Settings
- Theme toggle (Dark/Light) with Material icons — works live
- Poster style (text-only segmented: Rounded/Soft/Sharp)
- Card density (text-only segmented: Compact/Default/Comfortable)
- Single-line titles toggle
- Animation speed (text-only segmented: Fast/Normal/Slow)
- Clear history / Clear library (with confirm dialog)
- About section

### Detail
- Banner + cover overlap (using offset, not negative padding)
- Title, score, genres, synopsis (expandable), episodes
- Add to Library button (toggles)
- Back handler (physical + on-screen)

---

## ❌ Not Yet Implemented (Future Work)

### High Priority
1. **Filter state not wired to API** — the filter sheet has UI but the selected filters don't affect the AniList GraphQL query. Need to pass filter params to `client.search()`.
2. **Sort not wired to API** — sort selection in the UI doesn't change the actual API sort parameter.
3. **Recent searches not persisted** — stored in memory only, lost on app restart. Need DataStore persistence.
4. **Card density not fully applied** — setting exists but doesn't affect grid gaps in all screens.
5. **Poster style not applied to all covers** — only AnimeCard reads it; hero carousel, continue watching, detail screen don't.

### Medium Priority
6. **Custom on-screen keyboard** — the prototype has a custom QWERTY keyboard. On Android, the native keyboard appears.
7. **Swipe gestures** — the prototype has click-drag-to-swipe for screen navigation.
8. **Fullscreen mode** — mobile-only fullscreen button (immersive mode).
9. **Filter sheet score section** — the score slider exists but the value isn't used in the API query.

### Low Priority
10. **Hero carousel auto-advance** — subtle auto-rotate would be nice.
11. **Pull-to-refresh** — not implemented on any screen.
12. **More elaborate animations** — staggered card fade-in, blur overlay on header collapse.

---

## Build History

| Build # | Status | Key issue |
|---------|--------|-----------|
| 1 | ❌ | Missing launcher icon |
| 2 | ❌ | Missing serialization plugin |
| 3 | ❌ | Compilation errors (genres null, isNull, experimental APIs) |
| 4 | ✅ | First successful build |
| 5 | ✅ | Fixed crash (weight(0f)) + added launcher icon |
| 6 | ❌ | Compilation errors (AnimeApp.kt, LibraryScreen imports, SearchScreen width) |
| 7 | ✅ | All screens rebuilt to match prototype |
| 8 | ❌ | Missing Surface import in SearchScreen |
| 9 | ✅ | Background color, theme toggle, hero height, search source toggle |
| 10 | ❌ | SourceToggleBtn needs RowScope |
| 11 | ✅ | Search screen rebuilt, library tabs improved |
| 12 | ✅ | Collapsing search bar, recent searches, filter sheet |
| 13 | ✅ | Filter sheet with accordion+flat views, scroll animation fix |
| 14 | ✅ | ExtraBold everywhere, bigger titles, results padding |

---

## Build & Download

1. Push to `main` (changes in `Android_app/` trigger the build).
2. Go to the **Actions** tab on GitHub → **Build Android APK**.
3. Download the `anime-app-apk` artifact.
4. Install on a device: `adb install app-debug.apk`.

---

## Architecture

```
Android_app/Anime_App/
├── app/
│   ├── build.gradle.kts          ← dependencies (Compose, Coil, Ktor, DataStore, Navigation, material-icons-extended)
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/testplaybyte/animeapp/
│           ├── MainActivity.kt   ← reads theme from settings, applies AnimeAppTheme
│           ├── AnimeApp.kt       ← root composable
│           ├── theme/            ← M3 colors, typography (ExtraBold), theme
│           ├── model/            ← data models (Anime, LibraryItem, etc.)
│           ├── data/             ← AniList client + repositories (DataStore)
│           ├── navigation/       ← NavHost + floating bottom nav (Box overlay)
│           └── ui/
│               ├── components/   ← BottomNavBar, CollapsingHeader, AnimeCard, HeroCarousel, ContinueWatching, FilterSheet, NavIcons
│               └── screens/      ← 7 screens
├── build.gradle.kts              ← project-level (AGP, Kotlin, Compose, Serialization plugins)
├── settings.gradle.kts
├── gradle/wrapper/               ← Gradle 8.9 wrapper
├── gradlew
└── .gitignore
```

---

## Tech Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| UI | Jetpack Compose (BOM) | 2024.09.03 |
| Design | Material 3 (dark purple theme) | from BOM |
| Icons | material-icons-extended | 1.7.2 |
| Navigation | Navigation Compose | 2.8.1 |
| Images | Coil | 2.7.0 |
| Networking | Ktor (Android engine) | 2.3.12 |
| Serialization | kotlinx.serialization | 1.7.3 |
| Persistence | DataStore Preferences | 1.1.1 |
| Build | Gradle 8.9 + AGP 8.5.2 + Kotlin 2.0.20 |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 35 (Android 15) |

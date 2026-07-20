# Anime App — Native Android (Improvement Tracker)

This document tracks the gap between the native Android app and the web prototype.
Future agents should use this to know what's implemented, what needs work, and what's missing.

---

## ✅ Implemented

- **M3 dark purple theme** — matches the prototype's color palette (PrimaryDark #d0bcff, Surface tiers, etc.)
- **7 screens** — Home, Library, History, Schedule, Search, Settings, Detail
- **Navigation** — Navigation Compose with routes for each screen + detail push
- **Bottom nav** — floating pill with content-sized active item, 6 nav items
- **AniList API** — trending, seasonal, top-rated, search, detail, airing schedule (Ktor + GraphQL)
- **Library** — DataStore-backed, add/remove, status (Watching/Completed/Plan), grid + list layouts
- **Library multi-select** — long-press to enter selection mode, checkmark circles, action bar (Cancel/Category/Delete)
- **Library customize** — Layout, Columns (2-5), Text placement, Show/hide format+episodes, Episode badge position
- **History** — DataStore-backed, auto-added on detail view, simulated episode+progress
- **Continue Watching** — horizontal row with banner, play icon, episode label, progress bar
- **Settings** — theme toggle, poster style (Rounded/Soft/Sharp), card density (Compact/Default/Comfortable), single-line titles
- **Detail screen** — banner, cover, title, score, genres, synopsis (expandable), episodes, add to library
- **Schedule** — 7-day selector, airing list with time + relative, past dimmed, next-up highlighted
- **Search** — debounced search, results grid
- **Collapsing headers** — title shrinks on scroll
- **Card animations** — staggered fade-in
- **Image loading** — Coil (AsyncImage)

---

## ❌ Not Yet Implemented (Future Work)

### High Priority
1. **Custom on-screen keyboard** — the prototype has a custom QWERTY keyboard. On Android, the native keyboard appears. To replicate: create a custom `SoftKeyboard` composable that replaces the native IME for in-app inputs.
2. **Swipe gestures** — the prototype has click-drag-to-swipe for screen navigation. On Android, use `ViewPager` or swipe-to-navigate gestures.
3. **Fullscreen button** — the prototype has a mobile-only fullscreen button. On Android, use immersive mode (`WindowCompat.setDecorFitsSystemWindows(window, false)`).
4. **Theme persistence** — the app currently always starts in dark mode. Need to read the saved theme from SettingsRepository and apply it.
5. **Light theme** — the light theme colors are defined but not wired up. Need to toggle based on settings.

### Medium Priority
6. **Card density** — the density setting exists but doesn't affect the grid gap. Need to read the setting and adjust `Arrangement.spacedBy`.
7. **Poster style** — the setting exists but only AnimeCard reads it. Need to apply to all cover images (hero carousel, continue watching, detail screen).
8. **Filter sheet** — the search screen doesn't have the filter bottom sheet (genres, year, season, format, status, score slider).
9. **Sort dropdown** — the search screen doesn't have the sort options.
10. **Recent searches** — not implemented in the search screen.
11. **Source toggle** — AniList/Extension toggle not implemented.

### Low Priority
12. **Hero carousel auto-advance** — the prototype doesn't auto-advance, but a subtle auto-rotate would be nice.
13. **Pull-to-refresh** — not implemented on any screen.
14. **Animations** — the prototype has more elaborate animations (staggered card fade-in, blur overlay on header collapse). The Android app has basic animations but could be more polished.
15. **Status bar** — the prototype has a custom status bar (time, punch-hole, wifi, signal, battery). On Android, the real status bar is used. Could add a custom status bar overlay for the prototype look.
16. **Side panels** — the prototype has left/right info panels on desktop. Not applicable on mobile.

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
│   ├── build.gradle.kts          ← dependencies (Compose, Coil, Ktor, DataStore, Navigation)
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/testplaybyte/animeapp/
│           ├── MainActivity.kt   ← entry point, sets up Compose + theme
│           ├── AnimeApp.kt       ← root composable
│           ├── theme/            ← M3 colors, typography, theme
│           ├── model/            ← data models (Anime, LibraryItem, etc.)
│           ├── data/             ← AniList client + repositories (DataStore)
│           ├── navigation/       ← NavHost with routes
│           └── ui/
│               ├── components/   ← BottomNavBar, AnimeCard, HeroCarousel, ContinueWatching
│               └── screens/      ← 7 screens
├── build.gradle.kts              ← project-level (AGP, Kotlin plugins)
├── settings.gradle.kts           ← project name + repos
├── gradle.properties             ← JVM args, AndroidX
├── gradle/wrapper/               ← Gradle 8.9 wrapper
├── gradlew                       ← Unix wrapper script
└── .gitignore                    ← ignores build/, local.properties, *.apk
```

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| UI | Jetpack Compose (BOM 2024.09.03) |
| Design | Material 3 (dark purple theme) |
| Navigation | Navigation Compose 2.8.1 |
| Images | Coil 2.7.0 |
| Networking | Ktor 2.3.12 (Android engine) |
| Serialization | kotlinx.serialization 1.7.3 |
| Persistence | DataStore Preferences 1.1.1 |
| Build | Gradle 8.9 + AGP 8.5.2 + Kotlin 2.0.20 |
| Min SDK | 24 (Android 7.0+) |
| Target SDK | 35 (Android 15) |

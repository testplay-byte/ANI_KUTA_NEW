# 01-architecture / 04 — Navigation

> How Aniyomi navigates between screens: Voyager for Compose screens, with legacy
> Activities for the reader and player.

## Voyager (the Compose navigator)

Aniyomi uses **Voyager** 1.0.1 (`cafe.adriel.voyager`) for Compose navigation.

| Voyager concept | Aniyomi usage |
|---|---|
| `Navigator` | Holds the back stack of `Screen`s. The host `MainActivity` sets one up. |
| `Screen` | A Compose screen. `LibraryScreen`, `BrowseScreen`, `HistoryScreen`, `UpdatesScreen`, `MoreScreen`, etc. |
| `ScreenModel` | Per-screen state holder (see `03-state-and-async.md`). |
| `TabNavigator` | Bottom-nav tabs: Library, Updates, History, Browse, More. |
| `transitions` | Material-motion screen transitions. |

### The host

`MainActivity` (in `app/.../ui/main/`) hosts a `Navigator` and renders the
current `Screen`'s `Content()`. The bottom nav uses `TabNavigator` to switch
between the five top-level tabs.

### Pushing screens

```kotlin
navigator.push(MangaScreen(mangaId))
navigator.push(ReaderActivityIntent(...))   // for the legacy reader, see below
```

Voyager handles the back stack. `navigator.pop()` returns to the previous screen.

### Tab navigation

The five tabs (see `app/.../ui/main/`):

1. **Library** — your saved manga + anime.
2. **Updates** — new chapters/episodes for library items.
3. **History** — recently read/watched, with resume.
4. **Browse** — source catalogs + extensions.
5. **More** — settings, downloads, stats, about, etc.

## The legacy Activities (NOT Voyager)

Two screens are still old-style Activities because they wrap non-Compose views:

| Activity | Why legacy |
|---|---|
| `ReaderActivity` (`app/.../ui/reader/ReaderActivity.kt`) | The manga reader uses `subsampling-scale-image-view` (a View) + custom pagers. |
| `PlayerActivity` (`app/.../ui/player/PlayerActivity.kt`) | The anime player wraps an MPV `AniyomiMPVView` (a View). |

These are launched via `startActivity(Intent(...))`, not `navigator.push(...)`.
Their state lives in `ReaderViewModel` / `PlayerViewModel`
(`androidx.lifecycle.ViewModel`), not Voyager ScreenModels.

The rest of the app is Compose + Voyager.

## Deep links

`app/.../ui/deeplink/` handles deep links (e.g. from a URL or launcher shortcut)
and routes them to the right screen. The `shortcut-helper` plugin generates
launcher shortcuts.

## Settings navigation

Settings live under `app/.../ui/setting/` and are reached from the "More" tab.
They're Compose screens (Voyager) for the most part, with some preference
fragments still using `PreferenceFragmentCompat` in places.

## See also

- [`03-state-and-async.md`](03-state-and-async.md) — ScreenModels.
- `../../06-ui/screens.md` — the screen catalog.
- `../../05-key-flows/app-startup.md` — how the navigator is set up at launch.

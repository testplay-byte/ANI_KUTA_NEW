# 06-ui — The presentation layer

> How the Aniyomi UI is built: which screens exist, how they're themed, which
> Compose components are reused, and where the Views → Compose migration still
> has unfinished business.

The Aniyomi UI is **Jetpack Compose** with **Voyager** as the navigator, on top
of a Material 3 design system. Almost every screen is a Voyager `Screen` (or a
Voyager `Tab` for the bottom-nav areas). The two big exceptions — the manga
**Reader** and the anime **Player** — are still old-style `AppCompatActivity`s
because they host non-Compose `View`s (the Subsampling Scale ImageView pagers
and the MPV `AniyomiMPVView`). They embed Compose *on top* of those views for
their menus and dialogs. See [`compose-migration.md`](compose-migration.md) for
the full migration state.

## Files in this folder

| File | What it covers |
|---|---|
| [`theme-design.md`](theme-design.md) | Material 3 base, the 19 selectable themes (16 XML-backed + Cottoncandy + Mocha + dynamic-color Monet), the `ColorScheme` construction, AMOLED mode, dynamic color, typography, shapes, and theme-preference storage. |
| [`screens.md`](screens.md) | The full catalog of every Voyager `Screen` / `Tab` / legacy `Activity`, grouped by tab/area, with its `ScreenModel` (or `ViewModel` for the reader/player) and a one-line description. |
| [`components.md`](components.md) | The reusable Compose components a contributor will look up — covers, library cards, chapter/episode rows, source items, the search `AppBar`, `Pill`/`Badge`, swipe-to-action, bottom sheets, the `Scaffold`, etc. — with the file each lives in. |
| [`compose-migration.md`](compose-migration.md) | The ongoing migration from Views to Compose: what's done, what's still View-based (reader viewer + MPV view), the `setComposeContent` interop helper, and implications for the ANIKUTA port. |

## How the UI is wired (recap)

- **Host**: `MainActivity` (`../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/main/MainActivity.kt`)
  sets up a Voyager `Navigator` rooted at `HomeScreen` via the
  `setComposeContent { ... }` helper (which wraps everything in
  `TachiyomiTheme` + default `LocalContentColor` / `LocalTextStyle`).
- **Home**: `HomeScreen` (`../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/home/HomeScreen.kt`)
  hosts a Voyager `TabNavigator` and renders the bottom `NavigationBar`
  (phone) or `NavigationRail` (tablet) — see
  [`01-architecture/04-navigation.md`](../01-architecture/04-navigation.md).
- **State**: per-screen state lives in Voyager `ScreenModel`s (or
  `androidx.lifecycle.ViewModel` for the legacy Activities) — see
  [`01-architecture/03-state-and-async.md`](../01-architecture/03-state-and-async.md).
- **Theme**: the active `ColorScheme` is chosen at composition time by
  `TachiyomiTheme` (`../ANIYOMI/app/src/main/java/eu/kanade/presentation/theme/TachiyomiTheme.kt`)
  from the user's `AppTheme` preference and the system dark-mode state.
- **i18n**: every label flows from Moko Resources `MR.strings.*` /
  `AYMR.strings.*` through the `stringResource` Compose bridge in
  `:presentation-core` (see [`02-modules/presentation-core.md`](../02-modules/presentation-core.md)).

## Where to look first

- "Which screen does X?" → [`screens.md`](screens.md).
- "Where is the Y widget defined?" → [`components.md`](components.md).
- "How do I add a new theme / change colors?" → [`theme-design.md`](theme-design.md).
- "Why is the reader/player still an Activity?" → [`compose-migration.md`](compose-migration.md).

## See also

- [`01-architecture/04-navigation.md`](../01-architecture/04-navigation.md) — Voyager navigator, tab navigation.
- [`01-architecture/03-state-and-async.md`](../01-architecture/03-state-and-async.md) — `ScreenModel`s and state.
- [`01-architecture/05-preferences-system.md`](../01-architecture/05-preferences-system.md) — the preference model behind theme & display prefs.
- [`02-modules/app.md`](../02-modules/app.md) — the `:app` module that hosts all of this.
- [`02-modules/presentation-core.md`](../02-modules/presentation-core.md) — the shared design-system library.
- [`03-subsystems/manga-reader.md`](../03-subsystems/manga-reader.md) and
  [`03-subsystems/anime-player.md`](../03-subsystems/anime-player.md) — the two legacy Activities.

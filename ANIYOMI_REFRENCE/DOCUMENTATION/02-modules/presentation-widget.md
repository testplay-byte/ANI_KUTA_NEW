# 02-modules / `:presentation-widget` — Home-screen widgets

> `:presentation-widget` is the home-screen widget module. It ships **four
> AppWidget receivers** built on Jetpack Glance: a manga and an anime
> "Updates grid" widget for the home screen, and the same two for the lock
> screen / Samsung cover screen. Each renders a grid of recent chapter/episode
> cover thumbnails; tapping a cover opens `MainActivity` on that entry.

| | |
|---|---|
| **Path** | `../ANIYOMI/presentation-widget/` |
| **Namespace** | `tachiyomi.presentation.widget` |
| **Package roots** | `tachiyomi.presentation.widget.*` |
| **Build script** | `../ANIYOMI/presentation-widget/build.gradle.kts` |
| **Manifest** | `../ANIYOMI/presentation-widget/src/main/AndroidManifest.xml` (4 receivers) |
| **Approx. file count** | ~12 Kotlin + 8 res files |

## Purpose & role

This module exists to put a "recent updates" cover grid on the user's home
screen, lock screen, or Samsung cover screen. It is **separate from `:app`**
so that the widget can be loaded by the Android launcher without dragging in
the entire app — but it depends on `:domain` (for the updates query) and on
Injekt (so it can resolve `GetMangaUpdates` / `GetAnimeUpdates` and
`SecurityPreferences` from the same graph `:app` registers).

The module produces:

- Two **home-screen widgets** (manga + anime) — a resizable grid of recent
  update cover thumbnails.
- Two **lock-screen / Samsung cover-screen widgets** (manga + anime) — same
  content, transparent background, registered for the `keyguard` widget
  category and Samsung's `sub_screen` display.
- The `MangaWidgetManager` / `AnimeWidgetManager` classes that drive widget
  refreshes — these are instantiated by `App.onCreate()` (see
  [`app.md`](app.md)).

## Build config

`../ANIYOMI/presentation-widget/build.gradle.kts`

```kotlin
plugins {
    id("mihon.library")
    id("mihon.library.compose")
    kotlin("android")
}

android {
    namespace = "tachiyomi.presentation.widget"
}
```

### Dependencies

```kotlin
implementation(projects.core.common)         // Constants, util
implementation(projects.domain)              // GetMangaUpdates, GetAnimeUpdates, MangaCover
implementation(projects.presentationCore)    // stringResource, etc.
api(projects.i18n)                            // MR.strings.*
api(projects.i18nAniyomi)                     // AR.strings.*

implementation(compose.glance)                // Jetpack Glance for AppWidgets
implementation(libs.material)                 // Material color resources
implementation(kotlinx.immutables)            // ImmutableList for cover data
implementation(platform(libs.coil.bom))       // Coil 3 for cover bitmap loading
implementation(libs.coil.core)
api(libs.injekt)                              // Global DI for GetUpdates / preferences
```

Notable points:

- **`api(libs.injekt)`** — Injekt is exposed because the widget classes use
  `Injekt.get<GetMangaUpdates>()` etc. as default constructor arguments.
- **`api(projects.i18n)` + `api(projects.i18nAniyomi)`** — re-exposed so the
  widget composables can call `stringResource(MR.strings.*)` and
  `stringResource(AR.strings.*)`.
- **`implementation(projects.domain)`** — the widget reads recent updates
  directly from the domain interactors, not through `:app` or `:data`.
  `:data` is **not** a dependency; the widget trusts `:app` to have registered
  the bindings at process start (it runs in the same process as `:app`).
- **`compose.glance`** is the Jetpack Glance-for-AppWidgets library. It lets
  the widget UI be written as Compose-style composables (the
  `androidx.glance.*` API) while still rendering as a RemoteViews-backed
  AppWidget.

## Package layout

```
presentation-widget/src/main/
├── AndroidManifest.xml                          (4 <receiver> entries)
├── java/tachiyomi/presentation/widget/
│   ├── entries/
│   │   ├── manga/
│   │   │   ├── BaseMangaUpdatesGridGlanceWidget.kt     ← shared logic
│   │   │   ├── MangaUpdatesGridGlanceWidget.kt         ← home-screen theme
│   │   │   ├── MangaUpdatesGridGlanceReceiver.kt       ← home-screen receiver
│   │   │   ├── MangaUpdatesGridCoverScreenGlanceWidget.kt  ← cover-screen theme
│   │   │   ├── MangaUpdatesGridCoverScreenGlanceReceiver.kt
│   │   │   └── MangaWidgetManager.kt                   ← refresh driver
│   │   └── anime/
│   │       ├── BaseAnimeUpdatesGridGlanceWidget.kt
│   │       ├── AnimeUpdatesGridGlanceWidget.kt
│   │       ├── AnimeUpdatesGridGlanceReceiver.kt
│   │       ├── AnimeUpdatesGridCoverScreenGlanceWidget.kt
│   │       ├── AnimeUpdatesGridCoverScreenGlanceReceiver.kt
│   │       └── AnimeWidgetManager.kt
│   ├── components/
│   │   ├── manga/
│   │   │   ├── UpdatesMangaWidget.kt           ← grid Composable
│   │   │   ├── UpdatesMangaCover.kt            ← single cover Composable
│   │   │   └── LockedMangaWidget.kt            ← app-lock placeholder
│   │   └── anime/
│   │       ├── UpdatesAnimeWidget.kt
│   │       ├── UpdatesAnimeCover.kt
│   │       └── LockedAnimeWidget.kt
│   └── util/
│       └── GlanceUtils.kt                      ← corner-radius + row/col helpers
└── res/
    ├── drawable/                appwidget_background.xml, appwidget_coverscreen_background.xml,
    │                            appwidget_cover_error.xml
    ├── drawable-nodpi/           previews: updates_grid_widget_preview.webp,
    │                            updates_grid_coverscreen_widget_preview.webp
    ├── layout/                  appwidget_loading.xml, appwidget_coverscreen_loading.xml
    ├── values/                  colors_appwidget.xml, dimens.xml
    ├── values-v31/              colors_appwidget.xml, dimens.xml (Android 12+)
    ├── values-night-v31/        colors_appwidget.xml (Android 12+ dark)
    └── xml/                     updates_grid_homescreen_widget_info.xml,
                                 updates_grid_lockscreen_widget_info.xml,
                                 updates_grid_samsung_cover_widget_info.xml
```

The manga and anime halves are near-mirror images of each other (the dual
manga/anime pattern — see
[`../00-overview/05-project-conventions.md`](../00-overview/05-project-conventions.md)).

## The widget(s) provided

Four `AppWidgetProvider`s, registered in `AndroidManifest.xml` (see below):

| Receiver | Glance widget | Surface | Theme |
|---|---|---|---|
| `MangaUpdatesGridGlanceReceiver` | `MangaUpdatesGridGlanceWidget` | Home screen | Opaque `appwidget_background` |
| `AnimeUpdatesGridGlanceReceiver` | `AnimeUpdatesGridGlanceWidget` | Home screen | Opaque `appwidget_background` |
| `MangaUpdatesGridCoverScreenGlanceReceiver` | `MangaUpdatesGridCoverScreenGlanceWidget` | Lock screen + Samsung cover screen | Transparent `appwidget_coverscreen_background` |
| `AnimeUpdatesGridCoverScreenGlanceReceiver` | `AnimeUpdatesGridCoverScreenGlanceWidget` | Lock screen + Samsung cover screen | Transparent `appwidget_coverscreen_background` |

### Per-side class hierarchy

For each side (manga / anime), the class layout is:

```
GlanceAppWidgetReceiver  (Android entry point)
   └─ glanceAppWidget = XxxUpdatesGridGlanceWidget  (concrete theme)
                          └─ extends BaseXxxUpdatesGridGlanceWidget  (logic)
                                  └─ extends GlanceAppWidget  (Glance base)
```

- The **`Receiver`** is the manifest-registered `BroadcastReceiver` Android
  invokes when the widget is added/updated. It just returns the concrete
  widget from `glanceAppWidget`.
- The **concrete `Widget`** class (`MangaUpdatesGridGlanceWidget`,
  `MangaUpdatesGridCoverScreenGlanceWidget`, plus the anime pair) supplies
  the per-surface theme: `foreground` (`ColorProvider`), `background`
  (`ImageProvider` referencing the drawable), and `topPadding`/`bottomPadding`
  (so the cover-screen variant can avoid the system's cover-screen margins).
- The **`Base*GlanceWidget`** contains all the real logic in
  `provideGlance(context, id)` — see "How it fetches data" below.
- A separate **`XxxWidgetManager`** class drives refreshes.

## How it fetches data

The widget does not query `:data` or the network directly. It uses two
domain interactors from `:domain` (resolved via Injekt):

- `GetMangaUpdates` (`tachiyomi.domain.updates.manga.interactor.GetMangaUpdates`)
- `GetAnimeUpdates` (`tachiyomi.domain.updates.anime.interactor.GetAnimeUpdates`)

And one preference holder from `:app`:

- `SecurityPreferences` (`eu.kanade.tachiyomi.core.security.SecurityPreferences`)
  — only to read `useAuthenticator()` (the app-lock flag).

### `BaseXxxUpdatesGridGlanceWidget.provideGlance()`

```kotlin
abstract class BaseMangaUpdatesGridGlanceWidget(
    private val context: Context = Injekt.get<Application>(),
    private val getUpdates: GetMangaUpdates = Injekt.get(),
    private val preferences: SecurityPreferences = Injekt.get(),
) : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact
    abstract val foreground: ColorProvider
    abstract val background: ImageProvider
    abstract val topPadding: Dp
    abstract val bottomPadding: Dp

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val locked = preferences.useAuthenticator().get()
        // …compute row/column count from the widget's exact size…
        provideContent {
            if (locked) {
                LockedMangaWidget(foreground, containerModifier)   // app-lock placeholder
                return@provideContent
            }
            val flow = remember {
                getUpdates.subscribe(false, DateLimit.toEpochMilli())
                    .map { it.prepareData(rowCount, columnCount) }
            }
            val data by flow.collectAsState(initial = null)
            UpdatesMangaWidget(data, foreground, topPadding, bottomPadding, containerModifier)
        }
    }
}
```

Inside `prepareData()`:

1. `distinctBy { it.mangaId }` — dedupe by entry.
2. `.take(rowCount * columnCount)` — cap at the grid capacity.
3. For each item, build a Coil `ImageRequest` against the entry's `MangaCover`
   (or `AnimeCover`) at exactly `CoverWidth × CoverHeight` pixels, with
   `Precision.EXACT`, `Scale.FILL`, memory-cache disabled, and a
   `RoundedCornersTransformation` (only on Android < 12; on Android 12+ the
   system applies the corner radius itself).
4. `executeBlocking` the request, convert the result to a `Bitmap`, and pair
   it with the manga/anime id.

The resulting `ImmutableList<Pair<Long, Bitmap?>>` is the widget's data.

**Date limit:** `BaseMangaUpdatesGridGlanceWidget.DateLimit` is
`ZonedDateTime.now().minusMonths(3).toInstant()` — the widget only shows
updates from the last 3 months.

**Grid sizing:** `DpSize.calculateRowAndColumnCount(top, bottom)` in
`util/GlanceUtils.kt` derives rows = `((height - paddings) / 95dp).coerceIn(1, 10)`
and columns = `(width / 64dp).coerceIn(1, 10)`. The 10-item cap is a Glance
limitation.

**Taps:** each cover is wrapped in a `clickable(actionStartActivity(intent))`
where the intent targets `Constants.MAIN_ACTIVITY` with action
`Constants.SHORTCUT_MANGA` (or `SHORTCUT_ANIME`) and the entry id as
`Constants.MANGA_EXTRA` (`EPISODE_EXTRA` for anime). `MainActivity` interprets
these and navigates to the entry.

### App-lock behaviour

If `SecurityPreferences.useAuthenticator()` is on, the widget shows
`LockedMangaWidget` / `LockedAnimeWidget` — a single centered text that says
the widget is unavailable while the app is locked (string
`MR.strings.appwidget_unavailable_locked`). Tapping it launches
`MainActivity`, which then runs the unlock flow.

### `MangaWidgetManager` / `AnimeWidgetManager`

These classes (in `entries/manga/MangaWidgetManager.kt` and the anime
counterpart) are the **refresh drivers**. They are not receivers — they are
instantiated by `App.onCreate()` (see [`app.md`](app.md)):

```kotlin
with(MangaWidgetManager(Injekt.get(), Injekt.get())) {
    init(ProcessLifecycleOwner.get().lifecycleScope)
}
```

`init(scope)` builds a combined flow of `(updates, useAuthenticator)` and
calls `MangaUpdatesGridGlanceWidget().updateAll(this)` +
`MangaUpdatesGridCoverScreenGlanceWidget().updateAll(this)` whenever either
the updates set or the app-lock flag changes (deduped by chapter-id set
equality, on `Dispatchers.Default`). The anime manager does the same for the
two anime widgets.

So the data flow is:

```
App.onCreate()
   │
   ├── Injekt.importModule(AppModule)   ← binds GetMangaUpdates, SecurityPreferences, etc.
   │
   └── MangaWidgetManager(getUpdates, securityPrefs)
           .init(ProcessLifecycleOwner.lifecycleScope)
                  │
                  ▼
        combine(GetMangaUpdates.subscribe(read=false, after=DateLimit),
                SecurityPreferences.useAuthenticator().changes())
                  │  distinctUntilChanged { … }
                  ▼
        MangaUpdatesGridGlanceWidget().updateAll(context)
        MangaUpdatesGridCoverScreenGlanceWidget().updateAll(context)
                  │
                  ▼
        BaseMangaUpdatesGridGlanceWidget.provideGlance(ctx, id)
                  │
                  ├── if (locked) → LockedMangaWidget
                  └── else        → UpdatesMangaWidget(coverBitmaps)
```

In effect: whenever a new chapter arrives in the last 3 months OR the user
toggles the app-lock, every placed instance of both manga widgets is rebuilt.
The anime side mirrors this exactly.

## Layout XML (under `res/`)

Glance renders to RemoteViews, but a static initial layout is still required
for the brief moment before Glance takes over. Two are provided:

| File | Used by | Content |
|---|---|---|
| `res/layout/appwidget_loading.xml` | Home-screen widgets | `FrameLayout` with `appwidget_background` + centered "Loading" `TextView`. |
| `res/layout/appwidget_coverscreen_loading.xml` | Cover-screen widgets | Same shape but with the transparent cover-screen background. |

These are referenced from the `<appwidget-provider>` XML via
`android:initialLayout`.

### `<appwidget-provider>` XMLs

| File | Surface | Highlights |
|---|---|---|
| `res/xml/updates_grid_homescreen_widget_info.xml` | Home screen | `minWidth=240dp`, `minHeight=80dp`, `minResizeWidth=80dp`, `minResizeHeight=110dp`, `maxResizeWidth/Height=600dp`, `targetCellWidth=4`, `targetCellHeight=2`, `resizeMode=horizontal\|vertical`, `widgetCategory=home_screen`. |
| `res/xml/updates_grid_lockscreen_widget_info.xml` | Lock screen | `resizeMode=horizontal\|vertical`, `widgetCategory=keyguard`. Uses the cover-screen preview image and `appwidget_coverscreen_loading` initial layout. |
| `res/xml/updates_grid_samsung_cover_widget_info.xml` | Samsung cover screen | A `<samsung-appwidget-provider>` with `display="sub_screen"` and `privacyWidget="true"`. |

### Drawables

| File | Use |
|---|---|
| `res/drawable/appwidget_background.xml` | Rounded opaque background for home-screen widgets. |
| `res/drawable/appwidget_coverscreen_background.xml` | Transparent background for cover-screen widgets. |
| `res/drawable/appwidget_cover_error.xml` | Placeholder shown when a cover bitmap fails to load. |

### Previews

Two `.webp` preview images live in `res/drawable-nodpi/`:
`updates_grid_widget_preview.webp` (home screen) and
`updates_grid_coverscreen_widget_preview.webp` (cover screen). These are shown
in the Android widget picker.

### Colors & dimens

`res/values/colors_appwidget.xml` defines the widget's color slots, all
referencing the Tachiyomi-theme M3 colors from `:presentation-core`
(`tachiyomi_surface`, `tachiyomi_onSurface`, `tachiyomi_surfaceVariant`,
`tachiyomi_onSurfaceVariant`, `tachiyomi_secondaryContainer`,
`tachiyomi_onSecondaryContainer`):

```xml
<color name="appwidget_background">@color/tachiyomi_surface</color>
<color name="appwidget_coverscreen_background">#00000000</color>
<color name="appwidget_on_background">@color/tachiyomi_onSurface</color>
<color name="appwidget_surface_variant">@color/tachiyomi_surfaceVariant</color>
<color name="appwidget_on_surface_variant">@color/tachiyomi_onSurfaceVariant</color>
<color name="appwidget_secondary_container">@color/tachiyomi_secondaryContainer</color>
<color name="appwidget_on_secondary_container">@color/tachiyomi_onSecondaryContainer</color>
```

Variant overrides:

- `res/values-v31/colors_appwidget.xml` — Android 12+ uses dynamic system
  colors for some slots where applicable.
- `res/values-night-v31/colors_appwidget.xml` — Android 12+ dark variant.

`res/values/dimens.xml` (and `values-v31/dimens.xml`) define
`appwidget_background_radius`, `appwidget_inner_radius`, and the cover
dimensions (`CoverWidth`, `CoverHeight` are referenced from
`Base*GlanceWidget` via the `components/manga/UpdatesMangaCover.kt` and anime
counterpart).

## AppWidgetProvider registration

`../ANIYOMI/presentation-widget/src/main/AndroidManifest.xml`

All four receivers are registered inside a single `<application>` block (the
manifest is merged into `:app`'s manifest at build time):

```xml
<receiver
    android:name="tachiyomi.presentation.widget.entries.manga.MangaUpdatesGridGlanceReceiver"
    android:enabled="@bool/glance_appwidget_available"
    android:exported="false"
    android:label="@string/label_recent_updates">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/updates_grid_homescreen_widget_info" />
</receiver>
```

Key points:

- **`android:enabled="@bool/glance_appwidget_available"`** — the widget is
  only enabled on devices where Glance is supported (the `glance_appwidget_available`
  bool comes from the Glance library). On older devices the receivers are
  disabled so the widget doesn't appear in the picker.
- **`android:exported="false"`** — only the system can broadcast
  `APPWIDGET_UPDATE` to them.
- **`android:label`** is `@string/label_recent_updates` (manga) or
  `@string/label_recent_anime_updates` (anime) — both come from `:i18n` /
  `:i18n-aniyomi`.
- Each receiver declares the `<appwidget-provider>` XML via the
  `android.appwidget.provider` meta-data.
- The cover-screen receivers add **two extra meta-data entries**:
  - `com.samsung.android.appwidget.provider` →
    `@xml/updates_grid_samsung_cover_widget_info` (Samsung's sub-screen
    support).
  - `com.samsung.android.sdk.subscreen.widget.support_visibility_callback` →
    `true` (lets Samsung call back for visibility changes).

Because the manifest is merged, the four receivers appear in `:app`'s final
manifest alongside `:app`'s own components. The widgets therefore run in the
main process and share Injekt with the rest of the app.

## Key files table

| File | Why it matters |
|---|---|
| `../ANIYOMI/presentation-widget/build.gradle.kts` | Module config; `compose.glance` + `api(libs.injekt)`. |
| `../ANIYOMI/presentation-widget/src/main/AndroidManifest.xml` | The 4 `<receiver>` entries. |
| `../ANIYOMI/presentation-widget/src/main/java/tachiyomi/presentation/widget/entries/manga/BaseMangaUpdatesGridGlanceWidget.kt` | All the manga-widget logic: sizing, app-lock, fetch, bitmap prep, click intents. |
| `../ANIYOMI/presentation-widget/src/main/java/tachiyomi/presentation/widget/entries/anime/BaseAnimeUpdatesGridGlanceWidget.kt` | Anime counterpart. |
| `../ANIYOMI/presentation-widget/src/main/java/tachiyomi/presentation/widget/entries/manga/MangaWidgetManager.kt` | Refresh driver — instantiated by `App.onCreate()`. |
| `../ANIYOMI/presentation-widget/src/main/java/tachiyomi/presentation/widget/entries/anime/AnimeWidgetManager.kt` | Anime counterpart. |
| `../ANIYOMI/presentation-widget/src/main/java/tachiyomi/presentation/widget/entries/manga/MangaUpdatesGridGlanceReceiver.kt` | The manifest-registered receiver — `GlanceAppWidgetReceiver` returning the widget. |
| `../ANIYOMI/presentation-widget/src/main/java/tachiyomi/presentation/widget/components/manga/UpdatesMangaWidget.kt` | The Compose/Glance grid composable. |
| `../ANIYOMI/presentation-widget/src/main/java/tachiyomi/presentation/widget/components/manga/UpdatesMangaCover.kt` | Single cover composable (defines `CoverWidth`/`CoverHeight`). |
| `../ANIYOMI/presentation-widget/src/main/java/tachiyomi/presentation/widget/components/manga/LockedMangaWidget.kt` | App-lock placeholder. |
| `../ANIYOMI/presentation-widget/src/main/java/tachiyomi/presentation/widget/util/GlanceUtils.kt` | `appWidgetBackgroundRadius()`, `appWidgetInnerRadius()`, `calculateRowAndColumnCount()`. |
| `../ANIYOMI/presentation-widget/src/main/res/xml/updates_grid_homescreen_widget_info.xml` | Home-screen `<appwidget-provider>` (sizes, category, initial layout). |
| `../ANIYOMI/presentation-widget/src/main/res/xml/updates_grid_lockscreen_widget_info.xml` | Lock-screen `<appwidget-provider>` (`widgetCategory=keyguard`). |
| `../ANIYOMI/presentation-widget/src/main/res/xml/updates_grid_samsung_cover_widget_info.xml` | Samsung cover-screen `<samsung-appwidget-provider>`. |
| `../ANIYOMI/presentation-widget/src/main/res/layout/appwidget_loading.xml` | Initial home-screen loading layout. |
| `../ANIYOMI/presentation-widget/src/main/res/layout/appwidget_coverscreen_loading.xml` | Initial cover-screen loading layout. |
| `../ANIYOMI/presentation-widget/src/main/res/values/colors_appwidget.xml` | Widget color slots (light). |
| `../ANIYOMI/presentation-widget/src/main/res/values-v31/colors_appwidget.xml` | Android 12+ overrides. |
| `../ANIYOMI/presentation-widget/src/main/res/values-night-v31/colors_appwidget.xml` | Android 12+ dark overrides. |

## See also

- [`app.md`](app.md) — `App.onCreate()` instantiates the `MangaWidgetManager`
  and `AnimeWidgetManager` and starts them on the process-lifecycle scope.
- [`presentation-core.md`](presentation-core.md) — provides the
  `stringResource` bridge, `ImmutableList`, and the Tachiyomi-theme colors
  that the widget colors reference.
- [`../00-overview/03-module-map.md`](../00-overview/03-module-map.md) — module roster.
- [`../03-subsystems/updates.md`](../03-subsystems/updates.md) — the updates
  subsystem the widget surfaces; `GetMangaUpdates` / `GetAnimeUpdates` are
  documented there.
- [`../03-subsystems/notifications.md`](../03-subsystems/notifications.md) —
  companion surface for "what's new".
- [`../01-architecture/02-dependency-injection.md`](../01-architecture/02-dependency-injection.md)
  — Injekt, which the widget relies on for `GetMangaUpdates` etc.

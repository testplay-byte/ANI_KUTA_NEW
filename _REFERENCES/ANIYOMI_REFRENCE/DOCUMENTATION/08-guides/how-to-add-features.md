# 08-guides / how-to-add-features.md

> Step-by-step recipes for common "add X" tasks in the Aniyomi reference.
> All paths and class names **verified against the snapshot** at `../ANIYOMI/`.
>
> These describe how Aniyomi does it — useful when studying or porting. ANIKUTA
> may choose differently (record in `../../../DOCS/04-design-decisions.md`).

## 1. Add a new Voyager screen (Compose)

Aniyomi uses **Voyager** for Compose navigation. A screen = `Screen` + `ScreenModel`.

1. **Create the Screen** in the relevant feature package under
   `app/src/main/java/eu/kanade/tachiyomi/ui/<area>/`. Implement `voyager.navigator.Screen`:
   ```kotlin
   class FooScreen : Screen {
       @Composable
       override fun Content() {
           val screenModel = rememberScreenModel<FooScreenModel>()
           // ... Compose UI ...
       }
   }
   ```
2. **Create the ScreenModel** alongside it:
   ```kotlin
   class FooScreenModel(
       private val someInteractor: SomeInteractor = Injekt.get(),
   ) : ScreenModel {
       private val _state = MutableStateFlow(FooState())
       val state: StateFlow<FooState> = _state.asStateFlow()
   }
   ```
3. **Navigate to it** from an existing screen: `navigator.push(FooScreen())`.
4. **Add strings** in `i18n-aniyomi/src/commonMain/moko-resources/base/strings.xml` (for
   Aniyomi-specific strings) or `i18n/...` (for shared ones). Reference via
   `AYMR.strings.foo_title` / `MR.strings.foo_title`.
5. **Register any DI deps** in the relevant `*Module` called from `App.kt`.
6. **Build via CI** (never locally — see `../../../../DOCS/06-build-and-ci.md`).

> See `../01-architecture/04-navigation.md` for the Voyager model and
> `../06-ui/screens.md` for the screen catalog.

## 2. Add a new database table + migration (SQLDelight)

Aniyomi has **two parallel schemas** — pick the right one (or both):
- Manga: `data/src/main/sqldelight/data/` + `data/src/main/sqldelight/migrations/`
- Anime: `data/src/main/sqldelightanime/dataanime/` + `data/src/main/sqldelightanime/migrations/`

1. **Create the `.sq` file** (e.g. `data/src/main/sqldelight/data/foo.sq`):
   ```sql
   CREATE TABLE foo (
       _id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
       name TEXT NOT NULL
   );

   insertFoo : INSERT INTO foo(name) VALUES (:name);
   selectAllFoos : SELECT * FROM foo;
   ```
   SQLDelight generates `FooQueries` at build time.
2. **Add a migration** `.sqm` file in the matching `migrations/` folder. The
   filename is the **next version number**:
   - Manga: check the highest existing `N.sqm` (currently `32.sqm`) → create `33.sqm`.
   - Anime: check the highest (currently `135.sqm`) → create `136.sqm`.
   ```sql
   -- 33.sqm (manga)
   CREATE TABLE foo (
       _id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
       name TEXT NOT NULL
   );
   ```
3. **Bump the schema version** in the `Database`/`AnimeDatabase` interface
   definition (the `version =` in `data/build.gradle.kts` `sqldelight { }` block
   — verify the exact location by reading the build file).
4. **Add a domain model + repository interface** in `:domain`
   (`tachiyomi.domain.<area>/`), and a **repository impl** in `:data`
   (`tachiyomi.data.<area>/`) using the generated `*Queries`.
5. **Wire DI** (interface → impl) in the data module registered from `App.kt`.
6. Build via CI to confirm SQLDelight generates the queries.

> See `../04-data-models/database-schema.md` for the full schema and
> `../02-modules/data.md` for the dual-DB architecture.

## 3. Add a new preference

Preferences are grouped into `*Preferences` classes (each injected via Injekt).

1. **Find the right `*Preferences` class** (e.g. `ReaderPreferences`,
   `PlayerPreferences`, `LibraryPreferences`). See
   `../04-data-models/preferences-catalog.md` for the full list.
2. **Add a method** returning a `Preference<T>`:
   ```kotlin
   fun fooFlag() = store.getBoolean("pref_foo_flag", false)
   ```
   Use `getBoolean`, `getString`, `getEnum`, `getObject` per the type.
3. **Read/write** it: `prefs.fooFlag().get()` / `...set(true)`.
   **Observe:** `prefs.fooFlag().changes()` returns a `Flow<Boolean>`.
4. **Add a settings UI row** in the relevant settings screen
   (`ui/setting/...`) that calls `...set(...)`.
5. **Add a string** for the setting's title/summary in `i18n`/`i18n-aniyomi`.

> See `../01-architecture/05-preferences-system.md` for the full mechanism.

## 4. Add a new string (i18n)

Two catalogs (both use Moko Resources):
- `:i18n` (`i18n/src/commonMain/moko-resources/base/strings.xml`) — shared, from Mihon.
- `:i18n-aniyomi` (`i18n-aniyomi/src/commonMain/moko-resources/base/strings.xml`) — Aniyomi-specific.

1. **Add the string** to the `base/strings.xml` of the right catalog:
   ```xml
   <string name="foo_title">Foo</string>
   ```
2. **Add plurals** if needed in `base/plurals.xml`:
   ```xml
   <plurals name="foo_count">
       <item quantity="one">%d foo</item>
       <item quantity="other">%d foos</item>
   </plurals>
   ```
3. **Use it in Compose**: `stringResource(MR.strings.foo_title)` or
   `stringResource(AYMR.strings.foo_title)`; for plurals,
   `pluralStringResource(MR.plurals.foo_count, count, count)`.
4. Translations are handled per-locale by creating/updated `<locale>/strings.xml`
   alongside `base/`. Aniyomi ships 66–67 locales.

> See `../02-modules/i18n.md` and `../02-modules/i18n-aniyomi.md`.

## 5. Add a new tracker

Trackers live in `app/src/main/java/eu/kanade/tachiyomi/data/track/` (one
subfolder per tracker). Aniyomi ships **11** trackers (MAL, AniList, Shikimori,
Bangumi, MangaUpdates, Kitsu, Simkl, Komga, Kavita, Suwayomi, Jellyfin).

1. **Create a subfolder** `data/track/<name>/` with:
   - `<Name>Tracker.kt` — extends `BaseTracker`, mixes in `MangaTracker` and/or
     `AnimeTracker`, `Enhanced*Tracker`, `Deletable*Tracker` as applicable.
   - `<Name>Api.kt` — the HTTP/GraphQL client.
   - `<Name>Models.kt` — request/response models (kotlinx-serialization).
2. **Register it** in `TrackManager` with a stable integer id (check existing ids
   to avoid collision; anime-only trackers use 101/102+).
3. **Implement the OAuth/PIN login flow** — register a callback host in
   `TrackLoginActivity` (manifest declares `aniyomi://<name>-auth`). AniList
   returns the token in the URL *fragment*; MAL/Shikimori/Bangumi/Simkl return
   `?code=` as a *query* param.
4. **Implement `bind()`/`unbind()`/`refresh()`/`search()`/`update()`** per the
   `Tracker` interface.
5. **Wire auto-track-on-read** — the reader calls `updateTrackChapterRead`, the
   player calls `updateTrackEpisodeSeen` (note the anime side's extra
   `|| !hasTrackers` gate).
6. **Add the tracker icon** + **strings** for its name/login UI.

> See `../03-subsystems/trackers.md` for the full tracker architecture and the
> capability matrix.

## 6. Add a new source/extension (contract level)

Extensions are **external APKs** implementing `:source-api`. Aniyomi loads them
at runtime via `ChildFirstPathClassLoader`. To author one:

1. **Create a separate Android-library project** that depends on `:source-api`
   (published as a maven coordinate). KMP `commonMain` + `androidMain`.
2. **Implement the source class**:
   - Manga: extend `HttpSource` (or `ParsedHttpSource` for Jsoup-based) under
     `eu.kanade.tachiyomi.source.online`.
   - Anime: extend `AnimeHttpSource` (or `ParsedAnimeHttpSource`) under
     `eu.kanade.tachiyomi.animesource.online`.
3. **Override the hooks** (`*Request` for network calls, `*Parse` for HTML/JSON
   parsing; or Jsoup selectors if using `Parsed*`). The base class implements the
   `suspend get*` API by delegating to the legacy `fetch*` via
   `Observable.awaitSingle()`.
4. **Set the source `id`**: `MD5("$name/$lang/$versionId")` truncated to 64 bits.
   `LocalMangaSource`/`LocalAnimeSource` use `id = 0L`.
5. **Declare the extension in the APK manifest** with the metadata the
   `ExtensionLoader` queries (it uses `PackageManager.GET_META_DATA`).
6. **Pin the lib version** in the APK's `versionName`:
   - Manga: `1.4.x` or `1.5.x` (within `LIB_VERSION_MIN=1.4 .. LIB_VERSION_MAX=1.5`).
   - Anime: integer `12..16` (within `LIB_VERSION_MIN=12 .. LIB_VERSION_MAX=16`).
   Outside the range → the extension is marked obsolete and not loaded.
7. **Mark NSFW** if applicable (the `nsfw` field the loader reads).
8. Install the APK on a device running Aniyomi; the `ExtensionManager` detects it
   via `PackageManager` and registers it.

> See `../02-modules/source-api.md` (the contract) and
> `../03-subsystems/source-system.md` (the loader/installers).

## 7. Add a new color theme

Themes live in `:presentation-core` as paired `values/colors_<name>.xml` +
`values-night/colors_<name>.xml`. Aniyomi ships ~19 themes.

1. **Create** `presentation-core/src/main/res/values/colors_mytheme.xml` and
   `values-night/colors_mytheme.xml` defining the Material 3 color slots
   (primary, onPrimary, background, surface, etc.). The app relies on specific
   slot semantics: `secondary` = unread badge, `tertiary` = downloaded badge,
   `secondaryContainer` = nav-bar selector, `surfaceContainer` = nav-bar bg.
2. **Register the theme** in the theme enum / `BaseColorScheme` mechanism (see
   `../06-ui/theme-design.md` for the exact registration point — the active
   `ColorScheme` is built in `:app`, not `:presentation-core`).
3. **Add an AMOLED variant** if desired (forces background/surface to pure black
   + a 3-step near-black `surfaceContainer` ramp).
4. **Add the picker entry** + a string for its name.
5. (Optional) Add a pure-Kotlin theme like `Cottoncandy`/`Mocha` instead of XML.

> See `../06-ui/theme-design.md` for the 19-theme table and the ColorScheme
> construction logic.

## See also

- [`troubleshooting.md`](troubleshooting.md) — when something goes wrong.
- `../01-architecture/` — the patterns these recipes use.
- `../02-modules/` — where each recipe's files live.

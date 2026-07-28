# 02-modules / i18n-aniyomi — The `:i18n-aniyomi` module

> Aniyomi-specific string resources layered on top of [`:i18n`](i18n.md). Same
> Moko Resources setup as `:i18n`, but the catalog holds the strings Aniyomi
> added on top of the Mihon baseline — most visibly the entire **anime player**
> UI, the torrent streaming service, and several "Changed from mihon" overrides
> where Aniyomi rewords a string to mention both manga and anime.

## Purpose

`/home/z/.../ANIYOMI/i18n-aniyomi/` exists so that the Mihon-inherited
[`:i18n`](i18n.md) catalog can be merged forward from upstream without conflict.
Anything Aniyomi added — anime-only UI, the MPV player settings screens, hoster
configuration, torrent streaming, dual "chapter/episode" wording — lives here in
a parallel Moko Resources catalog. The generated accessor is
`tachiyomi.i18n.aniyomi.AYMR` (short for **AniYomi MR**).

## Build configuration

Source: `../ANIYOMI/i18n-aniyomi/build.gradle.kts`. It is structurally identical
to `:i18n`'s build file — same plugins, same KMP + Moko setup, same
`generateLocalesConfig` wiring. Only three things differ:

```kotlin
android {
    namespace = "tachiyomi.i18n.aniyomi"          // ← different namespace
    // …
}

multiplatformResources {
    resourcesClassName.set("AYMR")                 // ← different accessor class name
    resourcesPackage.set("tachiyomi.i18n.aniyomi") // ← different accessor package
}
```

The rest (KMP `androidTarget()`, `mihon.library` plugin, Moko plugin,
`lint { disable = [MissingTranslation, ExtraTranslation] }`, the
`generateLocalesConfig` preBuild task) is identical to `:i18n`. See
[`i18n.md`](i18n.md) for the full walkthrough.

The `resourcesClassName.set("AYMR")` line is the key difference at the
generated-code level: instead of the default `MR` object, Moko emits an `AYMR`
object. This avoids a name clash when both modules are on the classpath.

## Moko Resources structure

```
i18n-aniyomi/
├── build.gradle.kts
└── src/
    └── commonMain/
        └── moko-resources/
            ├── base/                   ← English source strings (Aniyomi-only keys)
            │   ├── strings.xml
            │   └── plurals.xml
            ├── ar/
            │   ├── strings.xml
            │   └── plurals.xml
            ├── zh-rCN/
            └── …                       (one folder per locale)
```

Same conventions as `:i18n`: per-locale folders with `strings.xml` and (for most
locales) `plurals.xml`; `base/` is the English source catalog; empty
`<resources/>` files are tolerated.

## Locale count

There are **68 locale folders** under
`i18n-aniyomi/src/commonMain/moko-resources/` (including `base`) — i.e.
**67 translated locales** plus the English source. This is one more locale than
`:i18n` (which has 66) because `:i18n-aniyomi` also includes an `ur` (Urdu)
catalog. The list (alphabetical):

```
am ar as base be bg bn ca ceb cs cv da de el eo es eu fa fi fil fr gl he hi
hr hu in it ja jv ka-rGE kk km kn ko lt lv ml mr ms my nb-rNO ne nl nn pl pt
pt-rBR ro ru sa sah sc sdh sk sq sr sv ta te th tr uk ur uz vi zh-rCN zh-rTW
```

## String catalog size and contents

The English `base/strings.xml` is **610 lines / 566 `<string>` entries**, plus
15 `<plurals>` entries in `base/plurals.xml` (63 lines). It is roughly
two-thirds the size of `:i18n`'s catalog.

The catalog is organised into clearly-commented sections. The full list of
section banners in `base/strings.xml`:

| Section | What it covers |
|---|---|
| `Generic strings` | A few overrides (e.g. `confirm_exit` is reused for "Enable Horizontal Seek"). |
| `Changed from mihon` | Strings reworded to mention both manga and anime (e.g. `manga` → "Manga", `pref_library_summary` → "Categories, global update, chapter/episode swipe"). |
| `Player settings` (Internal player / Gestures / Decoder / Subtitles / Audio / Custom Buttons / Script editor / Torrents / Advanced) | The entire MPV player settings UI — orientation, brightness, volume, gestures, hardware decoder selection, subtitle styling, audio tracks, custom Lua buttons, the in-player script editor, torrent hoster config, advanced MPV options. |
| `Player` / `Player - skip intro button` / `Player - Bottom left` / `Player - Bottom right` | In-player HUD strings: skip intro, controls, status text. |
| `Other` / `Errors` | Misc UI text and player/error messages. |
| `Sheets` (Audio delay / Sub delay / Subtitle colors / Subtitle settings / Subtitle Typography / Miscellaneous / Subtitles / Video filters / Audio tracks / Quality list / Decoders / More Sheet / Playback Speed / Subtitle tracks / screenshot / Chapters) | The bottom-sheet popups the player shows when you long-press a control. |
| `Reader section` | A handful of manga-reader overrides (Aniyomi rewords some reader strings to stay consistent with the player UI). |
| `Migrate dialog` | Migration UI strings (migrate manga/anime between sources). |
| `Torrent Service` | Torrserver integration strings (see [`../03-subsystems/torrent-streaming.md`](../03-subsystems/torrent-streaming.md)). |

So the module is dominated by **player UI** strings — that is the bulk of what
Aniyomi adds over Mihon. Keys like `pref_player_internal`, `pref_reduce_motion`,
`pref_hosters`, `pref_player_time_to_disappear`, `rotation_reverse_landscape`,
`action_search_player_settings`, `label_player_settings` are all defined here,
not in `:i18n`.

## How it differs from `:i18n`

| Aspect | `:i18n` | `:i18n-aniyomi` |
|---|---|---|
| Origin | Inherited from Mihon. | Aniyomi-only additions. |
| Generated accessor | `tachiyomi.i18n.MR` | `tachiyomi.i18n.aniyomi.AYMR` |
| `resourcesClassName` | _default_ (`MR`) | `AYMR` |
| Android namespace | `tachiyomi.i18n` | `tachiyomi.i18n.aniyomi` |
| Locales | 66 translated | 67 translated (adds `ur`) |
| `base/strings.xml` size | 847 entries / 986 lines | 566 entries / 610 lines |
| Content focus | Manga library, reader, downloads, trackers, backup, general settings. | **Player** (MPV) UI, torrent service, dual manga/anime rewordings, anime-specific sheets. |
| Upstream merge | Tracked against Mihon's catalog. | Independent; conflicts with Mihon are avoided by keeping Aniyomi-only keys here. |

The "Changed from mihon" section in `base/strings.xml` is the explicit seam:
when an Aniyomi string is the same key as a Mihon string but with different
wording (e.g. mentioning "episode" alongside "chapter"), the Aniyomi version
lives here and the `:app` code references `AYMR.strings.<key>` instead of
`MR.strings.<key>` for that key.

## How `:app` consumes both

`/home/z/.../ANIYOMI/app/build.gradle.kts` declares both modules:

```kotlin
implementation(projects.i18n)
implementation(projects.i18nAniyomi)
```

Code in `:app` then imports whichever accessor holds the key it needs:

```kotlin
import tachiyomi.i18n.MR           // Mihon-inherited keys
import tachiyomi.i18n.aniyomi.AYMR // Aniyomi-only keys
// …
stringResource(MR.strings.label_library)        // inherited
stringResource(AYMR.strings.pref_player_internal) // Aniyomi-only
```

A real example that uses both:
`../ANIYOMI/app/src/main/java/eu/kanade/presentation/browse/manga/MangaSourcesScreen.kt`
imports `tachiyomi.i18n.MR` and `tachiyomi.i18n.aniyomi.AYMR` in the same file.

Other modules that pull `:i18n-aniyomi` via `api(...)` so its accessor is
transitively visible:

- `:source-local`
- `:presentation-widget`

(`:presentation-core` exposes only `:i18n`, not `:i18n-aniyomi`.)

## Key files

| Path (relative to `DOCUMENTATION/`) | What it is |
|---|---|
| `../ANIYOMI/i18n-aniyomi/build.gradle.kts` | Moko + KMP build config; sets `resourcesClassName = AYMR`, `resourcesPackage = tachiyomi.i18n.aniyomi`. |
| `../ANIYOMI/i18n-aniyomi/src/commonMain/moko-resources/base/strings.xml` | English source catalog (610 lines, 566 entries); dominated by player UI. |
| `../ANIYOMI/i18n-aniyomi/src/commonMain/moko-resources/base/plurals.xml` | English plurals (63 lines, 15 entries). |
| `../ANIYOMI/i18n-aniyomi/src/commonMain/moko-resources/<locale>/strings.xml` | One per locale (67 translated locales). |
| `../ANIYOMI/i18n-aniyomi/src/commonMain/moko-resources/<locale>/plurals.xml` | Plurals for the locale (present for some locales; many ship only `strings.xml`). |
| `../ANIYOMI/buildSrc/src/main/kotlin/mihon/buildlogic/tasks/LocalesConfigTask.kt` | Same `generateLocalesConfig` task used by `:i18n`. |

## See also

- [`i18n.md`](i18n.md) — the Mihon-inherited baseline catalog.
- [`README.md`](README.md) — module index and dependency graph.
- [`../03-subsystems/anime-player.md`](../03-subsystems/anime-player.md) — where
  most of the `AYMR.strings.*` player keys are actually used.
- [`../03-subsystems/torrent-streaming.md`](../03-subsystems/torrent-streaming.md)
  — the `Torrent Service` section of the catalog.
- [`../00-overview/02-tech-stack.md`](../00-overview/02-tech-stack.md) — Moko
  Resources version and the localization stack entry.

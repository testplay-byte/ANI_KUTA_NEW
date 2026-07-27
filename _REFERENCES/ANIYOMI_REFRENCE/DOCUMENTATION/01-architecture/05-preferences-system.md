# 01-architecture / 05 — Preferences System

> How Aniyomi stores and accesses user settings. The preference system is one of
> the more idiosyncratic parts of the Tachiyomi/Mihon lineage.

## Core abstraction: `PreferenceStore`

| Concept | Location | Role |
|---|---|---|
| `PreferenceStore` (interface) | `:core:common` (`tachiyomi.core.preference`) | Factory for `Preference<T>` objects. Wraps `SharedPreferences`. |
| `Preference<T>` (interface) | `:core:common` | A typed, observable wrapper around a single pref key. |
| `PreferenceStoreImpl` | `:core:common` (or `:app`) | The `SharedPreferences`-backed impl. |
| `*Preferences` classes | Various | Group related prefs (e.g. `readerPreferences`, `playerPreferences`). |

## How a preference is declared

```kotlin
class ReaderPreferences(private val store: PreferenceStore) {
    fun defaultReaderType() = store.getObject(
        "pref_default_reader_type",
        ReaderType.LEFT_TO_RIGHT,
        ReaderType.Serializer,
    )
    fun cropBorders() = store.getBoolean("pref_crop_borders", false)
    fun themeDark() = store.getEnum("pref_theme_dark", ThemeDark.SYSTEM)
}
```

Each method returns a **`Preference<T>`**, not the raw value. Reading the value:
`readerPreferences.cropBorders().get()`. Writing: `...set(true)`. Observing:
`...changes()` returns a `Flow<T>`.

## Categories of preferences

Aniyomi splits preferences into themed groups, each a class injected via Injekt:

| Group | Owning class (typical) | Examples |
|---|---|---|
| Reader | `ReaderPreferences` | reader type, crop, background, color filter |
| Player | `PlayerPreferences` | hardware decoding, subtitles, seek interval, PiP |
| Library | `LibraryPreferences` | sort, filters, update interval, categories |
| Downloads | `DownloadPreferences` | concurrent downloads, download dir, only over Wi-Fi |
| Source | `SourcePreferences` | per-source settings (stored by source ID) |
| Tracker | `TrackPreferences` | auto-update tracker, refresh |
| Backup | `BackupPreferences` | auto-backup, backup dir |
| UI / device | `UiPreferences` / `DevicePreferences` | theme, tablet UI, battery optimization |
| Extension | `ExtensionPreferences` | extension repos |
| Security | `SecurityPreferences` | app lock, incognito mode |

## Storage

- Backed by `SharedPreferences` (via `androidx.preference`).
- A single default preferences file + per-source files for source prefs.
- Migration logic lives in `app/.../mihon/core/migration/migrations/`.

## Where they're wired

- `PreferenceModule` (in `app/.../di/` or `:core:common`) registers
  `PreferenceStore` + each `*Preferences` class in Injekt.
- Anywhere in the app: `Injekt.get<ReaderPreferences>()`.

## Per-source preferences

Sources (extensions) declare their own preferences, which are stored under a
namespaced key and edited from Settings → Extensions → (source) → Settings. The
`SourcePreferences` helper and the `SourceManager` facilitate this. See
`../../03-subsystems/source-system.md`.

## Migration

`mihon.core.migration` contains a chain of `Migration`s that run on app upgrade,
renaming/moving pref keys as needed. Each migration is a class in
`app/.../mihon/core/migration/migrations/`.

## See also

- [`02-dependency-injection.md`](02-dependency-injection.md) — how prefs are injected.
- `../../04-data-models/preferences-catalog.md` — the full key catalog.
- `../../02-modules/core-common.md`.

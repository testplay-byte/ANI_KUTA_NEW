# 01 — Format Overview

> The Aniyomi backup file format, at a high level.

## File structure

```
backup.tachibk
└── [gzip compressed]
    └── [protocol buffer encoded]
        └── Backup { ... }
```

- **Extension:** `.tachibk`
- **Outer encoding:** Gzip (magic bytes `0x1f 0x8b`)
- **Inner encoding:** Protocol Buffers (protobuf)
- **Schema:** Defined by `kotlinx-serialization-protobuf` with `@ProtoNumber`
  annotations in `eu.kanade.tachiyomi.data.backup.models.*`

## Detection

The file starts with gzip magic bytes `0x1f 0x8b`. After gunzip, the raw
protobuf bytes are decoded.

**Non-gzipped protobuf** is also possible (rare). In that case, the file
starts directly with protobuf field bytes (no magic detection — tried as a
fallback if gzip detection fails).

## Two root schemas

Aniyomi has **two** root backup schemas:

### 1. Modern `Backup` (current)

Used by recent Aniyomi versions. Anime are at **proto field 501**.

```kotlin
@Serializable
data class Backup(
    @ProtoNumber(1) val backupManga: List<BackupManga> = emptyList(),
    @ProtoNumber(2) val backupCategories: List<BackupCategory> = emptyList(),  // manga categories
    @ProtoNumber(101) val backupSources: List<BackupSource> = emptyList(),
    @ProtoNumber(104) val backupPreferences: List<BackupPreference> = emptyList(),
    @ProtoNumber(105) val backupSourcePreferences: List<BackupSourcePreferences> = emptyList(),
    @ProtoNumber(106) val backupMangaExtensionRepo: List<BackupExtensionRepos> = emptyList(),
    // Aniyomi-specific (500+)
    @ProtoNumber(500) val isLegacy: Boolean = true,
    @ProtoNumber(501) val backupAnime: List<BackupAnime> = emptyList(),
    @ProtoNumber(502) val backupAnimeCategories: List<BackupCategory> = emptyList(),
    @ProtoNumber(503) val backupAnimeSources: List<BackupAnimeSource> = emptyList(),
    @ProtoNumber(504) val backupExtensions: List<BackupExtension> = emptyList(),
    @ProtoNumber(505) val backupAnimeExtensionRepo: List<BackupExtensionRepos> = emptyList(),
    @ProtoNumber(506) val backupCustomButton: List<BackupCustomButtons> = emptyList(),
)
```

### 2. Legacy `LegacyBackup` (older versions)

Used by older Aniyomi versions. Anime are at **proto field 3**.

```kotlin
@Serializable
data class LegacyBackup(
    @ProtoNumber(1) val backupManga: List<BackupManga> = emptyList(),
    @ProtoNumber(2) val backupCategories: List<BackupCategory> = emptyList(),
    @ProtoNumber(3) val backupAnime: List<BackupAnime> = emptyList(),
    @ProtoNumber(4) val backupAnimeCategories: List<BackupCategory> = emptyList(),
    @ProtoNumber(101) val backupSources: List<BackupSource> = emptyList(),
    @ProtoNumber(103) val backupAnimeSources: List<BackupAnimeSource> = emptyList(),
    @ProtoNumber(104) val backupPreferences: List<BackupPreference> = emptyList(),
    @ProtoNumber(105) val backupSourcePreferences: List<BackupSourcePreferences> = emptyList(),
    @ProtoNumber(106) val backupExtensions: List<BackupExtension> = emptyList(),
    @ProtoNumber(107) val backupAnimeExtensionRepo: List<BackupExtensionRepos> = emptyList(),
    @ProtoNumber(108) val backupMangaExtensionRepo: List<BackupExtensionRepos> = emptyList(),
    @ProtoNumber(109) val backupCustomButton: List<BackupCustomButtons> = emptyList(),
)
```

### Detection logic (from Aniyomi's `BackupDetector`)

Aniyomi uses a minimal probe model to detect which format:

```kotlin
@Serializable
data class BackupDetector(
    @ProtoNumber(103) val backupAnimeSources: List<DetectAnimeSource> = emptyList(),
    @ProtoNumber(500) val isLegacy: Boolean = true,
)
```

If `isLegacy == true && backupAnimeSources.isNotEmpty()` → it's a legacy backup.
Otherwise → modern format.

**Our approach:** Try modern `Backup` first. If it has 0 anime, try `LegacyBackup`.
This is simpler and works for all cases.

## What's inside a backup

| Data | Modern field | Legacy field | Description |
|---|---|---|---|
| Manga (library) | 1 | 1 | Manga entries (we ignore — anime-first) |
| Manga categories | 2 | 2 | Manga category names + order |
| Anime (library) | 501 | 3 | **The main data we want** |
| Anime categories | 502 | 4 | User's custom anime categories |
| Sources (manga) | 101 | 101 | Source name + ID (for manga) |
| Anime sources | 503 | 103 | Source name + ID (for anime) |
| Preferences | 104 | 104 | App preferences (sealed class) |
| Source preferences | 105 | 105 | Per-source preferences |
| Extensions | 504 | 106 | Extension APK bundles (pkgName + apk bytes) |
| Anime extension repos | 505 | 107 | Extension repo URLs |
| Manga extension repos | 106 | 108 | Manga extension repo URLs |
| Custom buttons | 506 | 109 | Aniyomi custom button feature |

## What ANIKUTA cares about

| Aniyomi data | ANIKUTA uses? | Maps to |
|---|---|---|
| `backupAnime` | ✅ Yes | `BackupEntry.Library` + `BackupEntry.AnimeDetails` |
| `backupAnime.episodes` | ✅ Yes | `BackupEntry.Episodes` |
| `backupAnime.tracking` | ✅ Yes | `BackupEntry.Tracker` + AniList ID resolution |
| `backupAnime.history` | ✅ Yes | `BackupEntry.WatchProgress` |
| `backupAnime.categories` | ✅ Yes | `BackupEntry.Categories` (anime→category links) |
| `backupAnimeCategories` | ✅ Yes | `BackupEntry.Categories` (category definitions) |
| `backupAnimeSources` | ⚠️ Maybe | Source name → our extension source ID matching |
| `backupManga*` | ❌ No | Ignored (anime-first) |
| `backupPreferences` | ❌ No | Different app, different prefs |
| `backupSourcePreferences` | ❌ No | Different sources |
| `backupExtensions` | ❌ No | APK bundles — too heavy, we re-match by name |
| `backup*ExtensionRepo` | ❌ No | We have our own extension repo system |
| `backupCustomButton` | ❌ No | Aniyomi-specific feature |

## File size

A typical Aniyomi backup with ~50 anime + episodes + tracking is about
1-5 MB gzipped. Without gzip, 5-20 MB. The size is dominated by episode
metadata (names, URLs, dates) and history entries.

## Compression

Gzip is applied **after** protobuf encoding (not per-field). The entire
protobuf blob is gzipped as one unit. This gives good compression ratios
(~3-5x) because protobuf field tags + string data compress well.

## Versioning

Aniyomi doesn't have an explicit schema version field. Instead, it relies on:
- `@ProtoNumber` field numbers (stable, never change)
- Default values for new fields (backward compatible)
- Unknown fields are skipped by the protobuf decoder (forward compatible)

This means **any version** of Aniyomi backup can be decoded by our code, as
long as we only declare the fields we need (unknown fields are skipped).

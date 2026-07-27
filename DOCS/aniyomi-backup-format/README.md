# Aniyomi Backup Format — Documentation Index

> This folder contains a thorough analysis of the Aniyomi backup format,
> written to guide the implementation of a proper restore-from-Aniyomi
> feature in ANIKUTA.
>
> **Goal:** Enable ANIKUTA to import Aniyomi `.tachibk` backup files and
> translate all anime-related data into our AniList-based system.

## Documents

| # | Document | Purpose |
|---|---|---|
| 1 | [`01-format-overview.md`](01-format-overview.md) | High-level format description, file structure, encoding |
| 2 | [`02-protobuf-schema.md`](02-protobuf-schema.md) | Complete protobuf schema with all field numbers + types |
| 3 | [`03-data-models.md`](03-data-models.md) | What each model represents + how it maps to ANIKUTA |
| 4 | [`04-restore-flow.md`](04-restore-flow.md) | How Aniyomi's own restore works (step-by-step) |
| 5 | [`05-translation-plan.md`](05-translation-plan.md) | How ANIKUTA will translate Aniyomi backups (matching strategy, AniList search) |
| 6 | [`06-extension-mapping.md`](06-extension-mapping.md) | How Aniyomi's extension-based system maps to our AniList-based system |

## Quick summary

- **Format:** Gzipped protocol buffer (`.tachibk` extension)
- **Encoding:** `ProtoBuf` via `kotlinx-serialization-protobuf`
- **Two root schemas:** Modern `Backup` (anime at field 501) + `LegacyBackup` (anime at field 3)
- **Anime data:** Library anime, episodes, tracking, history, categories, sources
- **Manga data:** Also included (we ignore it — anime-first)
- **Extensions:** Not backed up as APKs (only extension repo URLs + preferences)
- **Preferences:** Sealed-class `PreferenceValue` (Int/Long/Float/String/Boolean/StringSet)

## Our current implementation

ANIKUTA already has a basic `AniyomiBackupFormat` (`core/backup/format/AniyomiBackupFormat.kt`)
that:
- Decodes the protobuf using minimal model classes
- Maps anime/episodes/categories/tracking/history to our `BackupContainer`
- Tries modern format first, falls back to legacy

**What's missing (to be implemented next):**
- AniList ID resolution for anime without tracker bindings (title-based search)
- Proper episode URL remapping (Aniyomi URLs → our AniList-keyed system)
- Category link remapping (old Aniyomi category IDs → our category IDs)
- Source/extension matching (Aniyomi source IDs → our extension source IDs)
- Better error handling + progress reporting

See [`05-translation-plan.md`](05-translation-plan.md) for the full implementation plan.

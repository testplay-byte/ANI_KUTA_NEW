# 07-reference / Index — Lookup layer

> The "I know what I'm looking for but not where it is" layer. These three
> reference files are jump tables — read them sideways, not linearly. Use them
> to find a file, decode a term, or map a subsystem to the modules and key
> files it cuts across.

## When to use this folder

| If you want to… | Open |
|---|---|
| Find the file(s) that own a feature, class, or concept | [`file-index.md`](file-index.md) |
| Look up a domain term you read in another doc (e.g. "What's a stub source?") | [`glossary.md`](glossary.md) |
| See, for a given subsystem, every module + key file you'll touch | [`cross-reference-matrix.md`](cross-reference-matrix.md) |

The first three documentation layers — [`../00-overview/`](../00-overview/),
[`../01-architecture/`](../01-architecture/),
[`../02-modules/`](../02-modules/) — are meant to be read top-to-bottom. The
subsystem layer ([`../03-subsystems/`](../03-subsystems/)) is meant to be read
topically. **This folder is meant to be searched.** Treat each file as a
database table keyed on a different question:

- **file-index.md** — keyed on *intent* ("where do I find the backup creator?").
- **glossary.md** — keyed on *term* ("what does *enhanced tracker* mean?").
- **cross-reference-matrix.md** — keyed on *subsystem* ("if I'm touching
  downloads, which modules and files do I care about?").

## Files in this folder

| File | Lines | Purpose |
|---|---|---|
| [`README.md`](README.md) | ~70 | This index. |
| [`file-index.md`](file-index.md) | ~430 | Big table of common tasks/questions → source file(s). Grouped by category (Build/Config, App core, UI, Reader/Player, Sources/Extensions, Data/DB, Downloads, Trackers, Backup, Notifications, Misc). All paths are relative to `../ANIYOMI/`. |
| [`glossary.md`](glossary.md) | ~440 | Alphabetical dictionary of every domain term (Anime, Aniyomi, AniList, AniSkip, Archive, Backup, Bangumi, Baseline profile, Category, Chapter, Cloudflare, Compose, Conscrypt, DexClassLoader, Download, Episode, Enhanced tracker, Extension, Extension repo, FFmpeg, Global search, History, Injekt, KMP, Kitsu, Library, Local source, MAL, Mihon, Moko Resources, MPV, NanoHTTPD, Obsolete extension, PiP, QuickJS, SAF, Scanlator, Season, Shikimori, Shizuku, Simkl, SManga/SAnime/SChapter/SEpisode, Source, Source-api, SQLDelight, Stub source, Tachiyomi, Theme, Torrent, Track, Tracker, UniFile, Updates, Voyager, WebP, WebView, WorkManager). Each entry cross-links to the doc that covers it. |
| [`cross-reference-matrix.md`](cross-reference-matrix.md) | ~310 | Subsystem × Module matrix. Rows are the 15 subsystems; columns are the 13 Gradle modules; cells are ✓ with 1–3 key files. Use it as a quick "if I'm working on subsystem X, which modules and files do I care about?" reference. |

## Conventions

- **Source paths** are relative to `../ANIYOMI/` (i.e. to the source-tree root),
  written like `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/App.kt` so
  they work whether you click them from this folder or from the docs root.
- **`{Manga,Anime}` braces** indicate the dual pattern: e.g.
  `data/library/{manga,anime}/` means there are two near-identical directories.
- Cross-links to other docs use relative markdown links.

## See also

- [`../README.md`](../README.md) — master documentation index.
- [`../00-overview/03-module-map.md`](../00-overview/03-module-map.md) — the
  13 Gradle modules at a glance.
- [`../02-modules/README.md`](../02-modules/README.md) — per-module deep dives.
- [`../03-subsystems/README.md`](../03-subsystems/README.md) — per-subsystem
  deep dives (with its own mini subsystem × module matrix).

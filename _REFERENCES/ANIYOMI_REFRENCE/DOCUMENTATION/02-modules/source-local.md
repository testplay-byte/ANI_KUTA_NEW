# 02-modules / source-local — The `:source-local` module

> The built-in source that turns a folder of files on disk into a fully
> functional Aniyomi library. Implements both `CatalogueSource` (manga) and
> `AnimeCatalogueSource` (anime) from [`:source-api`](source-api.md), with
> `id = 0L` so the app can recognise it as "Local".

## Purpose

`/home/z/.../ANIYOMI/source-local/` is the in-app source that lets a user keep a
personal library of manga and anime as plain files on the filesystem (typically
on external storage via the Storage Access Framework). It exists so that:

- A user can side-load CBZ/CBR/EPUB manga or MP4/MKV anime without installing
  any extension.
- Backed-up chapters / episodes can be re-imported by simply dropping them into
  the right folder.
- The local library can sit beside remote sources in Browse / Library screens
  and use the same `CatalogueSource` / `AnimeCatalogueSource` code paths the
  remote sources use.

The module is **KMP** (`commonMain` + `androidMain`) — the `commonMain` side
declares `expect class` shells and shared helpers (`Format`, archive-extension
lists, cover manager interfaces), while `androidMain` provides the `actual`
implementations that touch the Android filesystem, `:core:archive`,
`:core-metadata`, `:domain` (for `ChapterRecognition` / `EpisodeRecognition`),
and `ffmpeg-kit` (for thumbnail extraction).

## Build configuration

Source: `../ANIYOMI/source-local/build.gradle.kts`.

```kotlin
plugins {
    id("mihon.library")
    kotlin("multiplatform")
}

kotlin {
    androidTarget()
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(projects.sourceApi)
                api(projects.i18n)
                api(projects.i18nAniyomi)
                implementation(libs.unifile)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(projects.core.archive)
                implementation(projects.core.common)
                implementation(projects.coreMetadata)
                implementation(projects.domain)            // ChapterRecognition / EpisodeRecognition
                implementation(kotlinx.bundles.serialization)
            }
        }
    }
    compilerOptions {
        freeCompilerArgs.addAll("-Xexpect-actual-classes",
                                 "-opt-in=kotlinx.serialization.ExperimentalSerializationApi")
    }
}

android {
    namespace = "tachiyomi.source.local"
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    dependencies { implementation(aniyomilibs.ffmpeg.kit) }
}
```

Notable points:

- `commonMain` depends on `:source-api` (the contract it implements) and the
  i18n modules (for localised strings like "Local manga source").
- `androidMain` pulls in `:core:archive` (for `ArchiveReader` / `EpubReader`),
  `:core:common` (for `withIOContext`, `ImageUtil`, `UniFile` extensions,
  `DiskUtil`), `:core-metadata` (for `ComicInfo` and the `*Details` JSON
  schemas), and `:domain` (only for `ChapterRecognition` / `EpisodeRecognition`
  — see the `// Move ChapterRecognition to separate module?` TODO).
- `ffmpeg-kit` is used to extract cover / thumbnail / background stills from
  video files for local anime.

## Module layout

```
source-local/src/
├── commonMain/kotlin/tachiyomi/source/local/
│   ├── entries/
│   │   ├── manga/LocalMangaSource.kt           ← expect class : CatalogueSource, UnmeteredSource
│   │   └── anime/LocalAnimeSource.kt           ← expect class : AnimeCatalogueSource
│   │   └── anime/LocalAnimeFetchTypeManager.kt ← expect class
│   ├── io/
│   │   ├── Format.kt                           ← sealed interface (Directory/Archive/Epub)
│   │   ├── Archive.kt                          ← ArchiveManga / ArchiveAnime (extension allow-lists)
│   │   ├── manga/LocalMangaSourceFileSystem.kt ← expect class
│   │   └── anime/LocalAnimeSourceFileSystem.kt ← expect class
│   └── image/
│       ├── manga/LocalMangaCoverManager.kt     ← expect class
│       └── anime/
│           ├── LocalAnimeCoverManager.kt       ← expect class
│           ├── LocalAnimeBackgroundManager.kt  ← expect class
│           └── LocalEpisodeThumbnailManager.kt ← expect class
└── androidMain/kotlin/tachiyomi/source/local/
    ├── entries/
    │   ├── manga/LocalMangaSource.kt           ← actual class (CatalogueSource implementation)
    │   └── anime/LocalAnimeSource.kt           ← actual class (AnimeCatalogueSource implementation)
    │   └── anime/LocalAnimeFetchTypeManager.kt ← actual class
    ├── io/
    │   ├── manga/LocalMangaSourceFileSystem.kt ← actual class (delegates to StorageManager)
    │   └── anime/LocalAnimeSourceFileSystem.kt ← actual class
    ├── image/
    │   ├── manga/LocalMangaCoverManager.kt     ← actual class
    │   └── anime/{LocalAnimeCoverManager,LocalAnimeBackgroundManager,LocalEpisodeThumbnailManager}.kt
    ├── filter/
    │   ├── manga/MangaOrderBy.kt               ← Filter.Sort subclass (Title / Date)
    │   └── anime/AnimeOrderBy.kt               ← AnimeFilter.Sort subclass
    └── metadata/EpubReaderExtensions.kt        ← fillMetadata(SManga, SChapter) from EPUB
```

The expect/actual split lets the common-side `expect class` declarations enforce
the source-api contract portably while pushing all SAF / `Context` / `UniFile`
work into `androidMain`.

## How it implements the source-api contract

### Manga — `LocalMangaSource`

`actual class LocalMangaSource(context, fileSystem, coverManager) : CatalogueSource, UnmeteredSource`

- **Identity**: `id = 0L` (the `LocalMangaSource.ID` constant — how the app
  recognises a manga is local), `name` from `AYMR.strings.local_manga_source`,
  `lang = "other"`, `supportsLatest = true`. `toString()` returns the name.
- **Browse**: `getPopularManga` and `getLatestUpdates` both delegate to
  `getSearchManga` with different `FilterList`s (`PopularFilters` sorts by
  name, `LatestFilters` sorts by `lastModified` within the last 7 days).
  `getSearchManga(page, query, filters)`:
  1. Lists `fileSystem.getFilesInBaseDirectory()`.
  2. Keeps only directories not starting with `.`, deduped by name.
  3. Filters by `query` (substring, case-insensitive) or by `lastModified`
     threshold for "latest".
  4. Applies `MangaOrderBy.Popular` / `MangaOrderBy.Latest` sort.
  5. Maps each dir to an `SManga { title = dir.name, url = dir.name,
     thumbnail_url = coverManager.find(...)?.uri }`.
  6. Returns `MangasPage(mangas, hasNextPage = false)` (no pagination).
- **Manga details** (`getMangaDetails`): looks for a top-level `ComicInfo.xml`
  in the manga directory; if absent and there's a legacy `details.json`, parses
  it and **migrates** it to a freshly written `ComicInfo.xml`. If neither
  exists, copies `ComicInfo.xml` from the first chapter archive that contains
  one (and writes a `.noxml` marker to avoid re-scanning next time). The actual
  parsing uses `tachiyomi.core.metadata.comicinfo.ComicInfo` (see
  [`core-metadata.md`](core-metadata.md)) and `SManga.copyFromComicInfo(...)`.
- **Chapter list** (`getChapterList`):
  1. Looks for an optional `chapters.json` (a `List<ChapterDetails>`) to
     override names/dates/scanlators.
  2. Lists files in the manga directory, keeping directories, supported
     archive types (`.zip/.cbz/.rar/.cbr/.7z/.cb7/.tar/.cbt`), and `.epub`.
  3. For each, builds an `SChapter` with `url = "<mangaDir>/<chapterFile>"`,
     `name = nameWithoutExtension`, `date_upload = lastModified`,
     `chapter_number = ChapterRecognition.parseChapterNumber(manga.title, name, default)`.
  4. For `.epub` files, additionally calls `EpubReader.fillMetadata(manga, chapter)`
     (see `metadata/EpubReaderExtensions.kt`) to pull title / author / date /
     publisher (= scanlator) out of the EPUB package document.
  5. Merges any `chapters.json` overrides, sorts descending by chapter number
     (natural-order tie-break), and (if the manga has no cover yet) extracts a
     cover from the last chapter via `updateCover(chapter, manga)`.
- **Pages**: `getPageList(chapter)` **throws `UnsupportedOperationException`**
  — the local source doesn't participate in normal page-list fetching. Instead,
  the app's reader loader (in `:app`) recognises `LocalMangaSource` and uses
  `getFormat(chapter): Format` directly: a `Format.Directory`, `Format.Archive`
  (handled via `:core:archive`), or `Format.Epub` (handled via `EpubReader`).
- **Filters**: `getFilterList()` returns `FilterList(MangaOrderBy.Popular(context))`.
- **Helpers**: `Manga.isLocal()` and `MangaSource.isLocal()` extension functions
  check `source == LocalMangaSource.ID`.

### Anime — `LocalAnimeSource`

`actual class LocalAnimeSource(context, fileSystem, coverManager, backgroundManager, thumbnailManager, fetchTypeManager) : AnimeCatalogueSource, UnmeteredSource`

- **Identity**: same shape as manga — `id = 0L`, `name` from
  `AYMR.strings.local_anime_source`, `lang = "other"`, `supportsLatest = true`.
- **Browse**: same directory-listing logic, building `SAnime` entries with
  `fetch_type = fetchTypeManager.find(dir)` (Seasons vs Episodes), `thumbnail_url`
  from `coverManager`, and `background_url` from `backgroundManager`.
- **Fetch type detection** (`LocalAnimeFetchTypeManager.find(animeUrl)`):
  if any file in the anime directory is a supported video format → `Episodes`;
  else if any file is a directory → `Seasons`; else default `Episodes`.
- **Anime details** (`getAnimeDetails`): sets cover + background URLs, then
  looks for a `details.json` (a `tachiyomi.core.metadata.tachiyomi.AnimeDetails`)
  to override `title` / `author` / `artist` / `description` / `genre` / `status`.
  (No `ComicInfo.xml` equivalent for anime — only the legacy JSON format.)
- **Seasons** (`getSeasonList`): if the anime has subdirectories, each becomes
  a season `SAnime` whose `url` is `"<animeUrl>/<seasonName>"`. Used when
  `fetch_type == FetchType.Seasons`.
- **Episodes** (`getEpisodeList`):
  1. Optional `episodes.json` (`List<EpisodeDetails>`) for overrides.
  2. Lists supported archive files (`ArchiveAnime.isSupported`):
     `avi, flv, mkv, mov, mp4, webm, wmv, torrent`.
  3. Each becomes an `SEpisode` with `episode_number` derived from
     `EpisodeRecognition.parseEpisodeNumber(anime.title, name, default)`.
  4. Merges `episodes.json` overrides (name, date, scanlator, summary).
  5. If the episode has no `preview_url`, extracts a thumbnail at the
     midpoint of the video via `FFprobeKit` + `FFmpegKit` (see
     `updateImageFromVideo`) and writes it via `thumbnailManager.update`.
  6. If the anime has no cover / background, extracts one from the last
     episode using the same FFmpeg pipeline.
  7. Sorts descending by episode number (natural-order tie-break).
- **Videos**: `getVideoList(episode)` **throws `UnsupportedOperationException`**
  — the player loader in `:app` recognises `LocalAnimeSource` and plays the
  file directly via MPV.
- **Legacy Rx API**: still overrides `fetchPopularAnime` / `fetchLatestUpdates`
  / `fetchSearchAnime` (deprecated) for backwards compatibility with old code
  paths that haven't migrated to the suspend API; they wrap the suspend result
  in `Observable.just(...)` via `runBlocking`.

## Filesystem access

`LocalMangaSourceFileSystem` and `LocalAnimeSourceFileSystem` (both
`expect class` in `commonMain`, `actual class` in `androidMain`) wrap a single
`tachiyomi.domain.storage.service.StorageManager` (from `:domain`):

```kotlin
actual class LocalMangaSourceFileSystem(private val storageManager: StorageManager) {
    actual fun getBaseDirectory(): UniFile? = storageManager.getLocalMangaSourceDirectory()
    actual fun getFilesInBaseDirectory(): List<UniFile> = getBaseDirectory()?.listFiles().orEmpty().toList()
    actual fun getMangaDirectory(name: String): UniFile? =
        getBaseDirectory()?.findFile(name)?.takeIf { it.isDirectory }
    actual fun getFilesInMangaDirectory(name: String): List<UniFile> =
        getMangaDirectory(name)?.listFiles().orEmpty().toList()
}
```

`StorageManager` (in `:domain` → implemented in `:data`) resolves the SAF tree
the user picked for "Local manga" / "Local anime" in Settings → Storage. So the
whole local source is rooted at a single user-chosen `UniFile` per content type.

## Format detection & archive support

`io/Format.kt`:

```kotlin
sealed interface Format {
    data class Directory(val file: UniFile) : Format
    data class Archive(val file: UniFile) : Format
    data class Epub(val file: UniFile) : Format
    class UnknownFormatException : Exception()
    companion object {
        fun valueOf(file: UniFile) = when {
            file.isDirectory -> Directory(file)
            file.extension.equals("epub", true) -> Epub(file)
            ArchiveManga.isSupported(file) -> Archive(file)
            else -> throw UnknownFormatException()
        }
    }
}
```

`io/Archive.kt` defines the supported extension allow-lists:

| Object | Supported extensions |
|---|---|
| `ArchiveManga` | `zip, cbz, rar, cbr, 7z, cb7, tar, cbt` |
| `ArchiveAnime` | `avi, flv, mkv, mov, mp4, webm, wmv, torrent` |

## Cover / background / thumbnail managers

All four managers (`LocalMangaCoverManager`, `LocalAnimeCoverManager`,
`LocalAnimeBackgroundManager`, `LocalEpisodeThumbnailManager`) follow the same
shape:

```kotlin
expect class LocalMangaCoverManager {
    fun find(mangaUrl: String): UniFile?
    fun update(manga: SManga, inputStream: InputStream): UniFile?
}
```

The `actual` implementations look in the manga/anime directory for a file whose
`nameWithoutExtension` equals `"cover"` (case-insensitive) and that decodes as
an image (verified via `ImageUtil.isImage`). `update(...)` writes the given
`InputStream` to `cover.jpg` (or the existing cover file), creates a `.nomedia`
file via `DiskUtil.createNoMediaFile` so gallery apps ignore the directory, and
sets `thumbnail_url` on the model. The thumbnail manager keys files by both
`animeUrl` and `episode` so each episode can have its own preview image.

## Filters

`filter/manga/MangaOrderBy.kt` and `filter/anime/AnimeOrderBy.kt` are
androidMain-only (they need a `Context` for i18n). Each is a sealed subclass of
`Filter.Sort` / `AnimeFilter.Sort` with two concrete cases:

- `Popular(context)` — sorts by **Title** ascending (`Selection(0, true)`).
- `Latest(context)` — sorts by **Date** descending (`Selection(1, false)`).

These are the only filters the local source exposes; there are no genre / tag
filters because the local source doesn't have a tag taxonomy.

## EPUB metadata extraction

`metadata/EpubReaderExtensions.kt` (androidMain only) defines:

```kotlin
fun EpubReader.fillMetadata(manga: SManga, chapter: SChapter)
```

It opens the EPUB's package document (via `getPackageHref` + `getPackageDocument`),
pulls `dc:title` → `chapter.name`, `dc:creator` → `manga.author`, `dc:description`
→ `manga.description`, `dc:publisher` (or `dc:creator` fallback) → `chapter.scanlator`,
and `dc:date` (or `meta[property=dcterms:modified]`) → `chapter.date_upload`.
This is how EPUB-backed local chapters get sensible metadata without a
sidecar JSON file.

## Key files table

| File | Purpose |
|---|---|
| `../ANIYOMI/source-local/build.gradle.kts` | KMP module config; `commonMain` deps: `:source-api`, `:i18n`, `:i18n-aniyomi`, unifile. `androidMain` deps: `:core:archive`, `:core:common`, `:core-metadata`, `:domain`, serialization, ffmpeg-kit. |
| `../ANIYOMI/source-local/src/commonMain/kotlin/tachiyomi/source/local/entries/manga/LocalMangaSource.kt` | `expect class LocalMangaSource : CatalogueSource, UnmeteredSource`. |
| `../ANIYOMI/source-local/src/commonMain/kotlin/tachiyomi/source/local/entries/anime/LocalAnimeSource.kt` | `expect class LocalAnimeSource : AnimeCatalogueSource`. |
| `../ANIYOMI/source-local/src/commonMain/kotlin/tachiyomi/source/local/entries/anime/LocalAnimeFetchTypeManager.kt` | `expect class` — picks `FetchType.Seasons` vs `FetchType.Episodes`. |
| `../ANIYOMI/source-local/src/androidMain/kotlin/tachiyomi/source/local/entries/manga/LocalMangaSource.kt` | `actual class` — full manga implementation: browse, details (ComicInfo.xml migration), chapter list, EPUB metadata, cover fallback. |
| `../ANIYOMI/source-local/src/androidMain/kotlin/tachiyomi/source/local/entries/anime/LocalAnimeSource.kt` | `actual class` — full anime implementation: browse, details, seasons, episodes, FFmpeg thumbnail/cover/background extraction. |
| `../ANIYOMI/source-local/src/androidMain/kotlin/tachiyomi/source/local/entries/anime/LocalAnimeFetchTypeManager.kt` | `actual class` — file-presence heuristic for fetch type. |
| `../ANIYOMI/source-local/src/commonMain/kotlin/tachiyomi/source/local/io/Format.kt` | `sealed interface Format` (Directory/Archive/Epub) + `UnknownFormatException`. |
| `../ANIYOMI/source-local/src/commonMain/kotlin/tachiyomi/source/local/io/Archive.kt` | `ArchiveManga` / `ArchiveAnime` extension allow-lists. |
| `../ANIYOMI/source-local/src/commonMain/kotlin/tachiyomi/source/local/io/manga/LocalMangaSourceFileSystem.kt` | `expect class` filesystem wrapper. |
| `../ANIYOMI/source-local/src/commonMain/kotlin/tachiyomi/source/local/io/anime/LocalAnimeSourceFileSystem.kt` | `expect class` filesystem wrapper. |
| `../ANIYOMI/source-local/src/androidMain/kotlin/tachiyomi/source/local/io/manga/LocalMangaSourceFileSystem.kt` | `actual class` delegating to `StorageManager.getLocalMangaSourceDirectory()`. |
| `../ANIYOMI/source-local/src/androidMain/kotlin/tachiyomi/source/local/io/anime/LocalAnimeSourceFileSystem.kt` | `actual class` delegating to `StorageManager.getLocalAnimeSourceDirectory()`. |
| `../ANIYOMI/source-local/src/commonMain/kotlin/tachiyomi/source/local/image/manga/LocalMangaCoverManager.kt` | `expect class` cover manager. |
| `../ANIYOMI/source-local/src/commonMain/kotlin/tachiyomi/source/local/image/anime/LocalAnimeCoverManager.kt` | `expect class` cover manager. |
| `../ANIYOMI/source-local/src/commonMain/kotlin/tachiyomi/source/local/image/anime/LocalAnimeBackgroundManager.kt` | `expect class` background manager. |
| `../ANIYOMI/source-local/src/commonMain/kotlin/tachiyomi/source/local/image/anime/LocalEpisodeThumbnailManager.kt` | `expect class` episode thumbnail manager. |
| `../ANIYOMI/source-local/src/androidMain/kotlin/tachiyomi/source/local/image/...` | `actual class` implementations of all four managers. |
| `../ANIYOMI/source-local/src/androidMain/kotlin/tachiyomi/source/local/filter/manga/MangaOrderBy.kt` | `Filter.Sort` subclass — Title / Date. |
| `../ANIYOMI/source-local/src/androidMain/kotlin/tachiyomi/source/local/filter/anime/AnimeOrderBy.kt` | `AnimeFilter.Sort` subclass — Title / Date. |
| `../ANIYOMI/source-local/src/androidMain/kotlin/tachiyomi/source/local/metadata/EpubReaderExtensions.kt` | `EpubReader.fillMetadata(manga, chapter)` — pulls Dublin Core metadata. |

## See also

- [`source-api.md`](source-api.md) — the contract this module implements.
- [`core-archive.md`](core-archive.md) — `ArchiveReader` / `EpubReader` used to
  read CBZ/CBR/EPUB chapter files.
- [`core-metadata.md`](core-metadata.md) — `ComicInfo` schema and `*Details`
  JSON models used for local metadata.
- [`core-common.md`](core-common.md) — `withIOContext`, `ImageUtil`, `DiskUtil`,
  `UniFile` extensions.
- [`../03-subsystems/source-system.md`](../03-subsystems/source-system.md) —
  how the app's `SourceManager` registers and dispatches to the local source.
- [`../03-subsystems/storage-and-cache.md`](../03-subsystems/storage-and-cache.md)
  — SAF tree selection and the `StorageManager` that backs the local filesystem.
- [`../03-subsystems/manga-reader.md`](../03-subsystems/manga-reader.md) — how
  the reader loader uses `LocalMangaSource.getFormat()` directly instead of the
  `getPageList` API.
- [`../03-subsystems/anime-player.md`](../03-subsystems/anime-player.md) — how
  the player loads local video files via MPV.

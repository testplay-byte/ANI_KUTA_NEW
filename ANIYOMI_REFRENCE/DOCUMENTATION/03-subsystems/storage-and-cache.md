# 03-subsystems / Storage & Cache

> Where every byte Aniyomi writes actually lives on disk, how it picks the
> directories (SAF), the abstraction layer over the file system (`UniFile`),
> the disk caches, and the data-saver image-compression proxy.

Aniyomi has to live with two awkward Android facts: (1) since Android 11
external storage is no longer freely writable, and (2) the OS may evict
cache at any time. The codebase answers (1) with the **Storage Access
Framework** wrapped by the **`UniFile` library**, and (2) by separating
*cache* (evictable, in `context.cacheDir` or `context.externalCacheDir`) from
*user data* (persistent, in the SAF-picked base directory).

## The big picture

```
┌──────────────────────────────────────────────────────────────────┐
│  Settings → Data → Storage location                              │
│  ActivityResultContracts.OpenDocumentTree()  ── SAF picker       │
│  takePersistableUriPermission(uri, READ|WRITE)                   │
│  storagePreferences.baseStorageDirectory().set(uri)              │
└─────────────┬────────────────────────────────────────────────────┘
              │ stored as a content:// URI string
              ▼
┌──────────────────────────────────────────────────────────────────┐
│  StorageManager  (domain/.../storage/service/StorageManager.kt)  │
│  • watches baseStorageDirectory().changes()                      │
│  • on change: re-creates the well-known subdirs under baseDir    │
│  • exposes one getter per well-known dir as a UniFile            │
└─────────────┬────────────────────────────────────────────────────┘
              │
   ┌──────────┼──────────┬───────────┬───────────┬─────────────┐
   ▼          ▼          ▼           ▼           ▼             ▼
 autobackup/ downloads/ local/      localanime/ mpv-config/  (covers live in
 (auto       (manga +   (local      (local       fonts/       app external
  backups)    anime      manga       anime        scripts/     files dirs, see
              downloads) source)     source)      script-opts/ below)
                                          shaders/
```

## Storage Access Framework (SAF)

Aniyomi never assumes it can write to `/sdcard/...` directly. The user
explicitly grants access to a tree via the system's
`ActivityResultContracts.OpenDocumentTree` picker:

```kotlin
// SettingsDataScreen.kt
val pickStorageLocation = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocumentTree(),
) { uri ->
    if (uri != null) {
        val flags = FLAG_GRANT_READ_URI_PERMISSION or FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (e: SecurityException) { /* InkBook / Samsung quirk */ }
        UniFile.fromUri(context, uri)?.let { storageDirPref.set(it.uri.toString()) }
    }
}
```

Two important details:

- **`takePersistableUriPermission`** survives app restarts and reboots.
  Without it, the URI grant would be invalidated as soon as the process
  dies. Some devices (notably InkBook and certain Samsung ROMs) don't
  implement persistable grants properly; the call is wrapped in
  try/catch and falls back to "access works anyway because the ROM is
  non-compliant".
- The chosen URI is stored as a string in
  `StoragePreferences.baseStorageDirectory()` (an
  `appStateKey("storage_dir")`, default = `folderProvider.path()`).

`AndroidStorageFolderProvider` (`:core:common`) is the fallback when no
SAF location is configured: it returns
`Environment.getExternalStorageDirectory()/Aniyomi` — useful for first-run
before the user has picked anything.

## The `UniFile` abstraction

`com.hippo.unifile.UniFile` is the bridge between the SAF `DocumentFile`
world (tree URIs, content-resolver reads/writes) and a `java.io.File`-like
API. Almost every directory Aniyomi touches is a `UniFile`, not a `File`.

`UniFile.fromUri(context, uri)` is the constructor; from there you get
`createFile(name)`, `createDirectory(name)`, `findFile(name)`,
`listFiles()`, `openInputStream()`, `openOutputStream()`, `delete()`, etc.

Two extension files add ergonomics:

| File | Adds |
|---|---|
| `core/common/src/main/java/tachiyomi/core/common/storage/UniFileExtensions.kt` | `UniFile.extension`, `UniFile.nameWithoutExtension`, `UniFile.displayablePath` (filePath ?: uri) |
| `core/archive/src/main/kotlin/mihon/core/archive/UniFileExtensions.kt` | `UniFile.openArchive()` → `ArchiveReader` (used by the reader for CBZ/CBR; see [`../02-modules/core-archive.md`](../02-modules/core-archive.md)) |

## `StorageManager` — the directory registry

`domain/src/main/java/tachiyomi/domain/storage/service/StorageManager.kt`
owns the one `baseDir: UniFile?` and creates the well-known subdirectories
under it. On construction and whenever `baseStorageDirectory()` changes:

```kotlin
baseDir?.let { parent ->
    parent.createDirectory(AUTOMATIC_BACKUPS_PATH)   // "autobackup"
    parent.createDirectory(LOCAL_SOURCE_PATH)        // "local"        (LocalMangaSource)
    parent.createDirectory(LOCAL_ANIMESOURCE_PATH)   // "localanime"   (LocalAnimeSource)
    parent.createDirectory(DOWNLOADS_PATH).also {
        DiskUtil.createNoMediaFile(it, context)      // writes .nomedia to keep gallery apps out
    }
    parent.createDirectory(MPV_CONFIG_PATH)?.let { mpvDir ->   // "mpv-config"
        mpvDir.createDirectory(FONTS_PATH)           // "fonts"
        mpvDir.createDirectory(SCRIPTS_PATH)         // "scripts"
        mpvDir.createDirectory(SCRIPT_OPTS_PATH)     // "script-opts"
        mpvDir.createDirectory(SHADERS_PATH)         // "shaders"
    }
}
```

Getters exposed (each returns `UniFile?`, lazily creating if missing):

| Getter | Path | Used by |
|---|---|---|
| `getAutomaticBackupsDirectory()` | `autobackup/` | `BackupCreateJob` |
| `getDownloadsDirectory()` | `downloads/` | `MangaDownloadProvider`, `AnimeDownloadProvider` |
| `getLocalMangaSourceDirectory()` | `local/` | `LocalMangaSource` (see [`../02-modules/source-local.md`](../02-modules/source-local.md)) |
| `getLocalAnimeSourceDirectory()` | `localanime/` | `LocalAnimeSource` |
| `getMPVConfigDirectory()` | `mpv-config/` | The anime player — fonts, Lua scripts, shaders |
| `getFontsDirectory()` | `mpv-config/fonts/` | MPV `--sub-fonts-dir` |
| `getScriptsDirectory()` | `mpv-config/scripts/` | MPV Lua scripts (incl. custom buttons) |
| `getScriptOptsDirectory()` | `mpv-config/script-opts/` | MPV Lua script opts |
| `getShadersDirectory()` | `mpv-config/shaders/` | MPV user shaders (`glsl` files) |

A `Channel<Unit>` `changes` flow lets other components (e.g. the download
caches) react when the user re-picks the storage location.

## Download layout

Both manga and anime downloads share the **same** `downloads/` root; the
per-entry structure is:

```
downloads/
└── <source-name>/                  ← DiskUtil.buildValidFilename(source.toString())
    └── <manga-or-anime-title>/      ← buildValidFilename(title)
        └── <chapter-or-episode>/    ← buildValidFilename(name) or "<scanlator>_<name>"
            ├── 0001-001.jpg
            ├── 0002-001.jpg
            └── ...
        └── <chapter-or-episode>.cbz   ← alternatively, a CBZ archive
```

`MangaDownloadProvider` (`data/download/manga/MangaDownloadProvider.kt`)
and `AnimeDownloadProvider` are the path-resolvers. They:

- Build source/manga/chapter dir names with `DiskUtil.buildValidFilename`
  (sanitises for FAT, replaces `" * / : < > ? \ |` with `_`, caps at 240
  chars).
- Probe **two** chapter-dir name spellings when looking up an existing
  download: the plain name and the `.cbz` archive variant — because older
  Aniyomi versions stored folders, newer ones store CBZs.
- Optionally prepend `<scanlator>_` to chapter names so multiple scanlations
  of the same chapter coexist.

A `.nomedia` file is dropped at the `downloads/` root to stop Android
gallery apps from indexing manga pages and anime stills.

## Disk cache — chapter pages

`ChapterCache` (`data/cache/ChapterCache.kt`) is a **`DiskLruCache`** (the
Jake Wharton `com.jakewharton.disklrucache` port) that stores two kinds of
entry, both keyed by `DiskUtil.hashKeyForDisk(...)` (MD5):

| Entry type | Key | Value |
|---|---|---|
| **Page list** for a chapter | md5(`"${chapter.mangaId}${chapter.url}"`) | JSON-encoded `List<Page>` |
| **Page image** for a chapter | md5(imageUrl) | The raw image bytes (OkHttp `Response.body`) |

```kotlin
private val diskCache = DiskLruCache.open(
    File(context.cacheDir, "chapter_disk_cache"),
    PARAMETER_APP_VERSION,   // 1
    PARAMETER_VALUE_COUNT,   // 1 value per key
    PARAMETER_CACHE_SIZE,    // 100 MiB
)
```

Key facts:

- Located at `<cacheDir>/chapter_disk_cache/` — i.e. **app-private cache**,
  OS-evictable, counted against the app's cache quota.
- **Hard cap of 100 MiB.** No user-visible setting; LRU eviction is
  automatic.
- `clear()` removes everything except the `journal` (the DiskLruCache
  internal metadata file).
- This cache is manga-only — anime episodes are streamed (or downloaded as
  files via the download manager, not paged through a disk cache).

## Cover caches

Manga and anime have **separate** cover caches, plus the anime side has a
background-image cache. All three follow the same pattern: covers are
keyed by MD5 of the thumbnail URL and stored in the **app's external files
dir** (`context.getExternalFilesDir(...)` — i.e. persistent, not
OS-evictable, scoped to the app — falling back to internal `filesDir` if
external is unavailable).

| Class | Dir | Keys |
|---|---|---|
| `MangaCoverCache` | `covers/` + `covers/custom/` | md5(thumbnailUrl); custom: md5(mangaId) |
| `AnimeCoverCache` | `animecovers/` + `animecovers/custom/` | md5(thumbnailUrl); custom: md5(animeId) |
| `AnimeBackgroundCache` | `animebackgrounds/` + `animebackgrounds/custom/` | md5(backgroundUrl); custom: md5(animeId) |

Each provides `getCoverFile(url)`, `getCustomCoverFile(id)`,
`setCustomCoverToCache(entry, stream)`, `deleteFromCache(entry,
deleteCustomCover=false)`. Custom covers override source-provided ones;
the `CoverCache` Cooper interceptor checks for a custom file first.

The anime-only `AnimeBackgroundCache` exists because anime entries carry a
separate `backgroundUrl` (used as the blurred backdrop on the anime
details screen) — there is no manga equivalent.

## Image saver — sharing/saving pages & covers

`data/saver/ImageSaver.kt` is the one-stop shop for persisting an image to
a user-visible location. It handles the Android 10+ MediaStore API and
the legacy `File` API transparently.

```kotlin
sealed class Image {
    data class Cover(bitmap: Bitmap, name, location) : Image
    data class Page(inputStream: () -> InputStream, name, location) : Image
}
sealed interface Location {
    data class Pictures(relativePath: String) : Location   // → MediaStore/Pictures/Aniyomi/...
    data object Cache : Location                           // → context.cacheImageDir ("shared_image/")
}
```

- On **Android ≥ Q** with `Location.Pictures`, it writes via `MediaStore`,
  querying first to overwrite an existing file with the same name.
- On older Android, or for `Location.Cache`, it writes a plain `File` and
  calls `DiskUtil.scanMedia()` so gallery apps pick it up.
- `Location.Cache` resolves to `Context.cacheImageDir` =
  `File(cacheDir, "shared_image")` — a tiny scratch dir used when sharing
  a single page via intent (the file needs to be readable by the share
  sheet's target app via the FileProvider).

The companion `SaveImageNotifier` posts the result.

## Data saver — image compression for metered connections

`app/src/main/java/aniyomi/util/DataSaver.kt` defines a small `DataSaver`
interface with a single method:

```kotlin
interface DataSaver {
    fun compress(imageUrl: String): String
    companion object {
        val NoOp = object : DataSaver { override fun compress(u: String) = u }
        suspend fun HttpSource.getImage(page: Page, dataSaver: DataSaver): Response
    }
}
```

The factory `DataSaver(source, preferences)` picks a concrete
implementation based on `sourcePreferences.dataSaver().get()`:

| Pref value | Class | How it compresses |
|---|---|---|
| `NONE` (default) | `DataSaver.NoOp` | No-op; URLs untouched. |
| `BANDWIDTH_HERO` | `BandwidthHeroDataSaver` | Rewrites the URL to `${userServer}/?jpg=...&l=quality&bw=...&url=<encoded>` — needs a self-hosted Bandwidth Hero proxy. |
| `WSRV_NL` | `WsrvNlDataSaver` | Rewrites the URL to `https://wsrv.nl/?url=<url>&output=jpg\|webp&q=quality` — uses the public wsrv.nl service. |
| `RESMUSH_IT` | `ReSmushItDataSaver` | Synchronously calls `http://api.resmush.it/ws.php?img=<url>&qlty=<q>`, parses JSON for the compressed URL, returns it. |

Each implementation honours two bypass prefs:

- `dataSaverIgnoreJpeg` — don't recompress JPEGs (already compressed).
- `dataSaverIgnoreGif` — don't recompress GIFs (would break animation).

And one source-level bypass:

- `dataSaverExcludedSources` — a set of source IDs for which data-saver is
  disabled (e.g. a source that already serves compressed images).

The `HttpSource.getImage(page, dataSaver)` extension is the integration
point: it temporarily swaps `page.imageUrl` for the compressed URL,
fetches, then restores the original — so the rest of the reader pipeline
sees the unmodified URL (e.g. for the chapter cache key). This is wired
into the page-fetcher; see [`manga-reader.md`](manga-reader.md).

> The data saver is **manga-only** in this snapshot. The anime player does
> not route video through a compression proxy — only still-image pages do.

## Where each type of data lives (summary)

| Data type | Location | Persistent? | How big? |
|---|---|---|---|
| Library DBs (`tachiyomi.db`, `tachiyomi_mi.db`) | `<databases>/` (app internal) | Yes | Few MB |
| SharedPreferences | `<shared_prefs>/` (app internal) | Yes | Few KB |
| Auto backups | `<baseDir>/autobackup/*.tachibk` (SAF) | Yes | Varies (rolled to 4) |
| Manual backups | user-chosen (SAF) | Yes | Varies |
| Manga downloads | `<baseDir>/downloads/<source>/<manga>/<chapter>/` (SAF) | Yes | Unbounded |
| Anime downloads | `<baseDir>/downloads/<source>/<anime>/<episode>/` (SAF) | Yes | Unbounded |
| Local source (manga) | `<baseDir>/local/` (SAF) | Yes | User-managed |
| Local source (anime) | `<baseDir>/localanime/` (SAF) | Yes | User-managed |
| MPV config / fonts / scripts / shaders | `<baseDir>/mpv-config/...` (SAF) | Yes | Small |
| Extension APKs | `/data/app/...` (system-managed) | Yes | Per extension |
| Chapter page disk cache | `<cacheDir>/chapter_disk_cache/` (DiskLruCache) | **No** (OS-evictable) | 100 MiB cap |
| Cover cache (manga) | `<externalFilesDir>/covers/` (and `covers/custom/`) | Yes | One file per library entry |
| Cover cache (anime) | `<externalFilesDir>/animecovers/` (+ `custom/`) | Yes | Same |
| Background cache (anime) | `<externalFilesDir>/animebackgrounds/` (+ `custom/`) | Yes | Same |
| Saved/shared image scratch | `<cacheDir>/shared_image/` | No | One file at a time |
| Crash logs / error logs | `<cacheDir>/` (e.g. `aniyomi_restore_error.txt`) | No | Small |
| App-update APK | `<externalCacheDir>/update.apk` | No | ~50 MiB, transient |

`<baseDir>` is the SAF-picked tree, defaulting to `Aniyomi/` on the
primary external storage. `<cacheDir>`, `<externalCacheDir>`, and
`<externalFilesDir>` are app-scoped paths returned by `Context`.

## The Storage tab

`ui/storage/` is a UI-only subsystem (no new on-disk state) that shows
the user how much space each manga / anime in their library is using for
downloads, and lets them delete entries' downloads from there.

- `StorageTab.kt` — a 2-tab pager (anime, manga).
- `CommonStorageScreenModel<T>` — abstract base combining
  `downloadCache.changes`, `downloadCache.isInitializing`, the library
  Flow, the categories Flow, and the selected category into a sorted list
  of `StorageItem`s (id, title, size, count, thumbnail, deterministic
  color).
- `AnimeStorageScreenModel` / `MangaStorageScreenModel` — concrete
  subclasses that supply the manga- or anime-specific getters
  (`getDownloadSize`, `getDownloadCount`, etc.) and implement
  `deleteEntry(id)` by delegating to the relevant download manager.

The size/count come from the in-memory `AnimeDownloadCache` /
`MangaDownloadCache`, which periodically walks the `downloads/` tree (see
[`download-manager.md`](download-manager.md)).

## Key files

| File | Role |
|---|---|
| `../ANIYOMI/domain/src/main/java/tachiyomi/domain/storage/service/StorageManager.kt` | The directory registry; owns `baseDir`, creates well-known subdirs, exposes getters. |
| `../ANIYOMI/domain/src/main/java/tachiyomi/domain/storage/service/StoragePreferences.kt` | `baseStorageDirectory()` preference (SAF URI string). |
| `../ANIYOMI/core/common/src/main/java/tachiyomi/core/common/storage/FolderProvider.kt` | Interface for "default storage path". |
| `../ANIYOMI/core/common/src/main/java/tachiyomi/core/common/storage/AndroidStorageFolderProvider.kt` | Default impl: `Environment.getExternalStorageDirectory()/Aniyomi`. |
| `../ANIYOMI/core/common/src/main/java/tachiyomi/core/common/storage/UniFileExtensions.kt` | `extension`, `nameWithoutExtension`, `displayablePath` on `UniFile`. |
| `../ANIYOMI/core/common/src/main/java/eu/kanade/tachiyomi/util/storage/DiskUtil.kt` | `hashKeyForDisk` (MD5), `buildValidFilename`, `createNoMediaFile`, `scanMedia`, storage-space helpers. |
| `../ANIYOMI/core/common/src/main/java/eu/kanade/tachiyomi/util/storage/FileExtensions.kt` | `Context.cacheImageDir`, `File.getUriCompat` (FileProvider), `copyAndSetReadOnlyTo`. |
| `../ANIYOMI/app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsDataScreen.kt` | SAF picker (`OpenDocumentTree`), storage-location display, backup/restore entry points. |
| `../ANIYOMI/app/src/main/java/eu/kanade/presentation/more/settings/screen/data/StorageInfo.kt` | Per-volume free-space widget (uses `DiskUtil.getExternalStorages`). |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/cache/ChapterCache.kt` | 100 MiB DiskLruCache for page lists + page images. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/cache/MangaCoverCache.kt` | Manga cover + custom-cover store. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/cache/AnimeCoverCache.kt` | Anime cover + custom-cover store. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/cache/AnimeBackgroundCache.kt` | Anime-only background-image cache. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/saver/ImageSaver.kt` | `Image` / `Location` sealed hierarchies + MediaStore/File save logic. |
| `../ANIYOMI/app/src/main/java/aniyomi/util/DataSaver.kt` | `DataSaver` interface + `BandwidthHero` / `WsrvNl` / `ReSmushIt` implementations + `HttpSource.getImage(page, dataSaver)`. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/download/manga/MangaDownloadProvider.kt` | Path resolver for manga downloads. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/download/anime/AnimeDownloadProvider.kt` | Path resolver for anime downloads. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/storage/StorageTab.kt` | The Storage tab. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/storage/CommonStorageScreenModel.kt` | Per-library download-size screen model. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/storage/{manga,anime}/...` | Concrete `*StorageScreenModel` + tab composables. |

## See also

- [`backup-restore.md`](backup-restore.md) — backups live in `autobackup/`; the SAF picker is the same one used here.
- [`download-manager.md`](download-manager.md) — how the `downloads/` tree is written and walked.
- [`manga-reader.md`](manga-reader.md) — the chapter page cache (`ChapterCache`) and data-saver hook.
- [`../01-architecture/05-preferences-system.md`](../01-architecture/05-preferences-system.md) — `StoragePreferences`, `BackupPreferences`.
- [`../02-modules/core-common.md`](../02-modules/core-common.md) — `FolderProvider`, `DiskUtil`, `UniFileExtensions` are in `:core:common`.
- [`../02-modules/source-local.md`](../02-modules/source-local.md) — the `local/` and `localanime/` directories.

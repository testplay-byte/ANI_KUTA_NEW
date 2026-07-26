# 02-modules / core-archive — The `:core:archive` module

> Archive reading (CBZ / CBR / ZIP / RAR / 7z / tar) and EPUB parsing via
> libarchive. Tiny, self-contained module — no project dependencies — that
> gives the manga reader and the local source a uniform `ArchiveReader` API
> over arbitrary archive formats.

## Purpose

`/home/z/.../ANIYOMI/core/archive/` exists because Android's built-in `ZipFile`
only handles ZIP and is not happy with very large files or with non-UTF-8 entry
names. Aniyomi needs to read CBZ (ZIP), CBR (RAR), CB7 (7z), CBT (tar), and
EPUB (ZIP-with-XML) archives — and the libarchive native library handles all of
those with a single API. This module is the Kotlin/Java wrapper around
`me.zhanghai.android.libarchive`.

The module is a plain Android library (not KMP). It exposes:

- `ArchiveReader` — random-access read of an archive `mmap`'d from a
  `ParcelFileDescriptor`.
- `EpubReader` — EPUB-specific layer on top of `ArchiveReader` that understands
  `META-INF/container.xml`, the OPF package document, and the spine → page →
  image resolution chain.
- `ZipWriter` — writes a plain (uncompressed-store) ZIP via libarchive, used to
  re-package chapter images for export / backup.
- `UniFileExtensions.kt` — convenience functions `UniFile.archiveReader(context)`
  and `UniFile.epubReader(context)` so callers don't have to open a
  `ParcelFileDescriptor` themselves.

## Build configuration

Source: `../ANIYOMI/core/archive/build.gradle.kts`.

```kotlin
plugins {
    id("mihon.library")
    kotlin("android")
    kotlin("plugin.serialization")
}

android { namespace = "mihon.core.archive" }

dependencies {
    implementation(libs.jsoup)
    implementation(libs.libarchive)
    implementation(libs.unifile)
}
```

Three things to note:

- **No project dependencies.** This module is a leaf — it depends only on
  Jsoup (for EPUB OPF parsing), the libarchive JNI wrapper, and Unifile (for
  `UniFile`). That keeps it reusable and prevents the archive-reading layer
  from accidentally pulling in app concerns.
- The `kotlin("plugin.serialization")` plugin is applied but no `@Serializable`
  classes live here — it's there for future-proofing and parity with the other
  `mihon.*` modules.
- Namespace is `mihon.core.archive` — the module is inherited from Mihon and
  keeps that package name even though Aniyomi is the only consumer.

## Module layout

```
core/archive/src/main/
├── AndroidManifest.xml                    ← empty
└── kotlin/mihon/core/archive/
    ├── ArchiveEntry.kt                    ← data class (name, isFile)
    ├── ArchiveInputStream.kt              ← internal InputStream over libarchive
    ├── ArchiveReader.kt                   ← public Closeable reader
    ├── EpubReader.kt                      ← EPUB layer on top of ArchiveReader
    ├── UniFileExtensions.kt               ← UniFile.archiveReader / epubReader
    └── ZipWriter.kt                       ← ZIP writer via libarchive
```

## The archive abstraction

### `ArchiveEntry`

```kotlin
class ArchiveEntry(val name: String, val isFile: Boolean)
```

A tiny data class. `isFile` is `true` for regular files (`AE_IFREG` in
libarchive terms) and `false` for directories / symlinks / other entry types.
The reader filters out non-file entries when callers ask for the file list.

### `ArchiveInputStream` (internal)

`internal class ArchiveInputStream(buffer: Long, size: Long) : InputStream`

Wraps a libarchive `Archive` handle opened against an in-memory buffer. The
`init` block calls:

```kotlin
Archive.setCharset(archive, Charsets.UTF_8.name().toByteArray())
Archive.readSupportFilterAll(archive)   // enable all decompression filters
Archive.readSupportFormatAll(archive)   // enable all container formats
Archive.readOpenMemoryUnsafe(archive, buffer, size)
```

So a single stream can transparently read any libarchive-supported format
( zip / 7z / rar / tar / … ) with any compression (deflate, brotli, lzma, …).
`getNextEntry()` returns the next `ArchiveEntry` or `null` at end-of-archive;
`read()` reads bytes from the current entry's data.

### `ArchiveReader`

```kotlin
class ArchiveReader(pfd: ParcelFileDescriptor) : Closeable {
    private val size = pfd.statSize
    private val address = Os.mmap(0, size, PROT_READ, MAP_PRIVATE, pfd.fileDescriptor, 0)

    fun <T> useEntries(block: (Sequence<ArchiveEntry>) -> T): T
    fun getInputStream(entryName: String): InputStream?
    override fun close() { Os.munmap(address, size) }
}
```

Key design points:

- The archive is **`mmap`'d read-only** from the `ParcelFileDescriptor`. This
  avoids a full file copy into the JVM heap and lets libarchive seek freely
  inside the mapped region.
- `useEntries { sequence -> ... }` exposes the entry list as a Kotlin
  `Sequence<ArchiveEntry>` — lazy and auto-closed. Useful when you only need to
  scan the table of contents (e.g. to find the cover image).
- `getInputStream(entryName)` walks the archive from the start, returns an
  `InputStream` positioned at the matching entry, or `null` if no such entry
  exists. **It does not seek** — libarchive is a streaming reader, so getting
  file N requires walking past files 0..N-1. Callers that need many files
  should prefer `useEntries` + a single pass.
- `close()` unmaps the region. Forgetting to close leaks the mapping; the
  `Closeable` contract lets callers use `use { … }`.

### `UniFileExtensions.kt`

```kotlin
fun UniFile.openFileDescriptor(context: Context, mode: String): ParcelFileDescriptor
fun UniFile.archiveReader(context: Context): ArchiveReader   // open + mmap, returns Closeable
fun UniFile.epubReader(context: Context): EpubReader
```

These are the entry points used by `:source-local` (for CBZ/CBR/EPUB chapters)
and by `:app`'s manga reader (for downloaded chapter archives). They handle
the `ContentResolver.openFileDescriptor(uri, "r")` dance and `use { … }` the
pfd so the caller only sees the high-level `ArchiveReader` / `EpubReader`.

## The EPUB layer — `EpubReader`

`class EpubReader(private val reader: ArchiveReader) : Closeable by reader`

EPUB is a ZIP with a well-defined structure: `META-INF/container.xml` points
to the OPF package document, which lists every manifest item and the spine
(reading order) of XHTML pages; each page can reference images. `EpubReader`
walks that structure for you:

- `getPackageHref()` — reads `META-INF/container.xml`, returns the path to the
  OPF file. Falls back to `OEBPS/content.opf` for older EPUBs.
- `getPackageDocument(ref)` — Jsoup-parsed OPF document.
- `getImagesFromPages()` — resolves the spine → page XHTML files → `<img src=…>`
  and `<image xlink:href=…>` references. Returns the list of archive entry
  paths that the reader should display, in reading order.
- `getInputStream(entryName)` — passes through to the underlying `ArchiveReader`.
- `getPathSeparator()` — EPUBs occasionally use `\` instead of `/` (e.g. ones
  produced on Windows). The reader detects this by probing for
  `META-INF\\container.xml` and adapts path resolution accordingly.

`:source-local` uses `EpubReader` in two ways:

1. `EpubReaderExtensions.fillMetadata(manga, chapter)` (in `:source-local`)
   pulls Dublin Core metadata (`dc:title`, `dc:creator`, `dc:description`,
   `dc:publisher`, `dc:date`) out of the OPF.
2. The manga reader uses `getImagesFromPages()` as the page list when reading
   an EPUB chapter.

## Writing archives — `ZipWriter`

`class ZipWriter(context: Context, file: UniFile) : Closeable`

A thin wrapper around libarchive's write API. The constructor sets up an
`Archive` for the ZIP format with **store** compression (no compression —
images are already compressed, so re-compressing just wastes CPU):

```kotlin
Archive.setCharset(archive, UTF_8)
Archive.writeSetFormatZip(archive)
Archive.writeZipSetCompressionStore(archive)
Archive.writeOpenFd(archive, pfd.fd)
```

`write(file: UniFile)` appends one entry to the archive — it `fstat`s the
source file, sets the entry's pathname and stat metadata, writes the header,
streams the bytes via `Os.read(fd, buffer)` + `Archive.writeData`, and finishes
the entry. `close()` frees the archive handle and closes the pfd.

This is used by the backup / export pipelines in `:app` when packaging chapter
images into a CBZ for the user.

## How the reader uses it

The manga reader (`:app/ui/reader/`) has a `loader/` subpackage with one
loader per chapter source. For local-archive chapters the loader is
`ArchivePageLoader` (in `:app`), which holds an `ArchiveReader` opened via
`UniFile.archiveReader(context)` and produces a `List<Page>` by:

1. Calling `reader.useEntries { entries -> entries.filter { it.isFile }
   .sortedBy { it.name.compareToCaseInsensitiveNaturalOrder() } }` to get the
   page list in reading order.
2. Mapping each entry name to a `Page(index, url = entryName)` whose
   `imageUrl` is a `content://` URI served by a small in-app `FileProvider` /
   `PageLoader` that calls `reader.getInputStream(entryName)` on demand.

The reader viewer (paged or continuous) then loads each page's stream through
`tachiyomi.core.common.util.system.ImageUtil` for decoding.

## Key files table

| File | Purpose |
|---|---|
| `../ANIYOMI/core/archive/build.gradle.kts` | Android library, namespace `mihon.core.archive`. Deps: jsoup, libarchive, unifile. No project deps. |
| `../ANIYOMI/core/archive/src/main/AndroidManifest.xml` | Empty. |
| `../ANIYOMI/core/archive/src/main/kotlin/mihon/core/archive/ArchiveEntry.kt` | `data class ArchiveEntry(name, isFile)`. |
| `../ANIYOMI/core/archive/src/main/kotlin/mihon/core/archive/ArchiveInputStream.kt` | Internal `InputStream` over a libarchive `Archive` opened against an in-memory buffer. |
| `../ANIYOMI/core/archive/src/main/kotlin/mihon/core/archive/ArchiveReader.kt` | Public `Closeable` reader: `mmap`s a `ParcelFileDescriptor`, exposes `useEntries` and `getInputStream(entryName)`. |
| `../ANIYOMI/core/archive/src/main/kotlin/mihon/core/archive/EpubReader.kt` | EPUB layer: container.xml → OPF → spine → page → image resolution. |
| `../ANIYOMI/core/archive/src/main/kotlin/mihon/core/archive/ZipWriter.kt` | Writes a store-compressed ZIP via libarchive. |
| `../ANIYOMI/core/archive/src/main/kotlin/mihon/core/archive/UniFileExtensions.kt` | `UniFile.archiveReader(context)`, `UniFile.epubReader(context)`, `openFileDescriptor(context, mode)`. |

## See also

- [`../03-subsystems/manga-reader.md`](../03-subsystems/manga-reader.md) — the
  reader loader pipeline that consumes `ArchiveReader` for CBZ/CBR/EPUB
  chapters.
- [`source-local.md`](source-local.md) — uses `ArchiveReader` /
  `EpubReader` for local manga chapters, and `EpubReader.fillMetadata` for
  EPUB metadata.
- [`core-common.md`](core-common.md) — `ImageUtil` is what decodes the bytes
  this module returns.
- [`../03-subsystems/backup-restore.md`](../03-subsystems/backup-restore.md) —
  uses `ZipWriter` to package chapter images for export.

# 02-modules / core-metadata — The `:core-metadata` module

> Schema definitions for the metadata files Aniyomi reads (and writes) for
> local manga and anime: the `ComicInfo.xml` standard (v2.0) for manga, and
> the legacy Tachiyomi/Aniyomi JSON details format for both manga and anime.

## Purpose

`/home/z/.../ANIYOMI/core-metadata/` contains the data classes and conversion
helpers for the on-disk metadata files that accompany a local library entry.
This is the module that knows:

- What a `ComicInfo.xml` looks like (the [Anansi Project v2.0 schema](https://anansi-project.github.io/docs/comicinfo/schemas/v2.0)),
  how to deserialise it with XML util, and how to map its fields to / from a
  manga `SManga`.
- What a `details.json` / `chapters.json` / `episodes.json` sidecar looks like
  (the older, Tachiyomi-specific JSON format used before `ComicInfo.xml`
  support was added and the only format supported for anime).

It is consumed primarily by [`:source-local`](source-local.md), which uses
these classes when reading a local manga's `ComicInfo.xml` or writing one
during the legacy-JSON → ComicInfo migration, and when reading the `details.json`
/ `episodes.json` sidecars for local anime.

## Build configuration

Source: `../ANIYOMI/core-metadata/build.gradle.kts`.

```kotlin
plugins {
    id("mihon.library")
    kotlin("android")
    kotlin("plugin.serialization")
}

android {
    namespace = "tachiyomi.core.metadata"
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    implementation(projects.sourceApi)             // for SManga (status constants, etc.)
    implementation(kotlinx.bundles.serialization)   // kotlinx-serialization-json + xml util
}
```

Notes:

- Depends on `:source-api` so it can reference `SManga`'s status constants
  (`UNKNOWN`/`ONGOING`/`COMPLETED`/`LICENSED`/`PUBLISHING_FINISHED`/`CANCELLED`/
  `ON_HIATUS`) when mapping to and from `ComicInfo`.
- Uses **kotlinx-serialization** for the JSON sidecar format and **XML util**
  (`nl.adaptivity.xmlutil`) for `ComicInfo.xml`.
- Plain Android library, namespace `tachiyomi.core.metadata`. No DI, no
  network — just data classes and small conversion helpers.
- Ships `consumer-rules.pro` and `proguard-rules.pro` so the serialisation
  adapters are kept by R8 in release builds.

## Module layout

```
core-metadata/src/main/
├── AndroidManifest.xml                                  ← empty
└── java/tachiyomi/core/metadata/
    ├── comicinfo/
    │   └── ComicInfo.kt                                 ← ComicInfo.xml schema + helpers + publishing-status enum
    └── tachiyomi/
        ├── MangaDetails.kt                              ← @Serializable legacy JSON: manga
        ├── AnimeDetails.kt                              ← @Serializable legacy JSON: anime
        ├── ChapterDetails.kt                            ← @Serializable legacy JSON: chapter
        └── EpisodeDetails.kt                            ← @Serializable legacy JSON: episode
```

Two packages, two formats:

| Package | Format | Used by |
|---|---|---|
| `tachiyomi.core.metadata.comicinfo` | XML (`ComicInfo.xml`, [v2.0 schema](https://anansi-project.github.io/docs/comicinfo/schemas/v2.0)) | Manga only. Lives inside a chapter archive or at the top of a manga directory. |
| `tachiyomi.core.metadata.tachiyomi` | JSON sidecar (`details.json` / `chapters.json` / `episodes.json`) | Both manga (legacy, migrated to ComicInfo) and anime (only format available). |

## The `comicinfo/` package

### `ComicInfo` data class

`@Serializable @XmlSerialName("ComicInfo", "", "") data class ComicInfo(...)`
— the root element of a `ComicInfo.xml` file. Fields are nullable
`@XmlValue`-wrapped inner classes so missing elements round-trip cleanly:

| Field | XML element | Maps to `SManga` field |
|---|---|---|
| `title` | `<Title>` | (not mapped; chapter-level only) |
| `series` | `<Series>` | `title` |
| `number` | `<Number>` | (chapter number, chapter-level) |
| `summary` | `<Summary>` | `description` |
| `writer` | `<Writer>` | `author` |
| `penciller` | `<Penciller>` | `artist` (combined with other art-role fields) |
| `inker`, `colorist`, `letterer`, `coverArtist`, `translator` | same names | `artist` (merged, de-duplicated, comma-joined) |
| `genre` | `<Genre>` | `genre` (merged with `tags` + `categories`) |
| `tags` | `<Tags>` | `genre` |
| `web` | `<Web>` | (not mapped) |
| `publishingStatus` | `<PublishingStatusTachiyomi>` (Tachiyomi extension namespace `ty:`) | `status` |
| `categories` | `<Categories>` (Aniyomi extension namespace `ty:`) | `genre` |
| `source` | `<SourceAniyomi>` (Aniyomi extension namespace `ay:`) | (not mapped) |

The two `xmlns:xsd` / `xmlns:xsi` attributes are hardcoded into the data class
as `@XmlElement(false)` fields so the serialised XML matches the schema.

### `ComicInfoPublishingStatus` enum

```kotlin
enum class ComicInfoPublishingStatus(val comicInfoValue: String, val sMangaModelValue: Int) {
    ONGOING("Ongoing", SManga.ONGOING),
    COMPLETED("Completed", SManga.COMPLETED),
    LICENSED("Licensed", SManga.LICENSED),
    PUBLISHING_FINISHED("Publishing finished", SManga.PUBLISHING_FINISHED),
    CANCELLED("Cancelled", SManga.CANCELLED),
    ON_HIATUS("On hiatus", SManga.ON_HIATUS),
    UNKNOWN("Unknown", SManga.UNKNOWN);

    companion object {
        fun toComicInfoValue(value: Long): String
        fun toSMangaValue(value: String?): Int
    }
}
```

Bridges Aniyomi's integer status codes (defined on `SManga` in `:source-api`)
to the human-readable strings used in `ComicInfo.xml`.

### Conversion helpers

Two top-level extension functions on `SManga`:

- `SManga.getComicInfo(): ComicInfo` — produces a `ComicInfo` from the manga's
  current fields (used when writing a fresh `ComicInfo.xml`).
- `SManga.copyFromComicInfo(comicInfo: ComicInfo)` — applies a `ComicInfo`'s
  fields onto an existing `SManga` (used when reading a `ComicInfo.xml`).
  Genre is the comma-joined distinct union of `genre` + `tags` + `categories`;
  artist is the comma-joined distinct union of all art-role fields.

### `COMIC_INFO_FILE` constant

`const val COMIC_INFO_FILE = "ComicInfo.xml"` — the canonical filename, used
by `:source-local` to look for the file in a manga directory or inside a
chapter archive.

## The `tachiyomi/` package (legacy JSON sidecars)

Four small `@Serializable` classes used for the older Tachiyomi-specific JSON
format. The local source looks for these as `details.json`, `chapters.json`,
or `episodes.json` inside a manga / anime directory:

### `MangaDetails`

```kotlin
@Serializable
class MangaDetails(
    val title: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val genre: List<String>? = null,
    val status: Int? = null,
)
```

Read from `<mangaDir>/details.json`. `:source-local` reads this **and then
migrates it** to `ComicInfo.xml` (deleting the JSON file) so that future loads
use the standard format. The migration uses `SManga.getComicInfo()` from this
module.

### `AnimeDetails`

```kotlin
@Serializable
class AnimeDetails(
    val title: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val genre: List<String>? = null,
    val status: Int? = null,
)
```

Structurally identical to `MangaDetails` (no `ComicInfo.xml` equivalent for
anime exists). Read from `<animeDir>/details.json`. **No migration** — anime
keeps using the JSON format.

### `ChapterDetails`

```kotlin
@Serializable
class ChapterDetails(
    val chapter_number: Float,
    val name: String? = null,
    val date_upload: String? = null,     // ISO-8601 "yyyy-MM-dd'T'HH:mm:ss"
    val scanlator: String? = null,
)
```

Read from `<mangaDir>/chapters.json` (a `List<ChapterDetails>`). The local
source matches each entry to a detected chapter file by `chapter_number`
(within 0.0001 tolerance) and overrides `name` / `date_upload` / `scanlator`.
Date strings are parsed with `SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())`.

### `EpisodeDetails`

```kotlin
@Serializable
class EpisodeDetails(
    val episode_number: Float,
    val name: String? = null,
    val date_upload: String? = null,
    val fillermark: Boolean = false,
    val scanlator: String? = null,
    val summary: String? = null,
    val preview_url: String? = null,
)
```

Read from `<animeDir>/episodes.json` (a `List<EpisodeDetails>`). Matches by
`episode_number` and overrides the corresponding `SEpisode` fields. The
`fillermark`, `summary`, and `preview_url` fields exist on `SEpisode` (in
`:source-api`) but are anime-only — they have no equivalent in `ChapterDetails`.

## How it fits into the data flow

```
Local manga directory
├── cover.jpg
├── ComicInfo.xml ──── parsed by ──▶ ComicInfo (XML util)
│                                     │
│                                     ▼ SManga.copyFromComicInfo(...)
│                                   SManga { title, author, artist, description, genre, status }
│
├── details.json ───── (legacy) ──▶ MangaDetails (kotlinx.serialization)
│                                     │
│                                     ▼ manual field copy + SManga.getComicInfo()
│                                   (writes a fresh ComicInfo.xml, deletes details.json)
│
├── chapters.json ──── (optional) ─▶ List<ChapterDetails>
│                                     │
│                                     ▼ merge by chapter_number
│                                   List<SChapter> with overridden name/date/scanlator
│
└── Chapter01.cbz
    └── ComicInfo.xml ─ (per-chapter, copied to top level if no top-level file exists)

Local anime directory
├── cover.jpg
├── background.jpg
├── details.json ────── parsed by ──▶ AnimeDetails
│                                     │
│                                     ▼ manual field copy
│                                   SAnime { title, author, artist, description, genre, status }
│
├── episodes.json ──── (optional) ─▶ List<EpisodeDetails>
│                                     │
│                                     ▼ merge by episode_number
│                                   List<SEpisode> with overridden name/date/scanlator/summary/preview_url/fillermark
│
├── Season 01/
│   ├── 01.mp4 → SEpisode
│   ├── 02.mp4 → SEpisode
│   └── thumbnail.jpg
└── Season 02/
    └── 01.mp4 → SEpisode
```

The migration path (legacy JSON → ComicInfo.xml) only exists for manga. For
anime, the JSON sidecar is the canonical format. There is no plan to add a
ComicInfo.xml equivalent for anime — that standard is manga-specific.

## Key files table

| File | Purpose |
|---|---|
| `../ANIYOMI/core-metadata/build.gradle.kts` | Android library, namespace `tachiyomi.core.metadata`. Deps: `:source-api`, serialization bundle. |
| `../ANIYOMI/core-metadata/src/main/AndroidManifest.xml` | Empty. |
| `../ANIYOMI/core-metadata/consumer-rules.pro` | R8 keep rules for the serialisation adapters. |
| `../ANIYOMI/core-metadata/proguard-rules.pro` | R8 rules for this module. |
| `../ANIYOMI/core-metadata/src/main/java/tachiyomi/core/metadata/comicinfo/ComicInfo.kt` | `ComicInfo` data class (XML v2.0 schema), `ComicInfoPublishingStatus` enum, `getComicInfo` / `copyFromComicInfo` SManga extensions, `COMIC_INFO_FILE` constant. |
| `../ANIYOMI/core-metadata/src/main/java/tachiyomi/core/metadata/tachiyomi/MangaDetails.kt` | `@Serializable` legacy JSON manga-details model. |
| `../ANIYOMI/core-metadata/src/main/java/tachiyomi/core/metadata/tachiyomi/AnimeDetails.kt` | `@Serializable` legacy JSON anime-details model. |
| `../ANIYOMI/core-metadata/src/main/java/tachiyomi/core/metadata/tachiyomi/ChapterDetails.kt` | `@Serializable` per-chapter JSON override model. |
| `../ANIYOMI/core-metadata/src/main/java/tachiyomi/core/metadata/tachiyomi/EpisodeDetails.kt` | `@Serializable` per-episode JSON override model (anime-only fields: `fillermark`, `summary`, `preview_url`). |

## See also

- [`source-local.md`](source-local.md) — the primary consumer. Reads
  `ComicInfo.xml` via `copyFromComicInfo`, migrates `details.json` →
  `ComicInfo.xml` via `getComicInfo`, and merges `chapters.json` /
  `episodes.json` overrides.
- [`source-api.md`](source-api.md) — defines `SManga`, `SChapter`, `SAnime`,
  `SEpisode` (including the `status` constants this module bridges to
  ComicInfo).
- [`../03-subsystems/source-system.md`](../03-subsystems/source-system.md) —
  how the local source's metadata reading slots into the broader source
  dispatch.
- [`../03-subsystems/storage-and-cache.md`](../03-subsystems/storage-and-cache.md)
  — where `ComicInfo.xml` and the JSON sidecars live on disk.
- [`../04-data-models/domain-models.md`](../04-data-models/domain-models.md) —
  the domain-side `Manga` / `Anime` / `Chapter` / `Episode` models that the
  local source's `SManga` / `SAnime` / `SChapter` / `SEpisode` are mapped into
  after fetch.

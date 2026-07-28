# OLD ANIKUTA — Design-Language Analysis: Details / Episode List / Video Resolver

**Task ID:** D-2
**Scope:** Three screens the owner explicitly flagged as design references for
the new ANIKUTA project (per ADR-015 — Custom M3-inspired design language).
This file feeds `DESIGN_LANGUAGE/` for the new project.

The three screens, all read-only study targets under
`OLD_ANIKUTA/ANIKUTA_OLD/app/src/main/java/app/anikuta/`:

1. **Anime details screen** — the "quite beautiful" screen with blurred cover
   + gradient + AniList metadata fetching.
2. **Episode list** — the "proper example of design preferences" with B&W +
   blur for watched episodes.
3. **Video resolver (server / resolution / audio picker)** — the "complete
   module" that resolves a video before opening the player.

Each section below documents: file path, layout structure (ASCII diagram),
the owner-flagged design details, the metadata/resolution data flow, code
snippets of the key composables, "what to keep" and "what to improve."

---

## 0. Cross-cutting context (read first)

All three screens live in the `app.anikuta.ui.detail` package (and a couple
of helper files in `app.anikuta.player.controls` and `app.anikuta.data.*`).
Key files:

| Concern | File |
|---|---|
| Detail screen entry | `ui/detail/DetailScreen.kt` (973 lines) |
| Detail state / data flow | `ui/detail/DetailViewModel.kt` (1331 lines) |
| Dynamic color scheme | `ui/detail/DynamicTheming.kt` (200 lines) |
| 3-stage pull-to-refresh | `ui/detail/ThreeStagePullRefresh.kt` (180 lines) |
| Episode row container (swipe/click/grayscale wrapper) | `ui/detail/components/EpisodeRow.kt` (330 lines) |
| Episode row inner content (rich/simple layout) | `ui/detail/components/EpisodeRowContent.kt` (595 lines) |
| Watched-episode visual effect (B&W + blur) | `ui/detail/components/Grayscale.kt` (137 lines) |
| Audio pills (SUB/DUB/HSUB) | `ui/detail/components/AudioPills.kt` (89 lines) |
| Airing pill (countdown) | `ui/detail/components/AiringPill.kt` (81 lines) |
| Date / HTML formatters | `ui/detail/components/DetailFormatters.kt` (69 lines) |
| Download button (state-aware) | `ui/detail/components/DownloadButton.kt` (183 lines) |
| Long-press options sheet | `ui/detail/components/EpisodeOptionsSheet.kt` (207 lines) |
| Episode title parser | `ui/detail/EpisodeTitleParser.kt` (65 lines) |
| Video resolver bottom sheet | `ui/detail/VideoPickerSheet.kt` (362 lines) |
| Video title parser (server/audio/quality) | `ui/detail/VideoTitleParser.kt` (146 lines) |
| Episode metadata enrichment | `data/metadata/EpisodeMetadataFetcher.kt` (458 lines) |
| AniList GraphQL client | `data/anilist/repository/AniListRepository.kt` |
| AniList GraphQL queries | `data/anilist/api/AniListQueries.kt` |
| AniList data models | `data/anilist/model/AniListModels.kt` |
| 3-tier cache (Local → Supabase → AniList) | `data/cache/CacheManager.kt` |
| Watched-episode store | `data/cache/EpisodeSeenStore.kt` |

All paths are relative to:
`/home/z/ani_kuta_workspace/ANI_KUTA_NEW/OLD_ANIKUTA/ANIKUTA_OLD/app/src/main/java/app/anikuta/`

---

## 1. Anime Details Screen (`DetailScreen.kt`)

### 1.1 File paths

- **Main file:** `ui/detail/DetailScreen.kt` (973 lines, 1 top-level composable
  `DetailScreen` + 2 private helpers `DetailHeader` and `InfoRow` / `InfoCard`)
- **ViewModel:** `ui/detail/DetailViewModel.kt` (1331 lines)
- **Dynamic theming:** `ui/detail/DynamicTheming.kt` (200 lines)
- **Pull-to-refresh:** `ui/detail/ThreeStagePullRefresh.kt` (180 lines)

The screen is entered via `DetailScreen(anilistId: Int, autoPlayUrl: String,
onBack: () -> Unit)`. It is **not** a Voyager `Screen` — the old project
uses Compose-Navigation-style composable functions (`AnikutaNavGraph.kt`).

### 1.2 Layout structure (top → bottom)

```
 ┌──────────────────────────────────────────────────────────────┐
 │  ◀ Back   🔖 Save   ↗ Share   ⋮ More          (over banner)  │ ← statusBarsPadding, top row
 │                                                                │
 │                                                                │
 │              [BLURRED COVER IMAGE, 8dp blur]                   │ ← AsyncImage, fillMaxSize, 360dp tall
 │              [theme-color tint, 20% alpha]                     │ ← coverColor.copy(alpha=0.2f)
 │              [vertical gradient overlay]                       │ ← Brush.verticalGradient(black20% → transparent → background)
 │                                                                │
 │                                                                │
 │   ┌──────────┐   Title (titleLarge, bold)                     │ ← Bottom-aligned overlay (100×150dp cover thumbnail + meta)
 │   │  cover   │   ★ score · status · N eps                     │
 │   │ thumbnail│   [Ep 1016 in 2d 5h]  ← AiringPill             │
 │   └──────────┘                                                  │
 └──────────────────────────────────────────────────────────────┘
 ┌──────────────────────────────────────────────────────────────┐
 │  [Genre chip] [Genre chip] [Genre chip] …    (LazyRow)        │ ← AssistChip, 8dp gap
 ├──────────────────────────────────────────────────────────────┤
 │  Synopsis                                                     │
 │  <description, 4-line collapsed / Show more>                  │ ← cleanHtmlTags() strips AniList HTML
 ├──────────────────────────────────────────────────────────────┤
 │  Episodes  [⟳ Fetching metadata…]    N episodes   [Source]   │ ← header row with enrichment indicator
 │  ┌────────────────────────────────────────────────────────┐  │
 │  │ EpisodeRow #1 (alternating surfaceContainerLow bg)      │  │
 │  │ EpisodeRow #2 (alternating surfaceContainerHigh bg)     │  │
 │  │ EpisodeRow #3                                            │  │
 │  │ …                                                        │  │
 │  └────────────────────────────────────────────────────────┘  │
 ├──────────────────────────────────────────────────────────────┤
 │  Information                                                  │ ← position configurable: "above" or "below" episodes
 │   Format     TV                                               │
 │   Status     RELEASING                                        │
 │   Season     WINTER 2025                                      │
 │   Episodes   12                                               │
 │   Score      78 / 100                                         │
 └──────────────────────────────────────────────────────────────┘

 Pull-to-refresh overlay (3-stage):
   stage 1 (100dp pull) → "Release to refresh episodes"
   stage 2 (200dp pull) → "Release to refresh details"
   stage 3 (300dp pull) → "Release to refresh everything"
```

Two layout modes are switchable via the `animeInfoPosition` preference:
- `"above"` (default for power users) — episodes render as direct
  `LazyColumn` items so the whole page scrolls as one long list.
- `"below"` (default) — episodes live in an inner `LazyColumn` capped at
  `heightIn(max = 600.dp)` so the Information section below stays
  reachable without scrolling through the entire episode list.

### 1.3 The "quite beautiful" header — blurred cover + gradient + dynamic theming

The header lives in `DetailHeader` (`DetailScreen.kt:755-946`). The owner's
three explicit design asks — (a) blurred cover at top, (b) gradient
darkening overlay, (c) the same screen hosts metadata fetching — all live
here. Verbatim layout:

```kotlin
// DetailScreen.kt:766-802 — the blurred-cover banner
Box(modifier = Modifier.fillMaxWidth()) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp),  // Taller to truly cover behind status bar
    ) {
        // 1) BLURRED COVER IMAGE (the owner-flagged effect)
        AsyncImage(
            model = anime.coverImage.extraLarge ?: anime.coverImage.best(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .blur(radius = 8.dp),  // Very subtle blur — just enough to make text readable
            contentScale = ContentScale.Crop,
        )

        // 2) THEME COLOR TINT (very subtle, derived from AniList cover color)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(coverColor.copy(alpha = 0.2f)),
        )

        // 3) GRADIENT OVERLAY (the owner-flagged effect)
        // Vertical: black(20%) at top → transparent in middle → background at bottom
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.2f),
                            Color.Transparent,
                            MaterialTheme.colorScheme.background,
                        ),
                    ),
                ),
        )
    }
    // ... top-row buttons + bottom-aligned title block follow
}
```

**Key parameters the owner likes:**

| Effect | Value | Why |
|---|---|---|
| Banner height | `360.dp` | Tall enough to extend behind the status bar + leave room for the cover thumbnail + title to overlap the bottom edge |
| Cover blur radius | `8.dp` | "Very subtle blur — just enough to make text readable." Higher values destroy the cover art; lower values reduce text contrast |
| Theme tint alpha | `0.2f` | Adds cohesion without overwhelming the cover |
| Gradient stops | `[Black 20%, Transparent, Background]` | Top fade for button legibility; bottom fade makes the banner blend seamlessly into the page background (no hard edge) |

**Cover thumbnail + title overlay** (`DetailScreen.kt:894-944`):

The cover thumbnail is a `100.dp × 150.dp` `AsyncImage` with
`clip(RoundedCornerShape(12.dp))`, aligned to the bottom-start of the banner,
overlapping the gradient. Beside it sits a Column with:
- `title.preferred()` in `titleLarge`, bold, `maxLines = 3`
- A Row of small `bodySmall` metadata pills: `★ {averageScore}`, `· {status}`,
  `· {episodes} eps`
- An `AiringPill(airing)` (the next-episode countdown pill) — tappable to
  toggle between static text and a live `HH:MM:SS` countdown

### 1.4 Dynamic theming from the cover color (`DynamicTheming.kt`)

AniList provides `coverImage.color` as a hex string (e.g. `"#FF5722"`).
The old project uses **that single color** to generate the entire M3
`ColorScheme` for the detail page — no Palette API, no async image
decoding. From `DynamicTheming.kt:48-77`:

```kotlin
fun generateDynamicScheme(coverColor: Color): DynamicColorScheme {
    val (h, s, l) = rgbToHsl(coverColor)
    val accent          = hslToColor(h, min(s, 1f), l)
    val surfaceLow      = hslToColor(h, s * 0.25f, 0.12f)   // very dark, low sat — even-index episode cards
    val surfaceHigh     = hslToColor(h, s * 0.30f, 0.18f)   // slightly lighter — odd-index episode cards
    val surfaceContainer= hslToColor(h, s * 0.35f, 0.24f)   // medium — title/synopsis/thumbnail bg
    val background      = hslToColor(h, s * 0.20f, 0.08f)   // very dark with hint of cover hue
    val onSurface       = Color.White.copy(alpha = 0.92f)
    val onSurfaceVariant= Color.White.copy(alpha = 0.65f)
    return DynamicColorScheme(accent, surfaceLow, surfaceHigh, surfaceContainer, background, onSurface, onSurfaceVariant)
}
```

The scheme is then mapped to a full `MaterialTheme.colorScheme.*` via
`DynamicColorScheme.toM3ColorScheme()` (lines 98-134). The whole detail
page is wrapped in `MaterialTheme(colorScheme = themedColorScheme) { ... }`
so every child composable automatically inherits the cover-derived colors.

**Performance note the new project must keep:** both
`generateDynamicScheme(coverColor)` AND `.toM3ColorScheme()` are wrapped in
a single `remember(coverColor, dynamicThemingEnabled, defaultScheme) { ... }`
block. Calling `toM3ColorScheme()` on every recomposition would create a new
`ColorScheme` object each frame → `MaterialTheme` sees a different object →
forces ALL children to recompose on every scroll frame → massive jank
(comment at `DetailScreen.kt:255-259`).

**Dynamic theming is toggleable** via `PlayerPreferences.dynamicDetailTheming()`
(default `true`). When off, the page uses the default app `MaterialTheme`.

### 1.5 Metadata fetching — the dual-source pipeline

This is the screen's data flow per ADR-010/011 (AniList co-primary,
MetadataResolver with fallback). The old project's implementation lives in
`DetailViewModel.kt` and uses a **two-stage load**:

**Stage 1 — AniList metadata (with 3-tier cache):**

```kotlin
// DetailViewModel.kt:143-171
private fun loadAnimeDetails() {
    viewModelScope.launch {
        val data = cacheManager.getOrFetch(
            key = "anime_detail_$anilistId",
            ttlMs = CacheManager.TTL_DETAIL_LONG,            // 24 hours
            supabaseKey = "anime_$anilistId",                // shared Supabase cache
            fetch = { anilistRepo.getAnimeDetails(anilistId) },
            serialize = { json.encodeToString(AniListAnime.serializer(), it) },
            deserialize = { json.decodeFromString(AniListAnime.serializer(), it) },
        )
        if (data != null) {
            _anime.value = DetailState.Success(data)
            findEpisodeSource(data)   // → Stage 2
        }
    }
}
```

The `CacheManager` (`data/cache/CacheManager.kt`) is a 3-tier cache:
1. **Local cache** (fastest, 24h TTL for detail pages)
2. **Supabase** (shared cache across devices, 30-min TTL)
3. **AniList GraphQL API** (source of truth)

The AniList GraphQL `animeDetails` query (`AniListQueries.kt:114-140`)
fetches these fields:

| Field | GraphQL selection | Displayed in UI as |
|---|---|---|
| `id` / `idMal` | `id`, `idMal` | `idMal` is used as the bridge to Jikan/MAL and Kitsu for episode enrichment |
| `title` | `title { romaji english native }` | `title.preferred()` = english ?: romaji ?: native |
| `coverImage` | `coverImage { extraLarge large medium color }` | Blurred banner uses `extraLarge` ?: `best()`; the `color` hex drives dynamic theming |
| `bannerImage` | `bannerImage` | Used as the fallback episode thumbnail |
| `description` | `description` | Synopsis (HTML-stripped via `cleanHtmlTags`) |
| `averageScore` | `averageScore` | "★ 78" pill beside title |
| `episodes` | `episodes` | "· 12 eps" pill beside title; "Episodes" row in Information |
| `genres` | `genres` | LazyRow of `AssistChip`s |
| `season` / `seasonYear` | `season`, `seasonYear` | "Season WINTER 2025" row in Information |
| `format` | `format` | "Format TV" row in Information |
| `status` | `status` | "Status RELEASING" row + pill beside title; gates whether Notification/AutoDownload menu items appear (`isFullyReleased` check) |
| `nextAiringEpisode` | `nextAiringEpisode { airingAt episode timeUntilAiring }` | `AiringPill` (tappable countdown) |
| `streamingEpisodes` | `streamingEpisodes { title thumbnail url }` | Used by `EpisodeMetadataFetcher` to enrich episodes with thumbnails/titles |
| `studios`, `relations` | (fetched but not yet displayed in the old UI) | Available for future use |

**Stage 2 — Extension search + episode list:**

After AniList metadata loads, `findEpisodeSource(anime)` runs:

1. In-memory episode cache hit (survives navigation) → show instantly +
   background-refresh.
2. Disk episode cache hit (survives app restart) → show instantly +
   recover `matchedSource` from `AnimeSourceManager` (with up to 20×500ms
   retries while extensions load async) + background refresh.
3. Persistent source-match cache (`ext_match_$anilistId` +
   `ext_sanime_url_$anilistId` in PreferenceStore) — skips the slow
   full-extension search if we already know which source matched.
4. Full extension search via `AniyomiSourceBridge.findMatch(anime)` →
   returns `SourceMatchResult.{NotAired, NoMatch, SingleMatch,
   MultipleMatches}` (auto-picks best on multi-match).

**Stage 3 — Episode metadata enrichment (multi-source, parallel):**

`enrichEpisodesWithMetadata(eps, anime)` (`DetailViewModel.kt:385-485`)
runs in parallel from 4 sources, only when at least one episode is
missing thumbnails/titles/summaries and the user has the
`enableInAppMetadataFetch()` preference on:

| Source | Provides | Endpoint |
|---|---|---|
| **Anikage.cc** (TheTVDB) | titles + descriptions + thumbnails + air dates | `https://anikage.cc/api/media/anime/{anilistId}/episodes` |
| **AniList streaming episodes** | titles + thumbnails (already in anime object) | From `anime.streamingEpisodes` |
| **Jikan / MAL v4** | titles + air dates | `https://api.jikan.moe/v4/anime/{malId}/episodes` (uses `idMal`, with retries on 429/504) |
| **Kitsu** | titles + descriptions + thumbnails + air dates | `https://kitsu.app/api/edge/mappings` (MAL→Kitsu ID) then `/anime/{kitsuId}/episodes` |

Merge priority (matches the AniKoto extension's behavior):
- **Title**: Jikan → Anikage → Kitsu → AniList
- **Description**: Anikage → Kitsu
- **Thumbnail**: Anikage → AniList streaming → Kitsu → `anime.bannerImage` (fallback)
- **Air date**: Jikan → Anikage → Kitsu

The "Fetching metadata…" indicator (small surfaceVariant pill beside the
"Episodes" header) shows while enrichment runs. The new project must
preserve this — it gives the user clear feedback that the page is alive
after the episode list first appears.

**Important compose-level implementation detail:** when enrichment
finishes, the code creates **new `SEpisode` objects** instead of mutating
the existing ones (`DetailViewModel.kt:427-468`). Compose's `LazyColumn`
skips recomposition when it receives the same object references; by
creating new objects, Compose detects the change and recomposes visible
items immediately — no need to scroll to trigger a refresh. This is a
subtle but critical correctness fix worth carrying forward.

### 1.6 Three-stage pull-to-refresh (`ThreeStagePullRefresh.kt`)

A custom `NestedScrollConnection` that maps pull distance to 3 stages:

| Stage | Pull distance | Action |
|---|---|---|
| 1 | 100 dp | `refreshEpisodesOnly()` — re-fetch episodes from matched source |
| 2 | 200 dp | `refreshDetailsOnly()` — re-fetch AniList metadata (bypass cache) |
| 3 | 300 dp | `refreshEverything()` — clear caches + re-match source + re-fetch details + episodes |

Damping factor 0.5× on the pull gives a "natural feel" (line 101). A
guard of 5 minutes per anime prevents hammering on background refreshes.
The indicator is a floating `Surface(RoundedCornerShape(16.dp),
surfaceVariant 95% alpha)` positioned at `TopCenter` with `padding(top =
56.dp)` so it sits below the header buttons.

This is **novel** — aniyomi only has single-stage M3 `PullRefresh`. Worth
keeping.

### 1.7 What the owner likes (KEEP)

- **Blurred cover banner with 8dp blur** — the centerpiece "quite beautiful" effect.
- **Vertical gradient (black 20% → transparent → background)** — blends the
  banner seamlessly into the page; no hard edge between banner and content.
- **Cover-color-tinted overlay at 20% alpha** — adds cohesion without
  overpowering the cover.
- **Dynamic theming from AniList `coverImage.color`** — fast (no Palette
  API / image decode), reliable, gives every anime its own palette.
- **Alternating episode card backgrounds** (`surfaceLow` even / `surfaceHigh`
  odd) — subtle visual rhythm that helps eye-tracking on long lists.
- **Two info-position modes** ("above" vs "below" episodes) — power users
  get a single long scroll; casual users get a capped list with Information
  reachable below.
- **"Fetching metadata…" enrichment indicator** — clear feedback that the
  page is alive after the episode list first appears.
- **3-stage pull-to-refresh** — gives the user explicit control over how
  much to refresh.
- **Long-press bottom sheet** with context-aware options (download
  state-dependent + watched toggle) — better UX than a context menu.

### 1.8 What to improve

- **Header is `360.dp` tall** — fine on phones, but on tablets/foldables the
  cover thumbnail + title look lost in the wide banner. Needs a
  max-width / two-column variant for wider screens.
- **The `when (detailState) { Loading -> CircularProgressIndicator() }`
  pattern** is jarring — the screen flashes a black screen with a spinner
  before the banner appears. The new project should show a skeleton with
  the blurred-cover banner placeholder + a shimmer title block.
- **All the cover color logic assumes a dark theme.** `onSurface` is
  hardcoded to `Color.White.copy(alpha = 0.92f)` (line 65 of
  `DynamicTheming.kt`). For light theme, this breaks. The new project's
  `MetadataResolver` should pick light/dark surfaces based on the cover
  color's luminance (the code already computes `luminance()` for the
  accent text color but doesn't use it for surfaces).
- **`ThreeStagePullRefresh`** uses `pullDistance` in `remember` — when the
  user navigates away and back, the indicator state is lost. Use
  `rememberSaveable` for the pull distance.
- **No localization** — all strings are hardcoded ("Synopsis",
  "Information", "Format", "Status", "Episodes", "Score", "Show more",
  etc.). The new project must use Moko Resources or stock strings.xml.
- **AniList is the only metadata source** for the anime-level (header)
  fields. ADR-011 wants a true `MetadataResolver` that can fall back to
  extension data when AniList is incomplete. The old project only does
  this for **episode** fields (via `EpisodeMetadataFetcher`); the new
  project should extend it to **anime** fields too (cover, description,
  status, etc.).

---

## 2. Episode List (`EpisodeRow.kt` + `EpisodeRowContent.kt` + `Grayscale.kt`)

### 2.1 File paths

| Concern | File |
|---|---|
| Outer row (swipe + click + long-press + grayscale wrapper) | `ui/detail/components/EpisodeRow.kt` (330 lines) |
| Inner content (rich layout with thumbnail/summary vs simple text-only) | `ui/detail/components/EpisodeRowContent.kt` (595 lines) |
| Watched-episode visual effect (B&W + blur) | `ui/detail/components/Grayscale.kt` (137 lines) |
| Audio pills | `ui/detail/components/AudioPills.kt` (89 lines) |
| Airing pill | `ui/detail/components/AiringPill.kt` (81 lines) |
| Download button (state-aware, tall variant) | `ui/detail/components/DownloadButton.kt` (183 lines) |
| Long-press options sheet | `ui/detail/components/EpisodeOptionsSheet.kt` (207 lines) |
| Date / HTML formatters | `ui/detail/components/DetailFormatters.kt` (69 lines) |
| Episode title parser | `ui/detail/EpisodeTitleParser.kt` (65 lines) |
| Watched-episode store (persistence) | `data/cache/EpisodeSeenStore.kt` (102 lines) |

The episode list also has a **player-side sibling** at
`player/controls/EpisodeListView.kt` (851 lines) which reuses the same
design language (thumbnails, titles, summaries, dates, audio pills) but
has its own `PlayerEpisodePreferences` separate from the detail page's
`PlayerPreferences`. That file is **not** in scope for this analysis — it
lives inside the player, not the detail screen — but it confirms the
design is reusable across surfaces.

### 2.2 Layout structure — rich row (the owner's "proper example")

When the episode has a thumbnail (`preview_url`) and/or a summary, the row
uses the **rich** layout (`EpisodeRowRich`, `EpisodeRowContent.kt:144-377`).
All positions are user-configurable via `PlayerPreferences`:

```
 ┌────────────────────────────────────────────────────────────────┐
 │  [EP 5]   ┌────────────────────────────────────────────────┐  │
 │  overlay  │ Title (bold, titleSmall)         [EP 5 badge]   │  │ ← EpisodeTitleRow (surfaceContainer bg, 8dp corners)
 │  on thumb │ ──────────────────────────────────────────────  │  │
 │  ┌──────┐ │ [Jan 15, 2025] [S•D]   ← date pill + AudioPills │  │ ← DateAudioPillsRow (right_above_synopsis)
 │  │ THUMB│ │ ┌────────────────────────────────────────────┐ │  │
 │  │ NAIL │ │ │ Synopsis (3-line, expandable on tap)        │ │  │ ← SynopsisContent (surfaceContainer bg, 8dp corners)
 │  │      │ │ │ "Kafka joins the Defense Force and …"       │ │  │
 │  └──────┘ │ └────────────────────────────────────────────┘ │  │
 │           └────────────────────────────────────────────────┘  │
 │                                                                │
 │   ← alternating bg color (surfaceLow even / surfaceHigh odd)   │
 │   ← rounded 12dp, 6dp horizontal padding, 2dp vertical padding │
 │   ← WatchedEpisodeEffect applied at the row container level    │
 │   ← SwipeBackground revealed underneath during swipe gesture   │
 └────────────────────────────────────────────────────────────────┘
```

**Configurable positions** (per `EpisodeDisplaySettings` data class,
`EpisodeRowContent.kt:47-61`):

| Setting | Options | Default |
|---|---|---|
| `thumbnailPosition` | `"left"` / `"right"` | `"left"` |
| `thumbnailSize` | `"small"` (100×56dp) / `"medium"` (120×68dp) / `"large"` (160×90dp) | `"medium"` |
| `titlePosition` | `"right"` (beside thumb) / `"below"` (full-width) | `"right"` |
| `episodeNumberPosition` | `"overlay"` (on thumbnail, black-70% bg, white text) / `"badge"` (in title row, primaryContainer bg) / (anything else = circle badge to the left) | `"overlay"` |
| `synopsisPosition` | `"right"` (beside thumb) / `"below"` (full-width) | `"right"` |
| `datePosition` | `"right_above_synopsis"` / `"right_below_synopsis"` / `"below"` | `"right_below_synopsis"` |
| `downloadButtonPlacement` | `"episode_row"` (outside the row, compact tall button) / `"synopsis"` (inside the synopsis row, beside the synopsis panel) | `"episode_row"` |
| `showThumbnails` / `showSummaries` / `showTitles` / `showDates` / `showEpisodeNumber` / `showAudioPills` | boolean | all `true` |

### 2.3 Layout structure — simple row (no thumbnail/summary)

When neither thumbnail nor summary is available, `EpisodeRowSimple`
renders a compact text-only row:

```
 ┌────────────────────────────────────────────────────────────────┐
 │  (5)   ┌────────────────────────────────────────────────────┐ │
 │ circle │ Episode 5                            [EP 5 badge]   │ │ ← surfaceContainer bg, 8dp corners
 │ badge  │                                                       │ │
 │        └────────────────────────────────────────────────────┘ │
 │                          [Jan 15, 2025] [S•D]                  │ ← DateAudioPillsRow (6dp below)
 └────────────────────────────────────────────────────────────────┘
```

The circle badge is a 40×40dp `Surface(CircleShape, surfaceVariant)`
containing the episode number in `labelMedium` bold.

### 2.4 The owner-flagged effect: watched episodes are B&W + blurred

This is the screen's most distinctive design decision. The implementation
lives in `Grayscale.kt`:

```kotlin
// Grayscale.kt:20-53 — the four configurable modes
enum class WatchedEpisodeAppearance {
    NONE,        // No visual treatment — watched episodes look the same as unwatched
    GRAYSCALE,   // Desaturate the entire card (text, icons, thumbnail) to black & white
    BLUR,        // Apply a subtle blur to the entire card
    BOTH;        // Apply both grayscale AND blur (maximum visual distinction)
    // ...
    val appliesGrayscale: Boolean get() = this == GRAYSCALE || this == BOTH
    val appliesBlur: Boolean get() = this == BLUR || this == BOTH
}

// Grayscale.kt:85-116 — the modifier that applies the effect
fun Modifier.watchedEpisodeEffect(
    appearance: WatchedEpisodeAppearance,
    alpha: Float = 0.55f,
    blurRadiusDp: Float = 2f,
): Modifier {
    if (appearance == WatchedEpisodeAppearance.NONE) return this

    var result = this

    // --- Grayscale (GPU render effect) ---
    if (appearance.appliesGrayscale) {
        result = result.graphicsLayer {
            this.alpha = alpha
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val matrix = ColorMatrix().apply { setSaturation(0f) }
                this.renderEffect = RenderEffect.createColorFilterEffect(
                    ColorMatrixColorFilter(matrix)
                ).asComposeRenderEffect()
            }
        }
    }

    // --- Blur ---
    if (appearance.appliesBlur && blurRadiusDp > 0f) {
        result = result.blur(blurRadiusDp.dp)
    }

    return result
}
```

**Why `RenderEffect` (not `drawWithContent` + `ColorFilter`):** earlier
attempts used `drawWithContent` + `ColorFilter.colorMatrix` on a `Paint`
object. That only affects rasterised draw operations (images) and does
NOT affect Compose's text rendering pipeline — themed text colors stayed
unchanged, giving a "half-grayscale" appearance. The fix uses
`RenderEffect.createColorFilterEffect` via `Modifier.graphicsLayer`,
which intercepts the entire rendered output of the layer (text, icons,
shapes, images) and desaturates it uniformly. Comment at `Grayscale.kt:60-71`.

**Platform support:**
- Grayscale: Android 12+ (API 31+) via `RenderEffect`. Below API 31, only
  alpha dimming is applied (graceful degradation).
- Blur: Android 12+ (API 31+) via `Modifier.blur` (also uses `RenderEffect`
  internally). Below API 31, blur is a no-op (`Modifier.blur` handles
  this gracefully).

**Where the effect is applied** — at the **outer `EpisodeRow` container
level**, not on individual elements. This ensures the entire row (card
background, thumbnail, title, summary, audio pills, date pill —
everything) is desaturated uniformly. From `EpisodeRow.kt:168-176`:

```kotlin
// The foreground Box — this is where the watched effect is applied
Box(
    modifier = Modifier
        .fillMaxWidth()
        .watchedEpisodeEffect(
            appearance = if (isSeen) appearance else WatchedEpisodeAppearance.NONE,
            alpha = grayscaleAlpha,
            blurRadiusDp = blurRadiusDp,
        )
        .offset { IntOffset(swipeOffset.value.roundToInt(), 0) }
        .background(cardColor, RoundedCornerShape(12.dp))
        .clip(RoundedCornerShape(12.dp))
        // ... pointerInput for swipe, combinedClickable for tap/long-press
)
```

The user-configurable parameters (in `PlayerPreferences`):
- `watchedEpisodeAppearance()` — string pref: `"none"` / `"grayscale"` /
  `"blur"` / `"both"`. **Default: `"grayscale"`** (the owner's preferred).
- `watchedEpisodeBlurRadius()` — float pref, **default `2f` dp** (subtle).
- `watchedEpisodeAlpha()` — float pref, **default `0.55f`** (slightly more
  than half-opacity).

**Persistence:** watched state is tracked in `EpisodeSeenStore`
(`data/cache/EpisodeSeenStore.kt`), keyed by `"$anilistId:$episodeUrl"`.
The store is reactive via a `changes: Flow<Set<String>>` — the detail
page collects it in a `LaunchedEffect` so watched toggles from the player
or another tab update the row instantly. The store also re-reads on
`ON_RESUME` (comment at `DetailScreen.kt:84-94`) so returning from the
player refreshes the set without depending on the Flow timing.

### 2.5 Episode-row container features (`EpisodeRow.kt`)

The `EpisodeRow` composable is more than a layout — it's a full gesture
handler:

| Feature | Implementation |
|---|---|
| **Tap** → play episode | `combinedClickable(onClick = onClick)` |
| **Long-press** → options sheet | `combinedClickable(onLongClick = { haptic.LongPress; onLongClick() })` |
| **Swipe right (80dp)** → toggle watched | `detectHorizontalDragGestures` + threshold check on `onDragEnd` |
| **Swipe left (160dp)** → download | Same gesture handler, longer threshold to prevent accidental downloads |
| **Mid-drag haptic** | Single `HapticFeedbackType.LongPress` pulse when crossing threshold (does NOT trigger the action — only feedback) |
| **Action fires on release, not mid-drag** | Prevents accidental triggers if user swipes past threshold and drags back |
| **Spring-back animation** | `spring(DampingRatioMediumBouncy, StiffnessMedium)` returns the row to rest |
| **Max overshoot** | `1.3×` the threshold — prevents unreasonable dragging |
| **Vertical scroll coexistence** | `detectHorizontalDragGestures` only consumes horizontal drags; vertical drags pass through to the parent `LazyColumn` |
| **Swipe background reveal** | `SwipeBackground` (separate composable) shows a `primaryContainer`/`secondaryContainer` background with an eye/download icon that scales up + becomes opaque as the swipe approaches the threshold |

The card background alternates per `index`:
- `index % 2 == 0` → `surfaceContainerLow` (or `dynamicColors.surfaceLow`)
- `index % 2 == 1` → `surfaceContainerHigh` (or `dynamicColors.surfaceHigh`)

This creates the subtle "zebra stripe" rhythm that helps the eye track on
long lists. The new project should preserve this.

### 2.6 Audio pills (`AudioPills.kt`)

The `scanlator` field on `SEpisode` is parsed for `SUB` / `DUB` / `HSUB`
(case-insensitive). The pills have an adaptive width heuristic:

- **2+ audio versions**: labels shorten to first letter (`SUB→S`,
  `DUB→D`, `HSUB→H`) with 3dp circular dot separators, e.g. `S•D`.
  This guarantees they fit on one row.
- **1 audio version**: full label is shown (e.g. `SUB`).

The pill is a `Surface(RoundedCornerShape(6.dp), outlineVariant)` with
`labelSmall` semi-bold text in `onSurfaceVariant`. Why no
`BoxWithConstraints`: it's a `SubcomposeLayout` which crashes inside
`Row(height(IntrinsicSize.Min))` because intrinsic measurement of
subcompose layouts is not supported. The fixed-width heuristic avoids
this entirely (comment at `AudioPills.kt:29-34`).

### 2.7 Episode thumbnail with EP overlay (`EpisodeRowContent.kt:382-427`)

```kotlin
Box(modifier = Modifier.width(thumbWidth).height(thumbHeight)) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,  // placeholder color while loading
        modifier = Modifier.fillMaxSize(),
    ) {
        AsyncImage(
            model = episode.preview_url,
            contentDescription = "Episode thumbnail",
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop,
        )
    }
    if (showEpisodeNumber && episodeNumberPosition == "overlay") {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color.Black.copy(alpha = 0.7f),
            modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
        ) {
            Text(
                text = "EP ${EpisodeTitleParser.formatEpisodeNumber(episode.episode_number)}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}
```

The `EP N` overlay (top-start, black-70% bg, white text, 6dp rounded)
is the default episode-number position. It works regardless of whether
the thumbnail has loaded.

### 2.8 Long-press options sheet (`EpisodeOptionsSheet.kt`)

State-aware bottom sheet that shows different actions based on the
episode's download status and watched state:

| Episode state | Options shown |
|---|---|
| Downloaded (queue or disk) | Play downloaded / Delete download (destructive) / Mark as unwatched |
| Downloading / Queued / Resolving / Muxing / Reconnecting | Cancel download (destructive) / Mark as watched or unwatched |
| Paused | Resume / Cancel download (destructive) / watched toggle |
| Error | Retry / Cancel download (destructive) / watched toggle |
| Not downloaded | Download / watched toggle |

**Why this is a separate composable** (rather than inline
`longPressEpisode?.let { ModalBottomSheet(...) }`): Compose did not
reliably recompose the inline `?.let` block in the 800-line `DetailScreen`
function — likely due to the function's complex control flow and
recomposition scope boundaries. Extracting into a dedicated composable
that receives `episode` as a **parameter** makes Compose track the
parameter change and reliably recompose whenever `episode` transitions
from `null` to non-null (comment at `EpisodeOptionsSheet.kt:46-58`).
**This is a real Compose gotcha the new project should remember.**

### 2.9 What the owner likes (KEEP)

- **Watched = grayscale + blur (configurable: none / grayscale / blur / both)** —
  the owner's #1 flagged preference for the episode list. Subtle but
  unmistakable; works without bright colors or strikethroughs.
- **Effect applied at the row container level** — uniform desaturation
  across text, icons, thumbnail, and card background. No "half-grayscale"
  appearance.
- **Default `grayscale` mode at `alpha=0.55f`** — readable but clearly
  "done".
- **`BOTH` mode for maximum distinction** — useful for users who want a
  stronger signal.
- **GPU `RenderEffect` approach** (not `drawWithContent` + `ColorFilter`)
  — the only way to desaturate Compose's text rendering pipeline.
- **Alternating card backgrounds** — subtle zebra-stripe rhythm.
- **Rich layout with thumbnail + title + audio pills + date + synopsis** —
  matches the look of modern streaming apps (Netflix, Crunchyroll).
- **All positions configurable** — power users can rearrange; defaults
  match the owner's preference.
- **Swipe gestures with mid-drag haptic + action-on-release** — feels
  polished; prevents accidental triggers.
- **Long-press bottom sheet with state-aware options** — better than a
  context menu; clear destructive-action coloring.
- **Adaptive audio pills** (S•D short form when 2+) — guarantees they
  fit on one row.

### 2.10 What to improve

- **The `RenderEffect` grayscale requires API 31+.** On older devices,
  only alpha dimming is applied — this is documented but the user has no
  way to know. The new project should either (a) bump minSdk to 31
  (recommended — Android 12 is from 2021), or (b) show a settings notice
  explaining the limitation.
- **The `SEpisode.scanlator` field is the only signal for SUB/DUB/HSUB.**
  This is fragile — extensions use inconsistent formats ("SUB", "Subbed",
  "SUBBED", "[SUB]"). The regex `contains("SUB")` matches all of these
  but also matches "HSUB" (so an HSUB episode shows both H and S pills).
  The new project should use a proper audio-version taxonomy (probably
  derived from `VideoTitleParser`'s `AudioVersion` enum).
- **The rich row's `IntrinsicSize.Min` + `Row` + `Surface(weight(1f))`
  pattern is fragile** — `AudioPills` couldn't use `BoxWithConstraints`
  because of this. Consider a `Flow` or a fixed max-width pill cluster.
- **No filler badge** — the `SEpisode.fillermark` field exists but is
  never displayed in the row. The new project should add a "Filler" pill
  (the task description explicitly asks for this).
- **No "currently watching" indicator** — the player has the current
  episode index, but the detail page doesn't highlight it. Add a
  primary-colored accent border or a "now playing" icon.
- **No "downloaded" checkmark on the row itself** — the download button
  shows state, but a green checkmark on the thumbnail (like Aniyomi)
  would be more scannable.

---

## 3. Video Resolver — Server / Resolution / Audio Picker (`VideoPickerSheet.kt`)

### 3.1 File paths

| Concern | File |
|---|---|
| Resolver bottom sheet UI | `ui/detail/VideoPickerSheet.kt` (362 lines) |
| Video title parser (server / audio / quality extraction) | `ui/detail/VideoTitleParser.kt` (146 lines) |
| State machine (Resolving / Cached / Show / Hidden) | `ui/detail/DetailViewModel.kt:1305-1310` (`VideoPickerState`) |
| Resolver logic (getHosterList → getVideoList fallback) | `ui/detail/DetailViewModel.kt:496-760` |
| Player-side server/audio dropdowns (different UI, same data) | `player/controls/ServerVersionDropdowns.kt` (224 lines) |

The screen the owner flagged is the bottom sheet that appears **after the
user taps an episode** but **before the player opens**. The old project's
`DetailViewModel.playEpisode(episode)` does NOT directly launch the
player — it first resolves the video list, then either auto-plays (if
only 1 video) or shows `VideoPickerSheet` for the user to pick.

### 3.2 The resolution flow (when does the sheet appear?)

`DetailViewModel.playEpisode(episode)` (`DetailViewModel.kt:497-617`) is
the entry point. The flow:

```
 User taps episode row
        │
        ▼
 playEpisode(episode)
        │
        ├─ Is episode downloaded on disk? ──YES──▶ PlayDownloadedFile (offline)
        │                                           (skips the resolver entirely)
        │
        ├─ Is matchedSource null? ──YES──▶ Try to recover from cache
        │                                     (up to 20×500ms retries while
        │                                      extensions load async)
        │
        ├─ Is video list cached in-memory (10-min TTL)? ──YES──▶ Show picker
        │                                                            INSTANTLY with
        │                                                            "Refreshing…" badge +
        │                                                            background re-resolve
        │
        └─ Cache miss → set state = Resolving(episode)
                │
                ▼
         resolveVideos(episode, source)
                │
                ├─ Try source.getHosterList(episode)  ← preferred path
                │      └─ hoster.videoList?.filter { it.videoUrl.isNotBlank() }
                │
                └─ Fallback on ANY exception → source.getVideoList(episode)
                        └─ .filter { it.videoUrl.isNotBlank() }
                │
                ▼
         groupVideosByServer(allVideos)   ← VideoTitleParser.kt
                │
                ▼
         Result is empty? ──YES──▶ PlayRequest.Error("No playable video found")
                │
                ▼
         Result has exactly 1 video? ──YES──▶ Auto-play (skip picker)
                │
                ▼
         Result has 2+ videos? ──YES──▶ Cache (in-memory 10-min + disk for metadata)
                                        + Show VideoPickerState.Show(episode, serverSections)
                                        → VideoPickerSheet renders
```

**Important:** the disk video cache is **written but not read** for the
picker. The reason: cached `videoUrls` contain `localhost:PORT` proxy URLs
that are stale after app restart (the proxy server dies with the process).
Showing stale URLs in the picker would let the user tap a dead URL.
Instead, only the in-memory cache (10-min TTL, safe because the proxy is
alive) is used for picker display. Comment at `DetailViewModel.kt:581-588`.

### 3.3 The 4 states of `VideoPickerState`

```kotlin
// DetailViewModel.kt:1305-1310
sealed class VideoPickerState {
    data object Hidden : VideoPickerState()
    data class Resolving(val episode: SEpisode) : VideoPickerState()
    data class Cached(val episode: SEpisode, val serverSections: List<ServerSection>, val isRefreshing: Boolean) : VideoPickerState()
    data class Show(val episode: SEpisode, val serverSections: List<ServerSection>) : VideoPickerState()
}
```

| State | When | UI |
|---|---|---|
| `Hidden` | Default; no episode tapped | Nothing rendered |
| `Resolving` | First-time resolve (cache miss) | Full-screen black-60% overlay with a `Surface(surfaceContainerHigh, RoundedCornerShape(16.dp))` containing `CircularProgressIndicator` + "Resolving video…" + "Fetching servers from the extension" |
| `Cached` | Cache hit (10-min TTL) + background re-resolve in progress | The bottom sheet renders INSTANTLY with cached data + a "Refreshing…" badge in the header |
| `Show` | Fresh resolve OR background re-resolve finished | The bottom sheet renders with current data, no refreshing badge |

**Smooth-update logic** (`DetailViewModel.kt:619-710`): when the background
re-resolve finishes, the result is compared to the cached data using
`compareServerSections(a, b)` — which compares by `videoTitle` +
`resolution` (stable) instead of `videoUrl` (contains `localhost:PORT`
which changes each resolution). If unchanged, the picker transitions
silently from `Cached` → `Show` (removing the refreshing badge) without
re-animating the sheet. If changed, the picker updates the data smoothly
without closing/reopening. The new project should preserve this exact
behavior — without it, the bottom sheet re-animates on every background
refresh, which is jarring.

### 3.4 Layout structure of the bottom sheet (`VideoPickerBottomSheet`)

```
 ╔══════════════════════════════════════════════════════════════════╗
 ║  Select quality                            [⟳ Refreshing…]       ║ ← titleMedium bold + optional refreshing badge
 ║──────────────────────────────────────────────────────────────────║
 ║  ┌────────────────────────────────────────────────────────────┐ ║ ← LazyColumn, heightIn(max=420.dp), animateContentSize
 ║  │ ▼ VidPlay-1                            [HSUB] [DUB] [SUB]   │ ║ ← ServerHeader (surfaceVariant bg, 10dp corners)
 ║  │      [Hardsub]  3 qualities                              │ ║ ← AudioSubHeader (secondaryContainer 70% bg)
 ║  │      ▶                                          [1080p]    │ ║ ← VideoRow (PlayArrow icon + spacer + quality chip)
 ║  │      ────────────────────────────────────────────────────  │ ║ ← HorizontalDivider (outlineVariant 30% alpha)
 ║  │      ▶                                          [720p]     │ ║
 ║  │      ────────────────────────────────────────────────────  │ ║
 ║  │      ▶                                          [360p]     │ ║
 ║  │                                                            │ ║
 ║  │ ▶ HD-1                                [SUB]                 │ ║ ← collapsed server (only this one expands at a time — accordion)
 ║  │ ▶ VidPlay-2                           [SUB]                 │ ║
 ║  └────────────────────────────────────────────────────────────┘ ║
 ╚══════════════════════════════════════════════════════════════════╝
```

Key layout details:

- **`ModalBottomSheet`** with `dragHandle = null` (no default white pull
  bar) and `rememberModalBottomSheetState()` (does NOT skip partially
  expanded — allows the sheet to sit at a natural height instead of
  jumping to full-screen, which caused an auto-close glitch).
- **Header row**: `Text("Select quality", titleMedium, bold)` weighted
  to the left + optional `Row { CircularProgressIndicator(14dp, 2dp) +
  Text("Refreshing…", labelSmall) }` on the right.
- **`LazyColumn` with `heightIn(max = 420.dp)` + `animateContentSize`**:
  shrinks when all servers are collapsed, grows up to 420dp when one
  expands. Smooth height animation.
- **Accordion behavior**: only one server expanded at a time
  (`DetailViewModel.toggleServer(key)` collapses all others when
  expanding — `DetailViewModel.kt:99-109`). This keeps the sheet
  manageable when there are many servers.

### 3.5 The 3-tier hierarchy: Server → Audio → Quality

The data model lives in `VideoTitleParser.kt`:

```kotlin
// VideoTitleParser.kt:100-108
data class ServerSection(
    val serverName: String,
    val audioSections: List<AudioSubSection>,
)

data class AudioSubSection(
    val audio: AudioVersion,
    val videos: List<Video>,  // sorted by quality descending
)

enum class AudioVersion(val label: String) {
    SUB("Sub"),
    DUB("Dub"),
    HSUB("Hardsub"),
    ANY("Any");
    // ...
}
```

The grouping is built by `groupVideosByServer(videos)`:
1. Each `Video` is parsed by `VideoTitleParser.parse(video)` into
   `ParsedVideo(video, server, audio, quality)`.
2. Group by `server` (top level).
3. Within each server, group by `audio`.
4. Within each audio, sort videos by `quality` descending (1080p top,
   360p bottom).
5. Audio display order: `SUB, DUB, HSUB, ANY` (defined as `audioOrder`).

### 3.6 The `VideoTitleParser` — extracting server / audio / quality from a flat video list

Most extensions (AniKoto and others using the `anikototheme/multisrc`)
format video titles as `"{server} - {audio} - {quality}"`, e.g.:

- `"VidPlay-1 - SUB - 360p"`
- `"HD-1 - DUB - 1080p"`
- `"VidPlay-1 - HSUB - 720p"`

Some extensions return a flat list (1 server group "Default") where all
27 videos follow this same title format. The parser extracts the real
server/audio/quality from the title so we can re-group by audio version
and sort by quality — regardless of how the extension structured the
list. From `VideoTitleParser.kt:51-87`:

```kotlin
object VideoTitleParser {
    private val QUALITY_REGEX = Regex("""\b(\d{3,4})p\b""", RegexOption.IGNORE_CASE)
    private val AUDIO_REGEX  = Regex("""\b(SUB|DUB|HSUB|HARDSUB|SUBBED|DUBBED)\b""", RegexOption.IGNORE_CASE)

    fun parse(video: Video): ParsedVideo {
        val title = video.videoTitle

        // Quality: prefer the structured field, fall back to regex
        val quality = video.resolution
            ?: QUALITY_REGEX.find(title)?.groupValues?.get(1)?.toIntOrNull()

        // Audio version: scan for keyword
        val audioToken = AUDIO_REGEX.find(title)?.value
        val audio = AudioVersion.fromToken(audioToken)

        // Server: the token before the first " - " separator
        val server = if (title.contains(" - ")) {
            title.substringBefore(" - ").trim()
        } else {
            title.trim()
        }

        return ParsedVideo(
            video = video,
            server = server.ifBlank { "Server" },
            audio = audio,
            quality = quality,
        )
    }
}
```

**Graceful degradation:** if the title doesn't match the expected format
(other extension formats), falls back to:
- server = full title
- audio = `ANY`
- quality = `Video.resolution` or `null`

This means extensions that don't follow the `"Server - Audio - Quality"`
convention still work — they just show up as one big "server" with one
"Any" audio section. The new project's resolver module should keep this
graceful fallback.

### 3.7 The three row composables

**`ServerHeader`** (`VideoPickerSheet.kt:227-280`) — top-level collapsible
section:

```kotlin
Surface(
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 4.dp),
    shape = RoundedCornerShape(10.dp),
    color = MaterialTheme.colorScheme.surfaceVariant,
) {
    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), ...) {
        Icon(
            imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown
                          else Icons.Default.KeyboardArrowRight,
            contentDescription = if (isExpanded) "Collapse" else "Expand",
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = serverName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        // Audio tag chips — order: HSUB leftmost, DUB middle, SUB rightmost
        // (reversed from the default SUB-first order so SUB is on the right
        // per the user's preference).
        audioTags.reversed().forEach { audio ->
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            ) {
                Text(
                    text = audio.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
        }
    }
}
```

Note the audio tag chips are **reversed** (`audioTags.reversed()`) so SUB
is on the right per the owner's preference. (A small but specific
preference worth preserving.)

**`AudioSubHeader`** (`VideoPickerSheet.kt:286-316`) — shows the audio
version (SUB/DUB/HSUB) + video count. Uses `secondaryContainer` (NOT
blue/primary) per the owner's explicit request to avoid blue colors:

```kotlin
Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 6.dp),
    ...
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
    ) {
        Text(
            text = audio.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
    Spacer(Modifier.width(8.dp))
    Text(
        text = "$videoCount quality${if (videoCount != 1) "s" else ""}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
```

**`VideoRow`** (`VideoPickerSheet.kt:323-362`) — quality label on the
left (weighted), quality chip on the RIGHT. Only ONE quality
representation is shown as text; the chip is the visual indicator. No
duplicate plain text. Per the owner's preference:

```kotlin
Row(
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
        .padding(horizontal = 56.dp, vertical = 10.dp),
    ...
) {
    // Play icon on the left
    Icon(
        imageVector = Icons.Default.PlayArrow,
        contentDescription = "Play",
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.size(16.dp),
    )
    Spacer(Modifier.width(8.dp))
    // Spacer to push the quality chip to the right
    Spacer(Modifier.weight(1f))
    // Quality chip on the RIGHT side
    Surface(
        shape = RoundedCornerShape(50),  // pill shape
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = qualityLabel,   // e.g. "1080p" or "Unknown"
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}
```

`HorizontalDivider(color = outlineVariant.copy(alpha = 0.3f))` is drawn
between video rows for visual separation.

### 3.8 The "Resolving" overlay (cache-miss state)

When the picker state is `Resolving`, a full-screen overlay appears:

```kotlin
// VideoPickerSheet.kt:71-96
is VideoPickerState.Resolving -> {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Resolving video…", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Fetching servers from the extension",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
```

### 3.9 Extensibility analysis — how new extensions / formats plug in

This is the owner's explicit ask: "a COMPLETE MODULE to handle this
(server names, resolutions, audio versions) that can be easily extended
for new extensions."

The old project's resolver is **already mostly extensible**:

1. **The data model is generic.** `ServerSection` / `AudioSubSection` /
   `Video` are not tied to any specific extension format. New extensions
   that return `Video` objects with `videoTitle` strings automatically
   flow through the parser.

2. **The parser has graceful fallback.** If an extension's title doesn't
   match `"{server} - {audio} - {quality}"`, the parser falls back to
   server = full title, audio = `ANY`, quality = `Video.resolution` or
   `null`. So new extensions work without changes — they just don't get
   server/audio grouping.

3. **`AudioVersion` is a closed enum (SUB, DUB, HSUB, ANY).** This is the
   main extensibility gap. If a new extension provides a "Raw" audio
   version, or "Commentary", or "Dub (French)", the enum can't represent
   it without code changes. The new project should make `AudioVersion`
   an **open sealed class** or a **string-keyed data class** with a
   registry, so extensions can declare their own audio versions.

4. **The `AudioVersion.fromToken()` tokenizer is regex-based.** Adding
   new tokens requires editing the `AUDIO_REGEX`. The new project should
   let extensions declare their own token mappings.

5. **`Video.resolution` is the structured quality field.** Extensions
   that populate it (per the Aniyomi API) get proper quality sorting.
   Extensions that don't are regex-parsed from the title. This is
   already extensible.

6. **The hierarchy is fixed at 3 levels (Server → Audio → Quality).**
   Some extensions might want a 4th level (e.g., "mirror" within a
   server). The new project should make the hierarchy **configurable**
   or at least **open** to extension.

7. **The display order (`audioOrder = [SUB, DUB, HSUB, ANY]`) is
   hardcoded.** The new project should make this user-configurable (the
   owner might prefer DUB first, for example).

**Concrete recommendation for the new project:** extract the resolver
into its own Gradle module (`:feature:video-resolver`) with a
`VideoResolver` interface that extensions can implement to provide
custom parsing/grouping. The default implementation uses the
`VideoTitleParser` regex approach. Extensions that want richer metadata
(e.g., "this video is HDR", "this audio is 5.1 surround") can declare
it via a capability mechanism (per ADR-022 — capability declaration
system).

### 3.10 What the owner likes (KEEP)

- **Resolver appears BETWEEN episode tap and player open** — not direct
  play. Gives the user choice over server/audio/quality.
- **3-tier hierarchy: Server → Audio → Quality** — matches how users
  think about streaming sources.
- **Collapsible server sections (accordion)** — only one expanded at a
  time keeps the sheet manageable.
- **Audio tag chips on server headers** — at-a-glance summary of what's
  inside each server.
- **Quality chip on the RIGHT** with play icon on the left — clean,
  scannable. No duplicate plain-text quality label.
- **SUB on the right** of the audio tag chips (reversed order) — the
  owner's specific preference.
- **No blue colors** — uses `surfaceVariant` / `secondaryContainer` /
  `outlineVariant` throughout. The owner explicitly requested this.
- **"Resolving video…" overlay** with a clear explanation ("Fetching
  servers from the extension") — sets expectations during the network
  call.
- **Cache-hit instant render + background re-resolve** — the picker
  shows instantly with a "Refreshing…" badge, then updates silently if
  the data changed (or just removes the badge if it didn't). This is
  the gold-standard pattern for cached network data.
- **`compareServerSections` uses `videoTitle + resolution`** (stable)
  instead of `videoUrl` (contains changing `localhost:PORT`) — the
  correct way to detect "data unchanged" for proxied video URLs.
- **Single-video auto-play** — if the episode has only 1 video, skip the
  picker entirely. Don't make the user tap once for no reason.
- **Offline-first** — if the episode is downloaded, play the local file
  directly without resolving. Skips the network entirely.

### 3.11 What to improve

- **`AudioVersion` is a closed enum** — needs to be extensible for new
  extensions (Raw, Commentary, multi-language dubs, etc.).
- **No way to set a default server/audio/quality preference.** The user
  has to pick every time. The new project should remember the last
  selection per anime (or globally) and pre-select it.
- **No "best quality" auto-pick option.** Some users want to always play
  the highest quality on the preferred server without tapping. Should
  be a preference.
- **The disk video cache is written but never read** — wasteful. Either
  read it (with a proxy-URL refresh step) or stop writing it.
- **No error state UI in the picker.** If `resolveVideos` throws, the
  picker just hides and shows a Toast. The new project should show an
  inline error state with a Retry button inside the sheet.
- **No "Open in browser" fallback** for when all servers fail. Some
  users want to fall back to the source website.
- **The `Resolving` overlay is a full-screen black-60% scrim** — this
  blocks the detail page entirely. Consider an inline skeleton in the
  episode row instead (showing a spinner where the play icon would be).
- **The `ServerVersionDropdowns.kt` player-side UI is a completely
  different design** (two side-by-side dropdowns) than the resolver
  bottom sheet. The new project should unify these — same data, same
  component, different surface (sheet on detail page, compact dropdown
  on player).

---

## 4. Cross-cutting findings & recommendations for the new project

### 4.1 Caching strategy — 3 tiers, multiple TTLs

The old project uses a multi-layered cache:

| Cache | Scope | TTL | Purpose |
|---|---|---|---|
| Local cache (PreferenceStore) | Single device | 24h (detail) / 5min (home) | Fastest; survives process death |
| Supabase shared cache | All devices | 30 min | Shared across user devices; reduces AniList API load |
| AniList API | Source of truth | n/a | Final fallback |
| In-memory episode cache (`DetailViewModel.companion.episodeCache`) | Single ViewModel scope | Process lifetime | Survives navigation; avoids re-fetch on back-nav |
| Disk episode cache (`EpisodeCacheStore`) | Single device | Persistent | Survives app restart; preserves enriched metadata |
| In-memory video cache (`DetailViewModel.companion.videoCache`) | Single ViewModel scope | 10 min | Avoids re-resolve when re-opening the same episode |
| Persistent source-match cache (`ext_match_$anilistId`) | Single device | Persistent | Skips the slow full-extension search on re-open |
| Refresh guard (`ext_last_refresh_$anilistId`) | Single device | 5 min | Prevents hammering on background refreshes |

The new project should preserve this layering — it's well-thought-out.
The only addition: a `MetadataResolver` (per ADR-011) that sits between
the caches and the UI, exposing per-field provenance so the user can see
where each piece of metadata came from.

### 4.2 The "currently resolving" UX pattern

Three different "loading" UX patterns coexist in the old project:

1. **`DetailState.Loading`** → bare `CircularProgressIndicator` centered
   on a black screen. **Bad** — jarring flash. Replace with a skeleton.
2. **`EpisodeState.Searching` / `LoadingEpisodes`** → small inline
   `CircularProgressIndicator(16dp) + Text("Searching extensions…")`
   beside the Episodes header. **Good** — non-blocking, informative.
3. **`VideoPickerState.Resolving`** → full-screen black-60% scrim with
   a centered card. **Acceptable** but blocks the detail page; could be
   inline.
4. **`isEnrichingMetadata`** → small `CircularProgressIndicator(12dp) +
   "Fetching metadata…" label` in a `surfaceVariant` pill beside the
   Episodes header. **Best** — non-blocking, clearly indicates what's
   happening without obscuring content.

The new project should standardize on the **pill-in-header** pattern
(#4) for all background operations, and use skeletons only for the
initial page load.

### 4.3 Compose gotchas the new project must remember

These are documented in comments throughout the old code:

1. **Remember `ColorScheme` generation** — calling `.toM3ColorScheme()` on
   every recomposition creates a new object → forces all children to
   recompose on every scroll frame. Wrap in `remember(coverColor, ...)`
   (`DetailScreen.kt:255-259`).
2. **Create new `SEpisode` objects when enriching metadata** — Compose's
   `LazyColumn` skips recomposition when it receives the same object
   references. Mutating in place doesn't trigger UI updates
   (`DetailViewModel.kt:427-468`).
3. **Extract bottom sheets into dedicated composables that receive state
   as a parameter** — inline `?.let { ModalBottomSheet(...) }` doesn't
   reliably recompose in long, complex functions
   (`EpisodeOptionsSheet.kt:46-58`).
4. **Don't use `BoxWithConstraints` inside `Row(height(IntrinsicSize.Min))`**
   — `SubcomposeLayout`'s intrinsic measurement isn't supported there.
   Use a fixed-width heuristic instead (`AudioPills.kt:29-34`).
5. **Use `stateIn(scope).collectAsState()` for preferences** — `remember
   { .get() }` captures the value once and never updates when the user
   changes settings in another screen (`DetailScreen.kt:100-102`).
6. **Re-read watched state on `ON_RESUME`** — the `changes` Flow alone
   isn't enough; when returning from `PlayerActivity`, the Flow may not
   have emitted yet. Use a `LifecycleEventObserver` for `ON_RESUME`
   (`DetailScreen.kt:84-94`).
7. **Compare video lists by stable fields, not by URL** — proxied
   `localhost:PORT` URLs change every resolution. Compare by
   `videoTitle + resolution` to detect "data unchanged"
   (`DetailViewModel.kt:689-710`).

### 4.4 Owner-flagged design preferences (summary)

Across all three screens, the owner's preferences cluster as:

| Preference | Where applied |
|---|---|
| Blurred cover at top of details | 8dp blur, 360dp tall banner |
| Vertical gradient overlay (transparent → dark) | `Brush.verticalGradient(black20% → transparent → background)` |
| Cover-color-tinted overlay | 20% alpha |
| Dynamic theming from cover color | HSL manipulation, full M3 ColorScheme |
| Watched episodes = B&W + blur | `RenderEffect.createColorFilterEffect` (saturation=0) + `Modifier.blur(2dp)`, alpha 0.55 |
| Alternating card backgrounds | `surfaceLow` even / `surfaceHigh` odd |
| No blue colors in resolver | Use `surfaceVariant` / `secondaryContainer` / `outlineVariant` |
| Quality chip on the RIGHT | Play icon left, weighted spacer, chip right |
| SUB on the right of audio tag chips | `audioTags.reversed()` |
| Resolver between tap and player | No direct play; always resolve first |
| 3-tier hierarchy: Server → Audio → Quality | Collapsible accordion, one expanded at a time |
| Smooth cache-hit + background re-resolve | Instant render + "Refreshing…" badge + silent update |
| Long-press bottom sheet | State-aware options, destructive coloring |
| 3-stage pull-to-refresh | 100dp / 200dp / 300dp → episodes / details / everything |

### 4.5 Owner-flagged improvements (summary)

| Improvement | Where |
|---|---|
| Skeleton loader for initial detail page load | Replace `CircularProgressIndicator` |
| Light-theme support for dynamic theming | Use `luminance()` for surface choices |
| Filler badge on episode rows | Display `SEpisode.fillermark` |
| "Currently watching" indicator | Highlight the current episode |
| Downloaded checkmark on thumbnail | Like Aniyomi |
| Extensible `AudioVersion` (open class / registry) | For new extensions |
| Default server/audio/quality preferences | Remember last selection per anime |
| "Best quality" auto-pick preference | Skip the picker for power users |
| Inline error state in resolver | Retry button inside the sheet |
| Unify resolver + player-side dropdowns | Same component, different surface |
| Localization | Moko Resources or strings.xml |
| Wider-screen variant of the header | Two-column for tablets/foldables |

---

## 5. Appendix — quick reference for the new project's design-language docs

### 5.1 Key composables to port (with their line counts)

| Composable | Lines | Port as |
|---|---|---|
| `DetailHeader` (blurred banner + gradient + thumbnail overlay) | ~190 | `AnimeDetailHeader` |
| `generateDynamicScheme` + `toM3ColorScheme` | ~200 | `CoverColorTheme` |
| `EpisodeRow` (swipe + click + long-press + grayscale wrapper) | ~330 | `EpisodeRow` |
| `EpisodeRowRich` / `EpisodeRowSimple` | ~595 | `EpisodeRowContent` (rich) + `EpisodeRowCompact` (simple) |
| `watchedEpisodeEffect` modifier | ~137 | `Modifier.watchedEpisodeEffect(appearance, alpha, blurRadius)` |
| `AudioPills` | ~89 | `AudioPills(hasSub, hasDub, hasHsub, hasRaw?, …)` |
| `AiringPill` | ~81 | `NextEpisodePill` |
| `VideoPickerSheet` + `ServerHeader` + `AudioSubHeader` + `VideoRow` | ~362 | `VideoResolverSheet` |
| `VideoTitleParser` + `groupVideosByServer` | ~146 | `VideoResolver` (extract to `:feature:video-resolver`) |
| `EpisodeMetadataFetcher` (4-source parallel enrichment) | ~458 | `EpisodeMetadataResolver` (per ADR-011) |
| `ThreeStagePullRefresh` | ~180 | `ThreeStagePullRefresh` (novel — keep) |
| `EpisodeOptionsSheet` | ~207 | `EpisodeOptionsSheet` |
| `DownloadButtonTall` | ~183 | `DownloadButton` |

### 5.2 Key data models to port

```kotlin
// From AniListModels.kt
data class AniListAnime(
    val id: Int,
    val idMal: Int? = null,
    val title: AniListTitle,
    val coverImage: AniListCoverImage,  // includes `color: String?` for dynamic theming
    val bannerImage: String? = null,
    val description: String? = null,
    val averageScore: Int? = null,
    val episodes: Int? = null,
    val genres: List<String>? = null,
    val season: String? = null,
    val seasonYear: Int? = null,
    val format: String? = null,
    val status: String? = null,
    val nextAiringEpisode: AniListNextAiring? = null,
    val streamingEpisodes: List<AniListStreamingEpisode>? = null,
)

// From VideoTitleParser.kt — extend AudioVersion for new extensions
data class ServerSection(val serverName: String, val audioSections: List<AudioSubSection>)
data class AudioSubSection(val audio: AudioVersion, val videos: List<Video>)

// From Grayscale.kt
enum class WatchedEpisodeAppearance { NONE, GRAYSCALE, BLUR, BOTH }

// From DetailViewModel.kt
sealed class VideoPickerState {
    data object Hidden : VideoPickerState()
    data class Resolving(val episode: SEpisode) : VideoPickerState()
    data class Cached(val episode: SEpisode, val serverSections: List<ServerSection>, val isRefreshing: Boolean) : VideoPickerState()
    data class Show(val episode: SEpisode, val serverSections: List<ServerSection>) : VideoPickerState()
}
```

### 5.3 Reference paths (for the new project's design-language docs)

- Blurred-cover banner: `OLD_ANIKUTA/ANIKUTA_OLD/app/src/main/java/app/anikuta/ui/detail/DetailScreen.kt:755-946`
- Gradient overlay: `OLD_ANIKUTA/ANIKUTA_OLD/app/src/main/java/app/anikuta/ui/detail/DetailScreen.kt:788-801`
- Dynamic theming: `OLD_ANIKUTA/ANIKUTA_OLD/app/src/main/java/app/anikuta/ui/detail/DynamicTheming.kt:1-200`
- Watched B&W + blur effect: `OLD_ANIKUTA/ANIKUTA_OLD/app/src/main/java/app/anikuta/ui/detail/components/Grayscale.kt:85-116`
- Episode row container (swipe + grayscale wrapper): `OLD_ANIKUTA/ANIKUTA_OLD/app/src/main/java/app/anikuta/ui/detail/components/EpisodeRow.kt:109-260`
- Episode row rich content: `OLD_ANIKUTA/ANIKUTA_OLD/app/src/main/java/app/anikuta/ui/detail/components/EpisodeRowContent.kt:144-377`
- Video resolver bottom sheet: `OLD_ANIKUTA/ANIKUTA_OLD/app/src/main/java/app/anikuta/ui/detail/VideoPickerSheet.kt:63-362`
- Video title parser: `OLD_ANIKUTA/ANIKUTA_OLD/app/src/main/java/app/anikuta/ui/detail/VideoTitleParser.kt:51-137`
- Episode metadata enrichment (4 sources): `OLD_ANIKUTA/ANIKUTA_OLD/app/src/main/java/app/anikuta/data/metadata/EpisodeMetadataFetcher.kt:66-155`
- AniList GraphQL `animeDetails` query: `OLD_ANIKUTA/ANIKUTA_OLD/app/src/main/java/app/anikuta/data/anilist/api/AniListQueries.kt:114-140`

---

**End of analysis.** This file feeds:
- `DESIGN_LANGUAGE/04-screens/anime-details.md` (screen 1)
- `DESIGN_LANGUAGE/04-screens/episode-list.md` (screen 2)
- `DESIGN_LANGUAGE/04-screens/video-resolver.md` (screen 3)
- `DESIGN_LANGUAGE/02-components/blurred-cover-banner.md`
- `DESIGN_LANGUAGE/02-components/dynamic-color-theme.md`
- `DESIGN_LANGUAGE/02-components/watched-episode-effect.md`
- `DESIGN_LANGUAGE/02-components/audio-pills.md`
- `DESIGN_LANGUAGE/02-components/three-stage-pull-refresh.md`

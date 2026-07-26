# Aniyomi 1DM Reference Analysis

> **Source:** `_REFERENCES/ANIYOMI_REFRENCE/ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/`
> **Date:** 2026-07-25
> **Agent:** Agent 2

---

## Key Files Analyzed

| File | Purpose |
|---|---|
| `data/download/anime/AnimeDownloader.kt` | The download engine — internal + external |
| `data/download/anime/AnimeDownloadManager.kt` | Queue management, state machine |
| `data/download/anime/AnimeDownloadStore.kt` | Persists the queue |
| `data/download/anime/AnimeDownloadProvider.kt` | File path resolution |
| `data/download/anime/AnimeDownloadCache.kt` | What's downloaded cache |
| `presentation/more/settings/screen/SettingsDownloadScreen.kt` | Settings UI |

---

## External Downloader Architecture

### Intent Construction (1DM)

```kotlin
// From AnimeDownloader.kt:723
pkgName.startsWith("idm.internet.download.manager") -> {
    val headers = (video.headers ?: source.headers).toMap()
    val bundle = Bundle()
    for ((key, value) in headers) {
        bundle.putString(key, value)
    }
    intent.apply {
        component = ComponentName(pkgName, "idm.internet.download.manager.Downloader")
        action = Intent.ACTION_VIEW
        data = video.videoUrl.toUri()
        putExtra("extra_filename", "$filename.mkv")
        putExtra("extra_headers", bundle)
    }
}
```

### Intent Construction (ADM)

```kotlin
// From AnimeDownloader.kt:755
pkgName.startsWith("com.dv.adm") -> {
    val headers = (video.headers ?: source.headers).toList()
    val bundle = Bundle()
    headers.forEach { a ->
        bundle.putString(a.first, a.second.replace("http", "h_ttp"))
    }
    intent.apply {
        component = ComponentName(pkgName, "$pkgName.AEditor")
        action = Intent.ACTION_VIEW
        putExtra("com.dv.get.ACTION_LIST_ADD", "${video.videoUrl.toUri()}<info>$filename.mkv")
        putExtra("com.dv.get.ACTION_LIST_PATH", tmpDir.filePath!!.substringBeforeLast("_"))
        putExtra("android.media.intent.extra.HTTP_HEADERS", bundle)
    }
}
```

### Completion Check

```kotlin
// From AnimeDownloader.kt:790
private fun isDownloadSuccessful(download, tmpDir): Boolean {
    val downloadedVideo = tmpDir.listFiles().orEmpty().filterNot { it.extension == ".tmp" }
    return downloadedVideo.size == 1
}
```

### Supported Package Names

```kotlin
// From SettingsDownloadScreen.kt:365
"idm.internet.download.manager"         // 1DM
"idm.internet.download.manager.plus"    // 1DM+
"idm.internet.download.manager.adm.lite" // 1DM ADM Lite
"com.dv.adm"                            // ADM
```

---

## Settings UI

Aniyomi's `SettingsDownloadScreen.kt` has `getExternalDownloaderGroup()`:

1. **Toggle** — `useExternalDownloader()` (SwitchPreference)
2. **Dropdown** — `externalDownloaderSelection()` (ListPreference)
   - Entries: installed supported downloaders (detected via PackageManager)
   - Map: `""` → "None", `pkgName` → app label

---

## Key Differences from ANIKUTA

| Aspect | Aniyomi | ANIKUTA (current) | ANIKUTA (1DM plan) |
|---|---|---|---|
| Internal downloader | OkHttp streaming | OkHttp + HLS (segment concat) | Keep as DEFAULT method |
| External downloader | 1DM/ADM via Intent | Not implemented | Phase 1: implement |
| HLS support | Internal: no; External: via 1DM's ffmpeg | Internal: segment concat (unencrypted) | External: 1DM (encrypted + DASH) |
| Progress monitoring | Fire-and-forget (poll for file) | Real-time (byte-level) | Phase 1: poll; Phase 2: content provider |
| File location | 1DM's dir (manual move) | ANIKUTA SAF folder (auto) | Phase 1: move after completion |
| Resume | 1DM handles it | No (re-download from start) | External: 1DM; Phase 3: internal |
| Multi-threaded | 1DM handles it | No (single-thread) | External: 1DM; Phase 3: internal |

---

## Lessons Learned

1. **Fire-and-forget is acceptable for MVP** — Aniyomi ships with it. Users
   accept that 1DM manages the download; they check 1DM for progress.
2. **Intent extras are downloader-specific** — 1DM and ADM have different APIs.
   We need per-downloader Intent construction.
3. **Package detection** — use `PackageManager.getInstalledPackages()` to detect
   which downloaders are installed. Show only installed ones in the dropdown.
4. **File management** — 1DM downloads to its own directory. We need to either:
   - Pass the target directory to 1DM (if supported), OR
   - Move the file after 1DM reports completion
5. **Headers** — both 1DM and ADM accept HTTP headers via a Bundle. This is
   critical for extension video URLs that require Referer/User-Agent headers.

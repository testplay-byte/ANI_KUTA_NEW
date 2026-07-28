# 03-subsystems / torrent-streaming — Torrserver integration (anime)

> Streaming anime episodes from BitTorrent torrents via an in-process
> Torrserver. Anime-only — there is no manga equivalent. A torrent-aware
> source returns `Video` objects whose `videoUrl` is a `magnet:` URI,
> a `.torrent` file URL, or a `content://` URI to a local torrent file;
> Aniyomi hands that URL to Torrserver, which downloads the torrent and
> exposes a seekable HTTP stream that MPV (for playback) or FFmpeg (for
> download) can consume.

## Overview

```
   Anime source (extension APK)
       │  returns Video(videoUrl = "magnet:?xt=urn:btih:…")
       ▼
   PlayerActivity or AnimeDownloader
       │  torrentPreferences.torrServerEnable().get() == true
       ▼
   TorrentServerService.start()                  ← Android foreground service
       │  TorrServer.startServer(port, path, proxyMode, proxyUrl)
       ▼
   TorrentServerApi.addTorrent(magnet, title, …) ← POST /torrents (JSON RPC)
       │  Torrserver joins the swarm, starts downloading
       ▼
   TorrentServerUtils.getTorrentPlayLink(torrent, index)
       │  → http://127.0.0.1:<port>/stream/<name>?link=<hash>&index=<n>&play
       ▼
   MPVLib.command(["loadfile", streamUrl, …])    (playback)
       or
   FFmpegKit.executeWithArgumentsAsync(remux)    (download → .mkv)
```

Torrserver handles all the BitTorrent protocol details — DHT, peer
connections, piece selection, file prioritisation. Aniyomi just sees an
HTTP URL that serves the requested file as a seekable stream, so MPV and
FFmpeg can treat it like any other HTTP video.

## The moving parts

| Component | File (relative to `../ANIYOMI/`) | Role |
|---|---|---|
| `TorrServer` (JNA bindings) | external `xyz.secozzi.torrserver` library | Go-based Torrserver binary wrapped for Android. `startServer(port, path, proxyMode, proxyUrl)`, `stopServer()`, `addTrackers(csv)`, `registerLogCallback()`. |
| `TorrentServerService` | `app/src/main/java/eu/kanade/tachiyomi/data/torrent/service/TorrentServerService.kt` | Android foreground service. Starts/stops the Torrserver binary; shows a persistent "Torrent server is running" notification with a Stop action. |
| `TorrentServerApi` | `core/common/src/main/java/aniyomi/core/common/torrent/TorrentServerApi.kt` | HTTP client (`http://127.0.0.1:<port>`). `echo()`, `addTorrent(link, title, …)`, `uploadTorrent(inputStream, title, …)`. |
| `TorrentServerUtils` | `core/common/src/main/java/aniyomi/core/common/torrent/TorrentServerUtils.kt` | Builds the `/stream/<name>?link=<hash>&index=<n>&play` playback URL. Pushes the default tracker list into Torrserver on startup. |
| `TorrentPreferences` | `core/common/src/main/java/aniyomi/core/common/torrent/TorrentPreferences.kt` | `torrServerEnable`, `torrServerPort` (default `8090`), `torrServerTrackers` (a 25-line default list of anime-friendly tracker announce URLs), `torrServerProxyMode`, `torrServerProxyUrl`, `torrServerShownNotice`. |
| `TorrentUtils` | `source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/torrentutils/TorrentUtils.kt` | Source-side helper. `getTorrentInfo(url, title): TorrentInfo` — used by torrent sources to enumerate files in a torrent. |
| `TorrentInfo` / `TorrentFile` | `source-api/src/commonMain/.../torrentutils/model/` | Data classes. `TorrentFile.toMagnetURI()` builds a `magnet:?xt=urn:btih:<hash>&tr=<tracker>&index=<n>` URI for a single file. |
| `ProxyMode` enum | `core/common/src/main/java/aniyomi/core/common/torrent/TorrentPreferences.kt` | `None`/`Tracker`/`Peers`/`Full` — controls whether Torrserver uses the configured proxy for tracker announces, peer connections, or all traffic. |

## The Torrserver integration

### Starting the server

`TorrentServerService` is started via `Intent` action `ACTION_START`.
It runs as a **foreground service** with type
`FOREGROUND_SERVICE_TYPE_DATA_SYNC` (Android Q+) and posts an ongoing
notification so the OS doesn't kill it. The notification has a Stop
action that fires `ACTION_STOP`, which calls `TorrServer.stopServer()`
and `stopSelf()`.

```
private fun startServer() = serviceScope.launch {
    if (api.echo() == "") {                              // server not running yet
        if (networkPreferences.verboseLogging().get()) TorrServer.registerLogCallback()
        val proxyMode = torrentPreferences.torrServerProxyMode().get()
        val port = TorrServer.startServer(
            port = torrentPreferences.torrServerPort().get(),     // default 8090
            path = filesDir.absolutePath,                         // Torrserver stores DB / cache here
            proxyMode = proxyMode.value,
            proxyUrl = if (proxyMode == ProxyMode.None) "" else torrentPreferences.torrServerProxyUrl().get(),
        )
        if (port != -1) {
            api.setPort(port)                                     // tell the API what port it actually got
            wait(10)                                              // wait up to 10 s for /echo to respond
            torrentServerUtils.setTrackersList()                  // push the default tracker list
        }
    }
}
```

`TorrServer.startServer` returns the actual bound port (which may
differ from the requested one if it was in use). The `TorrentServerApi`
holds this port as a `@Volatile var port` and uses it to build every
URL (`http://127.0.0.1:$port`). The companion object exposes a static
`start()` / `stop()` / `wait(timeout)` so callers (the player, the
downloader) don't need an `Intent` — they just call
`TorrentServerService.start()` and the service starts itself.

### Adding a torrent

`TorrentServerApi.addTorrent(link, title, poster, data, save)` POSTs a
JSON body `{"action":"add","link":...,"title":...,"poster":...,"data":...,"save_to_db":...}`
to `/torrents`. The response is the `Torrent` model (title, hash,
fileStats, torrentSize, trackers). `save = false` means Torrserver
won't persist the torrent across restarts — Aniyomi adds torrents
ephemerally, only for the current playback/download session.

`uploadTorrent(inputStream, title, save)` is the alternative for
content-URI torrents: POSTs a multipart body with the `.torrent` file
bytes to `/torrent/upload`.

### The stream URL

`TorrentServerUtils.getTorrentPlayLink(torrent, index)` returns:

```
http://127.0.0.1:<port>/stream/<filename>?link=<hash>&index=<index>&play
```

The `<filename>` is the on-disk name of the file at `index` in the
torrent (or the torrent's title if the index doesn't match a file). The
`&play` query parameter tells Torrserver to start streaming immediately
(seeking is supported via HTTP Range requests). MPV loads this URL with
its built-in HTTP demuxer; FFmpeg demuxes it into a `.mkv`.

## How a torrent source provides episodes

A torrent-aware source returns `Video` objects whose `videoUrl` is one
of:

- `magnet:?xt=urn:btih:<hash>&dn=<name>&tr=<tracker>&index=<n>` — a
  magnet URI with an optional `index=` query parameter selecting a
  specific file inside the multi-file torrent.
- `https://example.com/foo.torrent` — a direct URL to a `.torrent`
  file.
- `http://127.0.0.1:<port>/stream/...?link=<hash>&index=<n>&play` —
  an already-resolved Torrserver stream URL (Aniyomi recognises these
  and skips re-adding the torrent).
- `content://...` — a local `.torrent` file the user picked via SAF.

To enumerate the files inside a torrent (so the source can build the
right `Video` list), the source calls
`TorrentUtils.getTorrentInfo(url, title)` from `:source-api`. That
method calls `torrentServerApi.addTorrent(url, title, "", "", false)`,
maps the resulting `Torrent.fileStats` to a `List<TorrentFile>`, and
returns a `TorrentInfo(title, files, hash, size, trackers)`.
`TorrentFile.toMagnetURI()` then builds a
`magnet:?xt=urn:btih:<hash>&tr=<trackers>&index=<n>` URI for each file.
The source picks the file whose name matches the episode (e.g.
`[Group] Anime Title - 01 [1080p].mkv`) and stuffs that magnet into a
`Video`.

`TorrentUtils` throws `DeadTorrentException` (on `SocketTimeoutException`
— the swarm has no seeders) or `DisabledTorrServerException` (any
other failure — Torrserver isn't running or isn't responding). These
are caught by the source and surfaced to the user as "torrent failed"
errors.

## How the player consumes a torrent

Source: `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt`, lines ~1086–1160.

When the user picks a video, `PlayerActivity.setVideo(...)` checks
whether Torrserver is enabled AND the URL looks torrent-like (starts
with the Torrserver host URL, `magnet`, or ends with `.torrent`). If
so, it launches `TorrentServerService.start()` and `torrentLinkHandler`:

1. If the URL is `content://` (a local torrent file) → opens it via
   `ContentResolver`, calls `torrentServerApi.uploadTorrent(...)`, then
   `MPVLib.command(["loadfile", getTorrentPlayLink(torrent, 0), ...])`.
2. If the URL is `magnet:?...&index=<n>` → parses the `index=` query
   param to know which file inside the multi-file torrent to stream.
3. Calls `torrentServerApi.addTorrent(videoUrl, title, "", "", false)`
   → returns a `Torrent` with the resolved hash.
4. Calls `torrentServerUtils.getTorrentPlayLink(torrent, index)` →
   returns the `/stream/...?link=<hash>&index=<n>&play` URL.
5. `MPVLib.command(["loadfile", streamUrl, "replace", "0", videoOptions])`
   — MPV loads the stream and starts playback while Torrserver continues
   downloading in the background. The user can seek within the
   downloaded portion; seeking past it triggers Torrserver to
   prioritise the requested pieces.

If `torrServerEnable` is `false`, the player falls through to the
`else` branch and tries to play the magnet URL directly with MPV —
which fails (MPV doesn't natively understand magnet URIs). This is
intentional: torrents are opt-in.

## How the downloader consumes a torrent

Source: `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/download/anime/AnimeDownloader.kt`,
`torrentDownload()` (lines ~521–548).

The downloader mirrors the player logic but feeds the stream URL to
FFmpeg instead of MPV:

1. `TorrentServerService.start()`.
2. If the URL was already a Torrserver stream URL, decode `link=` /
   `index=` back into a magnet URI.
3. `torrentServerApi.addTorrent(magnet, title, ...)` → `Torrent`.
4. `torrentServerUtils.getTorrentPlayLink(torrent, index)` → stream URL.
5. `ffmpegDownload(...)` — FFmpeg remuxes the HTTP stream to `.mkv`
   (with `-c:v copy -c:a copy -c:s copy`, no re-encode). See
   [`download-manager.md`](download-manager.md) for the full FFmpeg flow.

## The local NanoHTTPD bridge

The `AnimeHttpSource.server: HttpServer?` field (ext-lib-17, declared in
`source-api/src/commonMain/.../animesource/model/HttpServer.kt`) is
**separate** from Torrserver. It's a per-source `NanoHTTPD(0)` (random
port) that a source can spin up to proxy or rewrite URLs before handing
them to MPV — for example, to inject custom headers, transform a
playlist URL, or serve a local file. The source starts the server when
needed; the app closes it after the playback/download session ends (see
`AnimeDownloader.stopHttpServer`). Don't confuse it with Torrserver,
which is app-global and only handles BitTorrent.

## Caveats

- **Torrserver is opt-in** (`Settings → Player → Torrent streaming →
  Enable Torrserver`). Off by default — torrent sources appear in
  browse but their videos won't play until the user turns the toggle
  on and accepts the first-run notice (`torrServerShownNotice`).
- **Tracker list is configurable** — `TorrentPreferences.torrServerTrackers()`
  ships with a default list of 25 public anime torrent trackers
  (nyaa.tracker.wf, anidex.moe, tracker.anirena.com, etc.). Editable.
- **Torrserver uses disk space** — it caches downloaded pieces in
  `filesDir` (the app's private dir). Aniyomi doesn't automatically
  clean this up; the user can clear it from `Settings → Storage →
  Clear cache`.
- **No background downloading** — torrent streams only run while the
  player or downloader is active. There's no WorkManager job to keep
  seeding or pre-download torrents in the background.

## Key files

| File (relative to `../ANIYOMI/`) | Role |
|---|---|
| `app/src/main/java/eu/kanade/tachiyomi/data/torrent/service/TorrentServerService.kt` | Foreground service that starts/stops Torrserver and shows the "running" notification. |
| `core/common/src/main/java/aniyomi/core/common/torrent/TorrentServerApi.kt` | HTTP client for Torrserver (`echo`, `addTorrent`, `uploadTorrent`). Holds the dynamic port. |
| `core/common/src/main/java/aniyomi/core/common/torrent/TorrentServerUtils.kt` | Builds the `/stream/<name>?link=<hash>&index=<n>&play` playback URL. Pushes the default tracker list on startup. |
| `core/common/src/main/java/aniyomi/core/common/torrent/TorrentPreferences.kt` | All Torrserver preferences (enable, port, trackers, proxy mode, proxy URL, shown-notice flag). |
| `source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/torrentutils/TorrentUtils.kt` | Source-side helper: `getTorrentInfo(url, title): TorrentInfo`. |
| `source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/torrentutils/model/TorrentInfo.kt` | `TorrentInfo(title, files, hash, size, trackers)` + `DeadTorrentException`. |
| `source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/torrentutils/model/TorrentFile.kt` | `TorrentFile(path, indexFile, size, torrentHash, trackers)` + `toMagnetURI()`. |
| `app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt` | `setVideo()` + `torrentLinkHandler()` — the playback path. |
| `app/src/main/java/eu/kanade/tachiyomi/data/download/anime/AnimeDownloader.kt` | `torrentDownload()` — the download path. |
| `app/src/main/java/eu/kanade/presentation/more/settings/screen/player/PlayerSettingsTorrentScreen.kt` | The Compose settings screen for torrent streaming preferences. |
| `app/src/main/AndroidManifest.xml` | Declares `TorrentServerService` (line ~279). |

## See also

- [`anime-player.md`](anime-player.md) — the MPV-based player that
  consumes the Torrserver stream URL.
- [`download-manager.md`](download-manager.md) — the anime download
  pipeline (the `torrentDownload` path is one of three strategies).
- [`source-system.md`](source-system.md) — how a torrent-aware anime
  extension is loaded and registered.
- [`../02-modules/source-api.md`](../02-modules/source-api.md) — the
  `Video` model, the `torrentutils/` package, and the `HttpServer`
  field on `AnimeHttpSource` (ext-lib-17).
- [`../02-modules/core-common.md`](../02-modules/core-common.md) — the
  `aniyomi.core.common.torrent` package overview.

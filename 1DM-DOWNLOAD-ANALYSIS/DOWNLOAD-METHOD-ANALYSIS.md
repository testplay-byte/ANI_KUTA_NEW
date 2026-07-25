# Download Method Analysis — OLD_ANIKUTA vs Current ANIKUTA

> **Date:** 2026-07-25
> **Agent:** Agent 2 (Downloads & Offline Playback)
> **Status:** Analysis complete. Decision documented. Implementation pending.

---

## 1. OLD_ANIKUTA's Approach: FFmpegKit Single-Pass

The OLD_ANIKUTA project uses **FFmpegKit** (`com.arthenica.ffmpegkit`) for ALL
downloads — both HLS and direct video. The `SinglePassDownloadEngine` makes a
single FFmpeg call:

```
ffmpeg -headers '...' -i "URL" -map 0:v -map 0:a? -map 0:s? -c copy -f matroska output.mkv
```

### How it works:
1. **FFmpeg's HLS demuxer** fetches the `.m3u8` playlist natively (not OkHttp).
2. FFmpeg downloads `.ts` segments through its own HTTP stack (handles CDN auth,
   redirects, cookies).
3. FFmpeg muxes everything into one `.mkv` with correct duration + size.
4. **Progress** is tracked by polling the output file size every 500ms.
5. **Size estimation**: quality-based bitrate (1080p=400KB/s, 720p=150KB/s,
   else=50KB/s) × FFprobe duration. Refined during download (never shrinks
   below current size, only shrinks after 30% progress with 5 consecutive votes).

### Pros:
- ✅ **Handles ALL formats**: HLS, DASH, direct video, encrypted HLS (AES-128).
- ✅ **Proper muxing**: output is a valid `.mkv` with correct duration, size, streams.
- ✅ **Subtitle embedding**: FFmpeg maps subtitle tracks into the container.
- ✅ **No manual segment parsing**: FFmpeg's demuxer handles everything.
- ✅ **Proven approach**: this is exactly what Aniyomi uses.

### Cons:
- ⚠️ **APK size**: FFmpegKit adds ~15-30 MB (arm64-v8a only is ~15 MB).
- ⚠️ **No resume**: if the download is interrupted, it restarts from scratch
  (FFmpeg doesn't support resume for HLS). Aniyomi has the same limitation.
- ⚠️ **FFmpeg logs**: verbose; needs `disableRedirection()` during FFprobe calls.
- ⚠️ **Speed**: FFmpeg's HLS demuxer downloads sequentially (no multi-threading
  for HLS). Direct video can be multi-threaded via our AdvancedHttpDownloader.

---

## 2. Current ANIKUTA's Approach: OkHttp + Manual HLS

Our current approach:
- **Direct video**: OkHttp streaming (Normal) or multi-threaded Range requests
  (Advanced).
- **HLS**: Manual `.m3u8` parsing + segment downloading + concatenation into `.ts`.
- **Encrypted HLS**: Rejected (no AES-128 decryption).
- **DASH**: Rejected (no ffmpeg).

### Pros:
- ✅ **No APK size increase**: no FFmpegKit dependency.
- ✅ **Multi-threaded direct video**: AdvancedHttpDownloader uses parallel Range
  requests + resume.
- ✅ **Modular**: separate HlsDownloader, HttpDownloader, AdvancedHttpDownloader.

### Cons:
- ⚠️ **HLS is fragile**: manual segment parsing can fail on edge cases
  (discontinuities, ad breaks, fMP4 init segments, encrypted playlists).
- ⚠️ **No encrypted HLS**: AES-128 HLS is rejected.
- ⚠️ **No DASH**: `.mpd` streams are rejected.
- ⚠️ **No subtitle embedding**: subtitles are stored as separate files, not
  muxed into the container.
- ⚠️ **Size estimation is less accurate**: we don't have FFprobe to get the
  real duration/bitrate. Our DynamicProgressTracker uses heuristics.

---

## 3. Decision

**For now: keep the current OkHttp approach, but improve it.**

**Rationale:**
1. The owner said: "if you feel like there is something which you could improve
   in the current download system then let's improve it but if it is not doable
   then no worries. We will afterwards implement the 1DM method."
2. Adding FFmpegKit is a ~15-30 MB APK size increase — a significant trade-off
   that the owner should explicitly approve before we proceed.
3. The current OkHttp approach works for the majority of anime (unencrypted HLS
   + direct video). The main failure case is encrypted HLS, which is less common.
4. The 1DM method (Phase 1 of the 1DM plan) will handle encrypted HLS + DASH
   via the external 1DM app (which has ffmpeg). This is a better trade-off than
   bundling ffmpeg ourselves.

**Improvements to make now (without FFmpegKit):**
1. ✅ Better size estimation (quality-based bitrate × episode duration, like
   the old project does — already partially done via DynamicProgressTracker).
2. ✅ Better progress tracking (90% cap, unknown-total estimation — already done).
3. ✅ Faster direct-video downloads (AdvancedHttpDownloader with multi-threading —
   already done).
4. ⚠️ HLS resume: the current HlsDownloader doesn't support resume. The
   AdvancedHttpDownloader does (for direct video). HLS resume is complex
   (need to track which segments were downloaded) — deferred to the 1DM method.

**Future: 1DM method (Phase 1 of the 1DM plan)**
- Delegates to 1DM/ADM via Android Intent.
- 1DM has ffmpeg built in → handles encrypted HLS, DASH, all formats.
- No APK size increase (uses the user's installed 1DM app).
- This is the recommended path for full format support.

---

## 4. Questions for the Owner

1. **FFmpegKit**: are you OK with a ~15-30 MB APK size increase to bundle
   FFmpegKit for native HLS/DASH/encrypted-HLS support? OR do you prefer the
   1DM external-downloader approach (no size increase, but requires the user
   to install 1DM)?
2. **HLS resume**: the current HlsDownloader doesn't support resume (if the
   download is interrupted, it restarts). Is this acceptable for now, or do
   you want HLS resume before the 1DM method?

---

## 5. Logcat Filter

To see all download-related logs:

```
adb logcat -s AnikutaDownload:V AnikutaDownloadOrch:V AnikutaResolver:V
```

This shows:
- `AnikutaDownload` — the download engine (queue, downloader, storage, notifications)
- `AnikutaDownloadOrch` — the download orchestrator (resolve + enqueue)
- `AnikutaResolver` — the video resolver (shared with the watch flow)

Logs are minimal + structured (no sensitive data, no full request/response bodies).

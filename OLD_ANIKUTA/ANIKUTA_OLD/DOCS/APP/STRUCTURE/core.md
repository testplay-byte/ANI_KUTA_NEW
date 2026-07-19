# :core module — what we copied from aniyomi

> Step 1.3 — Copy `:core` from aniyomi's `:core:common`.
> Incremental copy: only the files needed for Steps 1.4–1.8 are copied now.
> Heavy optional deps (FFmpeg, libarchive, image-decoder, unifile, js-engine) are
> skipped — added later when needed.

---

## Source
- aniyomi module: `:core:common`
- aniyomi path: `REFERENCE/core/common/src/main/java/`
- aniyomi commit: `2f5cf77` (2025-11-05)
- Copied on: Session 15 (Step 1.3)

## What we copied (26 files)

### Preference system (7 files)
- `preference/Preference.kt` — preference interface + delegate
- `preference/PreferenceStore.kt` — preference store interface
- `preference/AndroidPreference.kt` — Android SharedPreferences impl
- `preference/AndroidPreferenceStore.kt` — Android store impl
- `preference/InMemoryPreferenceStore.kt` — in-memory store (for tests)
- `preference/CheckboxState.kt` — checkbox state enum
- `preference/TriState.kt` — tri-state enum

### Network layer (10 files)
- `network/NetworkHelper.kt` — OkHttp client + cookie jar + DoH
- `network/AndroidCookieJar.kt` — cookie jar using Android CookieManager
- `network/NetworkPreferences.kt` — network preference keys
- `network/Requests.kt` — HTTP request helpers (GET, POST, etc.)
- `network/OkHttpExtensions.kt` — Observable + coroutine extensions on Call
- `network/ProgressListener.kt` — download progress listener
- `network/ProgressResponseBody.kt` — progress-tracking response body
- `network/DohProviders.kt` — DNS-over-HTTPS providers (Cloudflare, Google, etc.)
- `network/interceptor/IgnoreGzipInterceptor.kt`
- `network/interceptor/RateLimitInterceptor.kt`
- `network/interceptor/SpecificHostRateLimitInterceptor.kt`
- `network/interceptor/UncaughtExceptionInterceptor.kt`
- `network/interceptor/UserAgentInterceptor.kt`

### Util/lang (4 files)
- `util/lang/CoroutinesExtensions.kt` — launchUI, launchIO, withIOContext
- `util/lang/RxCoroutineBridge.kt` — awaitSingle (Observable → coroutine)
- `util/lang/SortUtil.kt` — string collator sort
- `util/lang/BooleanExtensions.kt` — bool extensions

### Util/system (1 file)
- `util/system/LogcatExtensions.kt` — logging extensions

### Constants (1 file)
- `Constants.kt` — app constants (URLs, shortcut IDs)

## What we did NOT copy (added later when needed)

| File | Reason | When |
|------|--------|------|
| `i18n/Localize.kt` | We do our own strings (no moko-resources). | Never (our own) |
| `network/JavaScriptEngine.kt` | Heavy JS engine for Cloudflare bypass. | Later (with Cloudflare) |
| `network/interceptor/CloudflareInterceptor.kt` | Needs WebView. | Later (Phase 4+) |
| `network/interceptor/WebViewInterceptor.kt` | Needs WebView. | Later |
| `util/storage/*` (DiskUtil, FileExtensions, FFmpegUtils, UniFile*) | Need unifile + FFmpeg libs. | Phase 7 (downloads) |
| `util/system/*` (DeviceUtil, GLUtil, DensityExtensions, etc.) | UI-specific. | When needed |
| `util/lang/Hash.kt`, `StringExtensions.kt` | Not needed yet. | When needed |
| `security/SecurityPreferences.kt` | App-lock feature. | Later |
| `storage/*` (FolderProvider, AndroidStorageFolderProvider, etc.) | Need unifile. | Phase 7 |

## Changes made to copied files

1. **Package rename:** all `tachiyomi.core.common.*` → `app.anikuta.core.*`, all
   `eu.kanade.tachiyomi.network.*` → `app.anikuta.core.network.*`, all
   `eu.kanade.tachiyomi.core.*` → `app.anikuta.core.*`.
2. **Import rename:** all corresponding imports updated.
3. **Constants.kt:** string constants `eu.kanade.tachiyomi.*` → `app.anikuta.*`.
4. **NetworkHelper.kt:** `CloudflareInterceptor` commented out (needs WebView;
   TODO to add later).

## Dependencies added (to `:core` build.gradle.kts)

- `okhttp` (core + logging + brotli + dnsoverhttps) — 5.0.0-alpha.14
- `okio` — 3.10.2
- `rxjava` (1.x) — 1.3.8
- `logcat` — 0.1
- `kotlinx-coroutines` (core + android) — 1.10.1
- `kotlinx-serialization-json` (+ okio) — 1.9.0
- `androidx.preference:preference-ktx` — 1.2.1
- `jsoup` — 1.19.1

All exposed as `api` (except jsoup = `implementation`) since downstream modules
need them.

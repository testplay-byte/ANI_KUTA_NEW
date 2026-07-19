# 03-subsystems / App Self-Updater

> How Aniyomi checks GitHub for new app releases, prompts the user, and
> downloads + installs the APK — all without going through the Play Store
> (Aniyomi is not distributed via the Play Store).

This subsystem is **opt-in at build time** via the `UPDATER_ENABLED`
build-config flag. When off (the default), the entire flow is a no-op.

## Purpose

Aniyomi ships as a side-loaded APK. The self-updater:

1. Polls the GitHub releases API for the latest version.
2. Compares it to the running build's version.
3. If newer, posts a notification and (on app start) shows an in-app
   prompt with the changelog.
4. On user acceptance, downloads the new APK to the cache dir, then
   launches the system package installer.

It is **not** a silent auto-updater — the user must explicitly tap
"Download" and then "Install" for each release.

## The `UPDATER_ENABLED` flag

The flag is wired in three places:

| Layer | File | Value |
|---|---|---|
| Gradle property | `gradle.properties` or CI invocation | `-Penable-updater` sets the property |
| Convention plugin | `buildSrc/src/main/kotlin/mihon/buildlogic/BuildConfig.kt` | `val enableUpdater: Boolean = project.hasProperty("enable-updater")` (default `false`) |
| App build config | `app/build.gradle.kts` | `buildConfigField("boolean", "UPDATER_ENABLED", "${Config.enableUpdater}")` |
| Runtime helper | `app/.../util/system/BuildConfig.kt` | `val updaterEnabled: Boolean inline get() = BuildConfig.UPDATER_ENABLED` |

So by default — i.e. in a vanilla local build — the updater is **off**.
Official Aniyomi release and preview builds set `-Penable-updater` in CI,
turning it on. See [`../00-overview/04-build-system.md`](../00-overview/04-build-system.md).

When off, `MainActivity.CheckForUpdates` skips the check entirely:

```kotlin
LaunchedEffect(Unit) {
    if (updaterEnabled) {
        try {
            val result = AppUpdateChecker().checkForUpdate(context)
            if (result is GetApplicationRelease.Result.NewUpdate) {
                navigator.push(NewUpdateScreen(...))
            }
        } catch (e: Exception) { logcat(LogPriority.ERROR, e) }
    }
}
```

## Where it lives

```
app/src/main/java/eu/kanade/tachiyomi/data/updater/
├── AppUpdateChecker.kt       ← orchestrates the version check
├── AppUpdateDownloadJob.kt   ← WorkManager worker that downloads the APK
└── AppUpdateNotifier.kt      ← notifications (prompt, progress, install, error)

domain/src/main/java/tachiyomi/domain/release/
├── interactor/GetApplicationRelease.kt   ← throttle + version comparison
├── model/Release.kt                      ← the resolved release data class
└── service/ReleaseService.kt             ← interface (latest(arguments): Release?)

data/src/main/java/tachiyomi/data/release/
├── ReleaseServiceImpl.kt                 ← calls GitHub API, picks the right APK asset
└── GithubRelease.kt                      ← kotlinx.serialization model of the API response
```

## The release JSON it fetches

Aniyomi uses **GitHub's standard `releases/latest` REST endpoint**, not a
custom JSON file. From `ReleaseServiceImpl.latest()`:

```kotlin
networkService.client
    .newCall(GET("https://api.github.com/repos/${arguments.repository}/releases/latest"))
    .awaitSuccess()
    .parseAs<GithubRelease>()
```

The response model is `GithubRelease` (`data/release/GithubRelease.kt`):

```kotlin
@Serializable
data class GithubRelease(
    @SerialName("tag_name")  val version: String,        // e.g. "v0.18.1.2" or "r1234"
    @SerialName("body")      val info: String,           // the changelog markdown
    @SerialName("html_url")  val releaseLink: String,    // https://github.com/.../releases/tag/...
    @SerialName("assets")    val assets: List<GitHubAsset>,
)

@Serializable
data class GitHubAsset(
    val name: String,                                     // e.g. "aniyomi-arm64-v8a-v0.18.1.2.apk"
    @SerialName("browser_download_url") val downloadLink: String,
)
```

### Repository selection

`GITHUB_REPO` is resolved lazily in `AppUpdateChecker.kt`:

```kotlin
val GITHUB_REPO: String by lazy {
    if (isPreviewBuildType) "aniyomiorg/aniyomi-preview"
    else                    "aniyomiorg/aniyomi"
}
```

So **release** builds query `aniyomiorg/aniyomi` and **preview** builds
query `aniyomiorg/aniyomi-preview`. Each build type only ever sees its
own release track. The matching release tag is also computed lazily:

```kotlin
val RELEASE_TAG: String by lazy {
    if (isPreviewBuildType) "r${BuildConfig.COMMIT_COUNT}"
    else                    "v${BuildConfig.VERSION_NAME}"
}
```

(`RELEASE_TAG` and `RELEASE_URL` are not actually used by the check
itself; they're surfaced on the About screen for the "current version"
link.)

### Picking the right APK asset

`getDownloadLink(release)` matches assets to the device's preferred ABI:

```kotlin
private val BUILD_TYPES = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")

private fun getDownloadLink(release: GithubRelease): String? {
    val map = release.assets.associate { asset ->
        BUILD_TYPES.find { "-$it" in asset.name } to asset.downloadLink
    }
    return map[Build.SUPPORTED_ABIS[0]] ?: map[null]
}
```

So if the device's primary ABI is `arm64-v8a`, it looks for an asset whose
name contains `-arm64-v8a` (e.g. `aniyomi-arm64-v8a-v0.18.1.2.apk`). If
none exists, it falls back to an asset with no ABI suffix (the universal
APK). If neither exists, the release is treated as having no available
update (`return null`).

### Changelog post-processing

Before returning, the release body has GitHub `@mentions` rewritten to
markdown links, so the changelog renders nicely in the in-app prompt:

```kotlin
info = release.info.replace(gitHubUsernameMentionRegex) { mention ->
    "[${mention.value}](https://github.com/${mention.value.substring(1)})"
}
```

The regex matches a valid GitHub username per the GitHub Flavored Markdown
rules (alphanumeric + single hyphens, 1–39 chars, no leading/trailing
hyphen).

## The version-comparison logic

`GetApplicationRelease.isNewVersion()` (in `:domain`) is what decides
whether the fetched release is "newer" than the running build:

```kotlin
val newVersion = versionTag.replace("[^\\d.]".toRegex(), "")   // strip "v"/"r" prefixes
return if (isPreview) {
    // Preview builds are tagged "r<commitCount>"; a higher count = newer
    newVersion.toInt() > commitCount
} else {
    // Release builds are tagged "v<semver>"; compare component-wise
    val oldVersion = versionName.replace("[^\\d.]".toRegex(), "")
    val newSemVer = newVersion.split(".").map { it.toInt() }
    val oldSemVer = oldVersion.split(".").map { it.toInt() }
    oldSemVer.mapIndexed { index, i ->
        if (newSemVer[index] > i) return true
    }
    false
}
```

Two important quirks:

- **Preview builds compare by integer commit count**, not SemVer. A
  preview release tagged `r1234` is "newer" than a running build with
  `COMMIT_COUNT = 1230`.
- **Release builds compare SemVer component-wise but only in the
  forward direction** — the `mapIndexed { ... return true }` short-circuits
  on the first component where `new > old`, but if `new[i] < old[i]` it
  does **not** return false; it falls through. This means a downgrade
  tag would be silently treated as "not a new update" (the final
  `return false`), which is the desired behaviour.

## Throttling

`GetApplicationRelease` keeps a `last_app_check` long preference
(`Preference.appStateKey("last_app_check")`). On each call:

```kotlin
if (!arguments.forceCheck &&
    now.isBefore(Instant.ofEpochMilli(lastChecked.get()).plus(3, ChronoUnit.DAYS))
) {
    return Result.NoNewUpdate
}
```

So automatic checks (from `MainActivity` startup) are throttled to once
every **3 days**. Manual checks (the "Check for updates" button on the
About screen, which calls with `forceCheck = true`) bypass the throttle.
The `lastChecked` pref is updated only after a successful fetch.

## The update-check trigger

Two triggers:

1. **App startup** — `MainActivity.CheckForUpdates` `LaunchedEffect(Unit)`
   calls `AppUpdateChecker().checkForUpdate(context)` with
   `forceCheck = false`. Guarded by `if (updaterEnabled)`. Runs once per
   cold start, subject to the 3-day throttle.
2. **Manual** — Settings → About → "Check for updates" calls the same
   method with `forceCheck = true`. Toasts
   `update_check_no_new_updates` / `update_check_eol` / the exception
   message as appropriate.

There is **no periodic WorkManager job** for app updates — only the
on-startup check. (Library/extension updates *do* use periodic workers;
app updates do not, because GitHub rate-limits unauthenticated API
requests to 60/hour and a daily poll would be wasteful.)

## The result types

```kotlin
sealed interface Result {
    data class NewUpdate(val release: Release) : Result
    data object NoNewUpdate : Result
    data object OsTooOld : Result
}
```

`OsTooOld` is currently **never returned** — the check is commented out
in `AppUpdateChecker.checkForUpdate`:

```kotlin
// Disabling app update checks for older Android versions that we're going to drop support for
// if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
//    return GetApplicationRelease.Result.OsTooOld
// }
```

The About screen still handles it (toasts `update_check_eol`) for
forwards-compatibility.

## The `AppUpdateChecker` orchestrator

```kotlin
suspend fun checkForUpdate(context: Context, forceCheck: Boolean = false): Result {
    return withIOContext {
        val result = getApplicationRelease.await(
            GetApplicationRelease.Arguments(
                isPreviewBuildType,
                BuildConfig.COMMIT_COUNT.toInt(),
                BuildConfig.VERSION_NAME,
                GITHUB_REPO,
                forceCheck,
            ),
        )
        when (result) {
            is Result.NewUpdate -> AppUpdateNotifier(context).promptUpdate(result.release)
            else -> {}
        }
        result
    }
}
```

Note: **`AppUpdateChecker` is constructed ad-hoc** (`AppUpdateChecker()`),
not injected via Injekt. It uses `injectLazy` for `GetApplicationRelease`.
This is fine because it's stateless.

## The install flow

There are two paths from "new update detected" to "downloading":

### Path A: In-app prompt (startup check)

`MainActivity` pushes `NewUpdateScreen` with the release fields. The
screen shows version name, changelog (with the checksums section
stripped), and three actions:

| Button | Action |
|---|---|
| Open in browser | `context.openInBrowser(releaseLink)` |
| Reject | `navigator.pop()` |
| Accept | `AppUpdateDownloadJob.start(context, downloadLink, versionName)` then `navigator.pop()` |

### Path B: Notification (always, when `NewUpdate` is returned)

`AppUpdateChecker` always calls `AppUpdateNotifier.promptUpdate(release)`.
The notification has two actions:

| Action | Broadcasts | Effect |
|---|---|---|
| Download | `ACTION_START_APP_UPDATE` (extras: url, title) | `NotificationReceiver.startDownloadAppUpdate` → `AppUpdateDownloadJob.start(...)` |
| What's new | `ACTION_VIEW` on `release.releaseLink` | Opens the GitHub release page in the browser |

So the user can accept the in-app prompt (Path A) or tap the
notification later (Path B). Both end at `AppUpdateDownloadJob.start`.

## `AppUpdateDownloadJob` — the download worker

`data/updater/AppUpdateDownloadJob.kt` is a WorkManager `CoroutineWorker`:

```kotlin
class AppUpdateDownloadJob(context, workerParams) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        val url = inputData.getString(EXTRA_DOWNLOAD_URL)
        val title = inputData.getString(EXTRA_DOWNLOAD_TITLE) ?: appName
        setForeground(getForegroundInfo())   // DATA_SYNC foreground service
        withIOContext { downloadApk(title, url) }
        return Result.success()
    }
}
```

### `start(context, url, title)`

```kotlin
fun start(context, url, title?) {
    val request = OneTimeWorkRequestBuilder<AppUpdateDownloadJob>()
        .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
        .addTag(TAG)
        .setInputData(workDataOf(EXTRA_DOWNLOAD_URL to url, EXTRA_DOWNLOAD_TITLE to title))
        .build()
    context.workManager.enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, request)
}
```

`REPLACE` ensures only one download at a time — starting a new one
cancels any in-flight download. `stop(context)` cancels via
`workManager.cancelUniqueWork(TAG)`.

### The actual download

```kotlin
private suspend fun downloadApk(title, url) {
    notifier.onDownloadStarted(title)
    val progressListener = object : ProgressListener { ... throttled to 200ms ... }
    try {
        val response = network.client
            .newCachelessCallWithProgress(GET(url), progressListener)  // bypass HTTP cache
            .await()
        val apkFile = File(context.externalCacheDir, "update.apk")
        if (response.isSuccessful) {
            response.body.source().saveTo(apkFile)
        } else {
            response.close(); throw Exception("Unsuccessful response")
        }
        notifier.cancel()
        notifier.promptInstall(apkFile.getUriCompat(context))
    } catch (e: Exception) {
        val shouldCancel = e is CancellationException ||
            (e is StreamResetException && e.errorCode == ErrorCode.CANCEL)
        if (shouldCancel) notifier.cancel()
        else notifier.onDownloadError(url)
    }
}
```

Key details:

- **Cacheless OkHttp call** — `newCachelessCallWithProgress` deliberately
  bypasses the shared OkHttp cache so the APK is always fetched fresh.
- **Progress throttling** — the `ProgressListener` only calls
  `notifier.onProgressChange(progress)` when progress has increased AND
  at least 200ms have passed since the last notification update. This
  avoids flooding the notification manager.
- **Target file** — `context.externalCacheDir/update.apk`. The external
  cache is used (not internal) because the package installer needs to
  read the file via a content URI; `File.getUriCompat` exposes it via
  the app's `FileProvider`.
- **Cancellation** — `CancellationException` (from WorkManager) or an
  HTTP/2 `StreamResetException` with `ErrorCode.CANCEL` (from OkHttp
  when the call is cancelled mid-stream) are both treated as a silent
  dismissal. Any other exception becomes an error notification with a
  Retry action.

### Foreground service

`getForegroundInfo()` returns a `ForegroundInfo` with notification ID
`ID_APP_UPDATER`, the `onDownloadStarted` notification, and
`ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC` on Android Q+. This
allows the download to keep running when the app is backgrounded.

### Install prompt

On success, `notifier.promptInstall(apkUri)` posts a new notification on
`ID_APP_UPDATE_PROMPT` with an **Install** action that fires
`NotificationHandler.installApkPendingActivity(context, uri)`:

```kotlin
fun installApkPendingActivity(context, uri): PendingIntent {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, MangaExtensionInstaller.APK_MIME)   // "application/vnd.android.package-archive"
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
    }
    return PendingIntent.getActivity(context, 0, intent, FLAG_IMMUTABLE)
}
```

Tapping Install launches the system package installer, which takes over
the screen and shows the standard Android "Install / Cancel" dialog. The
`FLAG_GRANT_READ_URI_PERMISSION` is what lets the external installer
process read the APK from Aniyomi's cache via the FileProvider.

## Notifications

The full notification lifecycle is in `AppUpdateNotifier`. Four
notifications, all on `CHANNEL_APP_UPDATE`:

| Method | ID | Content | Actions |
|---|---|---|---|
| `promptUpdate(release)` | `ID_APP_UPDATER` | "Update available — <version>" | Download · What's new |
| `onDownloadStarted(title)` | `ID_APP_UPDATER` | "Downloading…" (ongoing, indeterminate) | Cancel |
| `onProgressChange(progress)` | `ID_APP_UPDATER` | "Downloading…" with `setProgress(100, progress, false)` | Cancel |
| `promptInstall(uri)` | `ID_APP_UPDATE_PROMPT` | "Download complete" | Install · Dismiss |
| `onDownloadError(url)` | `ID_APP_UPDATE_ERROR` | "Download error" | Retry · Cancel |

The Cancel action broadcasts `ACTION_CANCEL_APP_UPDATE_DOWNLOAD` →
`NotificationReceiver.cancelDownloadAppUpdate(context)` →
`AppUpdateDownloadJob.stop(context)`.

See [`notifications.md`](notifications.md) for the channel topology.

## Key files

| File | Role |
|---|---|
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/updater/AppUpdateChecker.kt` | Orchestrator: calls `GetApplicationRelease`, posts the prompt notification. Also defines `GITHUB_REPO`, `RELEASE_TAG`, `RELEASE_URL`. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/updater/AppUpdateDownloadJob.kt` | WorkManager `CoroutineWorker`; downloads the APK to `externalCacheDir/update.apk`, then prompts install. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/updater/AppUpdateNotifier.kt` | Five notification methods (prompt, started, progress, install, error). |
| `../ANIYOMI/domain/src/main/java/tachiyomi/domain/release/interactor/GetApplicationRelease.kt` | 3-day throttle + SemVer/commit-count comparison. Defines `Result` sealed interface. |
| `../ANIYOMI/domain/src/main/java/tachiyomi/domain/release/model/Release.kt` | `Release(version, info, releaseLink, downloadLink)`. |
| `../ANIYOMI/domain/src/main/java/tachiyomi/domain/release/service/ReleaseService.kt` | Interface: `suspend fun latest(arguments): Release?`. |
| `../ANIYOMI/data/src/main/java/tachiyomi/data/release/ReleaseServiceImpl.kt` | GitHub API call, ABI-asset selection, @mention → markdown-link rewrite. |
| `../ANIYOMI/data/src/main/java/tachiyomi/data/release/GithubRelease.kt` | kotlinx.serialization model of the GitHub `releases/latest` response. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/more/NewUpdateScreen.kt` | In-app update prompt screen. |
| `../ANIYOMI/app/src/main/java/eu/kanade/presentation/more/NewUpdateScreen.kt` | Compose presentation of the same. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/main/MainActivity.kt` | `CheckForUpdates` `LaunchedEffect` (the startup trigger). |
| `../ANIYOMI/app/src/main/java/eu/kanade/presentation/more/settings/screen/about/AboutScreen.kt` | Manual "Check for updates" button (`forceCheck = true`). |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/util/system/BuildConfig.kt` | `val updaterEnabled` inline helper. |
| `../ANIYOMI/buildSrc/src/main/kotlin/mihon/buildlogic/BuildConfig.kt` | `enableUpdater = project.hasProperty("enable-updater")`. |
| `../ANIYOMI/app/build.gradle.kts` | `buildConfigField("boolean", "UPDATER_ENABLED", ...)`. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/notification/NotificationReceiver.kt` | `ACTION_START_APP_UPDATE` / `ACTION_CANCEL_APP_UPDATE_DOWNLOAD` handlers. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/notification/NotificationHandler.kt` | `installApkPendingActivity(uri)` factory. |

## See also

- [`../00-overview/04-build-system.md`](../00-overview/04-build-system.md) — the `UPDATER_ENABLED` build flag and the `enable-updater` Gradle property.
- [`notifications.md`](notifications.md) — the `CHANNEL_APP_UPDATE` channel and the `AppUpdateNotifier` lifecycle.
- [`extensions-update.md`](extensions-update.md) — the parallel subsystem for **extension** updates (separate code, separate channels, similar shape).
- [`../02-modules/app.md`](../02-modules/app.md) — `App` startup, where the channel is created.
- [`../02-modules/data.md`](../02-modules/data.md) — `ReleaseServiceImpl` and `GithubRelease` live in `:data`.
- [`../02-modules/domain.md`](../02-modules/domain.md) — `GetApplicationRelease`, `Release`, `ReleaseService` live in `:domain`.

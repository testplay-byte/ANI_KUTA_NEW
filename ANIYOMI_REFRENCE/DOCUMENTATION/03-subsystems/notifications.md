# 03-subsystems / Notifications

> How Aniyomi talks to the user when it isn't on screen: the channel
> topology, the per-feature `*Notifier` classes that build notifications,
> and the global `BroadcastReceiver` that turns notification **actions**
> (buttons, taps, swipes) into app behaviour.

Android Oreo made notification **channels** mandatory, and Aniyomi leans
into that: each feature owns its channel(s), and the user can independently
mute, lower importance, or customise each one in the system settings.

## Channel topology

All channel and notification-ID constants live in
`data/notification/Notifications.kt`. Channels are created once, on app
startup, by `App.setupNotificationChannels()` → `Notifications.createChannels(context)`
(see [`../02-modules/app.md`](../02-modules/app.md) for where `App` is the
`Application` class).

```
Notifications.createChannels(context)
    │
    ├─ delete 9 deprecated channels (left over from older versions)
    │
    ├─ create 4 channel groups:
    │     GROUP_BACKUP_RESTORE    "Backup"
    │     GROUP_DOWNLOADER        "Downloader"
    │     GROUP_LIBRARY           "Library"
    │     GROUP_APK_UPDATES       "Updates"
    │
    └─ create 12 channels:

  COMMON                       LOW    (ungrouped)        "Common"
  LIBRARY_PROGRESS             LOW    GROUP_LIBRARY      "Progress"           no badge
  LIBRARY_ERROR                LOW    GROUP_LIBRARY      "Errors"             no badge
  NEW_CHAPTERS_EPISODES        DEFAULT (ungrouped)       "New chapters and episodes"
  DOWNLOADER_PROGRESS          LOW    GROUP_DOWNLOADER   "Progress"           no badge
  DOWNLOADER_ERROR             LOW    GROUP_DOWNLOADER   "Errors"             no badge
  BACKUP_RESTORE_PROGRESS      LOW    GROUP_BACKUP_RESTORE "Progress"         no badge
  BACKUP_RESTORE_COMPLETE      HIGH   GROUP_BACKUP_RESTORE "Complete"        no badge, silent
  INCOGNITO_MODE               LOW    (ungrouped)        "Incognito mode"
  TORRENT_SERVER               LOW    (ungrouped)        "Torrserver"         no badge
  APP_UPDATE                   DEFAULT GROUP_APK_UPDATES "App updates"
  EXTENSIONS_UPDATE            DEFAULT GROUP_APK_UPDATES "Extension updates"
```

A few things to note:

- **`IMPORTANCE_HIGH` is only used for `BACKUP_RESTORE_COMPLETE`**, and even
  then `setSound(null, null)` is applied so the high importance surfaces it
  as a heads-up without an audible alert.
- **`setShowBadge(false)`** is set on all the "progress / errors / silent"
  channels so the launcher icon badge isn't perpetually lit by background
  activity.
- The **deprecated channels list** is deleted every startup. This is how
  Aniyomi retires old channels without leaving zombie entries in the system
  settings screen. (E.g. `library_channel`, `crash_logs_channel`,
  `downloader_cache_renewal`.)
- The manga and anime **new-chapters/new-episodes** channels are merged
  into one (`CHANNEL_NEW_CHAPTERS_EPISODES`), but the per-message IDs are
  distinct: `ID_NEW_CHAPTERS = -301`, `ID_NEW_EPISODES = -1301`, and the
  group keys are separate strings
  (`eu.kanade.tachiyomi.NEW_CHAPTERS` / `...NEW_EPISODES`).

### Notification IDs

The IDs are mostly **negative** to avoid colliding with per-manga IDs
(which use `manga.id.hashCode()`):

| ID constant | Value | Channel | Posted by |
|---|---|---|---|
| `ID_DOWNLOAD_IMAGE` | `2` | COMMON | `SaveImageNotifier` |
| `ID_APP_UPDATER` | `1` | APP_UPDATE | `AppUpdateNotifier` (download progress) |
| `ID_APP_UPDATE_PROMPT` | `2` | APP_UPDATE | `AppUpdateNotifier` (install prompt) |
| `ID_APP_UPDATE_ERROR` | `3` | APP_UPDATE | `AppUpdateNotifier` (download error) |
| `ID_LIBRARY_PROGRESS` | `-101` | LIBRARY_PROGRESS | `MangaLibraryUpdateNotifier`, `AnimeLibraryUpdateNotifier` |
| `ID_LIBRARY_ERROR` | `-102` | LIBRARY_ERROR | same |
| `ID_LIBRARY_SIZE_WARNING` | `-103` | LIBRARY_PROGRESS | same |
| `ID_NEW_CHAPTERS` | `-301` | NEW_CHAPTERS_EPISODES | `MangaLibraryUpdateNotifier` (group summary + per-manga) |
| `ID_NEW_EPISODES` | `-1301` | NEW_CHAPTERS_EPISODES | `AnimeLibraryUpdateNotifier` |
| `ID_UPDATES_TO_EXTS` | `-401` | EXTENSIONS_UPDATE | `ExtensionUpdateNotifier` |
| `ID_EXTENSION_INSTALLER` | `-402` | EXTENSIONS_UPDATE | `MangaExtensionInstaller`, `AnimeExtensionInstaller` |
| `ID_BACKUP_PROGRESS` | `-501` | BACKUP_RESTORE_PROGRESS | `BackupNotifier` |
| `ID_BACKUP_COMPLETE` | `-502` | BACKUP_RESTORE_COMPLETE | `BackupNotifier` |
| `ID_RESTORE_PROGRESS` | `-503` | BACKUP_RESTORE_PROGRESS | `BackupNotifier` |
| `ID_RESTORE_COMPLETE` | `-504` | BACKUP_RESTORE_COMPLETE | `BackupNotifier` |
| `ID_DOWNLOAD_CHAPTER_PROGRESS` | `-201` | DOWNLOADER_PROGRESS | `MangaDownloadNotifier` |
| `ID_DOWNLOAD_CHAPTER_ERROR` | `-202` | DOWNLOADER_ERROR | `MangaDownloadNotifier` |
| `ID_DOWNLOAD_EPISODE_PROGRESS` | `-203` | DOWNLOADER_PROGRESS | `AnimeDownloadNotifier` |
| `ID_DOWNLOAD_EPISODE_ERROR` | `-204` | DOWNLOADER_ERROR | `AnimeDownloadNotifier` |
| `ID_INCOGNITO_MODE` | `-701` | INCOGNITO_MODE | `App` (process-scope) |
| `ID_TORRENT_SERVER` | `-801` | TORRENT_SERVER | Torrserver subsystem |

## The `*Notifier` classes

There is one notifier per feature. Each is a small, stateful class that
holds one or two `NotificationCompat.Builder` instances (lazily, via
`context.notificationBuilder(channel) { ... }`) and exposes `fun`s for
each lifecycle event it cares about.

| Notifier | File | Channel(s) | What it posts |
|---|---|---|---|
| `MangaDownloadNotifier` | `data/download/manga/MangaDownloadNotifier.kt` | DOWNLOADER_PROGRESS / ERROR | `dismissProgress`, `onProgressChange(MangaDownload)`, `onPaused`, `onComplete`, `onWarning(reason, ...)`, `onError(error, chapter, mangaTitle, mangaId)`. The progress notification has Resume / Pause / Clear actions; error notifications carry a Retry action. |
| `AnimeDownloadNotifier` | `data/download/anime/AnimeDownloadNotifier.kt` | same | Anime twin. Posts on `ID_DOWNLOAD_EPISODE_*`. |
| `MangaLibraryUpdateNotifier` | `data/library/manga/MangaLibraryUpdateNotifier.kt` | LIBRARY_PROGRESS / ERROR / NEW_CHAPTERS_EPISODES | `showProgressNotification(manga, current, total)` with Cancel action; `showQueueSizeWarningNotificationIfNeeded` (auto-dismiss after a timeout); `showUpdateErrorNotification(failed, uri)`; `showUpdateNotifications(updates)` — posts a group summary + one per-manga notification with **Mark as read / View chapters / Download** actions and a circular cover icon. |
| `AnimeLibraryUpdateNotifier` | `data/library/anime/AnimeLibraryUpdateNotifier.kt` | same | Anime twin. Uses `ID_NEW_EPISODES` and `GROUP_NEW_EPISODES`; per-anime actions are **Mark as seen / View episodes / Download**. |
| `BackupNotifier` | `data/backup/BackupNotifier.kt` | BACKUP_RESTORE_PROGRESS / COMPLETE | `showBackupProgress`, `showBackupError`, `showBackupComplete(file)` (with **Share** action), `showRestoreProgress(content, progress, max, sync)` (with **Cancel** action that broadcasts to `BackupRestoreJob.stop`), `showRestoreError`, `showRestoreComplete(time, errorCount, path, file, sync)` (with **Show errors** action that opens the error log). Honours `SecurityPreferences.hideNotificationContent()` for restore progress text. |
| `AppUpdateNotifier` | `data/updater/AppUpdateNotifier.kt` | APP_UPDATE | `promptUpdate(release)` (with **Download** + **What's new** actions); `onDownloadStarted(title)` / `onProgressChange(progress)` (with **Cancel**); `promptInstall(uri)` (with **Install** + **Dismiss**); `onDownloadError(url)` (with **Retry** + **Cancel**). |
| `ExtensionUpdateNotifier` | `extension/ExtensionUpdateNotifier.kt` | EXTENSIONS_UPDATE | `promptUpdates(names, anime)` — single notification listing extension names that have updates; tap opens the relevant extensions screen (manga or anime). `dismiss()` cancels it. |
| `SaveImageNotifier` | `ui/reader/SaveImageNotifier.kt` | COMMON | `onComplete(uri)` — BigPictureStyle notification with the saved page; `onError(e?)`. Used by the reader's "save image" / "share image" actions. |

Plus the incognito-mode notification is posted **directly from `App`** (no
notifier class) because it's process-scoped and tied to the
`basePreferences.incognitoMode()` flow:

```kotlin
basePreferences.incognitoMode().changes().onEach { enabled ->
    if (enabled) {
        disableIncognitoReceiver.register()
        notify(ID_INCOGNITO_MODE, CHANNEL_INCOGNITO_MODE) {
            setContentTitle(...); setSmallIcon(R.drawable.ic_glasses_24dp)
            setOngoing(true)
            setContentIntent(PendingIntent.getBroadcast(... ACTION_DISABLE_INCOGNITO_MODE ...))
        }
    } else {
        disableIncognitoReceiver.unregister()
        cancelNotification(ID_INCOGNITO_MODE)
    }
}
```

Tapping the incognito notification broadcasts `ACTION_DISABLE_INCOGNITO_MODE`
to a `DisableIncognitoReceiver` registered in `App`, which flips the pref
off — a quick escape hatch without having to open the app.

## How notifications are posted

Two thin helpers in `app/src/main/java/eu/kanade/tachiyomi/util/system/`:

- `Context.notificationBuilder(channelId, block)` — lazily fetches (and
  caches per channel) a `NotificationCompat.Builder` pre-configured with
  the channel ID, then applies `block` to it. Used for stateful notifiers
  that update the same notification repeatedly (download progress, library
  progress, app-update progress).
- `Context.notify(id, channelId, block)` — one-shot builder + post in a
  single call. Used for stateless notifications (new chapters, extension
  updates, backup complete).

Both ultimately call `NotificationManagerCompat.from(context).notify(id,
notification)`, which is the API that respects the user's per-channel
importance settings.

## `NotificationReceiver` — turning actions back into calls

`data/notification/NotificationReceiver.kt` is a single global
`BroadcastReceiver` registered in the manifest. Every notification action
the notifiers add uses a `PendingIntent.getBroadcast` targeting this
receiver with a custom action string + extras. On receipt it dispatches to
the appropriate subsystem.

Action → handler map (selected):

| Action | Handler | Used by |
|---|---|---|
| `ACTION_DISMISS_NOTIFICATION` | `dismissNotification(id, groupId?)` | All "dismiss" buttons; group-aware (dismisses group summary when the last child goes away) |
| `ACTION_RESUME_DOWNLOADS` / `ACTION_PAUSE_DOWNLOADS` / `ACTION_CLEAR_DOWNLOADS` | `MangaDownloadManager.{startDownloads,pauseDownloads,clearQueue}` | Manga download progress notification |
| `ACTION_RESUME_ANIME_DOWNLOADS` / `..._ANIME_...` | `AnimeDownloadManager` equivalents | Anime download progress |
| `ACTION_SHARE_IMAGE` | `shareImage(uri)` | Save-image notification |
| `ACTION_SHARE_BACKUP` | `shareFile(uri, "application/x-protobuf+gzip")` | Backup-complete notification |
| `ACTION_CANCEL_RESTORE` | `BackupRestoreJob.stop(context)` | Restore progress notification |
| `ACTION_CANCEL_LIBRARY_UPDATE` / `ACTION_CANCEL_ANIMELIB_UPDATE` | Cancels the WorkManager library-update job | Library-progress notification |
| `ACTION_START_APP_UPDATE` | `AppUpdateDownloadJob.start(context, url, title)` | App-update prompt |
| `ACTION_CANCEL_APP_UPDATE_DOWNLOAD` | `AppUpdateDownloadJob.stop(context)` | App-update progress notification |
| `ACTION_OPEN_CHAPTER` / `ACTION_OPEN_EPISODE` | `openChapter(mangaId, chapterId)` / `openEpisode(animeId, episodeId)` → launches `ReaderActivity` / `PlayerActivity` | New-chapters/episodes per-entry notification tap |
| `ACTION_MARK_AS_READ` / `ACTION_MARK_AS_SEEN` | `markAsRead(urls, mangaId)` / `markAsSeen(urls, animeId)` | New-chapters/episodes "mark as read/seen" button |
| `ACTION_DOWNLOAD_CHAPTER` / `ACTION_DOWNLOAD_EPISODE` | `downloadChapters(...)` / `downloadEpisodes(...)` via the relevant download manager | New-chapters/episodes "download" button |

The receiver also exposes a large number of `internal fun` **factories** on
its companion object (`openChapterPendingActivity`, `markAsViewedPendingBroadcast`,
`downloadChaptersPendingBroadcast`, `cancelRestorePendingBroadcast`,
`shareBackupPendingBroadcast`, `downloadAppUpdatePendingBroadcast`,
`openErrorLogPendingActivity`, `openExtensionsPendingActivity`,
`cancelLibraryUpdatePendingBroadcast`, etc.) — these are what the
notifiers call to construct the `PendingIntent`s they attach to
notifications.

## `NotificationHandler` — `PendingIntent` factory for *activities*

`data/notification/NotificationHandler.kt` is the symmetric helper for
**activity** pending intents (as opposed to broadcast). It builds
`PendingIntent.getActivity` instances for:

- `openDownloadManagerPendingActivity` / `openAnimeDownloadManagerPendingActivity` —
  launches `MainActivity` with `SHORTCUT_DOWNLOADS` / `SHORTCUT_ANIME_DOWNLOADS`.
- `openImagePendingActivity(uri)` — `ACTION_VIEW` with `image/*` MIME.
- `installApkPendingActivity(uri)` — `ACTION_VIEW` with the extension
  installer's `APK_MIME`; used by both the app-updater install prompt and
  the extension installer.
- `openUrl(url)` — `ACTION_VIEW` for arbitrary URLs (e.g. the "What's new"
  action on app-update notifications).

## Privacy: hiding notification content

`SecurityPreferences.hideNotificationContent()` is checked by every
notifier that includes user-content text (library progress, restore
progress, new-chapters summaries, extension-update names). When on, the
notification is still posted (so the user knows something happened) but
`setContentText` and `BigTextStyle` are omitted — useful for users who
don't want titles visible on the lock screen.

## Key files

| File | Role |
|---|---|
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/notification/Notifications.kt` | All channel IDs, notification IDs, group keys, deprecated-channel list, `createChannels(context)`. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/notification/NotificationReceiver.kt` | Global `BroadcastReceiver`; action dispatch table; `PendingIntent` factories for broadcasts. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/notification/NotificationHandler.kt` | `PendingIntent` factories for activities (downloads, image view, APK install, URL open). |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/backup/BackupNotifier.kt` | Backup/restore progress, error, complete. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/library/manga/MangaLibraryUpdateNotifier.kt` | Library update progress, queue-size warning, errors, new-chapters (group + per-manga). |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/library/anime/AnimeLibraryUpdateNotifier.kt` | Anime twin. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/download/manga/MangaDownloadNotifier.kt` | Manga download progress / paused / complete / warning / error. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/download/anime/AnimeDownloadNotifier.kt` | Anime twin. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/data/updater/AppUpdateNotifier.kt` | App-update prompt, download progress, install prompt, download error. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/extension/ExtensionUpdateNotifier.kt` | Extension-update prompt (manga and anime share the same class, `anime: Boolean` flag). |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/SaveImageNotifier.kt` | Saved-page BigPictureStyle notification. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/App.kt` | `setupNotificationChannels()` call; incognito-mode notification flow; `DisableIncognitoReceiver`. |
| `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/util/system/NotificationExtensions.kt` (or co-located) | `notificationBuilder`, `notify`, `cancelNotification` helpers. |

## See also

- [`../02-modules/app.md`](../02-modules/app.md) — `App.setupNotificationChannels()` and the `DisableIncognitoReceiver`.
- [`backup-restore.md`](backup-restore.md) — what `BackupNotifier` is posting about.
- [`updates.md`](updates.md) — what `MangaLibraryUpdateNotifier` / `AnimeLibraryUpdateNotifier` are posting about.
- [`download-manager.md`](download-manager.md) — the download notifiers' lifecycle.
- [`updater.md`](updater.md) — the app-updater notification flow.
- [`extensions-update.md`](extensions-update.md) — the `ExtensionUpdateNotifier` trigger.
- [`../01-architecture/05-preferences-system.md`](../01-architecture/05-preferences-system.md) — `SecurityPreferences.hideNotificationContent()`.

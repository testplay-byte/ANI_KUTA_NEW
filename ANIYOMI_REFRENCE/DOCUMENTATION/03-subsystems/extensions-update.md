# 03-subsystems / extensions-update — Extension repo management & updates

> How Aniyomi knows which extensions exist on the internet, which are
> installed, which have updates available, and how it installs / replaces
> / removes them. The runtime half of this story (loading, signing,
> class-loading) is in [`source-system.md`](source-system.md).

## Overview

Aniyomi extensions live in **extension repos** — ordinary HTTPS sites
that serve three things:

| Path                       | Purpose                                                        |
|----------------------------|----------------------------------------------------------------|
| `<repo>/index.min.json`   | The list of available extensions (one entry per extension).    |
| `<repo>/icon/<pkg>.png`   | The extension's icon.                                          |
| `<repo>/apk/<apk-name>`   | The actual APK file.                                           |
| `<repo>/repo.json`        | Repo metadata (name, short name, website, signing fingerprint). |

The app ships with a default repo URL baked in (the official
aniyomi-extensions GitHub Pages site) and the user can add more
(`Settings → Browse → Extension repos`). Multiple repos are queried in
parallel; their indices are merged by `pkgName`.

## The domain layer

Source: `../ANIYOMI/domain/src/main/java/mihon/domain/extensionrepo/`.

The package has a `model/`, a `service/`, an `exception/`, and two
parallel subtrees (`manga/` and `anime/`) each with a `repository/`
interface and six `interactor/`s. The anime subtree is a mirror of the
manga one.

### `ExtensionRepo`

```
data class ExtensionRepo(
    val baseUrl: String,
    val name: String,
    val shortName: String?,
    val website: String,
    val signingKeyFingerprint: String,
)
```

The `signingKeyFingerprint` is the SHA-256 of the repo's APK signing
certificate. Each repo's APKs are all signed with the same key, so when
a user installs an APK from a repo, the resulting signature can be
matched against the repo's fingerprint to verify provenance. (The trust
flow itself is described in [`source-system.md`](source-system.md).)

### The repository interface

`MangaExtensionRepoRepository` (and its anime twin) exposes `subscribeAll`,
`getAll`, `getRepo`, `getRepoBySigningKeyFingerprint`, `getCount`,
`insertRepo`, `upsertRepo`, `replaceRepo`, `deleteRepo`. The
implementation lives in `:data` (`mihon.data.repository`), backed by
the `extension_repos` SQLDelight table.

### `ExtensionRepoService.fetchRepoDetails(repo)`

GETs `<repo>/repo.json` and parses the result into an `ExtensionRepoMetaDto`
(`{ meta: ExtensionRepoDto }`). Returns `null` on any failure — the
caller (`CreateMangaExtensionRepo`) treats that as `Result.InvalidUrl`.

## The interactors

### `CreateMangaExtensionRepo.await(indexUrl)`

The user's "Add repo" entry point. Accepts a URL that must match
`^https://.*/index\.min\.json$`. Strips the suffix to get the base URL,
fetches `<base>/repo.json` for metadata, then `repository.insertRepo(...)`.
Returns a sealed `Result`:

| Result                                  | When                                                                          |
|-----------------------------------------|-------------------------------------------------------------------------------|
| `InvalidUrl`                            | URL doesn't match the regex, or `<base>/repo.json` fetch failed.              |
| `Success`                               | Insert succeeded.                                                             |
| `RepoAlreadyExists`                     | A repo with this `baseUrl` is already in the DB.                              |
| `DuplicateFingerprint(oldRepo, newRepo)`| A different repo already has this `signingKeyFingerprint`. Offers to replace. |
| `Error`                                 | Anything else (caught `SaveExtensionRepoException`).                         |

The duplicate-fingerprint case is the interesting one: the UI prompts
"Repo X has the same signing key as repo Y. Replace?" and on confirm
calls `ReplaceMangaExtensionRepo.await(newRepo)` which deletes the old
repo and inserts the new one.

### `UpdateMangaExtensionRepo.awaitAll()`

Iterates every repo in the DB, fetches `<base>/repo.json` for each
concurrently via `awaitAll`, and:

- If the existing entry's `signingKeyFingerprint` starts with
  `NOFINGERPRINT` (legacy entry from before fingerprints were tracked)
  OR the new fingerprint matches the existing one → `upsertRepo(newRepo)`
  (refreshes name/shortName/website).
- Otherwise → skip (the repo's signing key has changed, which is
  suspicious; the user is left to manually delete and re-add).

This is called by `MangaExtensionApi.checkForUpdates` before doing the
actual update check, so repo metadata is always fresh.

### `Get` / `Delete` / `GetCount`

Straightforward lookup / delete / count interactors. `GetCount` is used
by the "Browse" tab to decide whether to show the "Add repo" CTA.

## The update check

Source: `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/extension/{manga,anime}/api/*ExtensionApi.kt`.

`MangaExtensionApi.checkForUpdates(context, fromAvailableExtensionList)`:

1. Throttled to once per day when `fromAvailableExtensionList = true`
   (compares `Instant.now()` against the `last_ext_check` preference).
2. Calls `updateExtensionRepo.awaitAll()` to refresh every repo's
   metadata first (see `Update*ExtensionRepo` above).
3. Either reuses `availableExtensionsFlow.value` (if the available list
   was just refreshed by the caller) or fetches every repo's
   `index.min.json` via `findExtensions()`.
4. Re-loads every installed extension (`MangaExtensionLoader.loadMangaExtensions`)
   and compares each against the available list by `pkgName` — an update
   exists when `available.versionCode > installed.versionCode` OR
   `available.libVersion > installed.libVersion`.
5. If any updates exist, calls
   `ExtensionUpdateNotifier(context).promptUpdates(names = ...)`.
6. Returns the list of installed extensions that have updates.

### When is it called?

There is **no periodic WorkManager job** for extension updates (unlike
library updates, which use `MangaLibraryUpdateJob` /
`AnimeLibraryUpdateJob`). Instead, the check runs:

1. **At app startup** — `MainActivity.kt` has a `LaunchedEffect(Unit)`
   that calls both `AnimeExtensionApi().checkForUpdates(context)` and
   `MangaExtensionApi().checkForUpdates(context)`. The
   `fromAvailableExtensionList` parameter defaults to `false`, so this
   always hits the network on cold start.
2. **From the Extensions screen** — pull-to-refresh calls
   `findAvailableExtensions()` first, then
   `checkForUpdates(context, fromAvailableExtensionList = true)`. The
   `true` flag enables the once-a-day throttle so the network isn't
   hammered on every pull.

### `ExtensionUpdateNotifier`

Posts a notification on `Notifications.CHANNEL_EXTENSIONS_UPDATE` with
the count and the names of the extensions that have updates. Tapping it
opens the Extensions screen. Respects
`SecurityPreferences.hideNotificationContent()`. Auto-dismissed when
the pending-updates count drops to zero.

### `MangaExtensionManager.updatedInstalledExtensionsStatuses`

After `findAvailableExtensions()` returns, the manager walks every
installed extension:

- If `pkgName` is in the available list → recompute `hasUpdate`
  (true if `available.versionCode > installed.versionCode ||
  available.libVersion > installed.libVersion`), refresh `repoUrl`.
- If `pkgName` is *not* in the available list → set `isObsolete = true`.

The pending-updates count is written to
`SourcePreferences.mangaExtensionUpdatesCount()` /
`SourcePreferences.animeExtensionUpdatesCount()` — a single integer the
UI uses to render the badge on the Extensions tab without collecting
the whole extensions flow.

## Install / update flow

Updates go through the **same** install machinery as fresh installs —
see [`source-system.md`](source-system.md) for the full sequence. The
only differences:

- `MangaExtensionManager.updateExtension(installed)` resolves the
  `Available` twin and calls `installExtension(available)`.
- The APK download replaces the existing one; the system fires
  `ACTION_PACKAGE_REPLACED` (not `ADDED`), and
  `InstallationListener.onExtensionUpdated` fires.
- The loader re-runs, the source manager picks up the new source
  versions (keeping the same source IDs because they're deterministic),
  and any in-progress downloads for those sources continue with the new
  source instance.

For batch updates, "Update all" iterates every `Installed` extension
with `hasUpdate == true` and calls `installExtension(available)` on
each — the `PackageInstallerInstaller` queue processes them serially.
With the Shizuku installer, this is fully silent (no per-install user
prompt).

## Obsolete extension handling

An extension is **obsolete** when it's installed but its `pkgName` is
not present in any repo's `index.min.json`. This typically happens when
the extension has been delisted (the source it scraped went offline),
the user removed the last repo that hosted it, or the extension's
`libVersion` is outside `[LIB_VERSION_MIN, LIB_VERSION_MAX]` — in which
case the loader rejects it as `Error`, but if it was previously
installed and is now unrecognised, the manager flips `isObsolete`.

Obsolete extensions display an "Obsolete" badge in the Extensions
screen. They continue to function (their classes are still loaded if
they were loaded before becoming obsolete) but they will not receive
updates and may break as the source-api contract evolves.

A more severe form is **libVersion mismatch**: if the loader rejects
the APK because `libVersion < LIB_VERSION_MIN`, the extension is not
loaded at all and shows as "Error". The fix is to update (if a newer
version exists in any repo) or uninstall.

## Key files

| File (relative to `../ANIYOMI/`) | Role |
|---|---|
| `app/src/main/java/eu/kanade/tachiyomi/extension/{manga,anime}/api/*ExtensionApi.kt` | `findExtensions()` (repo JSON fetch), `checkForUpdates()`, `getApkUrl()`. Anime twin parses the `torrent` flag too. |
| `app/src/main/java/eu/kanade/tachiyomi/extension/InstallStep.kt` | The `Idle/Pending/Downloading/Installing/Installed/Error` enum shared by manga and anime installers. |
| `app/src/main/java/eu/kanade/tachiyomi/extension/ExtensionUpdateNotifier.kt` | Posts the "N extensions have updates" notification. |
| `app/src/main/java/eu/kanade/tachiyomi/extension/{manga,anime}/*ExtensionManager.kt` | `updatedInstalledExtensionsStatuses`, `updatePendingUpdatesCount`, `updateExtension`. |
| `domain/src/main/java/mihon/domain/extensionrepo/model/ExtensionRepo.kt` | The repo data class. |
| `domain/src/main/java/mihon/domain/extensionrepo/service/ExtensionRepoService.kt` | HTTP fetch of `<repo>/repo.json`. |
| `domain/src/main/java/mihon/domain/extensionrepo/service/ExtensionRepoDto.kt` | JSON DTO + `toExtensionRepo()` mapper. |
| `domain/src/main/java/mihon/domain/extensionrepo/{manga,anime}/repository/*ExtensionRepoRepository.kt` | Repository interface. |
| `domain/src/main/java/mihon/domain/extensionrepo/{manga,anime}/interactor/Create*ExtensionRepo.kt` | Add-repo flow with `InvalidUrl/Success/RepoAlreadyExists/DuplicateFingerprint/Error` results. |
| `domain/src/main/java/mihon/domain/extensionrepo/{manga,anime}/interactor/Update*ExtensionRepo.kt` | Refreshes repo metadata from `<repo>/repo.json`. |
| `domain/src/main/java/mihon/domain/extensionrepo/{manga,anime}/interactor/Replace*ExtensionRepo.kt` | Replace one repo with another (used by the duplicate-fingerprint flow). |
| `domain/src/main/java/mihon/domain/extensionrepo/{manga,anime}/interactor/{Get,Delete,GetCount}*ExtensionRepo.kt` | Lookup / delete / count. |
| `app/src/main/java/eu/kanade/tachiyomi/ui/main/MainActivity.kt` | `LaunchedEffect(Unit)` block that calls both `checkForUpdates` at app startup. |
| `app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt` | `mangaExtensionUpdatesCount()`, `animeExtensionUpdatesCount()`, `mangaExtensionRepos()`, `animeExtensionRepos()` preferences. |

## See also

- [`source-system.md`](source-system.md) — the runtime half: loader,
  signing, class-loading, install service, installers (LEGACY /
  PRIVATE / PACKAGEINSTALLER / SHIZUKU).
- [`../02-modules/domain.md`](../02-modules/domain.md) — the
  `mihon.domain.extensionrepo` package overview.
- [`../02-modules/data.md`](../02-modules/data.md) — the `extension_repos`
  SQLDelight table and `mihon.data.repository` implementations.
- [`updates.md`](updates.md) — the *library* update check (separate from
  the extension update check).
- [`notifications.md`](notifications.md) — the
  `CHANNEL_EXTENSIONS_UPDATE` notification channel.
- [`../04-data-models/preferences-catalog.md`](../04-data-models/preferences-catalog.md)
  — the `mangaExtensionRepos` / `animeExtensionRepos` /
  `mangaExtensionUpdatesCount` / `animeExtensionUpdatesCount` /
  `trusted_extensions` preference keys.

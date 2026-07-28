# 04-data-models / `preferences-catalog.md` — Preference keys catalog

> Catalog of Aniyomi's `*Preferences` classes and the key preference methods
> each one exposes. Use this to answer "where is the X preference stored?".
> For the **mechanism** (the `PreferenceStore` abstraction, `SharedPreferences`
> backing, the `appStateKey` / `privateKey` markers), see
> [`../01-architecture/05-preferences-system.md`](../01-architecture/05-preferences-system.md).

## How `*Preferences` classes are organized

Aniyomi splits preferences into themed groups, each a class that takes a
`PreferenceStore` (and sometimes a `Context` or `FolderProvider`) and exposes
one `fun foo(): Preference<T>` per pref key. Each method returns a
`Preference<T>` (not the raw value): read with `.get()`, write with `.set(v)`,
observe with `.changes(): Flow<T>`. Some prefs are `Preference.appStateKey(...)`
(stored separately, not backed up) or `Preference.privateKey(...)` (encrypted
prefs). Classes are registered in Injekt at startup; access with
`Injekt.get<ReaderPreferences>()`. For the mechanism, see
[`../01-architecture/05-preferences-system.md`](../01-architecture/05-preferences-system.md).

The classes fall into three ownership tiers: `:core:common` (`NetworkPreferences`,
`SecurityPreferences`, `TorrentPreferences`), `:domain` (`LibraryPreferences`,
`DownloadPreferences`, `StoragePreferences`, `BackupPreferences`), and `:app`
(`BasePreferences`, `UiPreferences`, `SourcePreferences`, `TrackPreferences`,
`ReaderPreferences`, `PlayerPreferences` + 5 player-pref siblings).

The dual manga/anime pattern shows up here too: most `*Preferences` classes
cover **both sides** in one class via paired methods
(`filterUnread()` / `filterUnseen()`, `downloadNewChapters()` /
`downloadNewEpisodes()`, `mangaSortingMode()` / `animeSortingMode()`).

---

## `LibraryPreferences`

File: `../ANIYOMI/domain/src/main/java/tachiyomi/domain/library/service/LibraryPreferences.kt`
Module: `:domain`.

The single largest preferences class — covers display, sorting, filters,
badges, columns, categories, default chapter/episode/season settings, and
season grid options for **both manga and anime** in one class.

| Method | Default | Meaning |
|---|---|---|
| `displayMode()` | `CompactGrid` | Library display mode (LibraryDisplayMode). |
| `mangaSortingMode()` | `Alphabetical / Ascending` | Manga library sort (MangaLibrarySort). |
| `animeSortingMode()` | `Alphabetical / Ascending` | Anime library sort (AnimeLibrarySort). |
| `lastUpdatedTimestamp()` | `0L` (appState) | Last library-update run. |
| `autoUpdateInterval()` | `0` | Library auto-update interval (hours; 0 = manual). |
| `autoUpdateDeviceRestrictions()` | `{wifi}` | When to allow updates: `wifi`, `network_not_metered`, `ac`. |
| `autoUpdateItemRestrictions()` | all four | Skip updates for: `manga_ongoing`, `manga_fully_read`, `manga_started`, `manga_outside_release_period`. |
| `autoUpdateMetadata()` | `false` | Refresh cover/details during library update. |
| `showContinueViewingButton()` | `false` | Show "Continue reading" button on library cards. |
| `categoryTabs()` | `true` | Show category tabs. |
| `categoryNumberOfItems()` | `false` | Show item count per category. |
| `categorizedDisplaySettings()` | `false` | Per-category display settings. |
| `hideHiddenCategoriesSettings()` | `false` | Hide hidden categories. |
| `filterIntervalCustom()` | `TriState.DISABLED` | Custom-interval filter. |
| `downloadBadge()` | `false` | Show download badge. |
| `unreadBadge()` | `true` | Show unread/unseen badge. |
| `localBadge()` | `true` | Show "local" badge. |
| `languageBadge()` | `false` | Show source-language badge. |
| `newShowUpdatesCount()` | `true` | Show updates-count badge on Updates tab. |
| `autoClearItemCache()` | `false` | Auto-clear chapter cache on update. |
| `randomAnimeSortSeed()` / `randomMangaSortSeed()` | `0` | Stable seed for random sort. |
| `animePortraitColumns()` / `mangaPortraitColumns()` | `0` | Library grid columns (portrait, 0 = auto). |
| `animeLandscapeColumns()` / `mangaLandscapeColumns()` | `0` | Same, landscape. |
| `filterDownloadedAnime()` / `filterDownloadedManga()` | `DISABLED` | Library downloaded filter (TriState). |
| `filterUnseen()` / `filterUnread()` | `DISABLED` | Unseen/unread filter (TriState). |
| `filterStartedAnime()` / `filterStartedManga()` | `DISABLED` | Started filter. |
| `filterBookmarkedAnime()` / `filterBookmarkedManga()` | `DISABLED` | Bookmarked filter. |
| `filterCompletedAnime()` / `filterCompletedManga()` | `DISABLED` | Completed filter. |
| `filterTrackedAnime(id)` / `filterTrackedManga(id)` | `DISABLED` | Per-tracker filter (TriState, keyed by tracker id). |
| `newMangaUpdatesCount()` / `newAnimeUpdatesCount()` | `0` | Cached updates count. |
| `defaultAnimeCategory()` / `defaultMangaCategory()` | `-1` | Default category (always-ask if `-1`). |
| `lastUsedAnimeCategory()` / `lastUsedMangaCategory()` | `0` (appState) | Last selected category. |
| `animeUpdateCategories()` / `mangaUpdateCategories()` | `{}` | Categories to include in updates. |
| `animeUpdateCategoriesExclude()` / `mangaUpdateCategoriesExclude()` | `{}` | Categories to exclude. |
| `filterEpisodeBySeen()` / `filterChapterByRead()` | `SHOW_ALL` | Default per-anime/manga filter (long, packed). |
| `filterEpisodeByDownloaded()` / `filterChapterByDownloaded()` | `SHOW_ALL` | Same, downloaded. |
| `filterEpisodeByBookmarked()` / `filterChapterByBookmarked()` | `SHOW_ALL` | Same, bookmarked. |
| `filterEpisodeByFillermarked()` | `SHOW_ALL` | Anime-only: fillermarked. |
| `sortEpisodeBySourceOrNumber()` / `sortChapterBySourceOrNumber()` | `EPISODE_SORTING_SOURCE` / `CHAPTER_SORTING_SOURCE` | Default sort. |
| `displayEpisodeByNameOrNumber()` / `displayChapterByNameOrNumber()` | `*_DISPLAY_NAME` | Default display. |
| `sortEpisodeByAscendingOrDescending()` / `sortChapterBy…` | `*_SORT_DESC` | Default sort direction. |
| `showEpisodeThumbnailPreviews()` | `EPISODE_SHOW_PREVIEWS` | Anime-only: preview thumbnails. |
| `showEpisodeSummaries()` | `EPISODE_SHOW_SUMMARIES` | Anime-only: episode text summaries. |
| `setEpisodeSettingsDefault(anime)` / `setChapterSettingsDefault(manga)` | — | Helper: bulk-write the per-item flags as the new defaults. |

**Season-related** (anime-only):

| Method | Default | Meaning |
|---|---|---|
| `filterSeasonByDownload()` / `filterSeasonByUnseen()` / `filterSeasonByStarted()` / `filterSeasonByCompleted()` / `filterSeasonByBookmarked()` / `filterSeasonByFillermarked()` | `SHOW_ALL` | Per-season filters (long, packed). |
| `sortSeasonBySourceOrNumber()` | `SEASON_SORT_SOURCE` | Season sort. |
| `sortSeasonByAscendingOrDescending()` | `SEASON_SORT_DESC` | Season sort direction. |
| `seasonDisplayGridMode()` | `CompactGrid` | Season grid display mode. |
| `seasonDisplayGridSize()` | `0` | Grid size override (0..15). |
| `seasonDownloadOverlay()` / `seasonUnseenOverlay()` / `seasonLocalOverlay()` / `seasonLangOverlay()` / `seasonContinueOverlay()` | `false` / `true` / `true` / `false` / `true` | Season-grid overlay toggles. |
| `seasonDisplayMode()` | `SEASON_DISPLAY_MODE_SOURCE` | Season-list display mode (source vs number). |
| `setSeasonSettingsDefault(anime)` | — | Helper: bulk-write season defaults. |
| `updateSeasonOnRefresh()` | `false` | Refresh seasons on library refresh. |
| `updateSeasonOnLibraryUpdate()` | `false` | Refresh seasons during library update. |

**Swipe actions:**

| Method | Default | Meaning |
|---|---|---|
| `swipeEpisodeStartAction()` / `swipeEpisodeEndAction()` | `ToggleSeen` / `ToggleBookmark` | Episode-list swipe (EpisodeSwipeAction enum). |
| `swipeChapterStartAction()` / `swipeChapterEndAction()` | `ToggleRead` / `ToggleBookmark` | Chapter-list swipe (ChapterSwipeAction enum). |
| `markDuplicateReadChapterAsRead()` / `markDuplicateSeenEpisodeAsSeen()` | `{}` | When to mark duplicate chapters/episodes as read/seen (string set with `new` / `existing` keys). |

---

## `ReaderPreferences`

File: `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/reader/setting/ReaderPreferences.kt`
Module: `:app`.

Manga-only — configures the manga reader.

| Method | Default | Meaning |
|---|---|---|
| `pageTransitions()` | `true` | Animate page transitions. |
| `flashOnPageChange()` | `false` | Flash screen on page change (e-ink aid). |
| `flashDurationMillis()` | `100` | Flash duration. |
| `flashPageInterval()` | `1` | Flash every N pages. |
| `flashColor()` | `BLACK` | Flash color (FlashColor enum). |
| `doubleTapAnimSpeed()` | `500` | Double-tap zoom anim speed (ms). |
| `showPageNumber()` | `true` | Show page number. |
| `showReadingMode()` | `true` | Show reading-mode overlay on entry. |
| `fullscreen()` | `true` | Fullscreen reader. |
| `cutoutShort()` | `true` | Cut out the display cutout on the short edge. |
| `keepScreenOn()` | `true` | Keep screen on while reading. |
| `defaultReadingMode()` | `RIGHT_TO_LEFT.flagValue` | Default reading mode. |
| `defaultOrientationType()` | `FREE.flagValue` | Default reader orientation. |
| `webtoonDoubleTapZoomEnabled()` | `true` | Allow double-tap zoom in webtoon. |
| `imageScaleType()` | `1` (Stretch) | Image scale type (0..5). |
| `zoomStart()` | `1` (Automatic) | Zoom start position (0..3). |
| `readerTheme()` | `1` | Reader background theme. |
| `alwaysShowChapterTransition()` | `true` | Show transition between chapters. |
| `preserveReadingPosition()` | `false` | Preserve position for read chapters. |
| `cropBorders()` | `false` | Crop borders (paged). |
| `navigateToPan()` | `true` | Use tap zones to pan. |
| `landscapeZoom()` | `true` | Auto-zoom landscape images. |
| `cropBordersWebtoon()` | `false` | Crop borders (webtoon). |
| `webtoonSidePadding()` | `0` (WEBTOON_PADDING_MIN) | Side padding % (0..25). |
| `readerHideThreshold()` | `LOW` | Auto-hide menu threshold (ReaderHideThreshold enum). |
| `folderPerManga()` | `false` | Per-manga download folders. |
| `skipRead()` | `false` | Skip read chapters. |
| `skipFiltered()` | `true` | Skip filtered-out chapters. |
| `skipDupe()` | `false` | Skip duplicate chapters. |
| `webtoonDisableZoomOut()` | `false` | Disable zoom-out in webtoon. |

**Split two-page spread:**

| Method | Default | Meaning |
|---|---|---|
| `dualPageSplitPaged()` / `dualPageSplitWebtoon()` | `false` | Split wide pages into two. |
| `dualPageInvertPaged()` / `dualPageInvertWebtoon()` | `false` | Invert split direction. |
| `dualPageRotateToFit()` / `dualPageRotateToFitWebtoon()` | `false` | Rotate split pages to fit. |
| `dualPageRotateToFitInvert()` / `dualPageRotateToFitInvertWebtoon()` | `false` | Invert rotation. |

**Color filter:**

| Method | Default | Meaning |
|---|---|---|
| `customBrightness()` | `false` | Enable custom brightness. |
| `customBrightnessValue()` | `0` | Custom brightness value (-75..100). |
| `colorFilter()` | `false` | Enable color filter. |
| `colorFilterValue()` | `0` | Color filter ARGB. |
| `colorFilterMode()` | `0` | Blend mode index (SrcOver/Modulate/Screen/Overlay/Lighten/Darken). |
| `grayscale()` | `false` | Grayscale. |
| `invertedColors()` | `false` | Invert colors. |

**Controls:**

| Method | Default | Meaning |
|---|---|---|
| `readWithLongTap()` | `true` | Long-press shows menu. |
| `readWithVolumeKeys()` | `false` | Volume keys navigate. |
| `readWithVolumeKeysInverted()` | `false` | Invert volume-key direction. |
| `navigationModePager()` / `navigationModeWebtoon()` | `0` | Tap-zone navigation layout (index into TapZones). |
| `pagerNavInverted()` / `webtoonNavInverted()` | `NONE` | Invert tap zones (TappingInvertMode enum). |
| `showNavigationOverlayNewUser()` | `true` | Show nav overlay for new users. |
| `showNavigationOverlayOnStart()` | `false` | Show nav overlay every time. |

---

## `PlayerPreferences` (and 5 sibling player-pref classes)

Files: `../ANIYOMI/app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/*.kt`
Module: `:app`.

The anime-player preferences are split across **six** classes: `PlayerPreferences`
(core), `DecoderPreferences`, `SubtitlePreferences`, `AudioPreferences`,
`GesturePreferences`, `AdvancedPlayerPreferences`. All wrap the same
`PreferenceStore`.

### `PlayerPreferences` (core)

| Method | Default | Meaning |
|---|---|---|
| `preserveWatchingPosition()` | `false` | Preserve watch position for seen episodes. |
| `progressPreference()` | `0.85F` | Episode progress fraction above which "seen" is auto-marked. |
| `defaultPlayerOrientationType()` | `SensorLandscape` | Default player orientation (PlayerOrientation enum). |
| `allowGestures()` | `false` | Allow gestures when finger is over a panel. |
| `showLoadingCircle()` | `true` | Show loading spinner. |
| `showCurrentChapter()` | `true` | Show current chapter name. |
| `rememberPlayerBrightness()` | `false` | Remember brightness between sessions. |
| `playerBrightnessValue()` | `-1.0F` | Last-saved brightness (-1 = unset). |
| `rememberPlayerVolume()` | `false` | Remember volume between sessions. |
| `playerVolumeValue()` | `-1.0F` | Last-saved volume. |
| `showFailedHosters()` | `false` | Show hosters that failed to load. |
| `showEmptyHosters()` | `false` | Show hosters with no videos. |
| `playerFullscreen()` | `true` | Fullscreen player. |
| `hideControls()` | `false` | Auto-hide controls. |
| `displayVolPer()` | `true` | Display volume as percentage. |
| `showSystemStatusBar()` | `false` | Show system status bar. |
| `reduceMotion()` | `false` | Reduce motion animations. |
| `playerTimeToDisappear()` | `4000` (ms) | Controls auto-hide timeout. |
| `panelOpacity()` | `60` | Player panel opacity %. |
| `enableSkipIntro()` | `true` | Show skip-intro button. |
| `autoSkipIntro()` | `false` | Auto-skip intro via AniSkip. |
| `enableNetflixStyleIntroSkip()` | `false` | Netflix-style skip countdown. |
| `waitingTimeIntroSkip()` | `5` (s) | Skip-countdown wait. |
| `aniSkipEnabled()` | `false` | Enable AniSkip integration. |
| `disableAniSkipOnChapters()` | `true` | Disable AniSkip on chapters already provided. |
| `enablePip()` | `true` | Enable Picture-in-Picture. |
| `pipEpisodeToasts()` | `true` | Show toast on PiP episode switch. |
| `pipOnExit()` | `false` | Auto-enter PiP on exit. |
| `pipReplaceWithPrevious()` | `false` | PiP "previous" goes back to library. |
| `alwaysUseExternalPlayer()` | `false` | Always use external player. |
| `externalPlayerPreference()` | `""` | Preferred external player package. |
| `playerSpeed()` | `1F` | Last-used playback speed. |
| `speedPresets()` | 12 default speeds | Speed-presets shown in the speed sheet. |
| `invertDuration()` | `false` | Invert seek bar direction. |
| `aspectState()` | `Fit` | Video aspect (VideoAspect enum). |
| `autoplayEnabled()` | `false` | Autoplay next episode. |

### `DecoderPreferences`

| Method | Default | Meaning |
|---|---|---|
| `tryHWDecoding()` | `true` | Try hardware decoding first. |
| `gpuNext()` | `false` | Use `vo=gpu-next`. |
| `videoDebanding()` | `None` | Debanding filter (Debanding enum). |
| `useYUV420P()` | `true` | Force YUV420P output. |
| `brightnessFilter()` / `saturationFilter()` / `contrastFilter()` / `gammaFilter()` / `hueFilter()` | (unset) | Video filter values (Int, no default). |

### `SubtitlePreferences`

| Method | Default | Meaning |
|---|---|---|
| `preferredSubLanguages()` | `""` | Comma-separated preferred subtitle languages. |
| `subtitleWhitelist()` / `subtitleBlacklist()` | `""` | Subtitle track whitelist / blacklist. |
| `screenshotSubtitles()` | `false` | Include subtitles in screenshots. |
| `subtitleFont()` | `"Sans Serif"` | Subtitle font family. |
| `subtitleFontSize()` | `55` | Subtitle font size. |
| `subtitleFontScale()` | `1F` | Subtitle scale multiplier. |
| `subtitleBorderSize()` | `3` | Subtitle border size. |
| `boldSubtitles()` / `italicSubtitles()` | `false` | Bold/italic subtitles. |
| `textColorSubtitles()` | `Color.White.toArgb()` | Subtitle text color. |
| `borderColorSubtitles()` | `Color.Black.toArgb()` | Subtitle border color. |
| `borderStyleSubtitles()` | `OutlineAndShadow` | Border style (SubtitlesBorderStyle enum). |
| `shadowOffsetSubtitles()` | `0` | Shadow offset. |
| `backgroundColorSubtitles()` | `Color.Transparent.toArgb()` | Subtitle background color. |
| `subtitleJustification()` | `Auto` | Text justification (SubtitleJustification enum). |
| `subtitlePos()` | `100` | Vertical subtitle position. |
| `overrideSubsASS()` | `false` | Override ASS script styling. |
| `subtitlesDelay()` | `0` | Subtitle delay (ms). |
| `subtitlesSpeed()` | `1F` | Subtitle speed multiplier. |
| `subtitlesSecondaryDelay()` | `0` | Secondary subtitle delay. |

### `AudioPreferences`

| Method | Default | Meaning |
|---|---|---|
| `preferredAudioLanguages()` | `""` | Comma-separated preferred audio languages. |
| `enablePitchCorrection()` | `true` | Enable audio pitch correction at non-1× speed. |
| `audioChannels()` | `AutoSafe` | Audio channel config (AudioChannels enum). |
| `volumeBoostCap()` | `30` (dB) | Max volume boost. |
| `audioDelay()` | `0` (ms) | Audio delay. |

### `GesturePreferences`

| Method | Default | Meaning |
|---|---|---|
| `gestureVolumeBrightness()` | `true` | Vertical-swipe gesture adjusts volume/brightness. |
| `swapVolumeBrightness()` | `false` | Swap which side does volume vs brightness. |
| `gestureHorizontalSeek()` | `true` | Horizontal-swipe gesture seeks. |
| `showSeekBar()` | `false` | Always show seek bar. |
| `defaultIntroLength()` | `85` (s) | Default skip-intro length. |
| `skipLengthPreference()` | `10` (s) | Double-tap skip length. |
| `playerSmoothSeek()` | `false` | Use exact-seek (slower) instead of keyframe-seek. |
| `leftDoubleTapGesture()` / `centerDoubleTapGesture()` / `rightDoubleTapGesture()` | `Seek` / `PlayPause` / `Seek` | Per-region double-tap action (SingleActionGesture enum). |
| `mediaPreviousGesture()` / `mediaPlayPauseGesture()` / `mediaNextGesture()` | `Switch` / `PlayPause` / `Switch` | Media-button actions. |

### `AdvancedPlayerPreferences`

| Method | Default | Meaning |
|---|---|---|
| `mpvUserFiles()` | `false` | Load user MPV scripts. |
| `mpvConf()` | `""` | User `mpv.conf` content. |
| `mpvInput()` | `""` | User `input.conf` content. |
| `playerStatisticsPage()` | `0` | Which MPV stats page to show (0 = none). |

---

## `DownloadPreferences`

File: `../ANIYOMI/domain/src/main/java/tachiyomi/domain/download/service/DownloadPreferences.kt`
Module: `:domain`. Covers **both manga and anime** downloads in one class.

| Method | Default | Meaning |
|---|---|---|
| `downloadOnlyOverWifi()` | `true` | Only download over Wi-Fi. |
| `useExternalDownloader()` | `false` | Use external downloader app. |
| `externalDownloaderSelection()` | `""` | Chosen external downloader package. |
| `saveChaptersAsCBZ()` | `true` | Save chapters as `.cbz` (manga). |
| `splitTallImages()` | `true` | Split tall webtoon images (manga). |
| `autoDownloadWhileReading()` | `0` | Auto-download N chapters ahead while reading (manga). |
| `autoDownloadWhileWatching()` | `0` | Same for anime. |
| `removeAfterReadSlots()` | `-1` | Auto-delete after read (slots; -1 = disabled). |
| `removeAfterMarkedAsRead()` | `false` | Delete on "mark as read". |
| `removeBookmarkedChapters()` | `false` | Allow deleting bookmarked chapters. |
| `downloadFillermarkedItems()` | `false` | Download filler episodes. |
| `removeExcludeCategories()` / `removeExcludeAnimeCategories()` | `{}` | Categories excluded from auto-delete. |
| `downloadNewChapters()` / `downloadNewEpisodes()` | `false` | Auto-download new chapters/episodes on update. |
| `downloadNewChapterCategories()` / `downloadNewEpisodeCategories()` | `{}` | Categories to auto-download. |
| `downloadNewChapterCategoriesExclude()` / `downloadNewEpisodeCategoriesExclude()` | `{}` | Categories to exclude. |
| `numberOfDownloads()` | `1` | Concurrent download slots. |
| `downloadSpeedLimit()` | `0` (unlimited) | Download speed limit (KB/s). |
| `downloadNewUnreadChaptersOnly()` / `downloadNewUnseenEpisodesOnly()` | `false` | Only download unread/unseen new items. |

---

## `SourcePreferences`

File: `../ANIYOMI/app/src/main/java/eu/kanade/domain/source/service/SourcePreferences.kt`
Module: `:app`.

| Method | Default | Meaning |
|---|---|---|
| `sourceDisplayMode()` | `CompactGrid` | Catalogue display mode. |
| `enabledLanguages()` | default set | Source languages to show. |
| `showNsfwSource()` | `true` | Show NSFW sources. |
| `migrationSortingMode()` / `migrationSortingDirection()` | `ALPHABETICAL` / `ASCENDING` | Source-migration sort. |
| `animeExtensionRepos()` / `mangaExtensionRepos()` | `{}` | Extension-repo URLs (legacy pref-list; the canonical list lives in the `extension_repos` tables). |
| `trustedExtensions()` | `{}` (appState) | Trusted extension signing keys. |
| `globalSearchFilterState()` | `false` (appState) | Whether global-search filters are active. |
| `disabledAnimeSources()` / `disabledMangaSources()` | `{}` | Hidden sources. |
| `incognitoAnimeExtensions()` / `incognitoMangaExtensions()` | `{}` | Extensions to run in incognito mode. |
| `pinnedAnimeSources()` / `pinnedMangaSources()` | `{}` | Pinned sources. |
| `lastUsedAnimeSource()` / `lastUsedMangaSource()` | `-1L` (appState) | Last-used source id. |
| `animeExtensionUpdatesCount()` / `mangaExtensionUpdatesCount()` | `0` | Pending extension updates count. |
| `hideInAnimeLibraryItems()` / `hideInMangaLibraryItems()` | `false` | Hide library items in browse. |

**Tachiyomi-SY data-saver:**

| Method | Default | Meaning |
|---|---|---|
| `dataSaver()` | `NONE` | Data-saver backend (DataSaver enum: NONE / BANDWIDTH_HERO / WSRV_NL / RESMUSH_IT). |
| `dataSaverIgnoreJpeg()` / `dataSaverIgnoreGif()` | `false` / `true` | Skip data-saver for JPEG/GIF. |
| `dataSaverImageQuality()` | `80` | Re-encode quality. |
| `dataSaverImageFormatJpeg()` | `false` | Output JPEG (vs WebP). |
| `dataSaverServer()` | `""` | Custom server URL. |
| `dataSaverColorBW()` | `false` | Convert to black-and-white. |
| `dataSaverExcludedSources()` | `{}` | Sources excluded from data-saver. |
| `dataSaverDownloader()` | `true` | Apply data-saver to downloads. |

---

## `TrackPreferences`

File: `../ANIYOMI/app/src/main/java/eu/kanade/domain/track/service/TrackPreferences.kt`
Module: `:app`.

| Method | Default | Meaning |
|---|---|---|
| `trackUsername(tracker)` | `""` (private) | Tracker username. |
| `trackPassword(tracker)` | `""` (private) | Tracker password. |
| `trackAuthExpired(tracker)` | `false` (private) | Auth has expired flag. |
| `trackToken(tracker)` | `""` (private) | Tracker OAuth token. |
| `setCredentials(tracker, u, p)` | — | Helper: set username+password+clear-expired. |
| `anilistScoreType()` | `Anilist.POINT_10` | AniList score display format. |
| `autoUpdateTrack()` | `true` | Auto-push progress to trackers. |
| `trackOnAddingToLibrary()` | `true` | Prompt to track on add-to-library. |
| `showNextEpisodeAiringTime()` | `true` | Show next-airing-episode badge on anime cards. |
| `autoUpdateTrackOnMarkRead()` | `ALWAYS` | When to auto-update tracker on mark-read (AutoTrackState enum). |

---

## `BackupPreferences`

File: `../ANIYOMI/domain/src/main/java/tachiyomi/domain/backup/service/BackupPreferences.kt`
Module: `:domain`.

| Method | Default | Meaning |
|---|---|---|
| `backupInterval()` | `12` (hours) | Auto-backup interval (0 = manual). |
| `lastAutoBackupTimestamp()` | `0L` (appState) | Last auto-backup run. |
| `backupFlags()` | `{1,2,4,8}` (categories/chapters/history/track) | What to include in backups (bit-flags; `FLAG_CATEGORIES`=`"1"`, `FLAG_CHAPTERS`=`"2"`, `FLAG_HISTORY`=`"4"`, `FLAG_TRACK`=`"8"`, `FLAG_SETTINGS`=`"10"`, `FLAG_EXT_SETTINGS`=`"20"`, `FLAG_EXTENSIONS`=`"40"` — defined in `PreferenceValues.kt`). |

---

## `UiPreferences`

File: `../ANIYOMI/app/src/main/java/eu/kanade/domain/ui/UiPreferences.kt`
Module: `:app`.

| Method | Default | Meaning |
|---|---|---|
| `themeMode()` | `SYSTEM` | Light/Dark/System (ThemeMode enum). |
| `appTheme()` | `MONET` if available else `DEFAULT` | App color theme (AppTheme enum). |
| `themeDarkAmoled()` | `false` | Use AMOLED black in dark mode. |
| `relativeTime()` | `true` | Show relative timestamps. |
| `dateFormat()` | `""` (localized SHORT) | Date-format pattern. |
| `tabletUiMode()` | `AUTOMATIC` | Tablet UI mode (TabletUiMode enum). |
| `startScreen()` | `ANIME` | Default start screen (StartScreen enum — note Aniyomi defaults to ANIME). |
| `navStyle()` | `MOVE_HISTORY_TO_MORE` | Bottom-rail nav style (NavStyle enum). |

---

## `BasePreferences`

File: `../ANIYOMI/app/src/main/java/eu/kanade/domain/base/BasePreferences.kt`
Module: `:app`.

| Method | Default | Meaning |
|---|---|---|
| `downloadedOnly()` | `false` (appState) | "Downloaded only" filter. |
| `incognitoMode()` | `false` (appState) | Incognito session flag. |
| `extensionInstaller()` | (system-dep) | Extension installer (ExtensionInstaller enum: LEGACY / PACKAGEINSTALLER / SHIZUKU / PRIVATE). |
| `deviceHasPip()` | (system-dep) | Whether the device supports PiP. |
| `shownOnboardingFlow()` | `false` (appState) | Onboarding has been completed. |
| `displayProfile()` | `""` | Display profile string. |
| `hardwareBitmapThreshold()` | `GLUtil.SAFE_TEXTURE_LIMIT` | Hardware bitmap threshold. |
| `alwaysDecodeLongStripWithSSIV()` | `false` | Force SSIV for long-strip decoding. |

---

## `StoragePreferences`

File: `../ANIYOMI/domain/src/main/java/tachiyomi/domain/storage/service/StoragePreferences.kt`
Module: `:domain`.

| Method | Default | Meaning |
|---|---|---|
| `baseStorageDirectory()` | `folderProvider.path()` (appState) | Base SAF directory for downloads/backup. |

A single pref — but important enough to have its own class because it's read
by `StorageManager` and used as the root for the download/backup directory trees.

---

## `NetworkPreferences`

File: `../ANIYOMI/core/common/src/main/java/eu/kanade/tachiyomi/network/NetworkPreferences.kt`
Module: `:core:common`.

| Method | Default | Meaning |
|---|---|---|
| `verboseLogging()` | (constructor arg, `false`) | Verbose OkHttp logging. |
| `dohProvider()` | `-1` (off) | DNS-over-HTTPS provider (-1 / Cloudflare / Google / …). |
| `defaultUserAgent()` | `"Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:136.0) Gecko/20100101 Firefox/136.0"` | Default User-Agent header. |

---

## `SecurityPreferences`

File: `../ANIYOMI/core/common/src/main/java/eu/kanade/tachiyomi/core/security/SecurityPreferences.kt`
Module: `:core:common`.

| Method | Default | Meaning |
|---|---|---|
| `useAuthenticator()` | `false` | Require biometric unlock. |
| `lockAppAfter()` | `0` (immediate) | Seconds after which to require unlock. |
| `secureScreen()` | `INCOGNITO` | When to hide app from screenshots/recents (SecureScreenMode enum: ALWAYS / INCOGNITO / NEVER). |
| `hideNotificationContent()` | `false` | Hide notification body. |
| `lastAppClosed()` | `0L` (appState) | Last app-close timestamp (for timed lock). |

---

## `TorrentPreferences`

File: `../ANIYOMI/core/common/src/main/java/aniyomi/core/common/torrent/TorrentPreferences.kt`
Module: `:core:common`. Anime-only (used by the Torrserver integration).

| Method | Default | Meaning |
|---|---|---|
| `torrServerEnable()` | `false` | Enable Torrserver torrent streaming. |
| `torrServerShownNotice()` | `false` | Whether the first-use notice was shown. |
| `torrServerPort()` | `"8090"` | Torrserver port. |
| `torrServerTrackers()` | a long newline-separated list of public tracker URLs | Default trackers list. |
| `torrServerProxyMode()` | `None` | Proxy mode (ProxyMode enum: None / Tracker / Peers / Full). |
| `torrServerProxyUrl()` | `""` | Proxy URL. |

---

## See also

- [`../01-architecture/05-preferences-system.md`](../01-architecture/05-preferences-system.md)
  — how the `PreferenceStore` abstraction works (this doc lists the *keys*,
  that doc explains the *mechanism*, including `appStateKey` / `privateKey` /
  `getObject` serializers / the Injekt registration of each `*Preferences` class).
- [`domain-models.md`](domain-models.md) — many of these preferences feed into
  domain-model flag bitmasks (`chapterFlags`/`episodeFlags`/`seasonFlags`/
  `viewerFlags`) via the `LibraryPreferences.set*SettingsDefault()` helpers.
- [`../03-subsystems/library-management.md`](../03-subsystems/library-management.md)
  — how `LibraryPreferences` drives the library screen.
- [`../03-subsystems/manga-reader.md`](../03-subsystems/manga-reader.md) — how
  `ReaderPreferences` is consumed by the reader viewers and the
  `ReaderSettingsScreenModel`.
- [`../03-subsystems/anime-player.md`](../03-subsystems/anime-player.md) — how
  `PlayerPreferences` + the 5 player-pref siblings feed `AniyomiMPVView.initOptions`
  and the player UI.
- [`../03-subsystems/download-manager.md`](../03-subsystems/download-manager.md)
  — how `DownloadPreferences` configures the manga/anime `DownloadManager`s.
- [`../03-subsystems/trackers.md`](../03-subsystems/trackers.md) — how
  `TrackPreferences` credentials are used per-tracker.
- [`../03-subsystems/backup-restore.md`](../03-subsystems/backup-restore.md)
  — how `BackupPreferences.backupFlags()` selects what to back up.
- [`../03-subsystems/source-system.md`](../03-subsystems/source-system.md) —
  how `SourcePreferences` filters and pins sources.
- [`../03-subsystems/torrent-streaming.md`](../03-subsystems/torrent-streaming.md)
  — how `TorrentPreferences` configures Torrserver.
- [`../02-modules/core-common.md`](../02-modules/core-common.md) — narrative
  description of the `:core:common` preference system that all these classes
  build on.

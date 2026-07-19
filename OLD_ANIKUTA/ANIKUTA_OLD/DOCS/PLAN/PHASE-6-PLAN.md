# Phase 6 — App Functionality + Polish

> **Status: PLANNING (not started).** This document defines the goal, scope,
> tasks, open questions, and dependencies. Implementation begins after the user
> answers the open questions and gives the go-ahead.
>
> **Prerequisite:** Phase 5 complete ✅ (extension chain works, real episodes
> play, Library/History/Search/Settings functional).
>
> **Note:** The original Phase 6 (4 designs + theming) has been moved to
> **Phase 8** (see `PHASE-8-DESIGNS-PLAN.md`). The user decided the app needs
> to be fully functional and polished before adding more visual designs.

---

## 1. Goal

**Make the app fully functional and polished.** The core works (search →
detail → play → save → resume), but it's missing key features that make an
anime app usable day-to-day. By the end of Phase 6:

1. **Extension management** — install, uninstall, update, enable/disable
   extensions from within the app (no more sideloading APKs manually).
2. **AniList tracking** — login to AniList, sync watch progress, mark episodes
   as watched, update anime status (watching/completed/plan to watch).
3. **Video downloads** — download episodes for offline viewing; a downloads
   queue with progress; offline playback in the player.
4. **Settings reorganization** — split the cluttered single-page MoreScreen
   into proper subpages (General, Player, Extensions, Downloads, Tracking,
   About).
5. **Player UX improvements** — faster video start (preload before showing
   player), better loading feedback, smoother experience.
6. **General polish** — bug fixes, performance, edge cases, error handling.

---

## 2. Scope — what's IN Phase 6

### 2.1 Extension management (was Phase 7)

Currently extensions are detected and loaded, but the user has no way to
install/uninstall/update them from within the app. They have to sideload APKs.

**Tasks:**
- **6.1** Extensions screen: list installed extensions (name, version, source
  count, enable/disable toggle). Accessible from Settings → Extensions.
- **6.2** Available extensions: fetch the extension repo index
  (`index.min.json` from the configured repo), show available extensions with
  install buttons.
- **6.3** Extension installer: download APK from repo → install via
  `PackageInstaller` (Android system installer) → reload extension manager.
- **6.4** Extension uninstaller: uninstall via `PackageInstaller` or Intent.
- **6.5** Extension updates: check for newer versions in the repo, show update
  badge, update button.
- **6.6** Extension repo management: add/remove extension repos (URLs), default
  to `Confused-Creature-180/aniyomi-extensions`.

### 2.2 AniList tracking (was Phase 8)

Currently the app uses AniList for discovery only (no login). Phase 6 adds
AniList OAuth login + progress sync.

**Tasks:**
- **6.7** AniList OAuth login: open browser → AniList auth → callback → store
  access token. Settings → Tracking → "Login with AniList".
- **6.8** AniList API client: authenticated queries (update progress, get user
  lists, search user's anime list).
- **6.9** Progress sync: when the user watches an episode (progress saved),
  also update AniList (mark episode as watched, update progress %).
- **6.10** Anime status: in detail page, show + edit the anime's status
  (Watching / Completed / Paused / Dropped / Planning) — synced to AniList.
- **6.11** User library sync: pull the user's AniList library into the app's
  Library tab (merge with locally-saved anime).

### 2.3 Video downloads (was Phase 7)

Download episodes for offline viewing. This is a large feature.

**Tasks:**
- **6.12** Download manager: WorkManager-based queue. Downloads episodes in the
  background, survives app restart.
- **6.13** Download UI: queue screen (Settings → Downloads) showing
  downloading/completed/failed items with progress bars.
- **6.14** Download storage: save to the user-selected folder (from onboarding
  step 3). Format: MP4/MKV via FFmpeg (ffmpeg-kit is already a dependency).
- **6.15** Offline playback: player reads downloaded files instead of streaming
  when available. "Downloaded" badge on episodes in the detail page.
- **6.16** Download settings: quality preference (360p/720p/1080p), max
  concurrent downloads, delete after watching toggle.

### 2.4 Settings reorganization

The current MoreScreen is a single page with 4 groups — getting cluttered.
Split into subpages.

**Tasks:**
- **6.17** Settings home: a list of categories (General, Player, Extensions,
  Downloads, Tracking, About). Each → a subpage.
- **6.18** General subpage: clear cache, storage info, default home view.
- **6.19** Player subpage: speed, hwdec, audio language, subtitle settings
  (basic), gesture toggles.
- **6.20** Extensions subpage: installed + available + repos (task 6.1-6.6).
- **6.21** Downloads subpage: queue + settings (task 6.13 + 6.16).
- **6.22** Tracking subpage: AniList login + sync settings (task 6.7).
- **6.23** About subpage: version, build, GitHub, licenses, debug screen entry.
- **6.24** Navigation: each subpage has a back button, proper titles, M3
  Expressive styling.

### 2.5 Player UX improvements

The user reported: "video does not directly start to play. It loads on the
video player page a bit too and after some time then the video starts to play."

**Tasks:**
- **6.25** Preload video URL before opening player: keep the loading overlay on
  the detail page until the video URL is resolved AND the first frame is ready,
  then open the player already playing. (Currently the player opens, then
  loads.)
- **6.26** Player loading state: show a spinner + "Buffering…" in the player
  while MPV loads the stream, instead of a blank screen.
- **6.27** Resume prompt: when opening an episode with saved progress, show a
  "Resume from X:XX?" dialog before the player opens (instead of auto-seeking
  silently).

### 2.6 General polish

**Tasks:**
- **6.28** Error handling audit: every screen should have proper Error states
  with retry buttons (some do, some don't).
- **6.29** Empty states: every list/grid should have a helpful empty state
  (some do, some don't).
- **6.30** Loading skeletons: replace spinners with skeleton placeholders on
  Home, Library, History, Search.
- **6.31** Performance: profile startup time, scroll smoothness, image loading.
  Fix obvious bottlenecks.
- **6.32** Edge cases: no internet, extension crashes, invalid video URLs,
  expired cache, low storage.

---

## 3. Scope — what's NOT in Phase 6

- **4 designs + theming** — moved to Phase 8. The app stays Material 3
  Expressive for now.
- **Player gestures** (swipe for volume/brightness/seek) — deferred to Phase 8
  (player-polish, alongside designs).
- **Player settings panels** (subtitle typography, decoder presets, mpv.conf) —
  deferred to Phase 8.
- **PiP, media session, AniSkip, screenshots, chapters** — deferred.
- **Backup/restore** — deferred to Phase 8 (polish).
- **Manga support** — not planned (anime-only, Decision D2).

---

## 4. Dependencies + risks

### 4.1 Extension installer (6.3) — biggest risk

Installing APKs from within an app requires either:
- `PackageInstaller` API (Android 8+, requires `REQUEST_INSTALL_PACKAGES`
  permission + user confirmation dialog), OR
- Launching the system installer via `Intent.ACTION_VIEW` + `application/vnd.android.package-archive`

The `REQUEST_INSTALL_PACKAGES` permission is restricted on Google Play but
fine for sideloaded apps. aniyomi uses this approach. Risk: some OEMs/Oppo/
Xiaomi may block unknown-source installs with extra dialogs.

### 4.2 AniList OAuth (6.7) — needs a client ID

AniList OAuth requires registering an AniList app to get a client ID + secret.
The user needs to register at `anilist.co/settings/developer` and provide the
client ID. This is a user action — I'll ask in the questions.

### 4.3 Downloads (6.12-6.16) — large feature, FFmpeg dependency

Downloads need FFmpeg to mux streams into MP4/MKV. We already have `ffmpeg-kit`
(from Phase 4). The download manager needs WorkManager (not yet a dependency).
Storage: the user selected a folder in onboarding step 3 — we need to use that
via `DocumentFile` (scoped storage).

### 4.4 Settings subpages — needs navigation refactoring

The current NavGraph has a single `More` route. We need nested navigation or
multiple routes (`settings/general`, `settings/player`, `settings/extensions`,
etc.). This is a moderate refactor.

---

## 5. Task breakdown (proposed order)

| # | Task | Depends on | Est. complexity |
|---|------|------------|-----------------|
| **Settings reorg** | | | |
| 6.17 | Settings home (category list) | — | Medium |
| 6.18-6.24 | 7 subpages | 6.17 | Medium |
| **Player UX** | | | |
| 6.25 | Preload video before opening player | — | Medium |
| 6.26 | Player loading state (spinner + "Buffering…") | — | Low |
| 6.27 | Resume prompt dialog | — | Low |
| **Extension mgmt** | | | |
| 6.1 | Extensions screen (installed list) | 6.20 | Medium |
| 6.2 | Available extensions (repo index) | 6.6 | Medium |
| 6.3 | Extension installer (APK download + install) | 6.2 | High |
| 6.4 | Extension uninstaller | 6.1 | Low |
| 6.5 | Extension updates | 6.2, 6.3 | Medium |
| 6.6 | Extension repo management | — | Medium |
| **AniList tracking** | | | |
| 6.7 | AniList OAuth login | — | High |
| 6.8 | AniList API client (authenticated) | 6.7 | Medium |
| 6.9 | Progress sync | 6.8, 6.7 | Medium |
| 6.10 | Anime status (detail page) | 6.8 | Low |
| 6.11 | User library sync | 6.8 | Medium |
| **Downloads** | | | |
| 6.12 | Download manager (WorkManager) | — | High |
| 6.13 | Download UI (queue) | 6.12, 6.21 | Medium |
| 6.14 | Download storage (FFmpeg mux) | 6.12 | High |
| 6.15 | Offline playback | 6.14 | Medium |
| 6.16 | Download settings | 6.12 | Low |
| **Polish** | | | |
| 6.28 | Error handling audit | — | Medium |
| 6.29 | Empty states audit | — | Low |
| 6.30 | Loading skeletons | — | Medium |
| 6.31 | Performance pass | — | Medium |
| 6.32 | Edge cases | — | Medium |
| 6.33 | Phase 6 verification | all | — |

**Suggested order:** Settings reorg (6.17-6.24) first — it's the foundation
for the other features (extensions/downloads/tracking all need settings
subpages). Then Player UX (6.25-6.27) — quick wins. Then Extension mgmt
(6.1-6.6) — highest user value. Then AniList tracking (6.7-6.11). Then
Downloads (6.12-6.16) — largest feature, last. Then Polish (6.28-6.32).
Then verify (6.33).

---

## 6. Open questions for the user

**Q1 — Priority order:** Which section do you want first? My proposal:
1. Settings reorganization (foundation for everything else)
2. Player UX improvements (quick wins, you flagged the slow start)
3. Extension management (highest user value — no more sideloading)
4. AniList tracking (needs your client ID)
5. Downloads (largest feature)
6. Polish
Agree, or reorder?

**Q2 — AniList client ID:** AniList OAuth needs a registered app. Do you want
to register one at `anilist.co/settings/developer` and provide the client ID +
secret, or should I defer tracking to Phase 8 and focus on the other features?
My proposal: **register it now** — tracking is a core feature and you'll want
it eventually.

**Q3 — Extension repo:** Should we default to `Confused-Creature-180/aniyomi-
extensions` (the one from onboarding), or allow the user to add multiple repos?
My proposal: **default to that repo + allow adding more** (like aniyomi does).

**Q4 — Download format:** Should downloads be MP4 (universally compatible) or
MKV (preserves all streams/audio tracks)? My proposal: **MKV** — preserves
quality + multiple audio tracks, and our player (MPV) plays MKV natively.

**Q5 — Download quality:** Should the user pick a preferred quality (e.g.,
720p) that auto-applies to all downloads, or pick per-episode? My proposal:
**preferred quality in settings + per-episode override** (long-press episode →
download at X quality).

**Q6 — Settings subpages navigation:** Should subpages be separate routes in
the NavGraph (`settings/extensions`, `settings/player`, etc.) or a nested
navigation graph? My proposal: **separate routes** — simpler, back button
works naturally, deep-linkable.

**Q7 — Player preload (6.25):** Should the player (a) open immediately and
show a loading state inside the player, or (b) stay on the detail page with a
loading overlay until the video is ready, then open the player already playing?
My proposal: **(b)** — you flagged the "player opens then loads" as annoying,
so we keep the loading on the detail page and open the player ready to play.

**Q8 — Resume prompt:** When opening an episode with saved progress, should we
(a) auto-resume silently (current behavior), (b) show a "Resume from X:XX?"
dialog, or (c) show "Resume from X:XX / Start from beginning" buttons? My
proposal: **(c)** — two buttons, clear and non-blocking.

---

## 7. How we'll manage Phase 6

- **Same incremental approach (D1):** each feature is built incrementally,
  one commit per task. No bulk copy-paste.
- **UI/logic separation:** ViewModels in `app.anikuta.ui.*`, interactors/repos
  in `domain`/`data`. Settings subpages are thin UI over existing stores.
- **M3 Expressive throughout:** all new screens use the existing `AnikutaTheme`.
  No new design system (that's Phase 8).
- **GitHub:** each task = one commit. Push after each. Build must be green.
- **Documentation:** each major feature gets a `DOCS/APP/STRUCTURE/` doc
  (extensions.md, tracking.md, downloads.md, settings.md).
- **ntfy:** sent at the end of each task + when builds go green/red.

---

## 8. Verification (Phase 6 done = these all pass)

- [ ] Settings → 6 subpages, each with proper content + back navigation.
- [ ] Player: tap episode → loading overlay on detail page → player opens
  already playing (no blank-screen-then-load).
- [ ] Resume prompt: "Resume from X:XX / Start from beginning" buttons.
- [ ] Extensions screen: installed list with enable/disable + uninstall.
- [ ] Extensions screen: available list with install button → system install
  dialog → extension appears in installed list.
- [ ] Extension updates: update badge → update button → new version installed.
- [ ] AniList login: Settings → Tracking → "Login with AniList" → browser →
  callback → "Logged in as [user]".
- [ ] Watch an episode → AniList progress updates (check on anilist.co).
- [ ] Detail page: anime status (Watching/Completed/etc.) shows + editable.
- [ ] Downloads: long-press episode → download → progress in Downloads screen.
- [ ] Downloads: play downloaded episode offline (no internet) → plays from
  local file.
- [ ] Every screen has proper Loading / Error / Empty states.
- [ ] No crashes on edge cases (no internet, extension crash, invalid URL).

---

_Last updated: Session 21 (Phase 6 re-planned — designs moved to Phase 8).
Implementation starts after user answers the open questions._

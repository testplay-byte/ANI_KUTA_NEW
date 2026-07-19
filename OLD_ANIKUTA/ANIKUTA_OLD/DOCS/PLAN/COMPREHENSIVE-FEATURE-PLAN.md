# Comprehensive Feature Plan — Tracking, Backup, Detail Page, Notifications

> **Status: PLANNING — awaiting user approval before any code is written.**
> Last updated: 2026-07-17.
>
> This plan covers ALL features the user requested in their latest message.
> It's broken into independent work streams that can be built sequentially.

---

## Analysis Summary (what exists vs what's missing)

### Episode Watched/Unwatched Status
- ✅ SQLDelight `episodes` table HAS a `seen` column (Boolean)
- ✅ Domain `Episode` model HAS `seen: Boolean` + `lastSecondSeen: Long` + `totalSeconds: Long`
- ✅ Watch threshold preference exists (`watch_threshold`, default 0.85f)
- ❌ Player does NOT mark episodes as seen (only saves position to WatchProgressStore)
- ❌ Detail page does NOT show watched/unwatched status
- ❌ The 85% threshold is NOT used anywhere

### Tracking System (AniList)
- ✅ `AniListTracker` exists with OAuth login + `updateProgress()` + `updateStatus()`
- ✅ Player calls `syncToAniList()` when an episode finishes
- ❌ No reverse sync (AniList → app) — can't pull watched status from AniList
- ❌ No score, started/finished dates, rewatching support
- ❌ No tracking display on the detail page

### Onboarding Backup Restore
- ✅ Step 4 lets user SELECT a backup file URI
- ❌ The backup is NEVER actually restored — `onComplete()` skips `backupFileUri`
- ❌ No processing animation or confirmation screen

### Backup Format Detection
- ✅ Fixed (CI #448 passed) — reads all bytes once, no mark/reset
- ❌ Aniyomi import doesn't actually restore data (just counts entries)
- ❌ No translation layer (sourceId+url → anilistId)

### Detail Page Features
- ✅ Airing time shown as text ("Ep N in Xd Yh")
- ❌ No countdown timer view (clicking does nothing)
- ❌ No swipe gestures on episodes
- ❌ No watched/unwatched visual indicator

### Notification System
- ✅ Full system implemented (Phases N-1 through N-7)
- ❓ Needs end-to-end verification

---

## Work Streams (ordered by dependency)

### Stream 1: Episode Watched/Unwatched System (FOUNDATION)
**Why first:** Everything else depends on this. Backup restore needs to set `seen`. Detail page needs to show `seen`. Tracking sync needs `seen`.

**Tasks:**
1. **Player: mark episodes as seen** — when playback position >= 85% of duration, set `seen = true` in SQLDelight. Also save `lastSecondSeen` + `totalSeconds`.
2. **Player: unmark on resume** — if user resumes a seen episode, keep `seen = true` (don't unmark).
3. **Detail page: show watched status** — green checkmark or dimmed appearance for seen episodes.
4. **Settings: watch threshold** — already exists, just needs to be wired to the player.

### Stream 2: Detail Page Swipe Gestures + Airing Countdown
**Depends on:** Stream 1 (swipe right = toggle seen)

**Tasks:**
1. **Swipe right on episode** → toggle `seen` (mark as watched/unwatched)
2. **Swipe left on episode** → queue for download
3. **Settings: customize gestures** — let user configure what right/left swipe does
4. **Airing countdown** — clicking the "Ep N in Xd Yh" text switches to a countdown timer view (live updating)

### Stream 3: Backup Restore in Onboarding + Aniyomi Translation Layer
**Depends on:** Stream 1 (needs `seen` to restore watched status)

**Tasks:**
1. **Onboarding: actually restore the backup** — process `backupFileUri` before completing onboarding
2. **Onboarding: processing animation** — show a loading spinner while restoring
3. **Onboarding: confirmation screen** — show what was restored, then "Next" → final screen → app
4. **Aniyomi translation layer** — 4-tier AniList ID resolver:
   - Tier A: `BackupAnimeTracking[syncId=2].mediaId` → anilistId (exact)
   - Tier B: Search AniList by title (fuzzy, user confirms)
   - Tier C: Parse URL for `anilist.co/anime/ID`
   - Tier D: Skip unmatched (log)
5. **Aniyomi restore: episodes** — map `BackupEpisode.seen` → SQLDelight `seen`, `lastSecondSeen/totalSeconds` → resume position
6. **Aniyomi restore: history** — map `BackupAnimeHistory.lastRead` → WatchProgressStore
7. **Aniyomi restore: categories** — map by name (not ID)
8. **Aniyomi restore: tracking** — map AniList tracking (syncId=2) → our tracker

### Stream 4: Tracking System Improvements
**Depends on:** Stream 1 (needs `seen` for sync)

**Tasks:**
1. **Reverse sync** — pull AniList library + watched status into the app
2. **Score/dates** — support score, started/finished watching dates
3. **Detail page tracking display** — show tracking status (watching/completed/dropped, score, progress)
4. **Tracking settings** — configure auto-sync, what to sync

### Stream 5: Notification System Verification
**Independent — can run in parallel**

**Tasks:**
1. **Verify check logic** — does ReleaseTracker properly check at the right time?
2. **Verify notification dispatch** — does NotificationDispatcher properly fire?
3. **Verify auto-download** — does triggerAutoDownload work?
4. **Fix any bugs found**

### Stream 6: Documentation
**Final — after all features are built**

**Tasks:**
1. Update CURRENT-STATE.md
2. Update TECHNICAL-OVERVIEW.md
3. Update TESTING.md
4. Create feature-specific docs

---

## Execution Order

```
Stream 1 (Episode seen system) ← FOUNDATION
    ↓
Stream 2 (Swipe gestures + countdown) ← depends on Stream 1
    ↓ (parallel)
Stream 3 (Backup onboarding + Aniyomi import) ← depends on Stream 1
    ↓
Stream 4 (Tracking improvements) ← depends on Stream 1
    ↓ (parallel)
Stream 5 (Notification verification) ← independent
    ↓
Stream 6 (Documentation) ← final
```

**Estimated effort:** This is a large multi-session effort. Each stream will be built on its own feature branch with CI verification per phase.

---

## Key Design Decisions

### 1. Episode seen status: SQLDelight is the source of truth
- The `episodes` table's `seen` column is the canonical watched/unwatched flag
- WatchProgressStore (SharedPreferences) tracks resume position only
- The player writes to BOTH: position → WatchProgressStore, seen → SQLDelight

### 2. Aniyomi import: offline-first, user confirms fuzzy matches
- Tier A (tracking mediaId): auto-match, no user interaction
- Tier B (title search): show candidates, user picks
- Tier C (URL parse): auto-match
- Tier D: skip, log
- Don't call AniList API during import for Tier A/C — only for Tier B fuzzy search

### 3. Swipe gestures: customizable, sensible defaults
- Default: right swipe = toggle seen, left swipe = download
- Settings: let user swap or disable each gesture
- Use Compose's `SwipeToDismissBox` (Material 3)

### 4. Airing countdown: live updating, click to toggle
- Default: show "Ep N in Xd Yh" (text)
- Click → switch to countdown timer (live updating, shows seconds ticking)
- Click again → switch back to text

---

## Open Questions for the User

1. **Aniyomi fuzzy matching:** For anime without AniList tracking in the backup, should I auto-search AniList by title and pick the best match, or show a list for the user to confirm?
2. **Swipe gestures:** Should the default be right=toggle seen, left=download? Or would you prefer different defaults?
3. **Tracking reverse sync:** Should the app automatically pull AniList library data on startup, or only when the user manually triggers it?
4. **Countdown timer:** Should it be a full-screen view, or an inline expandable section on the detail page?

---

_This plan is a DRAFT. No code will be written until the user approves._

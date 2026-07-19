# Upstream Tracking

> How we keep ANI-KUTA in sync with aniyomi upstream — without breaking our
> customizations. The monthly comparison process.

---

## Why track upstream?

aniyomi is actively developed. Bugs get fixed, the player improves, new
extensions land. We want those improvements — but we have our own UI, our own
AniList layer, and our own customizations. We can't just merge upstream.

So we **track which aniyomi subsystems we use**, **diff upstream monthly**, and
**selectively adopt** improvements.

---

## What we track

Every aniyomi subsystem we reuse is recorded in `DOCS/APP/STRUCTURE/` with:
- Which aniyomi files/packages we copied.
- The aniyomi commit we copied from.
- Any modifications we made (and why).

Initial tracked subsystems (per `ANIYOMI-INTEGRATION.md`):
- `:source-api` (anime side)
- `AnimeSourceManager` + `AnimeExtensionManager`
- `PlayerActivity` + `PlayerViewModel` + MPV
- `AnimeDownloadManager` + download job
- `AnimeDatabase` (SQLDelight)
- `BackupCreator` + `BackupRestorer` (anime fields)
- Injekt modules (anime bindings)

---

## The monthly comparison process

1. **Take a fresh aniyomi snapshot** → `REFERENCE-STAGING/`.
   - Shallow-clone latest aniyomi `main`.
   - Record the new commit hash + date.

2. **Diff** `REFERENCE-STAGING/` vs `REFERENCE/` (the old snapshot).
   - `diff -qr REFERENCE/ REFERENCE-STAGING/` for a file-level overview.
   - Focus on the **tracked subsystems** (ignore manga-side, UI, i18n changes
     we don't use).

3. **For each tracked subsystem that changed:**
   - Read the diff.
   - Assess: is this a bug fix? A feature? A refactor? A breaking change?
   - Does it affect our customizations?

4. **Decide: adopt or skip.**
   - **Adopt** → port the change into our working code. Record in
     `MEMORY/DECISIONS/` (ADR style: context → decision → consequence).
   - **Skip** → note why (not relevant, we've diverged, too risky, etc.).

5. **Promote the new snapshot.**
   - Replace `REFERENCE/` with `REFERENCE-STAGING/`.
   - Update `DOCS/REFERENCE-DOCS/SOURCE-SNAPSHOT.md` with the new commit.
   - Clear `REFERENCE-STAGING/`.

6. **Update our tracking docs** in `DOCS/APP/STRUCTURE/` with the new commit
   hash for any subsystem we updated.

---

## How often?

- **Monthly** (target). Set a recurring reminder.
- **On-demand** if a specific aniyomi release fixes a bug we hit or adds a
  feature we want.
- **Not weekly** — too noisy, too much overhead.

---

## What we do NOT track

- Manga-side changes (we're anime-only).
- aniyomi UI/theme changes (we have our own UI).
- i18n changes (we have our own strings).
- Build/CI changes (we have our own build).
- Macrobenchmark changes (not shipped).

> This keeps the monthly diff focused on what matters to us.

---

## Risk: divergence

The longer we go between adoptions, the more our code diverges from aniyomi's.
If we diverge too far, adopting upstream changes becomes a manual port (not a
diff). Mitigation:
- Keep our backend layer's package structure **close to aniyomi's** (per the
  hybrid layout decision — D1).
- Adopt small fixes promptly (low cost, high value).
- Document every modification we make to aniyomi code (in `DOCS/APP/STRUCTURE/`)
  so future adoptions know what's ours vs. upstream.

---

## Open questions

- [ ] Monthly cadence: right, or too often/not enough?
- [ ] Who triggers it: reminder, or manual?
- [ ] How to handle breaking upstream changes (e.g. source-api v2)?

---

_Last updated: Session 8 (initial draft)._

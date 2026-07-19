# REFERENCE/ Source Snapshot

> Records exactly which version of aniyomi is in `REFERENCE/`.
> Update this file **every time** `REFERENCE/` is refreshed.

---

## Current snapshot

| Field | Value |
|-------|-------|
| Source repo | https://github.com/aniyomiorg/aniyomi |
| Source branch | `main` |
| Source commit (full) | `2f5cf775c4d93832aa5acee6b4bca776a869e7ef` |
| Source commit (short) | `2f5cf77` |
| Commit date | 2025-11-05 |
| Commit message | `chore(i18n): Translations update from Hosted Weblate (#2217)` |
| Copied into REFERENCE/ on | Session 2 (repo setup phase) |
| Clone method | `git clone --depth 1` (shallow — no history) |
| Files copied | 1,988 |
| Approx. size | 24 MB |
| `.git` history included? | **No** — removed (REFERENCE/ is a plain file snapshot) |

---

## How this snapshot was created

1. Shallow-cloned aniyomi `main` to a temp dir (`--depth 1`).
2. Recorded the commit hash + date (above).
3. Removed the temp clone's `.git/` directory.
4. Copied all files (including hidden) into `REFERENCE/`.
5. Force-added `.idea/icon.png` and `app/.idea/*` (these are part of
   aniyomi's committed snapshot per upstream `.gitignore` exceptions).
6. Committed + pushed to the anikuta repo.

---

## How to refresh REFERENCE/ later

When we want to pull in upstream updates:

1. Copy the **latest** aniyomi into `REFERENCE-STAGING/` (same shallow-clone
   method, record its commit in `REFERENCE-STAGING-SNAPSHOT.md` if you create
   one, or just note it here).
2. Diff `REFERENCE-STAGING/` against `REFERENCE/`:
   `diff -qr REFERENCE/ REFERENCE-STAGING/` (or use a git diff tool).
3. Review what changed.
4. Decide what to adopt → apply to our working `app/` code.
5. After review is accepted, **replace** `REFERENCE/` with the reviewed
   `REFERENCE-STAGING/` copy (so REFERENCE/ becomes the new trusted snapshot).
6. Update the snapshot info in **this file**.
7. Commit + push.

---

## Notes

- `REFERENCE/` is **read-only**. Never edit files inside it.
- Never build from `REFERENCE/`. It is reference only.
- The aniyomi `.gitignore` and `.gitattributes` inside `REFERENCE/` are part
  of the snapshot — leave them as-is.

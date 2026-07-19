# ANIYOMI_REFRENCE/

This directory holds a **frozen, read-only point-in-time snapshot** of the original
[Aniyomi](https://github.com/aniyomiorg/aniyomi) source code, kept purely as a
**reference** for the ANIKUTA project.

## What's inside

```
ANIYOMI_REFRENCE/
└── ANIYOMI/            ← full Aniyomi source tree (no .git history)
```

## Rules

- 🔒 **READ-ONLY.** Do not modify, reorganize, or build inside this folder.
  It exists so we can read and compare against the original at any time.
- 🚫 **Do not build here.** No `./gradlew` runs inside `ANIYOMI/`. Builds happen
  only for `ANIKUTA_PROJECT/ANIKUTA/` and only via GitHub Actions.
- 📋 **Copying ideas is allowed and encouraged.** When porting a concept, copy the
  relevant code into `ANIKUTA_PROJECT/ANIKUTA/` and adapt it there — do not edit
  the reference in place.

## Provenance

| Field | Value |
|---|---|
| Upstream repo | `https://github.com/aniyomiorg/aniyomi` |
| Upstream branch | `main` |
| Snapshot method | GitHub source tarball (`archive/refs/heads/main.tar.gz`) |
| Includes `.git` history? | **No** — source files only (intentional; this is "not a complete backup") |
| Snapshot date | See the commit that added this folder (`git log -- ANIYOMI_REFRENCE/`) |
| Upstream license | Apache License 2.0 (preserved at `ANIYOMI/LICENSE`) |

## Updating the snapshot

This snapshot is **not** kept in sync with upstream. If we ever want a newer
snapshot, the procedure is:

1. Download the new tarball from upstream.
2. Replace the contents of `ANIYOMI/` entirely.
3. Commit with message: `chore(reference): refresh Aniyomi snapshot to <upstream commit sha>`.
4. Record the change in `docs/04-design-decisions.md`.

Do **not** attempt to `git pull` upstream into this folder — it is not a submodule
and has no `.git` of its own by design.

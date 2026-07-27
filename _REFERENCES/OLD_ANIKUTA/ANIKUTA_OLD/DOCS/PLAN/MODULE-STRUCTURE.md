# Module Structure — Single vs Multi-module

> Should ANI-KUTA be one Gradle module or several? This doc explains both,
> with pros/cons, so you can decide.
>
> **My recommendation: multi-module (5 modules), but fewer than aniyomi's 13.**
> Reasoning below.

---

## What's a "module"?

A Gradle module is a separately-compiled chunk of code with its own `build.gradle.kts`.
Modules depend on each other (e.g. `:app` depends on `:data`). The compiler
**enforces** the boundaries — a module can only use what it explicitly depends on.

- **Single module** = all code in one `app/` folder, one build file.
- **Multi-module** = code split into `:app`, `:core`, `:data`, `:domain`, etc.

---

## Option A — Single module (`:app` only)

All code in one folder. Simplest.

```
anikuta-app/
└── app/
    └── src/main/java/app/anikuta/...
```

### Pros
- **Simplest setup** — one build file, one module.
- **Fast incremental builds** — no module boundaries to recompile.
- **Easy to navigate** — everything in one place.
- **No boilerplate** — no module declarations, no `:settings.gradle.kts` includes.
- **Good for small/new projects** — less overhead.

### Cons
- **No enforced boundaries** — the UI can directly touch the database. Nothing
  stops a developer from importing SQLDelight in a Compose composable. Our
  UI/logic separation rule relies on discipline, not the compiler.
- **Compile time grows** — as code grows, the single module takes longer to
  compile (everything recompiles on change).
- **No reuse** — can't extract a piece to reuse in another app (e.g. a wear-OS
  companion).
- **Doesn't match aniyomi** — aniyomi is multi-module; copying a subsystem into
  a single module means flattening its structure, making upstream tracking harder.

---

## Option B — Multi-module (like aniyomi)

Code split into modules by layer. aniyomi has 13; we'd have fewer.

### Proposed modules (5)

```
anikuta-app/
├── app/                ← :app — the Android app (UI, onboarding, nav, DI glue)
├── core/               ← :core — shared utilities (DI, preferences, storage)
├── data/               ← :data — SQLDelight DB + repositories (anime only)
├── domain/             ← :domain — domain models + use cases + repo interfaces
└── source-api/         ← :source-api — the anime source/extension contract
```

**Dependency graph:**
```
:app  →  :data  →  :domain  →  :source-api  →  :core
 :app  →  :domain
 :app  →  :source-api
 :app  →  :core
```

### Pros
- **Compiler-enforced boundaries** — the UI (`:app`) literally cannot import
  SQLDelight (it's in `:data`, which `:app` depends on, but the DB classes are
  internal). Our UI/logic separation rule becomes a compile-time guarantee.
- **Matches aniyomi** — the modules map to aniyomi's modules (`:source-api`,
  `:domain`, `:data`, `:core:common`). Copying a subsystem = copying into the
  matching module. Upstream tracking is cleaner.
- **Faster clean builds** — modules compile in parallel.
- **Clear ownership** — each module has a clear purpose.
- **Future reuse** — if we ever do a wear-OS app, `:core` + `:domain` + `:data`
  could be shared.

### Cons
- **More setup** — 5 build files instead of 1; `:settings.gradle.kts` includes.
- **Slower incremental builds** — changing `:core` recompiles everything that
  depends on it. (Mitigated: incremental builds are still fast for small changes.)
- **More complex** — a newcomer has to understand the module graph.
- **Boilerplate** — each module needs its own build config.

---

## Why I recommend multi-module (Option B, 5 modules)

1. **Enforces our #1 rule (UI/logic separation).** With single-module, the rule
   is just words. With multi-module, the compiler enforces it — the UI can't
   reach the DB. This is huge for keeping the codebase clean long-term.
2. **Matches aniyomi's structure.** We're selectively copying aniyomi's backend
   (D1). aniyomi's backend is already split into `:source-api`, `:domain`,
   `:data`, `:core:common`. Copying into matching modules is cleaner than
   flattening into one folder.
3. **Upstream tracking is easier.** When aniyomi updates `:data`, we look at our
   `:data` module. The mapping is 1:1. With single-module, the mapping is muddy.
4. **5 modules, not 13.** We drop manga-only modules, i18n (we do our own
   strings), presentation-core/widget (we do our own UI), macrobenchmark,
   core/archive, core-metadata, source-local. Only what we need.

### Why NOT aniyomi's full 13 modules?
- We're anime-only (D2) → no manga modules.
- We do our own UI (4 designs) → no `:presentation-core` / `:presentation-widget`.
- We do our own strings → no `:i18n` / `:i18n-aniyomi`.
- No benchmarks yet → no `:macrobenchmark`.
- `:core:archive` + `:core-metadata` are manga-archive-focused → skip (add later
  if needed).
- `:source-local` (local manga source) → skip (add later if we support local
  anime files).

Result: 5 modules instead of 13. Simpler, but still enforces boundaries + maps
to aniyomi for upstream tracking.

---

## Decision needed

- **Option A (single module)** — simplest, fastest incremental builds, no
  enforced boundaries.
- **Option B (5 modules, recommended)** — enforced UI/logic separation, matches
  aniyomi for easier upstream tracking, slightly more setup.

> My strong recommendation: **Option B (5 modules)**. The enforced boundaries
> are worth the small setup cost, and the aniyomi mapping makes our selective
> copy-paste strategy (D1) much cleaner.

---

## Open questions

- [ ] User decides: single or multi-module?
- [ ] If multi: are these 5 modules right, or add/remove any?

# Decisions Analysis (pros/cons, no decisions yet)

> Four architectural decisions are on the table for when we start building
> ANI-KUTA. This doc lays out the **trade-offs** for each so we can decide
> later with full understanding. **No decision is made here** — that's the
> user's call.
>
> Informed by `ANALYSIS-DUAL-MODEL.md` and the `SUBSYSTEMS/*.md` docs.
> aniyomi snapshot: `2f5cf77` (2025-11-05).

---

## Decision 1 — Package layout: mirror aniyomi vs. our own structure

**The question:** Should our working `app/` mirror aniyomi's package layout
(`eu.kanade.tachiyomi.*` + `eu.kanade.tachiyomi.animesource.*` + `mihon.*`),
or restructure into our own `app.anikuta` + feature folders?

### Option A — Mirror aniyomi's layout (rename root to `app.anikuta` only)

Keep aniyomi's internal package structure almost as-is, only changing the
root applicationId/package to `app.anikuta`.

**Pros:**
- **Maximum code-copyability.** We can lift whole files/packages from
  `REFERENCE/` with minimal edits. Re-adding manga later = near-trivial copy.
- **Upstream updates apply easily.** Diffs from aniyomi map 1:1 to our code.
- **Lower rewrite risk.** Less chance of introducing bugs during restructuring.
- **Faster initial build.** Copy anime side → rename → ship.

**Cons:**
- We inherit aniyomi's **legacy naming** (`eu.kanade.tachiyomi.*` is a
  Tachiyomi-era namespace, not `app.anikuta`). Inconsistent with our app id.
- We inherit aniyomi's **mixed architecture** (some legacy Views + Compose,
  Injekt, SQLDelight) unless we actively refactor within the mirrored layout.
- Less alignment with our "feature-based folders" rule — aniyomi is layered
  (`ui/`, `data/`, `domain/`) not strictly feature-foldered.
- Harder to apply our UI/logic separation cleanly without fighting the existing structure.

### Option B — Restructure into `app.anikuta` + feature folders

Define our own package layout: `app.anikuta.<feature>.*` with each feature
holding its own ui/logic/data sub-packages.

**Pros:**
- **Clean alignment with our rules** (feature folders, UI/logic separation).
- **Custom identity** — everything under `app.anikuta`, no legacy namespace.
- **Easier to reason about** for new contributors (feature = folder).
- We can be **Compose-first** and **anime-only from the start** without
  carrying manga baggage.

**Cons:**
- **Code can't be copied 1:1** from aniyomi — every adoption is a port.
  Slower initial build; more rewrite risk.
- **Upstream updates are manual ports**, not diffs. More ongoing work,
  especially if we want to track aniyomi fixes.
- **Re-adding manga later is a full port**, not a copy. Higher cost.
- Risk of "improving" things we don't fully understand yet.

### Hybrid (worth considering)
- Mirror aniyomi's layout for the **core/shared** parts (source-api, core,
  data, domain) so they stay copy-friendly.
- Use our own `app.anikuta.<feature>` structure for the **app UI layer**
  (screens, player UI) where we want customization.
- Trade-off: medium copyability + medium custom structure; some boundary friction.

**Key input from the dual-model analysis:** because anime and manga are
parallel stacks, the choice mainly affects **how easily we can re-add manga
later** and **how easily we adopt upstream updates**. If we expect to track
aniyomi closely, mirroring helps. If we expect to diverge, restructuring is
cleaner long-term.

---

## Decision 2 — Anime-only now vs. keep both vs. add manga later

**The question:** Should ANI-KUTA ship anime-only first, keep both from the
start, or build anime-only and add manga later?

### Option A — Anime-only now, add manga later (user's stated preference)

Drop the manga half (see `ANALYSIS-DUAL-MODEL.md` §4 for the procedure).

**Pros:**
- **Smaller, focused codebase.** ~half the code to understand, build, debug.
- **Faster to a working anime app.** Less surface area.
- **Feasible** — the analysis proves anime-only is mechanically clean; the
  anime stack is self-contained.
- **Re-adding manga later is possible** (also mechanical) if we keep our
  layout reasonably close to aniyomi's (see Decision 1).
- Forward-compat: keep backup proto's manga field numbers reserved so old
  backups survive a future manga re-add.

**Cons:**
- **Re-adding manga later is NOT free.** It's mechanical only if our layout
  stays close to aniyomi's. If we've restructured (Decision 1 Option B),
  re-adding is a full port.
- The 5 cross-cutting feeds (Library/History/Updates/Stats/Storage) must be
  simplified to anime-only now, then **re-generalized** when manga returns —
  some rework.
- If manga demand is high, doing it once (Option B) might've been cheaper
  than build-then-re-add.
- We lose manga users entirely until re-added.

### Option B — Keep both (like aniyomi)

Keep the full dual stack.

**Pros:**
- **No rework.** Both audiences served from day one.
- **Stays closest to upstream** — easiest to track aniyomi updates.
- No risk of "can't cleanly re-add manga later."

**Cons:**
- **~2× the code** to understand, build, debug, and maintain.
- Slower to a working app.
- Carries manga complexity we may not need (the user leans anime-first).
- More DI bindings, more DBs, more trackers, more UI surfaces to test.

### Option C — Anime-only, never add manga

Commit to anime-only permanently.

**Pros:** simplest long-term; no manga maintenance burden.
**Cons:** forfeits manga users; can't easily reverse if需求 changes.

**User's current direction:** anime-first, add manga later if easily doable.
The analysis says "easily doable" is **conditional on Decision 1** — if we
mirror aniyomi's layout, re-adding manga is mechanical; if we restructure,
it's a port. So **Decisions 1 and 2 are linked.**

---

## Decision 3 — DI framework: Injekt vs. Hilt vs. Koin

**The question:** Keep aniyomi's Injekt, or switch to Hilt (Dagger) or Koin?

### Option A — Keep Injekt
**Pros:** zero migration; copy aniyomi's `AppModule`/`DomainModule` as-is; works today.
**Cons:** Injekt is a niche/legacy framework (Mihon fork); smaller community; fewer learning resources; no compile-time graph verification; harder for new contributors.

### Option B — Switch to Hilt (Dagger)
**Pros:** industry standard; compile-time DI graph verification; huge community; great tooling; well-documented.
**Cons:** significant migration (rewrite all `InjektModule` bindings as `@Module`/`@Provides`/`@Inject`); more boilerplate; longer build times; can't copy aniyomi's DI code.

### Option C — Switch to Koin
**Pros:** pure-Kotlin DSL (no annotation processing); fast builds; runtime DI (flexible); easier than Hilt; good docs.
**Cons:** runtime errors (no compile-time verification); still a migration from Injekt; can't copy aniyomi's DI code.

**Trade-off summary:** Injekt = fastest start, worst long-term tooling. Hilt = best long-term, biggest migration. Koin = middle ground. If we mirror aniyomi (Decision 1 Option A), keeping Injekt is cheap. If we restructure, switching DI is natural.

---

## Decision 4 — Persistence: SQLDelight vs. Room

**The question:** Keep aniyomi's SQLDelight, or switch to Room?

### Option A — Keep SQLDelight
**Pros:** zero migration; copy aniyomi's `.sq` schemas + repos as-is; type-safe SQL; good multiplatform story; works today.
**Cons:** smaller community than Room; `.sq` files less familiar to many Android devs; schema migrations handled differently; fewer GUI tools.

### Option B — Switch to Room
**Pros:** industry standard Android ORM; huge community; annotation-based (`@Entity`/`@Dao`); excellent IDE support; well-documented.
**Cons:** significant migration (rewrite all `.sq` → `@Entity`, all queries → `@Dao`); can't copy aniyomi's data layer; lose the dual-DB pattern (Room can do multiple DBs but it's less idiomatic); migration risk.

**Trade-off summary:** SQLDelight = fastest start, copy aniyomi's data layer. Room = more familiar/toolable, but a full data-layer rewrite. Same linkage as Decision 3: mirroring aniyomi favors keeping SQLDelight; restructuring favors Room.

---

## How the 4 decisions interact

They're not independent:

- **Mirroring aniyomi (D1-A) + keeping Injekt (D3-A) + keeping SQLDelight (D4-A) + anime-only-now (D2-A)** = fastest path to a working anime app, easiest upstream tracking, easiest manga re-add. Cost: we inherit aniyomi's legacy choices.
- **Restructuring (D1-B) + Hilt (D3-B) + Room (D4-B) + anime-only-now (D2-A)** = cleanest long-term architecture, fully our own. Cost: slower start, every upstream update is a manual port, manga re-add is a full port.
- **Hybrid (D1-hybrid) + keep Injekt + keep SQLDelight + anime-only-now** = copy the core/data layers, customize the UI. Middle ground.

The user's stated direction (custom package `app.anikuta`, anime-first) leans
toward **some restructuring** — but the full cost of restructuring (D1-B +
D3-B + D4-B) is high. The hybrid is likely the sweet spot, but that's a
decision for later — not this doc.

---

## No decisions made

Per the user's instruction, this doc only **analyzes**. The actual decisions
will be recorded in `MEMORY/DECISIONS/` when we're ready to start building.

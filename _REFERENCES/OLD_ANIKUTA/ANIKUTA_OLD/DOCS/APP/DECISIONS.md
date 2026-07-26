# Decisions — ANI-KUTA build

> The architectural decisions for our app, with reasoning. One-line summaries
> live in `MEMORY/DECISIONS/`; deeper reasoning lives here.
>
> The 4 build decisions (D1-D4) were analyzed in
> `DOCS/REFERENCE-DOCS/DECISIONS-ANALYSIS.md`. This file records the final
> choices.

---

## D1 — Package layout: Selective copy-paste (refined hybrid)

**Decision:** Selective copy-paste with documentation.
- We **do NOT mirror aniyomi's package structure** wholesale. Instead, we use
  our own `app.anikuta.*` packages throughout.
- We **copy the specific aniyomi parts we need** (source-api, player, download
  manager, data layer, DI wiring) into our app, adapting them into our package
  structure.
- **Every copied part is documented** in `DOCS/APP/STRUCTURE/` — what we copied,
  from which aniyomi commit, what we changed, and why — so we know exactly what's
  ours vs. upstream.

**Reasoning:**
- Gives us a clean, custom identity everywhere (`app.anikuta.*`).
- We only pull in what we actually use (no dead manga code, no legacy UI).
- Documentation of every adoption makes upstream tracking precise: when aniyomi
  updates a file we copied, we know exactly where our version lives and what we
  changed.
- Matches the user's vision: "copy-pasting the parts which we need from aniyomi
  and using it in our own application and documenting it along the way."

**Trade-offs accepted:**
- Upstream adoption is a **port** (not a diff) — we read the aniyomi change,
  decide if we want it, then adapt it into our structure. Slower than mirroring,
  but cleaner.
- Re-adding manga later is also a port. Accepted — the dual-model analysis shows
  manga is a parallel stack, so the port is mechanical.
- We must be disciplined about documenting every adoption, or upstream tracking
  breaks down.

**Linked decisions:** D2 (anime-only), D3 (keep Injekt — we copy aniyomi's DI
wiring into our structure), D4 (keep SQLDelight — we copy aniyomi's schemas).

---

## D2 — Anime-only now, add manga later

**Decision:** Ship anime-only. Manga can be added later as a parallel stack.

**Reasoning:**
- The dual-model analysis (`REFERENCE-DOCS/ANALYSIS-DUAL-MODEL.md`) proved
  anime & manga are parallel, not coupled. Anime-only is mechanically clean.
- Smaller, focused codebase → faster to a working app.
- Re-adding manga later is feasible because we mirror the backend (D1) — the
  manga packages can be copied back with minimal porting.

**Trade-offs accepted:**
- Manga users aren't served until manga is added.
- The 5 cross-cutting feeds (Library/History/Updates/Stats/Storage) need
  simplifying to anime-only now, re-generalizing when manga returns. Some
  rework, but bounded.

**Forward-compat:** Keep backup proto's manga field numbers reserved so old
backups survive a future manga re-add.

---

## D3 — DI framework: Keep Injekt

**Decision:** Keep Injekt (aniyomi's choice). Copy aniyomi's DI wiring into our
app structure, adapted to `app.anikuta.*`.

**Reasoning:**
- We're selectively copying aniyomi's backend (D1), including its DI wiring.
  Keeping Injekt means we can copy the wiring near-verbatim.
- Switching to Hilt/Koin = rewriting all DI wiring for no immediate benefit.
- Injekt works. If it becomes a pain point, we can switch later.

**Known limitations of Injekt (flagged for awareness, accepted):**
- **Niche / small community:** fewer tutorials and Stack Overflow answers than
  Hilt/Koin. Mitigation: aniyomi's codebase IS the reference — we copy working
  patterns.
- **No compile-time DI graph verification:** errors surface at runtime, not at
  build time (unlike Hilt). Mitigation: test early, test each binding as we wire it.
- **Less IDE tooling:** Hilt has better Android Studio support (navigation,
  find usages). Mitigation: disciplined naming + documentation.
- **Not under active development:** Injekt is a Mihon fork (`com.github.mihonapp:injekt`).
  It's stable but won't get new features. Mitigation: it does what we need; no
  new features required.
- **Verbose module syntax:** `addSingletonFactory` / `addFactory` calls are more
  verbose than Hilt annotations. Mitigation: accepted — copy-paste from aniyomi.

**When we'd reconsider:** if we hit a DI-related bug we can't debug, or if
onboarding new contributors proves hard due to Injekt's obscurity.

---

## D4 — Persistence: SQLDelight

**Decision:** Keep SQLDelight (aniyomi's choice).

**Reasoning:**
- We're mirroring the data layer (D1), so we copy aniyomi's `.sq` schemas +
  repository implementations directly. Zero migration cost.
- SQLDelight is type-safe, has good multiplatform story, works well.
- Switching to Room = full data-layer rewrite for no immediate benefit.

**Trade-offs accepted:**
- SQLDelight is less widely known than Room. Mitigated by the fact that we're
  copying aniyomi's working data layer (we don't need to be SQLDelight experts
  to use it).
- `.sq` files are less familiar to some contributors. Accepted.

---

## How these decisions interact

- **D1 (hybrid) + D2 (anime-only) + D4 (SQLDelight)** form a coherent "copy
  the backend, build our own UI" strategy.
- **D3 (if keep Injekt)** completes the "copy-friendly backend" picture.
- If D3 were "switch to Hilt", it would partially undermine the copy-friendly
  backend (we'd rewrite DI wiring). Still viable, just costlier.

---

## Other architectural decisions (beyond D1-D4)

These are made/recorded in `MEMORY/DECISIONS/` and detailed here as needed:

- **AniList as discovery layer** — NOT aniyomi's model. Our key differentiator.
  See `PLAN/PRODUCT-VISION.md` + `PLAN/ANIYOMI-INTEGRATION.md`.
- **Compose-first UI** — no legacy Views. aniyomi is mid-migration; we start clean.
- **4-design customization system** — planned from day one. See `PLAN/CUSTOMIZATION.md`.
- **Monthly upstream tracking** — selective adoption. See `PLAN/UPSTREAM-TRACKING.md`.

---

_Last updated: Session 9. All 4 decisions decided (D1 selective copy-paste, D2 anime-only, D3 keep Injekt, D4 keep SQLDelight)._

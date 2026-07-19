# ANI_KUTA_NEW

> A ground-up restructure and reimagining of [Aniyomi](https://github.com/aniyomiorg/aniyomi) — a manga & anime reader app for Android.
> We reuse Aniyomi's core ideas and functionality, but with a complete redesign, restructure, and rework tailored to our own preferences.

---

## What is this repository?

This is the **monorepo** that holds **everything** related to the ANIKUTA project:

1. A **read-only reference snapshot** of the original Aniyomi source code (`ANIYOMI_REFRENCE/`) — for study, comparison, and porting.
2. A **read-only snapshot of our previous ANIKUTA attempt** (`OLD_ANIKUTA/`) — the earlier project that reached a working state but had structural issues. We mine it for prior research and lessons learned, but rebuild fresh.
3. Our **actual new project**, `ANIKUTA` (`ANIKUTA_PROJECT/`), being built from scratch using Aniyomi as a conceptual foundation — not as a fork.
4. **Documentation, design decisions, AI-agent rules, and session handoff notes** so the work can be continued by any new contributor (human or AI agent) without losing context.

> ⚠️ **This is NOT a fork of Aniyomi.** The Aniyomi source lives under `ANIYOMI_REFRENCE/` purely as a reference. All new work happens under `ANIKUTA_PROJECT/`.

---

## Repository layout

```
ANI_KUTA_NEW/                       ← repository root (kept clean & navigable)
├── README.md                       ← you are here (start here)
├── ARCHITECTURE.md                 ← ⭐ SINGLE SOURCE OF TRUTH (read FIRST per RULES/ai-agent-rules.md §2)
├── AGENTS.md                       ← orientation guide for AI agents (READ THIS if you are an agent)
├── LICENSE                         ← Apache 2.0 (inherited from Aniyomi reference; see DOCS/04)
├── .gitignore
├── .github/
│   ├── CODEOWNERS
│   └── workflows/                  ← CI / build pipelines (GitHub Actions ONLY — no local APK builds)
├── ANIYOMI_REFRENCE/               ← READ-ONLY reference: original Aniyomi source
│   ├── ANIYOMI/                    ← full Aniyomi source (no .git history; see its README)
│   └── DOCUMENTATION/              ← 68-doc analysis of the Aniyomi source (read this!)
├── OLD_ANIKUTA/                    ← READ-ONLY reference: our previous ANIKUTA attempt
│   ├── ANIKUTA_OLD/                ← the old project (source-only); has prior Aniyomi analysis
│   └── ANALYSIS/                   ← analysis of the old project's key screens (design references)
├── ANIKUTA_PROJECT/                ← OUR project (all new work lives here)
│   └── ANIKUTA/                    ← the actual app codebase (skeleton for now)
├── DOCS/                           ← architecture draft, design decisions (ADRs), roadmap
│   ├── 01-project-overview.md
│   ├── 02-target-architecture.md
│   ├── 03-reference-module-map.md
│   ├── 04-design-decisions.md      ← ADRs 001–022 (vision + project decisions)
│   ├── 05-roadmap.md
│   └── 06-build-and-ci.md
├── PLANNING/                       ← detailed specs (feature specs, screen specs, data model, module architecture)
├── DESIGN_LANGUAGE/                ← the ANIKUTA design language spec (principles, components, themes, per-screen)
├── AGENT_CONTEXT/                  ← ⭐ onboarding checkpoint for new AI agents (read START_HERE.md first)
└── RULES/                          ← AI-agent operating rules & session handoff notes
    ├── ai-agent-rules.md           ← the comprehensive general ruleset (14 sections)
    ├── project-conventions.md      ← ANIKUTA-specific rules (reference boundary, CI-only, etc.)
    ├── notifications.md            ← ntfy.sh task-completion notification rule
    ├── session-handoff-template.md
    └── sessions/                   ← session handoff notes (newest = most recent)
```

---

## Current status

**Phase 0b — Design & Planning** (in progress). See [`DOCS/05-roadmap.md`](DOCS/05-roadmap.md).

What is done:
- ✅ Repository created and structured.
- ✅ Aniyomi reference source downloaded → `ANIYOMI_REFRENCE/ANIYOMI/`.
- ✅ Aniyomi reference fully documented → `ANIYOMI_REFRENCE/DOCUMENTATION/` (68 docs, ~21,900 lines).
- ✅ Old ANIKUTA project snapshot downloaded → `OLD_ANIKUTA/ANIKUTA_OLD/`.
- ✅ Old ANIKUTA key screens analyzed → `OLD_ANIKUTA/ANALYSIS/` (design references).
- ✅ Project docs (`DOCS/`) and AI-agent rules (`RULES/`) in place.
- ✅ `ARCHITECTURE.md` stub created (will be finalized in this phase).
- ✅ Build policy established: **GitHub Actions only.** No APK is ever built locally.
- ✅ ntfy.sh task-completion notification rule established.
- ✅ Comprehensive AI agent ruleset established (`RULES/ai-agent-rules.md`).
- ✅ Vision clarified → 14 ADRs (009–022) in `DOCS/04-design-decisions.md`.
- ✅ Design language docs started → `DESIGN_LANGUAGE/` (principles, components, themes, bottom-nav, watch-page).
- ✅ Planning folders created → `PLANNING/` (feature specs, screen specs, data model, module architecture).
- ✅ Agent onboarding checkpoint → `AGENT_CONTEXT/START_HERE.md`.

What is NOT done yet (deliberately — to be decided next):
- ❌ The actual `ANIKUTA` app codebase (only a placeholder README exists for now).
- ❌ `ARCHITECTURE.md` finalization (module list, DI, persistence, player embedding).
- ❌ Remaining open decisions in `DOCS/04` (DI framework, persistence, SDK, etc.).
- ❌ Episode metadata source (the owner will specify).
- ❌ Remaining per-screen design specs in `DESIGN_LANGUAGE/04-screens/`.

See [`DOCS/05-roadmap.md`](DOCS/05-roadmap.md) for the full plan.

---

## Build & release policy

- **APKs are built exclusively via GitHub Actions.** No contributor (human or AI agent) is expected to produce an APK in their local environment.
- The CI workflow under `.github/workflows/` will be expanded in later phases to compile, test, and release.
- See [`DOCS/06-build-and-ci.md`](DOCS/06-build-and-ci.md) for details.

---

## For AI agents picking up this work

1. **Read [`ARCHITECTURE.md`](ARCHITECTURE.md) FIRST** — it's the single source of truth (per [`RULES/ai-agent-rules.md`](RULES/ai-agent-rules.md) §2).
2. **Read [`AGENTS.md`](AGENTS.md)** — orientation guide.
3. **Read [`RULES/`](RULES/)** — the rules (general + project-specific).
4. **Read [`DOCS/`](DOCS/)** — the project planning docs.
5. **Read the newest session note in [`RULES/sessions/`](RULES/sessions/)** — what the last agent was doing.

---

## License

Apache License 2.0 — inherited from the Aniyomi reference (which is Apache-2.0 licensed). Derivative works must preserve attribution. See [`LICENSE`](LICENSE) and the licensing note in [`DOCS/04-design-decisions.md`](DOCS/04-design-decisions.md). This choice is a sensible default for an Apache-2.0 derivative and can be revisited.

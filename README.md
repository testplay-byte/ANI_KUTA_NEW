# ANI_KUTA_NEW

> A ground-up restructure and reimagining of [Aniyomi](https://github.com/aniyomiorg/aniyomi) — a manga & anime reader app for Android.
> We reuse Aniyomi's core ideas and functionality, but with a complete redesign, restructure, and rework tailored to our own preferences.

---

## What is this repository?

This is the **monorepo** that holds **everything** related to the ANIKUTA project:

1. A **read-only reference snapshot** of the original Aniyomi source code (for study, comparison, and porting).
2. Our **actual project**, `ANIKUTA`, which is being built from scratch using Aniyomi as a conceptual foundation — not as a fork.
3. **Documentation, design decisions, AI-agent rules, and session handoff notes** so the work can be continued by any new contributor (human or AI agent) without losing context.

> ⚠️ **This is NOT a fork of Aniyomi.** The Aniyomi source lives under `ANIYOMI_REFRENCE/` purely as a reference. All new work happens under `ANIKUTA_PROJECT/`.

---

## Repository layout

```
ANI_KUTA_NEW/                       ← repository root (kept clean & navigable)
├── README.md                       ← you are here (start here)
├── AGENTS.md                       ← orientation guide for AI agents (READ THIS if you are an agent)
├── LICENSE                         ← Apache 2.0 (inherited from Aniyomi reference; see docs/04)
├── .gitignore
├── .github/
│   ├── CODEOWNERS
│   └── workflows/                  ← CI / build pipelines (GitHub Actions ONLY — no local APK builds)
├── ANIYOMI_REFRENCE/               ← READ-ONLY reference snapshot
│   └── ANIYOMI/                    ← full Aniyomi source (no .git history; see its README)
├── ANIKUTA_PROJECT/                ← OUR project (all new work lives here)
│   └── ANIKUTA/                    ← the actual app codebase (skeleton for now)
├── docs/                           ← architecture, design decisions, roadmap, reference map
│   ├── 01-project-overview.md
│   ├── 02-target-architecture.md
│   ├── 03-reference-module-map.md
│   ├── 04-design-decisions.md
│   ├── 05-roadmap.md
│   └── 06-build-and-ci.md
└── rules/                          ← AI-agent operating rules & session handoff notes
    ├── agent-conventions.md
    └── session-handoff-template.md
```

---

## Current status

**Phase 0 — Repository foundation (this commit).**

What is done:
- ✅ Repository created and structured.
- ✅ Aniyomi reference source downloaded and placed under `ANIYOMI_REFRENCE/ANIYOMI/`.
- ✅ Documentation skeleton (`docs/`) and AI-agent rules (`rules/`) in place.
- ✅ Build policy established: **GitHub Actions only.** No APK is ever built locally.

What is NOT done yet (deliberately — to be decided next):
- ❌ The actual `ANIKUTA` app codebase (only a placeholder README exists for now).
- ❌ Final architecture & module breakdown for ANIKUTA.
- ❌ Feature scope (which Aniyomi features to keep / hide / rework).

See [`docs/05-roadmap.md`](docs/05-roadmap.md) for the full plan.

---

## Build & release policy

- **APKs are built exclusively via GitHub Actions.** No contributor (human or AI agent) is expected to produce an APK in their local environment.
- The CI workflow under `.github/workflows/` will be expanded in later phases to compile, test, and release.
- See [`docs/06-build-and-ci.md`](docs/06-build-and-ci.md) for details.

---

## For AI agents picking up this work

**Read [`AGENTS.md`](AGENTS.md) first.** It tells you exactly where to find context, where you may write code, and what conventions to follow. Then read `rules/` and `docs/`.

---

## License

Apache License 2.0 — inherited from the Aniyomi reference (which is Apache-2.0 licensed). Derivative works must preserve attribution. See [`LICENSE`](LICENSE) and the licensing note in [`docs/04-design-decisions.md`](docs/04-design-decisions.md). This choice is a sensible default for an Apache-2.0 derivative and can be revisited.

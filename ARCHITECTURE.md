# ARCHITECTURE.md

> **This is the single source of truth for the ANIKUTA project.**
> Per [`RULES/ai-agent-rules.md`](RULES/ai-agent-rules.md) §2, every AI session
> MUST read this file before writing any code.

---

## ⚠️ Status: BEING PLANNED (Phase 0b)

The ANIKUTA architecture has **not yet been finalized**. We are in the design
phase (Phase 0b per [`DOCS/05-roadmap.md`](DOCS/05-roadmap.md)). This file is a
**stub** — it will be filled in once the architecture decisions are made.

Until then:
- The **open questions** are listed in [`DOCS/04-design-decisions.md`](DOCS/04-design-decisions.md).
- The **draft thinking** is in [`DOCS/02-target-architecture.md`](DOCS/02-target-architecture.md).
- The **Aniyomi reference** (for study) is in [`ANIYOMI_REFRENCE/`](ANIYOMI_REFRENCE/) with full documentation at [`ANIYOMI_REFRENCE/DOCUMENTATION/`](ANIYOMI_REFRENCE/DOCUMENTATION/).

**Do not start writing ANIKUTA code until this file is finalized.**

---

## What this file WILL contain (once architecture is planned)

- [ ] Module structure (the `:feature:*`, `:core`, `:data` layout per `ai-agent-rules.md` §4).
- [ ] Folder conventions for each module.
- [ ] Naming rules.
- [ ] Architecture patterns (MVVM / MVI / etc.).
- [ ] Dependency choices (DI framework, networking, persistence, etc.).
- [ ] Data flow diagram.
- [ ] Design system location (`:core:designsystem`).
- [ ] AI-specific rules and conventions.

Each decision will be recorded as an ADR in [`DOCS/04-design-decisions.md`](DOCS/04-design-decisions.md)
and summarized here.

---

## How the rules relate to this file

| Rule file | Role |
|---|---|
| [`RULES/ai-agent-rules.md`](RULES/ai-agent-rules.md) | General behavioral rules (data flow, modularity, deps, etc.). Says "follow ARCHITECTURE.md". |
| [`RULES/project-conventions.md`](RULES/project-conventions.md) | ANIKUTA-specific rules (reference boundaries, CI-only builds, notifications, handoff). |
| **This file** (`ARCHITECTURE.md`) | The technical source of truth: modules, patterns, conventions. Currently a stub. |

---

## Reference (for study, NOT the architecture we'll use)

The Aniyomi reference at [`ANIYOMI_REFRENCE/ANIYOMI/`](ANIYOMI_REFRENCE/ANIYOMI/) uses
a different architecture (Tachiyomi/Mihon lineage: `:app`, `:core:common`, `:data`,
`:domain`, Injekt DI, etc.). We are **not** copying it verbatim — our architecture
will follow the patterns in `RULES/ai-agent-rules.md` (`:feature:*`, `:core`,
`:data`). Study the reference for ideas, but implement in our own style.

See [`ANIYOMI_REFRENCE/DOCUMENTATION/`](ANIYOMI_REFRENCE/DOCUMENTATION/) for a
complete analysis of how Aniyomi is built (68 docs, ~21,900 lines).

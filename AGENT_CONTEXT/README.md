# AGENT_CONTEXT/

> **Onboarding checkpoint for new AI agents.**
> Read [`START_HERE.md`](START_HERE.md) first — it's the single entry point that
> gives a new agent the full project context.

## Why this folder exists

This folder is a **context backup for future AI agents**. If a new agent picks up
the project, they read `START_HERE.md` and immediately get: the vision, current
progress, where everything lives, and what to do next — without scrolling through
chat history.

## Files

| File | Purpose |
|---|---|
| [`START_HERE.md`](START_HERE.md) | The single entry point. Read this first. |

## Maintenance

Keep `START_HERE.md` updated as the project evolves. After major milestones
(phase completions, architecture finalization, new feature decisions), update
the "What's been done" and "Current phase" sections.

The goal: a cold-start agent reads only this file + the read-order it points to,
and has enough context to continue working.

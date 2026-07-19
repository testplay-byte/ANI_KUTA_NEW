# Agent Conventions

> Hard rules for any AI agent (or human) working in this repo. Follow them.

## 1. Read before you write

Before changing anything, read in this order:
1. `/README.md`, `/AGENTS.md`
2. `/rules/agent-conventions.md` (this file)
3. The newest file in `/rules/sessions/` (if any)
4. `/docs/04-design-decisions.md` and `/docs/05-roadmap.md`
5. The specific area you are about to touch

## 2. Respect the reference boundary

- `ANIYOMI_REFRENCE/` is **read-only** (ADR-005). Never edit it.
- To port a concept: read it in the reference, then implement fresh under
  `ANIKUTA_PROJECT/ANIKUTA/`.

## 3. Build policy (ADR-003)

- Never produce an APK/AAB locally.
- Never run `./gradlew assemble*` / `bundle*` locally.
- Never run any build inside `ANIYOMI_REFRENCE/`.
- Static checks and edits are fine. CI builds.

## 4. One concern per commit

- Small, focused commits. A reviewer (or future agent) should understand each
  commit from its title + diff.
- Commit message format:
  - `feat(anikuta): ...` — new feature in the app
  - `fix(anikuta): ...` — bug fix
  - `refactor(anikuta): ...` — internal change, no behavior shift
  - `docs: ...` — documentation only
  - `docs(adr): ADR-NNN ...` — a new/revised decision record
  - `chore(reference): ...` — reference snapshot refresh
  - `ci: ...` — workflow changes
  - `docs(rules): session handoff YYYY-MM-DD` — a session handoff note

## 5. Document as you go

- New module? Add its `README.md` in the same commit.
- New decision? Add an ADR in `docs/04-design-decisions.md` in the same commit.
- Superseding a decision? Strike through the old one, link the new ADR. Never delete.
- Finished a phase task? Tick it in `docs/05-roadmap.md`.

## 6. Don't assume undecided things

- `docs/02-target-architecture.md` and `docs/04-design-decisions.md` list open
  questions. If you need an answer that isn't there, **ask the human** rather than
  silently picking one. Record the answer as an ADR once given.

## 7. Secrets

- Never commit secrets, tokens, keystores, or `google-services.json`.
- The `.gitignore` blocks common ones; stay vigilant anyway.
- If you accidentally commit a secret, treat it as compromised and tell the human.

## 8. Leave the repo better than you found it

- If you spot a stale doc, fix it.
- If you spot a missing module README, add it.
- If you spot a contradiction between docs, resolve it and note the resolution.

## 9. Hand off cleanly

- At the end of every session, write a handoff note in `rules/sessions/` using
  `session-handoff-template.md`.
- The note must let a cold-start agent continue your work without asking you
  questions.

## 10. When in doubt, ask

- The owner prefers questions over silent assumptions. If a decision is
  load-bearing and not documented, ask before proceeding.

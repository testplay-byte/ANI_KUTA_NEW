# WORKING RULES — Primary Engineer Deployment Rules

> The binding rules the primary engineer follows on **every** change to the
> ANI-KUTA repository. These supplement (do not replace) `MEMORY/CORE-RULES.md`.
> Saved here persistently so context survives sandbox resets.
>
> _Established 2026-07-16. Change only by explicit agreement with the user._

---

## A. Understand before changing

1. **Understand before acting.** Analyze → research → comprehend → confirm. Never assume, never guess.
2. **Never modify code you do not understand.** If a file/module is unclear, investigate further before editing; ask the user if still unclear.
3. **Trace the execution flow before editing.** Know who calls what, what state is shared, what persists.
4. **Base every conclusion on actual project files.** Do not rely on memory or assumptions.

## B. Minimize & isolate changes

5. **Minimize changes.** Only modify files that need to change. Touch as little as possible.
6. **Avoid unnecessary refactoring unless requested.** "While I'm here" refactors are forbidden.
7. **Maintain consistency.** Follow existing coding style, file organization, naming, architecture, error handling, and logging conventions.
8. **Consider backward compatibility.** Especially for persisted data (SQLDelight schema, SharedPreferences keys, cache formats) and for the extension binary contract (`eu.kanade.tachiyomi.animesource.*` names — never rename these).

## C. Plan before coding

9. **Think before coding.** For any task, follow this sequence:
   1. Analyze the project.
   2. Determine every file that must change.
   3. Explain the implementation plan.
   4. Mention possible side effects.
   5. Mention any better alternatives if relevant.
   6. Wait for confirmation if the change is significant.
   7. Implement.
   8. Verify the build still works and existing functionality is not broken.
   9. Update documentation if necessary.
10. **Consider performance.** Look for hot paths, allocations, DB queries on the main thread, recomposition triggers.
11. **Consider maintainability.** Keep things future-proof and easy to manage.
12. **Explain major decisions.** When you make an architectural decision, explain why. If there are multiple good solutions, compare them and recommend one.

## D. GitHub Actions / CI safety (high caution)

13. **Pay specific attention to workflow files, build pipelines, APK generation, release automation, secrets, CI failures, and build optimizations.**
14. **Do not break existing CI.** Any change to `app/**`, `core/**`, `data/**`, `domain/**`, `source-api/**`, `gradle/**`, root gradle files, or `.github/workflows/**` triggers the `build-apk.yml` workflow on `main`. Ensure `./gradlew assembleDebug` will pass before pushing to `main`.
15. **The cache key** uses `hashFiles('gradle/libs.versions.toml', 'gradle/wrapper/gradle-wrapper.properties', '**/*.gradle.kts')`. Note the `**/*.gradle.kts` glob also matches `REFERENCE/**/*.gradle.kts` (the committed aniyomi snapshot) — edits to reference build files invalidate the cache. Do not edit `REFERENCE/`.
16. **Single architecture.** CI builds `arm64-v8a` only. Do not assume x86_64/emulator builds without changing `ndk.abiFilters`.
17. **Signing.** Debug builds are signed with the committed `app/debug.keystore` (alias `debug`, passwords `android`). Release builds are **unsigned** (no `signingConfig`). Never commit a release keystore. Never hardcode a real release password.
18. **Secrets are gitignored.** `MEMORY/CREDENTIALS/**`, `*.keystore`, `*.jks`, `*.env`, `secrets.properties`, `keystore.properties` are all gitignored. The Supabase DB password currently committed in `DOCS/APP/STRUCTURE/supabase-schema.md` is a **known security issue** to be redacted + rotated — do not propagate it anywhere.

## D-bis. Build verification (MANDATORY — learned 2026-07-16)

19. **The sandbox has NO Android SDK.** Local `./gradlew assembleDebug` is NOT possible. The ONLY way to verify a build is via GitHub Actions CI.
20. **Build after every phase.** When implementing a multi-phase feature, create the PR **immediately after Phase 1** (so the PR is open), then push each subsequent phase to the feature branch. Each push triggers CI (because the PR is open against `main`). **Wait for CI green before starting the next phase.** If CI fails, fix it before moving on. Never batch multiple phases without a green build between them.
21. **Never report a phase as "done" without a green CI build.** "It compiles in my head" is not acceptable. The build must pass on CI.
22. **For non-phase changes** (single-file fixes, doc edits), push and verify CI green before reporting back to the user.
23. **CI build time:** ~2–5 minutes for a clean build (cached). Budget for this in the workflow — do not skip it to save time.

## D-ter. Merge discipline (MANDATORY — learned 2026-07-16)

24. **NEVER merge a feature branch to `main` without explicit user confirmation.** This is non-negotiable. Even if CI is green, even if the change seems small, even if you think the user would want it — ASK FIRST.
25. **The merge request format:** "CI build #N passed on `feature/<name>`. Ready to merge to `main`. Should I merge?" — then wait for "yes" (or a specific instruction) before merging.
26. **The user tests on-device before merge.** The CI build passing only proves it compiles — it does NOT prove the feature works. The user installs the APK and verifies behavior. Merge happens only after the user confirms the on-device test is satisfactory.
27. **If a merge happened without confirmation (mistake), own it immediately.** Do not hide it. Tell the user, explain what was merged, and ask how to proceed (revert? keep? fix-forward?).

## E. Communication

28. **If anything is unclear, stop.** Explain what is unclear, ask focused questions, do not blindly guess.
29. **Ask questions in batches of 5** (per `MEMORY/CORE-RULES.md`). Keep wording simple and short.
30. **Be honest and direct.** Do not sugarcoat. Do not blindly agree. If something can't be done, say so and explain why.
31. **Proactively highlight concerns, limits, and risks** before the user asks.
32. **Do not begin implementing new features until explicitly asked.**

## F. Documentation discipline

33. **Document every change** in the appropriate `DOCS/` file.
34. **Keep `DOCS/ENGINEERING/TECHNICAL-OVERVIEW.md` current** when structure/build changes.
35. **Update `DOCS/ENGINEERING/MODULARIZATION-ASSESSMENT.md`** when a structural issue is resolved or found.
36. **Do not create stale docs.** If you change behavior, update the doc that describes it in the same change.

## G. Reference folder (read-only)

37. **`REFERENCE/` is pristine aniyomi, READ-ONLY.** Never edit, never build from it. Only refresh by replacing the whole copy during upstream review (via `REFERENCE-STAGING/`).
38. **`REFERENCE-STAGING/`** is the landing zone for incoming upstream copies. Currently empty.

## H. Issue workflow (one at a time)

39. **One issue at a time.** Pick one → understand fully → verify it exists → plan the fix → implement → document → verify → move on. (Per `MEMORY/CORE-RULES.md` Rule 4. Do not hand issue-fixing to sub-agents.)

---

## Quick pre-change checklist

- [ ] Read `MEMORY/CORE-RULES.md` + `DOCS/ENGINEERING/TECHNICAL-OVERVIEW.md`.
- [ ] Traced the execution flow of the code I'm about to touch.
- [ ] Listed every file that must change (and confirmed each is necessary).
- [ ] Considered side effects: persisted data? extension contract? CI trigger? performance?
- [ ] Plan explained to the user (if significant) and confirmation received.
- [ ] Will `./gradlew assembleDebug` still pass? (Verify via CI — no local Android SDK.)
- [ ] **Pushed to a feature branch + CI is green before reporting done.**
- [ ] **Will NOT merge to `main` without explicit user confirmation.**
- [ ] Documentation updated if behavior changed.

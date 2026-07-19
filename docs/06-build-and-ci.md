# 06 — Build & CI

## Hard rule

> **APKs are built exclusively via GitHub Actions. No contributor (human or AI
> agent) is expected to build an APK locally.** — ADR-003.

This exists so that:
- Builds are reproducible across machines.
- No one needs an Android toolchain installed locally to contribute.
- Release artifacts always come from a trusted CI environment.

## Current state (Phase 0)

The only workflow is [`.github/workflows/ci-placeholder.yml`](../.github/workflows/ci-placeholder.yml).
It runs a lightweight **repo-sanity** job that verifies:
- The reference snapshot is present.
- The ANIKUTA skeleton is present.
- All required documentation files exist.

It does **not** build an APK, because the ANIKUTA app codebase does not exist yet.

## Planned state (Phase 1 onward)

A `build-anikuta` job (currently sketched as a comment block in the placeholder
workflow) will:

1. Check out the repo.
2. Set up JDK 17 (Temurin).
3. Set up Gradle (with caching).
4. Run `./gradlew assembleDebug` from `ANIKUTA_PROJECT/ANIKUTA/`.
5. Upload the debug APK as a build artifact.

Later phases add:
- `lintDebug`, `testDebugUnitTest` jobs.
- A release job (on tagged pushes) that signs the APK with CI secrets and
  publishes a GitHub Release. Signing keys are stored as GitHub Actions secrets —
  **never** in the repo.
- Baseline profile generation (mirroring Aniyomi's `:macrobenchmark`).

## What agents/contributors MAY do locally

- Edit, lint, and type-check code (where it does not require producing an APK).
- Run static analysis that the architecture later introduces.
- Commit and push; let CI build.

## What agents/contributors MUST NOT do locally

- `./gradlew assembleDebug` / `assembleRelease` / `bundleRelease`.
- Any command whose output is an APK/AAB.
- Generating signing keystores or signing artifacts locally for release.
- Running `./gradlew` *inside* `ANIYOMI_REFRENCE/ANIYOMI/` (the reference is
  read-only and not built).

## CI secrets (to configure before Phase 6 release)

When we reach the release phase, the following secrets must be added via the
GitHub repo settings → Secrets and variables → Actions:

| Secret | Purpose |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | Base64-encoded release keystore. |
| `SIGNING_KEY_ALIAS` | Keystore key alias. |
| `SIGNING_KEY_PASSWORD` | Key password. |
| `SIGNING_STORE_PASSWORD` | Keystore store password. |

Do **not** add these now. They are listed here so the plan is complete.

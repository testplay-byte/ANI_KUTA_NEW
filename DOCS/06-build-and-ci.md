# 06 — Build & CI

## Hard rule

> **APKs are built exclusively via GitHub Actions. No contributor (human or AI
> agent) is expected to build an APK locally.** — ADR-003.

This exists so that:
- Builds are reproducible across machines.
- No one needs an Android toolchain installed locally to contribute.
- Release artifacts always come from a trusted CI environment.

## Current state (Phase 8+)

The CI workflow lives at [`.github/workflows/ci.yml`](../.github/workflows/ci.yml).
It triggers on:
- Push to `main` (auto).
- Pull requests to `main` (auto).
- Manual `workflow_dispatch` (for feature branches).

It runs two jobs:

### `build` job
1. Checks out the repo.
2. Sets up JDK 17 (Temurin).
3. Sets up Gradle (with caching).
4. Runs `./gradlew :app:assembleDebug --stacktrace` from `ANIKUTA_PROJECT/ANIKUTA/`.
   - ABI: arm64-v8a only (ADR-032) — single APK, no universal APK.
   - Stable debug signing (committed `anikuta-debug.keystore`) so CI builds can
     update over previous versions without uninstalling.
5. Runs unit tests: `./gradlew :data:anime:testDebugUnitTest --stacktrace`.
6. Uploads the debug APK as a build artifact (`anikuta-debug-arm64-v8a`, ~39 MB).

### `repo-sanity` job
Verifies:
- The `_REFERENCES/` backup snapshots are present.
- All required documentation files exist.

## Known CI limitations (to address in Phase 9)

- **No full build or lint.** CI only runs `:app:assembleDebug` + the `:data:anime`
  unit tests. It does **not** run `./gradlew build` or `:app:lint`, so latent
  issues (like the missing `:i18n` module folder — referenced in
  `settings.gradle.kts` but never created) can slip through. Expanding CI is on
  the Phase 9 roadmap.
- **No release job.** Release signing + GitHub Releases are not yet wired (ADR-003
  lists them as future). The committed keystore is debug-only.

## What agents/contributors MAY do locally

- Edit, lint, and type-check code (where it does not require producing an APK).
- Run static analysis that the architecture later introduces.
- Commit and push; let CI build.

## What agents/contributors MUST NOT do locally

- `./gradlew assembleDebug` / `assembleRelease` / `bundleRelease`.
- Any command whose output is an APK/AAB.
- Generating signing keystores or signing artifacts locally for release.
- Running `./gradlew` *inside* `_REFERENCES/` (the reference snapshots are
  read-only backups and not built — see `_REFERENCES/README.md`).

## CI secrets (to configure before the release phase)

When we reach the release phase, the following secrets must be added via the
GitHub repo settings → Secrets and variables → Actions:

| Secret | Purpose |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | Base64-encoded release keystore. |
| `SIGNING_KEY_ALIAS` | Keystore key alias. |
| `SIGNING_KEY_PASSWORD` | Key password. |
| `SIGNING_STORE_PASSWORD` | Keystore store password. |

Do **not** add these now. They are listed here so the plan is complete.

# Build Environment — GitHub Actions

> How we build ANI-KUTA. We use **GitHub Actions** (the sandbox has limited
> RAM/storage, so we don't build locally). We build **one APK** only: **ARM64-v8a**.

---

## Why GitHub Actions?

The sandbox environment has:
- **Limited storage** — can't hold the Android SDK + Gradle cache + build outputs.
- **Limited RAM** — Gradle builds are RAM-hungry; the sandbox would OOM.

GitHub Actions gives us:
- **Generous free tier** — 2000 min/month for public repos (we're public).
- **Proper build environment** — 7 GB RAM, 14 GB storage on `ubuntu-latest` runners.
- **Reproducible builds** — same environment every time.
- **Artifact storage** — APKs uploaded as artifacts, downloadable.

---

## Build target

- **One APK only** — not per-ABI splits.
- **ABI: `arm64-v8a`** — the modern 64-bit ARM architecture. Covers ~99% of
  Android devices made since 2019. Smaller APK, no unused ABIs.
- **Build type:** `debug` (for now, signed with a debug keystore). `release`
  comes later (Phase 8, with proper signing).

> Why only `arm64-v8a`? Modern Android phones are all 64-bit ARM. Building other
> ABIs (`armeabi-v7a`, `x86_64`) would 3× the APK size and build time for ~0
> extra users. If we ever need x86_64 (emulators), we add it then.

---

## Min SDK

- **minSdk = 26 (Android 8.0)** — covers ~95% of active Android devices.
- **compileSdk / targetSdk** — latest stable (Android 15 / API 35, or whatever's
  current when we build).
- **JDK** — 17 (Android Gradle Plugin requirement).

### Notes on higher APIs
Some features we want need higher APIs — we gate them at runtime:
- **API 29 (Android 10)** — scoped storage (we use SAF anyway).
- **API 31 (Android 12)** — dynamic color (Monet) + `RenderEffect` for blur
  (Neon design's backdrop-blur). We degrade gracefully on lower APIs.
- **API 33 (Android 13)** — notification permission. We request at runtime.

We do NOT raise minSdk for these — we check + degrade.

---

## The GitHub Actions workflow (proposed)

```yaml
# .github/workflows/build-apk.yml
name: Build APK

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]
  workflow_dispatch:  # manual trigger

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17

      - name: Set up Android SDK
        uses: android-actions/setup-android@v3

      - name: Cache Gradle
        uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}

      - name: Build debug APK (arm64-v8a only)
        run: ./gradlew assembleDebug -Pandroid.injected.build.abi=arm64-v8a

      - name: Upload APK artifact
        uses: actions/upload-artifact@v4
        with:
          name: anikuta-debug-arm64-v8a
          path: app/build/outputs/apk/debug/*.apk
```

### How it works
- **Triggers:** on push to `main`, on PR, or manual.
- **Builds:** `./gradlew assembleDebug` with the ABI pinned to `arm64-v8a`.
- **Uploads:** the APK as a downloadable artifact (kept for ~90 days on GitHub).
- **Caching:** Gradle dependencies cached for faster subsequent builds.

### Build output
- One file: `anikuta-debug-arm64-v8a.apk` (or similar).
- Downloadable from the Actions run page.
- User installs it on their ARM64 phone (sideload).

---

## Signing (Phase 8, not now)

For now (debug), we use the auto-generated debug keystore. When we hit Phase 8
(release):
- Generate a stable release keystore (`keytool`).
- Store the keystore + passwords as GitHub Actions **secrets** (encrypted, not
  in the repo).
- The workflow signs the release APK with those secrets.
- The keystore is **stable** so signed updates install over prior builds
  (no uninstall/reinstall).

See `SETUP/README.md` for the signing plan.

---

## What the sandbox does vs. what GitHub Actions does

| Task | Where | Why |
|------|-------|-----|
| Writing/editing code | Sandbox | I do this here. |
| Lint + type-check (fast) | Sandbox (if feasible) | Quick feedback. |
| Full Gradle build + APK | **GitHub Actions** | RAM/storage limits in sandbox. |
| Running the app | User's phone | We send the user the APK; they install + test. |

> I write the code in the sandbox, commit + push to GitHub. GitHub Actions builds
> the APK. The user downloads the APK artifact + installs on their phone.

---

## Open questions

- [ ] GitHub repo: do we want the build workflow on `testplay-byte/anikuta`
  (current), or a separate repo? (Current is fine — the workflow lives in
  `.github/workflows/`.)
- [ ] Build on push to `main` only, or also on a `dev` branch?
- [ ] Keep APK artifacts for how long? (GitHub default is 90 days.)
- [ ] Auto-tag releases? (When we're ready for versioned releases.)

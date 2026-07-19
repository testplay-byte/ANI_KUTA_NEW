# Environment Setup

How to set up a working development environment for the **ANI-KUTA** Android app.
This guide is for a contributor or a fresh sandbox session starting from a clean
machine.

> **Status:** The Android app source (`app/`, `core/`, `data/`, `domain/`,
> `source-api/`) **exists and is fully built** — Phases 0–7 are complete on
> `main`. Debug APKs build via GitHub Actions and locally. See
> `DOCS/CURRENT-STATE.md` for the latest status.

---

## Prerequisites

| Tool | Requirement | Notes |
|------|-------------|-------|
| JDK | **JDK 17** | Required by AGP 8.9.0. Verify with `java -version`. |
| Android SDK | Platforms + build-tools for `compileSdk = 35` | Installed via Android Studio's SDK Manager, or `android-actions/setup-android@v3` in CI. |
| Android Studio (optional) | Latest stable | For IDE support. Not required — command-line `./gradlew` works fine. |
| Git | Any recent version | For cloning and pushing. |
| `keytool` | Bundled with the JDK | Only needed if generating a new keystore. |

Verify after install:
- `java -version` reports JDK 17.
- `git --version` works.
- (If using Android Studio) It launches and the Android SDK location is configured.

---

## Toolchain (verified)

| Component | Version |
|-----------|---------|
| Gradle | 8.13 (wrapper) |
| Android Gradle Plugin (AGP) | 8.9.0 |
| Kotlin | 2.2.0 |
| JDK | 17 |
| compileSdk / targetSdk | 35 |
| minSdk | 26 (Android 8.0) |
| ABI | `arm64-v8a` only |
| Compose BOM | 2025.04.01 |
| SQLDelight | 2.0.2 |

All versions are pinned in `gradle/libs.versions.toml`.

---

## Clone & open

1. Clone the repo:
   ```bash
   git clone https://github.com/testplay-byte/anikuta.git
   cd anikuta
   ```
2. To build from the command line:
   ```bash
   ./gradlew assembleDebug
   ```
   The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`.
3. To open in Android Studio: `File → Open → select the anikuta folder`. Let
   Gradle sync complete; accept any SDK-platform install prompts.

> **Do NOT build from `REFERENCE/`.** It is a read-only copy of the aniyomi
> source kept only for reference. Our working code lives under `app/`, `core/`,
> `data/`, `domain/`, `source-api/`. Building from `REFERENCE/` is explicitly
> out of scope.

---

## Gradle / SDK configuration notes

- **`local.properties` must NOT be committed.** It contains machine-specific
  paths (notably `sdk.dir`). It is covered by `.gitignore`.
- **Secrets must NOT be committed.** Keystores, passwords, and tokens live
  under `MEMORY/CREDENTIALS/` (gitignored). See the signing section below.
- The toolchain versions are centralized in `gradle/libs.versions.toml`
  (the Gradle version catalog). Do not hardcode versions in module
  `build.gradle.kts` files.

---

## APK signing (current state)

### Debug builds — signed, committed keystore (working today)

Debug APKs are signed with a **committed debug keystore** so that new builds
install over previous ones on a device without uninstalling.

- **Keystore file:** `app/debug.keystore` (committed to the repo via
  `git add -f` in commit `8ed9a2c`; bypasses the `*.keystore` rule in `.gitignore`).
- **Alias:** `debug`
- **Store/key password:** `android` (hardcoded in `app/build.gradle.kts`)
- This is safe — it's a debug key, not a release key. The build file documents
  this explicitly.

The signing config is wired in `app/build.gradle.kts`:
```kotlin
signingConfigs {
    getByName("debug") {
        storeFile = file("debug.keystore")
        storePassword = "android"
        keyAlias = "debug"
        keyPassword = "android"
    }
}
buildTypes {
    debug {
        signingConfig = signingConfigs.getByName("debug")
        ...
    }
}
```

### Release builds — UNSIGNED (not yet set up)

- The `release` and `release-debuggable` build types have **no `signingConfig`**.
- Output release APKs are **unsigned** and not installable as-is.
- **Distribution target:** GitHub releases (NOT Play Store). A real release
  signing flow (GitHub Actions secret + gitignored keystore) will be set up
  when we're ready to publish. For now, temporary/debug signing is fine.

### Build types

| Build type | Signed? | Minify | Debuggable | Use |
|------------|---------|--------|------------|-----|
| `debug` | ✅ (committed debug keystore) | off | true | Development + on-device testing |
| `release` | ❌ (unsigned) | off | false | Production (planned — needs signing config) |
| `release-debuggable` | ❌ (unsigned) | off | true | Performance testing with debugging enabled |

> **Note:** `isMinifyEnabled = false` in all three build types. The
> `release-debuggable` build-file comment mentions "R8 optimization" but R8
> does not run because minify is off. Enabling minify later will require
> substantial ProGuard keep-rule work (none exist today) for Injekt
> (reflection), kotlinx.serialization, SQLDelight, JSoup, and extension
> classloading.

---

## First-run checklist

- [ ] JDK 17 installed and `java -version` confirms it.
- [ ] Repo cloned: `git clone https://github.com/testplay-byte/anikuta.git`.
- [ ] `./gradlew assembleDebug` succeeds (or Android Studio Gradle sync passes).
- [ ] Missing SDK components installed (compileSdk 35 / build-tools).
- [ ] `local.properties` exists locally and is **not** staged for commit.
- [ ] Read `DOCS/ENGINEERING/AI-AGENT-ONBOARDING.md` (the entry point).
- [ ] Read `DOCS/CURRENT-STATE.md` (the current status snapshot).
- [ ] Read `DOCS/ENGINEERING/WORKING-RULES.md` (the binding change rules).

---

## Related

- `BUILD-APK/README.md` — how to build APKs and where outputs land.
- `DOCS/ENGINEERING/TECHNICAL-OVERVIEW.md` — full technical map (build system §11, CI §12).
- `DOCS/CURRENT-STATE.md` — current project status.
- `DOCS/ENGINEERING/AI-AGENT-ONBOARDING.md` — read-this-first entry point.

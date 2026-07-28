# Build APK (BUILD-APK/)

This folder holds **built APK outputs** for the ANI-KUTA app. APKs are large
binaries and are gitignored — only this README is tracked.

> **Status:** The build is fully working. Debug APKs are produced by GitHub
> Actions on every push to `main` (and on PRs / manual dispatch). See
> `DOCS/CURRENT-STATE.md` for the latest build status.

---

## What lives here

Built APK files, renamed to follow the naming convention below, copied here
from the Gradle build output directory after a build. The APK files themselves
are gitignored; only this README is tracked.

## Naming convention

Every APK copied here follows this pattern:

```
anikuta-<version>-<variant>-<date>-<short-git-sha>.apk
```

- `<version>` — the app version, e.g. `0.1.0` (matches `versionName`).
- `<variant>` — the build variant: `debug`, `release`, or `release-debuggable`.
- `<date>` — build date in `YYYYMMDD` (UTC).
- `<short-git-sha>` — the short git commit hash at build time (7 chars).

Example:
```
anikuta-0.1.0-debug-20260716-ca644ad.apk
```

## Build variants

| Variant | Signed with | Minify | Use |
|---------|-------------|--------|-----|
| `debug` | Committed debug keystore (`app/debug.keystore`, alias `debug`, password `android`) | off | Development + on-device testing. The stable signing identity allows installing a new build over the previous one without uninstalling. |
| `release` | ❌ Unsigned (no `signingConfig` yet) | off | Production (planned — needs a release signing config). |
| `release-debuggable` | ❌ Unsigned | off | Performance testing with debugging enabled. |

> **Debug keystore:** committed to the repo (force-added in commit `8ed9a2c`,
> bypassing `.gitignore`). Safe because it's a debug key. See `SETUP/README.md`
> → "APK signing" for details.
>
> **Release keystore:** does not exist yet. Release signing will be set up when
> we're ready to publish GitHub releases. Distribution target is GitHub (NOT
> Play Store).

## Gitignore

APK files are gitignored. The `.gitignore` covers `*.apk`, `BUILD-APK/*.apk`,
`BUILD-APK/**/*.apk`, and `*.aab`. Do not commit APKs to the repo — they bloat
history. GitHub is the backup for documentation and memory, not for binaries.

## How to build

### From the command line (Gradle)

From the repo root (where `gradlew` lives):

```bash
# Debug variant (signed with committed debug keystore — installable)
./gradlew assembleDebug

# Release variant (UNSIGNED — not installable as-is)
./gradlew assembleRelease

# Release-debuggable variant (UNSIGNED, debuggable)
./gradlew assembleReleaseDebuggable
```

### From Android Studio

1. Select the build variant in the `Build Variants` tool window
   (e.g. `app -> debug`).
2. `Build → Build Bundle(s) / APK(s) → Build APK(s)`.
3. Once the build finishes, click "locate" in the notification, or open the
   output directory directly (see below).

### Via GitHub Actions (CI)

The workflow `.github/workflows/build-apk.yml` runs `./gradlew assembleDebug`
on every push to `main` (path-filtered), on PRs to `main`, and on manual
dispatch. The debug APK is uploaded as an artifact (`anikuta-debug-arm64-v8a`,
90-day retention). See `DOCS/ENGINEERING/TECHNICAL-OVERVIEW.md` §12 for the
full CI detail.

## Where build outputs land

Gradle writes APKs to the standard output directory:

```
app/build/outputs/apk/<variant>/
```

For example:
```
app/build/outputs/apk/debug/app-debug.apk
```

After a successful local build, **copy** the APK from there into this folder
(`BUILD-APK/`) and rename it using the naming convention above:

```bash
cp app/build/outputs/apk/debug/app-debug.apk \
   BUILD-APK/anikuta-0.1.0-debug-$(date -u +%Y%m%d)-$(git rev-parse --short=7 HEAD).apk
```

> A small script (e.g. `scripts/copy-apk.sh`) to automate the rename+copy step
> would keep naming consistent — not yet created.

## Single-arch build note

The app builds **`arm64-v8a` only** (hardcoded in `app/build.gradle.kts`
`ndk { abiFilters += "arm64-v8a" }`). This means:
- APKs run on physical 64-bit ARM Android devices (the vast majority).
- APKs do **not** run on x86_64 emulators. To test on an emulator, you'd need
  to temporarily add `x86_64` to `abiFilters` (and the MPV/FFmpeg native libs
  must be available for that ABI — they currently are not bundled).

## Related

- `SETUP/README.md` — environment setup + APK signing detail.
- `DOCS/ENGINEERING/TECHNICAL-OVERVIEW.md` §11 (build) + §12 (CI) — full technical detail.
- `DOCS/CURRENT-STATE.md` — current build status.
- `MEMORY/DECISIONS/` — record any decisions about variant/signing changes.

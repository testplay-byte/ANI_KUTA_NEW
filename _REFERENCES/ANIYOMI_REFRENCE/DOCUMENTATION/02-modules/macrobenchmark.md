# 02-modules / macrobenchmark — The `:macrobenchmark` module

> Macrobenchmark and **baseline profile** generation for the app. This module is
> a `com.android.test` project that instruments `:app`'s `benchmark` build type
> to measure startup timing and to produce the `app/src/main/baseline-prof.txt`
> rules file that ships inside release APKs for AOT compilation of the startup
> path.

## Purpose

`/home/z/.../ANIYOMI/macrobenchmark/` does **not** ship in the app APK. It is a
developer/CI-only test module that:

1. Drives the app on an emulator using UI Automator.
2. Measures cold/warm/hot startup time under several `CompilationMode`s —
   including "baseline profile required", "baseline profile disabled", and
   "full compilation" — so you can compare the win.
3. Generates the **baseline profile**: a list of classes/methods to AOT-compile
   ahead of first run. The generated file is then hand-copied to
   `../ANIYOMI/app/src/main/baseline-prof.txt`, which the Android Gradle Plugin
   picks up automatically for release/minified builds.

The runtime payoff: significantly faster cold start, because ART does not have
to JIT-compile the startup path on first launch.

## Build configuration

Source: `../ANIYOMI/macrobenchmark/build.gradle.kts`.

```kotlin
plugins {
    id("mihon.benchmark")                       // com.android.test + kotlin("android") + mihon.code.lint
}

android {
    namespace = "tachiyomi.macrobenchmark"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.enabledRules"] = "BaselineProfile"
    }

    buildTypes {
        create("benchmark") {                   // benchmark build type for the test module itself
            isDebuggable = true
            signingConfig = getByName("debug").signingConfig
            matchingFallbacks.add("release")
        }
    }

    targetProjectPath = ":app"                  // ← the app being benchmarked
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation(androidx.test.ext)
    implementation(androidx.test.espresso.core)
    implementation(androidx.test.uiautomator)
    implementation(androidx.benchmark.macro)
}

androidComponents {
    beforeVariants(selector().all()) {
        it.enable = it.buildType == "benchmark" // only build the "benchmark" variant
    }
}
```

Key points:

- **Plugin `mihon.benchmark`** — the convention plugin at
  `../ANIYOMI/buildSrc/src/main/kotlin/mihon.benchmark.gradle.kts`. It applies
  `com.android.test` (the test-APK Android plugin), `kotlin("android")`, and
  `mihon.code.lint`. See
  [`../00-overview/04-build-system.md`](../00-overview/04-build-system.md) for
  the full `buildSrc` plugin table.
- **`targetProjectPath = ":app"`** — this is **not** a compile-time dependency.
  The test module targets `:app` so its instrumentation runs against the app's
  installed APK. There is no `implementation(projects.app)` line — the link is
  purely at the Gradle/AGP level.
- **`androidx.benchmark.enabledRules = BaselineProfile`** — default JUnit rule
  filter; lets you run only baseline-profile tests by default (use
  `-PandroidTestInstrumentationRunnerArguments.androidx.benchmark.enabledRules=Macrobenchmark`
  to run timing benchmarks instead).
- **`self-instrumenting`** — lets the test APK re-install itself, required for
  macrobenchmark runs.
- **`beforeVariants { … it.enable = it.buildType == "benchmark" }`** — only the
  `benchmark` build variant of this test module is built; nothing else is wired
  up.
- The `benchmark` build type **inside `:app`** (not here) is what produces the
  APK that gets instrumented. See the section below.

### The `benchmark` build type in `:app`

`/home/z.../ANIYOMI/app/build.gradle.kts` declares a `benchmark` build type that
this module targets:

```kotlin
create("benchmark") {
    initWith(release)
    isDebuggable = false
    isProfileable = true
    versionNameSuffix = "-benchmark"
    applicationIdSuffix = ".benchmark"
    signingConfig = debug.signingConfig
    matchingFallbacks.addAll(listOf("release"))
}
```

So the benchmarked app is:

- **release-optimized** (R8 minifying on, resource shrinking on),
- **profileable** (`isProfileable = true` — lets the benchmark collect detailed
  CPU traces without debug overhead),
- **not debuggable** (`isDebuggable = false`),
- installed as `xyz.jmir.tachiyomi.mi.benchmark` (the release application ID
  `xyz.jmir.tachiyomi.mi` plus the `.benchmark` suffix).

The `benchmark` variant is restricted to the `default` + `dev` flavor
combination via `androidComponents.beforeVariants` in `:app`'s build script. See
[`../00-overview/04-build-system.md`](../00-overview/04-build-system.md) for the
full build-type table.

## Benchmark classes

The module ships **two source files** under
`../ANIYOMI/macrobenchmark/src/main/java/tachiyomi/macrobenchmark/`:

### 1. `StartupBenchmark.kt`

Defines an abstract `AbstractStartupBenchmark(startupMode)` and three concrete
JUnit4 test classes — one per `StartupMode`:

| Class | Startup mode | What it measures |
|---|---|---|
| `ColdStartupBenchmark` | `StartupMode.COLD` | App process not running; full process + Activity startup. |
| `WarmStartupBenchmark` | `StartupMode.WARM` | Process running, Activity recreated. |
| `HotStartupBenchmark` | `StartupMode.HOT` | Process and Activity already in memory; just brought to foreground. |

Each concrete class is annotated `@RunWith(AndroidJUnit4ClassRunner::class)`
and extends `AbstractStartupBenchmark`. The abstract class runs four `@Test`
methods, one per `CompilationMode`:

| Test method | Compilation mode | Why |
|---|---|---|
| `startupNoCompilation()` | `CompilationMode.None()` | Baseline: no AOT at all, fully JIT. |
| `startupBaselineProfileDisabled()` | `Partial(baselineProfileMode = Disable, warmupIterations = 1)` | Same as no baseline profile, for comparison. |
| `startupBaselineProfile()` | `Partial(baselineProfileMode = Require)` | **The headline number**: startup with the baseline profile applied. |
| `startupFullCompilation()` | `Full()` | Everything AOT-compiled (upper bound). |

Each test calls `benchmarkRule.measureRepeated(...)` with 10 iterations, the
`StartupTimingMetric()`, and a `setupBlock` that presses Home before launching
the app via `startActivityAndWait()`. The target package is
`xyz.jmir.tachiyomi.mi.benchmark` — note this is the app's `benchmark` build
type application ID, hardcoded as a string literal in the test.

### 2. `BaselineProfileGenerator.kt`

A single `@Test` method (`generate()`) wrapped in a `BaselineProfileRule`. It
drives the app through a representative user journey so AGP can record which
classes/methods get executed, then emit the profile:

```kotlin
@Test
fun generate() = baselineProfileRule.collect(
    packageName = "xyz.jmir.tachiyomi.mi.benchmark",
    profileBlock = {
        pressHome()
        startActivityAndWait()

        device.findObject(By.text("Manga")).click()
        device.findObject(By.text("Updates")).click()
        device.findObject(By.text("History")).click()

        // Browse / Extensions omitted (need storage permissions)
        device.findObject(By.text("More")).click()
        device.findObject(By.text("Settings")).click()
    },
)
```

The journey covers the main bottom-nav tabs (Manga library, Updates, History,
More → Settings) — i.e. the screens most users hit at startup. A TODO notes
that Browse/Extensions are skipped because automating the storage permission
grant is non-trivial. The resulting profile is captured by AGP into the test
module's build output; the developer then copies it to
`../ANIYOMI/app/src/main/baseline-prof.txt` (see the module README at
`../ANIYOMI/macrobenchmark/README.md`).

## How the baseline profile is generated and consumed

```
 :macrobenchmark                       :app
 ─────────────────                     ─────────
 BaselineProfileGenerator              app/src/main/
   │ drives UI on AOSP emulator  ──▶  baseline-prof.txt  ◀── checked in
   │ AGP records executed classes        │
   │ and methods                         │ AGP picks up automatically
   ▼                                     ▼ (well-known file name)
 build/outputs/.dm                    release/benchmark APK
 (developer copies out)               ships baseline.prof
                                      → ART AOT-compiles
                                        startup path on install
```

Workflow (per `../ANIYOMI/macrobenchmark/README.md`):

1. Select the `devBenchmark` build variant of `:app`.
2. Run the `BaselineProfileGenerator` test on an **AOSP Android Emulator**
   (AOSP, not Google APIs — baseline profile generation needs a non-GMS image).
3. Copy the generated profile from the emulator/build output to
   `../ANIYOMI/app/src/main/baseline-prof.txt`.
4. The next release build of `:app` will embed the profile and ART will
   AOT-compile the listed methods at install time.

The `baseline-prof.txt` file currently in the repo is **37,789 lines** of
R8/D8-style rules (`HSPL…` = hot startup + startup + paged + link, etc.) covering
`androidx.*`, `eu.kanade.tachiyomi.*`, Kotlin stdlib, Compose runtime, and SQL
Delight — i.e. everything on the cold-start path. The README notes that the file
needs to be re-generated whenever code touching app startup changes.

## Key files

| Path (relative to `DOCUMENTATION/`) | What it is |
|---|---|
| `../ANIYOMI/macrobenchmark/build.gradle.kts` | `mihon.benchmark` plugin, `targetProjectPath = :app`, benchmark build type, AGP variant filter. |
| `../ANIYOMI/macrobenchmark/README.md` | Workflow instructions: how to regenerate the baseline profile. |
| `../ANIYOMI/macrobenchmark/src/main/AndroidManifest.xml` | Empty `<manifest/>` (test module — no components of its own). |
| `../ANIYOMI/macrobenchmark/src/main/java/tachiyomi/macrobenchmark/StartupBenchmark.kt` | Cold/Warm/Hot startup benchmarks; 4 compilation modes each. |
| `../ANIYOMI/macrobenchmark/src/main/java/tachiyomi/macrobenchmark/BaselineProfileGenerator.kt` | The `BaselineProfileRule` test that produces the baseline profile. |
| `../ANIYOMI/app/src/main/baseline-prof.txt` | **Output, consumed by `:app`** — 37,789-line baseline profile shipped in release APKs. |
| `../ANIYOMI/app/build.gradle.kts` | The `benchmark` build type (`initWith(release)`, `isProfileable = true`, `.benchmark` suffix) that this module targets. |
| `../ANIYOMI/buildSrc/src/main/kotlin/mihon.benchmark.gradle.kts` | The convention plugin: applies `com.android.test` + Kotlin + lint. |

## See also

- [`README.md`](README.md) — module index and dependency graph.
- [`../00-overview/04-build-system.md`](../00-overview/04-build-system.md) —
  the `mihon.benchmark` plugin entry in the buildSrc table, and the `:app`
  build-type table including `benchmark`.
- [`../05-key-flows/app-startup.md`](../05-key-flows/app-startup.md) — what the
  baseline profile actually speeds up.
- Android docs: <https://developer.android.com/studio/profile/baselineprofiles>.

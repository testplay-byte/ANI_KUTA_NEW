# Testing Strategy — ANI-KUTA

> How we test the ANI-KUTA Android app. Read this before adding tests or
> verifying a change.
>
> Last updated: 2026-07-16.

---

## Current approach: manual + thin automated

ANI-KUTA uses a **hybrid testing strategy**:

1. **Manual on-device testing** (primary) — the user installs a debug APK on a
   real Android device and verifies behavior. This is the main verification
   method because:
   - The player uses MPV native code — only a real device catches native
     crashes (SIGABRT/SIGSEGV) and real playback issues.
   - The extension system loads external APKs — needs a real Android
     package manager.
   - SAF storage, foreground services, notifications — all need a real device.
2. **Thin automated unit tests** (secondary) — JVM unit tests for the pure-logic
   pieces that don't need Android. These run via `./gradlew :domain:test` and
   catch logic regressions that manual testing might miss.

### Why not a full test strategy (yet)?

A full instrumented test suite (UI + integration + instrumented) is a large
investment and would still not catch MPV native crashes. The current phase
prioritizes feature velocity. As the project stabilizes toward release, we'll
expand automated coverage (see §"Future expansion" below).

---

## What's covered by automated tests (the thin layer)

Unit tests live in `domain/src/test/java/app/anikuta/domain/`. They target
**pure-logic, no-Android-dependency** code:

| Target | File | What's tested | Why it matters |
|--------|------|---------------|----------------|
| `EpisodeRecognition` | `items/episode/service/EpisodeRecognitionTest.kt` | Parses episode numbers from messy release titles (`"One Piece - 1015 [1080p]"` → 1015; `s01e01v2` → 1; `Episode 12.5` → 12.5; alpha/extra/omake/special suffixes). | If this breaks, every episode list breaks. Regex is bug-prone. |
| `SeasonRecognition` | `items/season/service/SeasonRecognitionTest.kt` | Parses season numbers from titles (`S02` → 2; `Season 2` → 2; `Boku no Hero 2` → 2). | Same as above, for seasons. |
| `AnimeFetchInterval` | `entries/anime/interactor/AnimeFetchIntervalTest.kt` | Calculates the refresh interval from episode upload/fetch dates; the 3-tier logic (upload dates ≥3 → fetch dates ≥3 → default 7 days); `coerceIn(1, 28)`. | A bug here = wrong "when to refresh this anime" scheduling. |
| `GetApplicationRelease` | `release/interactor/GetApplicationReleaseTest.kt` | The 3-day throttle; the SemVer comparison (`v0.1.2` vs `v0.1.3`); the preview-build commit-count comparison; the "no new update" path. | A bug here = broken update checks (either never checks, or always says "new update"). |

### Testability gap (documented, not yet fixed)

`CreateAnimeExtensionRepo` was a **planned** 5th test target but is **not
unit-testable as currently written**. It depends on `ExtensionRepoService`,
which is a **concrete class** (not an interface) whose constructor needs
`NetworkHelper`, which needs an Android `Context`. This means the interactor
cannot be constructed in a pure JVM unit test.

**To make it testable, one of these small refactors is needed (not yet done —
awaiting user direction):**
1. Extract a functional interface `IExtensionRepoService` and have
   `ExtensionRepoService` implement it; `CreateAnimeExtensionRepo` depends on
   the interface. (Small, clean.)
2. Extract the URL-normalization + regex validation into a pure top-level
   function and test that directly. (Smallest change; tests the most bug-prone
   piece.)

This is tracked in `DOCS/ENGINEERING/MODULARIZATION-ASSESSMENT.md` as a P2 item.
Until then, `CreateAnimeExtensionRepo` is covered by **manual testing only**.

### What's NOT covered by automated tests (intentionally)

- **UI / Compose screens** — manual testing only (no `androidTest` infrastructure yet).
- **Player / MPV** — manual on-device only (native code, can't unit-test).
- **Download engines** — manual (FFmpeg + SAF + WorkManager).
- **AniList / Supabase network calls** — manual (real network).
- **SQLDelight DB** — manual (real Android DB).
- **Extension classloading** — manual (real package manager).

---

## How to run the automated tests

```bash
# From the repo root:
./gradlew :domain:test

# To see detailed output:
./gradlew :domain:test --info

# To run a single test class:
./gradlew :domain:test --tests "app.anikuta.domain.items.episode.service.EpisodeRecognitionTest"
```

Test reports land at `domain/build/reports/tests/test/index.html`.

> **CI note:** The tests are NOT yet wired into the GitHub Actions workflow
> (`.github/workflows/build-apk.yml` only runs `assembleDebug`). Adding a
> `./gradlew :domain:test` step to CI is a planned follow-up (see
> `DOCS/ENGINEERING/MODULARIZATION-ASSESSMENT.md` P2.11).

---

## How to add a new automated test

1. **Confirm the target is pure logic.** If it needs Android framework classes
   (`Context`, `Activity`, `ViewModel`, Compose), it belongs in instrumented
   tests (not yet set up) — use manual testing instead.
2. **Put it in the matching package** under `domain/src/test/java/app/anikuta/domain/`.
   Mirror the source package structure.
3. **Use JUnit 4** (already in `domain/build.gradle.kts` as `testImplementation(libs.junit)`).
   No mocking framework yet — use simple fakes (hand-written classes that
   implement the needed interface).
4. **Name test methods descriptively:** `parseEpisodeNumber_s01e01v2_returns1()`.
5. **Cover edge cases:** empty input, null input, multiple numbers, the "no
   match" path, boundary values.
6. **Run `./gradlew :domain:test`** before committing (or rely on CI once wired).

### Fake example (for classes with dependencies)

For a class like `CreateAnimeExtensionRepo` that depends on
`AnimeExtensionRepoRepository` + `ExtensionRepoService`, write a fake:

```kotlin
private class FakeRepoRepository : AnimeExtensionRepoRepository {
    val repos = mutableMapOf<String, ExtensionRepo>()
    override suspend fun getRepo(baseUrl: String) = repos[baseUrl]
    // ... implement other methods with simple in-memory state
}
```

(See `CreateAnimeExtensionRepoTest.kt` for the full pattern.)

---

## Manual testing checklist (for the user)

When you (the user) test a build on-device, here's the golden-path checklist.
Report results against these items:

### Core flows
- [ ] App launches → onboarding (first boot) OR home screen (subsequent boots).
- [ ] Home screen loads AniList trending/popular (real data, not empty).
- [ ] Tap an anime card → detail page opens with metadata + episode list.
- [ ] Tap an episode → player opens → video plays.
- [ ] Seek (drag the seekbar) → playback jumps to the new position.
- [ ] Pause → resume → playback continues.
- [ ] Back out of player → return to detail page.
- [ ] Open History → the episode you just watched appears with resume position.
- [ ] Tap the history item → player resumes from where you left off.

### Library / Search
- [ ] Search an anime by name → AniList results appear.
- [ ] Toggle to Extensions search → extension results appear.
- [ ] Save an anime to Library → it appears on the Library tab.
- [ ] Library display toggle (grid/list) works.

### Downloads
- [ ] Download an episode → progress shows → completes.
- [ ] Open the Downloads page → the downloaded episode appears.
- [ ] Pause/cancel a download → stops cleanly.

### Player settings
- [ ] Switch audio track (SUB/DUB) → audio changes.
- [ ] Switch subtitle track → subtitles change.
- [ ] Subtitle settings (size/color) → apply correctly.

### Settings
- [ ] Settings hub opens → all subpages reachable.
- [ ] Extension management: install/uninstall an extension.
- [ ] Player settings: change a setting → persists across restart.

### Crash handling
- [ ] No crashes during the above flows.
- [ ] If a crash occurs, the ErrorActivity shows (not a silent crash).

> **Report format:** When you test, tell me which items passed/failed and what
> the failure looked like (error message, blank screen, crash, wrong behavior).
> I'll diagnose from there.

---

## Future expansion (not started)

As the project moves toward release, expand automated coverage in this order:

1. **Wire `:domain:test` into CI** — add a test job to `.github/workflows/`.
2. **Add tests for more domain interactors** — `GetAnime`, `NetworkToLocalAnime`,
   `SetAnimeSeasonFlags`, `SetAnimeEpisodeFlags` (bit-flag CRUD).
3. **Add tests for `:core`** — `AndroidPreference` serialization, `NetworkHelper`
   DoH provider selection, `OkHttpExtensions`.
4. **Add instrumented tests (`androidTest`)** — for the 3-tier `CacheManager`,
   the `AnimeDatabaseHandler` (real SQLDelight on a test device).
5. **Add a smoke UI test** — launch app → home loads → tap a card → detail opens.
   (Requires `androidx.test` deps — not in the catalog yet.)
6. **Add a player smoke test** — only feasible as instrumented, and only
   verifies "player opens without crashing" (can't verify video playback
   deterministically).

These are tracked in `DOCS/ENGINEERING/MODULARIZATION-ASSESSMENT.md` P2.11.

---

## Test file inventory

| Test file | Target | Test count |
|-----------|--------|------------|
| `domain/src/test/java/app/anikuta/domain/items/episode/service/EpisodeRecognitionTest.kt` | `EpisodeRecognition` | 20 |
| `domain/src/test/java/app/anikuta/domain/items/season/service/SeasonRecognitionTest.kt` | `SeasonRecognition` | 16 |
| `domain/src/test/java/app/anikuta/domain/entries/anime/interactor/AnimeFetchIntervalTest.kt` | `AnimeFetchInterval` | 11 |
| `domain/src/test/java/app/anikuta/domain/release/interactor/GetApplicationReleaseTest.kt` | `GetApplicationRelease` | 11 |

**Total: 4 test files, 58 test cases.**

> `CreateAnimeExtensionRepo` is not in this list — see "Testability gap" above.

---

## Rules

1. **Never delete a test** without understanding why it exists. If it's failing,
   fix the code or fix the test — don't just delete it.
2. **Never weaken a test** to make it pass. If the assertion is too strict,
   discuss before relaxing it.
3. **Add a test when you fix a bug** — the test should fail before your fix and
   pass after. This prevents regressions.
4. **Pure logic only** in unit tests. Anything needing Android goes in manual
   testing (for now) or instrumented tests (future).
5. **Fakes over mocks** — hand-written fakes are clearer and don't break when
   the interface changes. No mocking framework is in the dependencies.

---

_Related: `DOCS/ENGINEERING/MODULARIZATION-ASSESSMENT.md` P2.11 (test scaffolding)._

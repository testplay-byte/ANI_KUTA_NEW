# Phase 1 — Detailed Plan (Skeleton + Onboarding + Home Page)

> Phase 1 delivers: a buildable APK (via GitHub Actions) that runs the 7-step
> onboarding wizard and lands on a working home page (AniList data, Material 3).
>
> **Approach: incremental copy-paste.** We copy aniyomi parts as needed along
> the way — NOT all at once. Each copy is documented. First priority: the
> extension system (needed for onboarding step 4).
>
> See `BUILD-ENVIRONMENT.md` (GitHub Actions, arm64-v8a) + `PACKAGE-LAYOUT.md`
> (module structure).

---

## Phase 1 sub-steps (in order)

### Step 1.1 — Gradle project scaffold
- Create the Android Gradle project: 5 modules (`:app`, `:core`, `:data`, `:domain`, `:source-api`).
- `applicationId = app.anikuta`, `minSdk = 26`, `compileSdk/targetSdk = latest`, JDK 17.
- Kotlin + Compose enabled.
- Version catalogs (Gradle `.toml`) for dependencies.
- Settings: `settings.gradle.kts` with the 5 module includes.
- Each module gets a `build.gradle.kts`.
- App launches to a blank Compose screen (proves the build works).
- **No aniyomi code copied yet.**

### Step 1.2 — GitHub Actions workflow
- `.github/workflows/build-apk.yml` (per `BUILD-ENVIRONMENT.md`).
- Triggers: push to main, PR, manual.
- Builds `assembleDebug` (arm64-v8a only).
- Uploads the APK as an artifact.
- First green build = the scaffold works end-to-end.

### Step 1.3 — Copy `:core` module (from aniyomi `:core:common`)
- **What we copy:** `PreferenceStore`, `AndroidPreference`, storage helpers, system/image utils, i18n bridge — the foundation utilities.
- **From:** `REFERENCE/core/common/src/main/java/tachiyomi/core/common/`
- **To:** `:core` → `app.anikuta.core.*`
- **Adapt:** rename packages `tachiyomi.core.common.*` → `app.anikuta.core.*`. Drop what we don't need.
- **Document:** `DOCS/APP/STRUCTURE/core.md` — what we copied, from where, changes.
- **Verify:** app still builds + launches.

### Step 1.4 — Copy `:source-api` module (anime contract)
- **What we copy:** the anime source/extension contract — `AnimeSource`, `AnimeHttpSource`, `ParsedAnimeHttpSource`, `SAnime`, `SEpisode`, `Video`, `Hoster`, `AnimeFilterList`, `AnimeUpdateStrategy`.
- **From:** `REFERENCE/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/` (+ androidMain)
- **To:** `:source-api` → `app.anikuta.source.api.*`
- **Adapt:** rename packages. Drop the manga side (`eu.kanade.tachiyomi.source/`) entirely.
- **Document:** `DOCS/APP/STRUCTURE/source-api.md`.
- **Verify:** `:source-api` module compiles.
- **This is the first aniyomi subsystem — the plugin boundary.**

### Step 1.5 — Copy `:domain` module (anime)
- **What we copy:** anime domain models + use cases + repo interfaces.
- **From:** `REFERENCE/domain/src/main/java/aniyomi/domain/anime/` + shared `tachiyomi.domain.*` (anime-relevant parts).
- **To:** `:domain` → `app.anikuta.domain.*`
- **Adapt:** rename packages. Drop manga-only domain (`tachiyomi.domain.*` manga-only parts).
- **Document:** `DOCS/APP/STRUCTURE/domain.md`.
- **Verify:** `:domain` compiles against `:source-api` + `:core`.

### Step 1.6 — Copy `:data` module (anime DB + repos)
- **What we copy:** the anime SQLDelight database (`sqldelightanime/`) + anime repository implementations.
- **From:** `REFERENCE/data/src/main/sqldelightanime/` + `REFERENCE/data/src/main/java/tachiyomi/mi/data/` (anime repos).
- **To:** `:data` → `app.anikuta.data.*`
- **Adapt:** rename packages. Drop the manga DB (`sqldelight/`) + manga repos.
- **Document:** `DOCS/APP/STRUCTURE/data.md`.
- **Verify:** `:data` compiles + the DB initializes on app launch.

### Step 1.7 — Copy extension + source managers (→ `:app`)
- **What we copy:** `AnimeExtensionManager`, `AnimeExtensionLoader`, `AnimeSourceManager`.
- **From:** `REFERENCE/app/src/main/java/eu/kanade/tachiyomi/extension/anime/` + `.../source/anime/`
- **To:** `:app` → `app.anikuta.extension.*` + `app.anikuta.source.*`
- **Adapt:** rename packages. This is the core of the extension system — the first priority per the user.
- **Document:** `DOCS/APP/STRUCTURE/extension-system.md`.
- **Verify:** app can list installed anime extensions (none yet, but the manager works).

### Step 1.8 — Copy Injekt DI wiring (→ `:app`)
- **What we copy:** `AppModule`, `PreferenceModule`, `DomainModule` (anime bindings only).
- **From:** `REFERENCE/app/src/main/java/eu/kanade/tachiyomi/di/`
- **To:** `:app` → `app.anikuta.di.*`
- **Adapt:** rename packages. Keep anime bindings; drop manga bindings; drop `SYDomainModule` (manga-only).
- **Document:** `DOCS/APP/STRUCTURE/di.md`.
- **Verify:** app launches with DI wired (backend live: DB + source mgr + extension mgr).

### Step 1.9 — Material 3 theme
- Build the Material 3 Compose theme (`app.anikuta.ui.theme.material3`).
- `MaterialTheme` with light/dark + dynamic color (Monet) + a brand seed color fallback.
- This is the **fallback design** — all other designs fall back to it.
- **No custom design yet** (Neon/Neobrutalism/Coffee = Phase 6).

### Step 1.10 — AniList client (ours)
- Build the AniList GraphQL client (`app.anikuta.data.anilist`).
- Queries: trending, popular, freshly updated, genres, schedule (the home page sections).
- Data models + mappers.
- This is OURS — aniyomi doesn't have it. Learned from the aniwatch reference.
- **No caching yet** (3-step cache = Phase 2; for now, direct AniList fetch).

### Step 1.11 — Onboarding wizard (7 steps)
- Compose navigation graph for the setup wizard.
- Step 1: Welcome. Step 2: Permissions. Step 3: Storage (SAF picker).
- Step 4: Extension selection — install AniKoto 180 (from bundled APK or repo URL), pick primary/secondary.
- Step 5: Backup restore (file picker, optional).
- Step 6: Design selection (Material 3 only for now; other 3 shown as "coming soon").
- Step 7: All set → home.
- Onboarding state in preferences (`onboarding_complete`, `onboarding_step`).
- Required steps (3, 4, 6) enforced.

### Step 1.12 — Navigation shell
- Bottom nav: Home, Library, History, Search, More.
- Library/History/Search/More are empty placeholders for now.
- Home navigates to the home screen.

### Step 1.13 — Home page UI (Material 3, AniList data)
- The 6 sections: Hero, Trending Now, Freshly Updated, Browse by Genre, Most Popular, Coming Up Next.
- Each section pulls from the AniList client.
- Anime cards: cover, name, rating, episodes, genres.
- Carousels (horizontal scroll).
- Tap a card → (placeholder detail page for now; full detail = Phase 3).
- Material 3 design throughout.

### Step 1.14 — APK download page (on the live preview)
- `/#apk` subpage on the live preview.
- Links to the latest GitHub Actions artifact (the APK).
- One click → download starts (or redirects to GitHub's download).
- Built after Step 1.13 produces a working APK.

### Step 1.15 — Phase 1 verification + commit
- Install the APK on a phone.
- Run through onboarding.
- Land on home page (AniList data loads).
- User tests + reports.
- Fix any issues.
- Phase 1 complete → move to Phase 2.

---

## What Phase 1 does NOT include
- **No streaming/player** (Phase 4) — tapping an episode does nothing yet.
- **No Supabase / 3-step cache** (Phase 2) — direct AniList fetches only.
- **No downloads** (Phase 7).
- **No the other 3 designs** (Phase 6) — Material 3 only.
- **No AniList login/tracking** (Phase 8) — home page works without login.
- **No detail page content** (Phase 3) — placeholder only.

---

## The incremental copy-paste principle

We do NOT copy all of aniyomi's backend at once. We copy **what each step needs**:
- Step 1.3 needs core utils → copy `:core`.
- Step 1.4 needs the source contract → copy `:source-api`.
- Step 1.7 needs extension management → copy extension/source managers.
- etc.

Each copy is:
1. **Understood** — we read the aniyomi code, know what it does.
2. **Analyzed** — we decide if we need it as-is, adapted, or rewritten.
3. **Copied + adapted** — renamed to `app.anikuta.*`, pruned to anime-only.
4. **Documented** — `DOCS/APP/STRUCTURE/<module>.md` records what/where/changes.

When aniyomi updates, we compare our copied files against the new version,
analyze the diff, and decide whether to adopt.

**First priority subsystems (per user):**
1. Extension managing (Step 1.7)
2. Communication between extensions (Step 1.4 — the contract)
3. Getting data from extensions (Step 1.7 — the managers call the contract)
4. Showing the data (Step 1.13 — the home page)

---

## Open questions (minimal — per user's request, only necessary ones)

- [ ] Brand seed color for Material 3 (the static fallback when Monet is off)?
- [ ] APK artifact retention: GitHub default 90 days, or set longer?

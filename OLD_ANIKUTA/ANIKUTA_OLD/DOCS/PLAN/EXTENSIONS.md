# Extensions — Sources & Repos

> The extension system for ANI-KUTA. We reuse aniyomi's anime extension system
> (per D1 — selective copy-paste). This doc records the recommended repo + APK
> the user provided, plus how users add more.

---

## The recommended extension repo

| Field | Value |
|-------|-------|
| Repo index URL | `https://raw.githubusercontent.com/Confused-Creature-180/aniyomi-extensions/repo/index.min.json` |
| Repo owner | Confused-Creature-180 |
| Repo branch | `repo` |
| Format | aniyomi extension index (JSON) |

### Current contents (as of Session 11)

The repo contains **1 extension**:

| Field | Value |
|-------|-------|
| Name | AniKoto 180 |
| Package | `eu.kanade.tachiyomi.animeextension.en.anikoto180` |
| APK file | `aniyomi-en.anikoto180-v16.9-release.apk` |
| Language | en |
| Version | 16.9 |
| Code | 9 |
| NSFW | 0 (no) |
| Source name | AniKoto 180 |
| Source base URL | `https://anikototv.to` |
| Source ID | `178825880993122333` |

> This is the **recommended default extension** we offer in onboarding (step 4).
> The user can install it from the repo, or we can bundle the APK.

## The bundled APK

| Field | Value |
|-------|-------|
| File | `aniyomi-en.anikoto180-v16.9-release.apk` |
| Size | ~262 KB |
| Stored at (sandbox) | `/home/z/my-project/upload/aniyomi-en.anikoto180-v16.9-release.apk` |
| Use | Offered in onboarding step 4 as "install from APK" option |

> The APK is the same extension as in the repo. We can offer both paths
> (install from repo URL, or install from bundled APK) in onboarding.

## How users add more extensions

Per the user's design:
- The user can **add other repos** (by URL) in Settings → Extensions.
- The user can **install other extensions** from any added repo.
- The recommended repo (above) is **pre-added** in onboarding.

## Onboarding step 4 (extension selection) — options

1. **Install the recommended extension (AniKoto 180)** — one tap, from the
   bundled APK or the recommended repo.
2. **Install from repo URL** — user pastes a repo URL, browses its extensions,
   picks one.
3. **Install from APK file** — user picks an APK file from their device.
4. **Already have extensions?** — if the user has anime extensions installed
   (from a previous install), list them and let the user pick.

After installing, the user selects:
- **Primary extension** (required) — used first for streaming.
- **Secondary extension** (optional) — used as fallback if primary fails.

## Technical notes

- We reuse aniyomi's `AnimeExtensionManager` + `AnimeExtensionLoader` (copied +
  adapted per D1).
- Extension APKs declare the `tachiyomi.animeextension` feature (per aniyomi's
  source-api — see `DOCS/REFERENCE-DOCS/SUBSYSTEMS/SOURCE-SYSTEM.md`).
- The recommended repo's index format is compatible with aniyomi's extension
  manager (it's an aniyomi-style extension repo).

## Open questions

- [ ] Do we bundle the AniKoto 180 APK in our app, or always install from the
  repo URL? (Bundling = works offline first boot; repo = smaller app APK.)
- [ ] Should the recommended repo be editable/removable, or locked?
- [ ] Auto-update extensions? (aniyomi checks for extension updates periodically.)

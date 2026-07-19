@file:Suppress("ObjectPropertyName", "PropertyName")

/**
 * Compatibility aliases — keep our app code using `app.anikuta.source.api.*`
 * while the REAL classes live at `eu.kanade.tachiyomi.animesource.*` (the
 * package extensions compile against).
 *
 * Background (Phase 5 root-cause fix): when we originally copied aniyomi's
 * source-api module (Decision D1), we renamed the package from
 * `eu.kanade.tachiyomi.animesource` to `app.anikuta.source.api`. This broke
 * extension loading: extensions are compiled against
 * `eu.kanade.tachiyomi.animesource.*` binary names, so when our classloader
 * tried to load an extension's source class, it failed with
 * `NoClassDefFoundError: eu.kanade.tachiyomi.animesource.online.AnimeHttpSource`
 * — the parent class wasn't at that path.
 *
 * Fix: the real classes now live at `eu.kanade.tachiyomi.animesource.*`
 * (matching aniyomi + what extensions compile against). These typealiases let
 * our existing app code keep importing `app.anikuta.source.api.*` without
 * touching 28+ files.
 */

package app.anikuta.source.api

typealias AnimeSource = eu.kanade.tachiyomi.animesource.AnimeSource
typealias AnimeCatalogueSource = eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
typealias AnimeSourceFactory = eu.kanade.tachiyomi.animesource.AnimeSourceFactory
typealias ConfigurableAnimeSource = eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
typealias PreferenceScreen = eu.kanade.tachiyomi.animesource.PreferenceScreen
typealias UnmeteredSource = eu.kanade.tachiyomi.animesource.UnmeteredSource

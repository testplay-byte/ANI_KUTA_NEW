package app.confused.anikuta.di

import app.confused.anikuta.core.episodemetadata.repository.EpisodeMetadataCache
import app.confused.anikuta.core.player.PlaybackStateStore
import app.confused.anikuta.core.player.WatchProgressStore
import app.confused.anikuta.data.extension.cache.SourceLinkStore
import app.confused.anikuta.migration.ContentIdMigrator
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin module for the [ContentIdMigrator] (Phase 5, ADR-050).
 *
 * The migrator lives in `:app` because it needs access to all cross-cutting
 * stores (WatchProgressStore, PlaybackStateStore, EpisodeMetadataCache,
 * SourceLinkStore) which span multiple modules. `:app` is the composition root
 * that depends on all of them.
 *
 * Called by:
 * - [app.confused.anikuta.navigation.AppController.switchAnilistAnime] — when
 *   the user corrects a wrong auto-match (content_id changes from al:old → al:new).
 * - [app.confused.anikuta.feature.search.viewmodel.ExtensionLinkingViewModel] —
 *   when an unlinked extension anime gets linked to AniList (content_id changes
 *   from local_id → al:anilistId). [Phase 5 future wiring — currently the link
 *   flow doesn't have the old local_id to migrate from; this is a known gap that
 *   Phase 6 will address when downloads also migrate.]
 */
val contentIdMigratorModule: Module = module {
    single {
        ContentIdMigrator(
            watchProgressStore = get<WatchProgressStore>(),
            playbackStateStore = get<PlaybackStateStore>(),
            episodeMetadataCache = get<EpisodeMetadataCache>(),
            sourceLinkStore = get<SourceLinkStore>(),
        )
    }
}

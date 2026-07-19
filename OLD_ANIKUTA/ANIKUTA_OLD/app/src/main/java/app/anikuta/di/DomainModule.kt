package app.anikuta.di

import app.anikuta.core.preference.PreferenceStore
import app.anikuta.data.category.anime.AnimeCategoryRepositoryImpl
import app.anikuta.data.entries.anime.AnimeRepositoryImpl
import app.anikuta.data.handlers.anime.AnimeDatabaseHandler
import app.anikuta.data.history.anime.AnimeHistoryRepositoryImpl
import app.anikuta.data.items.episode.EpisodeRepositoryImpl
import app.anikuta.domain.category.anime.interactor.CreateAnimeCategoryWithName
import app.anikuta.domain.category.anime.interactor.DeleteAnimeCategory
import app.anikuta.domain.category.anime.interactor.GetAnimeCategories
import app.anikuta.domain.category.anime.interactor.GetVisibleAnimeCategories
import app.anikuta.domain.category.anime.interactor.HideAnimeCategory
import app.anikuta.domain.category.anime.interactor.RenameAnimeCategory
import app.anikuta.domain.category.anime.interactor.ReorderAnimeCategory
import app.anikuta.domain.category.anime.interactor.ResetAnimeCategoryFlags
import app.anikuta.domain.category.anime.interactor.SetAnimeCategories
import app.anikuta.domain.category.anime.interactor.SetAnimeDisplayMode
import app.anikuta.domain.category.anime.interactor.SetSortModeForAnimeCategory
import app.anikuta.domain.category.anime.interactor.UpdateAnimeCategory
import app.anikuta.domain.category.anime.repository.AnimeCategoryRepository
import app.anikuta.domain.download.service.DownloadPreferences as DomainDownloadPreferences
import app.anikuta.domain.entries.anime.interactor.AnimeFetchInterval
import app.anikuta.domain.entries.anime.interactor.GetAnime
import app.anikuta.domain.entries.anime.interactor.GetAnimeByUrlAndSourceId
import app.anikuta.domain.entries.anime.interactor.GetAnimeFavorites
import app.anikuta.domain.entries.anime.interactor.GetAnimeWithEpisodesAndSeasons
import app.anikuta.domain.entries.anime.interactor.GetDuplicateLibraryAnime
import app.anikuta.domain.entries.anime.interactor.GetLibraryAnime
import app.anikuta.domain.entries.anime.interactor.NetworkToLocalAnime
import app.anikuta.domain.entries.anime.interactor.ResetAnimeViewerFlags
import app.anikuta.domain.entries.anime.interactor.SetAnimeEpisodeFlags
import app.anikuta.domain.entries.anime.interactor.SetAnimeSeasonFlags
import app.anikuta.domain.entries.anime.repository.AnimeRepository
import app.anikuta.domain.history.anime.interactor.GetAnimeHistory
import app.anikuta.domain.history.anime.interactor.GetNextEpisodes
import app.anikuta.domain.history.anime.interactor.RemoveAnimeHistory
import app.anikuta.domain.history.anime.interactor.UpsertAnimeHistory
import app.anikuta.domain.history.anime.repository.AnimeHistoryRepository
import app.anikuta.domain.items.episode.interactor.GetEpisode
import app.anikuta.domain.items.episode.interactor.GetEpisodeByUrlAndAnimeId
import app.anikuta.domain.items.episode.interactor.GetEpisodesByAnimeId
import app.anikuta.domain.items.episode.interactor.SetAnimeDefaultEpisodeFlags
import app.anikuta.domain.items.episode.interactor.ShouldUpdateDbEpisode
import app.anikuta.domain.items.episode.interactor.UpdateEpisode
import app.anikuta.domain.items.episode.repository.EpisodeRepository
import app.anikuta.domain.library.service.LibraryPreferences
import app.anikuta.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

/**
 * Domain-layer DI module — registers the SQLDelight-backed repositories +
 * interactors for episodes, history, categories, and anime entries.
 *
 * This was previously dead code (files existed but were never registered).
 * Phase 0 of the Library/History/Search revamp wires it all up.
 *
 * Related files (edit one → check the others):
 *   - AppModule.kt           — registers the DB + handler this module depends on
 *   - PreferenceModule.kt    — registers PreferenceStore
 *   - data/.../Impl.kt       — the repository implementations
 *   - domain/.../interactor/ — the interactors registered here
 *
 * Adaptation note: aniyomi puts this in a `DomainModule.kt` in the `:app`
 * module (not `:data`). We follow the same pattern. Our app is AniList-first,
 * so `animes.source` + `animes.url` (NOT NULL) are only populated after
 * AniyomiSourceBridge resolves — the repos are available now but writes
 * happen lazily.
 */
class DomainModule : InjektModule {
    override fun InjektRegistrar.registerInjectables() {
        // -----------------------------------------------------------
        // Preferences
        // -----------------------------------------------------------

        // LibraryPreferences — 381-line façade for display modes, sorts,
        // badges, filters, categories, grid columns. Previously dead code.
        addSingletonFactory { LibraryPreferences(get<PreferenceStore>()) }

        // Domain DownloadPreferences — distinct from the app DownloadPreferences
        // (different package, different API). Needed only by DeleteAnimeCategory.
        // Registered with a type-aliased import to avoid collision.
        addSingletonFactory { DomainDownloadPreferences(get<PreferenceStore>()) }

        // -----------------------------------------------------------
        // Repositories (all backed by AnimeDatabaseHandler)
        // -----------------------------------------------------------

        addSingletonFactory<EpisodeRepository> { EpisodeRepositoryImpl(get<AnimeDatabaseHandler>()) }
        addSingletonFactory<AnimeHistoryRepository> { AnimeHistoryRepositoryImpl(get<AnimeDatabaseHandler>()) }
        addSingletonFactory<AnimeCategoryRepository> { AnimeCategoryRepositoryImpl(get<AnimeDatabaseHandler>()) }
        addSingletonFactory<AnimeRepository> { AnimeRepositoryImpl(get<AnimeDatabaseHandler>()) }

        // -----------------------------------------------------------
        // Episode interactors
        // -----------------------------------------------------------

        addSingletonFactory { GetEpisode(get<EpisodeRepository>()) }
        addSingletonFactory { GetEpisodeByUrlAndAnimeId(get<EpisodeRepository>()) }
        addSingletonFactory { GetEpisodesByAnimeId(get<EpisodeRepository>()) }
        addSingletonFactory { UpdateEpisode(get<EpisodeRepository>()) }
        addSingletonFactory { ShouldUpdateDbEpisode() }
        // SetAnimeDefaultEpisodeFlags depends on LibraryPreferences + SetAnimeEpisodeFlags + GetAnimeFavorites
        addSingletonFactory { SetAnimeDefaultEpisodeFlags(get<LibraryPreferences>(), get<SetAnimeEpisodeFlags>(), get<GetAnimeFavorites>()) }

        // -----------------------------------------------------------
        // History interactors
        // -----------------------------------------------------------

        addSingletonFactory { GetAnimeHistory(get<AnimeHistoryRepository>()) }
        addSingletonFactory { RemoveAnimeHistory(get<AnimeHistoryRepository>()) }
        addSingletonFactory { UpsertAnimeHistory(get<AnimeHistoryRepository>()) }
        // GetNextEpisodes depends on GetEpisodesByAnimeId + GetAnime + AnimeHistoryRepository
        addSingletonFactory { GetNextEpisodes(get<GetEpisodesByAnimeId>(), get<GetAnime>(), get<AnimeHistoryRepository>()) }

        // -----------------------------------------------------------
        // Category interactors
        // -----------------------------------------------------------

        addSingletonFactory { GetAnimeCategories(get<AnimeCategoryRepository>()) }
        addSingletonFactory { GetVisibleAnimeCategories(get<AnimeCategoryRepository>()) }
        addSingletonFactory { CreateAnimeCategoryWithName(get<AnimeCategoryRepository>(), get<LibraryPreferences>()) }
        addSingletonFactory { RenameAnimeCategory(get<AnimeCategoryRepository>()) }
        addSingletonFactory { ReorderAnimeCategory(get<AnimeCategoryRepository>()) }
        addSingletonFactory { HideAnimeCategory(get<AnimeCategoryRepository>()) }
        addSingletonFactory { UpdateAnimeCategory(get<AnimeCategoryRepository>()) }
        addSingletonFactory { ResetAnimeCategoryFlags(get<LibraryPreferences>(), get<AnimeCategoryRepository>()) }
        addSingletonFactory { SetAnimeDisplayMode(get<LibraryPreferences>()) }
        addSingletonFactory { SetSortModeForAnimeCategory(get<LibraryPreferences>(), get<AnimeCategoryRepository>()) }
        // DeleteAnimeCategory needs the domain DownloadPreferences (type-aliased)
        addSingletonFactory { DeleteAnimeCategory(get<AnimeCategoryRepository>(), get<LibraryPreferences>(), get<DomainDownloadPreferences>()) }
        // SetAnimeCategories uses AnimeRepository.setAnimeCategories
        addSingletonFactory { SetAnimeCategories(get<AnimeRepository>()) }

        // -----------------------------------------------------------
        // Anime (entries) interactors
        // -----------------------------------------------------------

        addSingletonFactory { GetAnime(get<AnimeRepository>()) }
        addSingletonFactory { GetAnimeByUrlAndSourceId(get<AnimeRepository>()) }
        addSingletonFactory { GetAnimeFavorites(get<AnimeRepository>()) }
        addSingletonFactory { GetAnimeWithEpisodesAndSeasons(get<AnimeRepository>(), get<EpisodeRepository>()) }
        addSingletonFactory { GetDuplicateLibraryAnime(get<AnimeRepository>()) }
        addSingletonFactory { GetLibraryAnime(get<AnimeRepository>()) }
        addSingletonFactory { NetworkToLocalAnime(get<AnimeRepository>(), get<AnimeSourceManager>()) }
        addSingletonFactory { ResetAnimeViewerFlags(get<AnimeRepository>()) }
        addSingletonFactory { SetAnimeEpisodeFlags(get<AnimeRepository>()) }
        addSingletonFactory { SetAnimeSeasonFlags(get<AnimeRepository>()) }
        addSingletonFactory { AnimeFetchInterval(get<GetEpisodesByAnimeId>()) }
    }
}

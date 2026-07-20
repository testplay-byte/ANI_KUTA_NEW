package app.confused.anikuta.di

import app.confused.anikuta.core.common.di.DefaultDispatcherProvider
import app.confused.anikuta.core.common.di.DispatcherProvider
import app.confused.anikuta.core.common.repository.AnimeRepository
import app.confused.anikuta.core.common.repository.EpisodeRepository
import app.confused.anikuta.core.common.repository.HistoryRepository
import app.confused.anikuta.data.anime.AnimeRepositoryImpl
import app.confused.anikuta.data.anime.EpisodeRepositoryImpl
import app.confused.anikuta.data.history.HistoryRepositoryImpl
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin module for repository bindings (ADR-023).
 *
 * Binds interfaces (in `:core:common`) to implementations (in `:data:*`).
 * Per `RULES/ai-agent-rules.md` §3: ViewModels depend on the interface only.
 *
 * Also provides [DispatcherProvider] (injected, not hardcoded — per ADR-023
 * and `RULES/ai-agent-rules.md` §10 testability).
 */
val repositoryModule: Module = module {
    single<DispatcherProvider> { DefaultDispatcherProvider() }
    single<AnimeRepository> { AnimeRepositoryImpl(get(), get()) }
    single<EpisodeRepository> { EpisodeRepositoryImpl(get(), get()) }
    single<HistoryRepository> { HistoryRepositoryImpl(get(), get()) }
}

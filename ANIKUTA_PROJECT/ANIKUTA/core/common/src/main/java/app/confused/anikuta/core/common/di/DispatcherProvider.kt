package app.confused.anikuta.core.common.di

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Provides coroutine dispatchers for the app.
 *
 * Per ADR-023 (Koin) and `RULES/ai-agent-rules.md` §10 (testability):
 * dispatchers are injected, NOT hardcoded, so tests can swap them for
 * [kotlinx.coroutines.test.TestDispatcher].
 *
 * Default implementation uses [Dispatchers.IO] for database/network work
 * and [Dispatchers.Main] for UI work.
 */
interface DispatcherProvider {
    val io: CoroutineDispatcher
    val main: CoroutineDispatcher
    val default: CoroutineDispatcher
}

/** Default production implementation. */
class DefaultDispatcherProvider : DispatcherProvider {
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val main: CoroutineDispatcher = Dispatchers.Main
    override val default: CoroutineDispatcher = Dispatchers.Default
}

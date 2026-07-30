package app.confused.anikuta.core.common.model.details

/**
 * Registry of all registered [AnimeDetailsProvider]s.
 *
 * Populated via Koin multi-binding (`single<List<AnimeDetailsProvider>>`),
 * the same pattern used for `List<BackupProvider>` (see `BackupModule.kt`).
 *
 * Usage from a ViewModel:
 * ```
 * val provider = registry.forSource(currentDataSource)
 * val result = provider.load(request)
 * ```
 *
 * Adding a provider = one new class + one entry in the `listOf(...)` Koin
 * binding. No changes to this registry or the details page.
 */
class AnimeDetailsProviderRegistry(
    private val providers: List<AnimeDetailsProvider>,
) {
    /** All registered providers (rarely needed directly — use [forSource]). */
    val all: List<AnimeDetailsProvider> get() = providers

    /**
     * @return the provider for [dataSource], or throws if none registered.
     * @throws IllegalStateException if no provider matches (configuration error).
     */
    fun forSource(dataSource: DataSource): AnimeDetailsProvider =
        providers.firstOrNull { it.dataSource == dataSource }
            ?: error("No AnimeDetailsProvider registered for $dataSource")
}

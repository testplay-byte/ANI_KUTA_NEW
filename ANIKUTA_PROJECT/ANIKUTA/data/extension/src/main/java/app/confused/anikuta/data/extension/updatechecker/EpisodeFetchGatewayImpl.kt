package app.confused.anikuta.data.extension.updatechecker

import android.util.Log
import app.confused.anikuta.core.updatechecker.EpisodeFetchGateway
import app.confused.anikuta.core.updatechecker.EpisodeFetchResult
import app.confused.anikuta.core.updatechecker.EpisodeInfo
import app.confused.anikuta.data.extension.matcher.SourceMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * `:data:extension` implementation of [EpisodeFetchGateway].
 *
 * Bridges the `:core:update-checker` module (which cannot depend on
 * `:data:extension` per ARCHITECTURE.md §3) to the real extension stack.
 *
 * Flow:
 *  1. [SourceMatcher.matchAll] — searches every trusted catalogue source for
 *     the title; returns ranked `SourceMatch`(s) (exact-match short-circuits).
 *  2. Take the first match (highest score).
 *  3. `source.getEpisodeList(sAnime)` — fetch the current episode list. This
 *     is a `suspend` call that internally delegates to RxJava `awaitSingle()`
 *     → `call.execute()`, so it MUST run on `Dispatchers.IO` (wrapped here).
 *  4. Map `SEpisode` → [EpisodeInfo] (our serializable, classloader-agnostic
 *     data class — we don't pass `SEpisode` across module boundaries because
 *     it's a mutable `Serializable` tied to the extension classloader).
 *
 * Registered as the [EpisodeFetchGateway] binding in `extensionModule`
 * (Koin `single<EpisodeFetchGateway> { ... }`).
 *
 * **Future optimization:** consult `ExtensionLinkStore` to skip re-matching
 * for anime that were previously linked to a specific source. For v1 we
 * re-match every check — `SourceMatcher.matchAll` is sequential with an
 * exact-match short-circuit, so it's acceptably fast for a manual
 * pull-to-refresh. A background worker should add the link-store cache.
 */
class EpisodeFetchGatewayImpl(
    private val sourceMatcher: SourceMatcher,
) : EpisodeFetchGateway {

    override suspend fun fetchEpisodes(animeTitle: String): EpisodeFetchResult =
        withContext(Dispatchers.IO) {
            if (animeTitle.isBlank()) return@withContext EpisodeFetchResult.NoSource

            val matches = try {
                sourceMatcher.matchAll(animeTitle)
            } catch (t: Throwable) {
                Log.e(TAG, "matchAll failed for \"$animeTitle\"", t)
                return@withContext EpisodeFetchResult.NoSource
            }

            val match = matches.firstOrNull() ?: return@withContext EpisodeFetchResult.NoSource
            val source = match.source
            val sAnime = match.sAnime

            val episodes = try {
                source.getEpisodeList(sAnime)
            } catch (t: Throwable) {
                // catch Throwable — extension bytecode can throw
                // IncompatibleClassChangeError / NoClassDefFoundError.
                Log.e(TAG, "getEpisodeList failed on ${source.name} for \"$animeTitle\"", t)
                return@withContext EpisodeFetchResult.NoSource
            }

            if (episodes.isEmpty()) {
                Log.i(TAG, "Source ${source.name} returned 0 episodes for \"$animeTitle\"")
                return@withContext EpisodeFetchResult.NoSource
            }

            val mapped = episodes.map { ep ->
                EpisodeInfo(
                    episodeNumber = ep.episode_number,
                    title = ep.name,
                    url = ep.url,
                    dateUpload = ep.date_upload,
                    scanlator = ep.scanlator,
                )
            }

            EpisodeFetchResult.Success(
                sourceName = source.name,
                episodes = mapped,
            )
        }

    companion object {
        private const val TAG = "EpisodeFetchGateway"
    }
}

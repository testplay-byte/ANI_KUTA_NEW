package app.anikuta.domain.source.anime.model

import app.anikuta.source.api.AnimeSource
import app.anikuta.source.api.model.SAnime
import app.anikuta.source.api.model.SEpisode
import app.anikuta.source.api.model.Video

@Suppress("OverridingDeprecatedMember")
class StubAnimeSource(
    override val id: Long,
    override val lang: String,
    override val name: String,
) : AnimeSource {

    private val isInvalid: Boolean = name.isBlank() || lang.isBlank()

    override suspend fun getAnimeDetails(anime: SAnime): SAnime =
        throw AnimeSourceNotInstalledException()

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> =
        throw AnimeSourceNotInstalledException()

    override suspend fun getSeasonList(anime: SAnime): List<SAnime> =
        throw AnimeSourceNotInstalledException()

    override suspend fun getVideoList(episode: SEpisode): List<Video> =
        throw AnimeSourceNotInstalledException()

    override fun toString(): String =
        if (!isInvalid) "$name (${lang.uppercase()})" else id.toString()

    companion object {
        fun from(source: AnimeSource): StubAnimeSource {
            return StubAnimeSource(id = source.id, lang = source.lang, name = source.name)
        }
    }
}
class AnimeSourceNotInstalledException : Exception()

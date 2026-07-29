package app.confused.anikuta.migration

import app.confused.anikuta.core.episodemetadata.model.EpisodeMetadata
import app.confused.anikuta.core.episodemetadata.repository.EpisodeMetadataCache
import app.confused.anikuta.core.player.PlaybackStateStore
import app.confused.anikuta.core.player.WatchProgressStore
import app.confused.anikuta.core.preferences.Preference
import app.confused.anikuta.core.preferences.PreferenceStore
import app.confused.anikuta.data.extension.cache.SourceLinkStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [ContentIdMigrator] (Phase 5, ADR-050).
 *
 * Verifies the re-keying of all cross-cutting stores when a content_id changes
 * (link/unlink/switch events). Uses in-memory fake stores to verify the
 * rekey calls + counts.
 *
 * Per `plan/03_testing_strategy.md` Phase 5.
 */
class ContentIdMigratorTest {

    /**
     * A minimal in-memory PreferenceStore stub for testing.
     * Only implements getObject (the only method the stores use).
     */
    private class StubPreferenceStore : PreferenceStore {
        val data = mutableMapOf<String, Any?>()

        @Suppress("UNCHECKED_CAST")
        override fun <T> getObject(
            key: String,
            defaultValue: T,
            serializer: (T) -> String,
            deserializer: (String) -> T,
        ): Preference<T> {
            return object : Preference<T> {
                override fun key(): String = key
                override fun get(): T = (data[key] as? T) ?: defaultValue
                override fun set(value: T) { data[key] = value }
                override fun isSet(): Boolean = data.containsKey(key)
                override fun delete() { data.remove(key) }
                override fun defaultValue(): T = defaultValue
                override fun changes(): Flow<T> = flowOf(get())
                override fun stateIn(scope: CoroutineScope) = MutableStateFlow(get())
            }
        }

        override fun getString(key: String, defaultValue: String) = throw NotImplementedError()
        override fun getLong(key: String, defaultValue: Long) = throw NotImplementedError()
        override fun getInt(key: String, defaultValue: Int) = throw NotImplementedError()
        override fun getFloat(key: String, defaultValue: Float) = throw NotImplementedError()
        override fun getBoolean(key: String, defaultValue: Boolean) = throw NotImplementedError()
        override fun getStringSet(key: String, defaultValue: Set<String>) = throw NotImplementedError()
        override fun getAll(): Map<String, *> = emptyMap<String, Any?>()
    }

    @Test
    fun `migrate is no-op when oldContentId equals newContentId`() {
        val store = StubPreferenceStore()
        val migrator = ContentIdMigrator(
            WatchProgressStore(store),
            PlaybackStateStore(store),
            EpisodeMetadataCache(store),
            SourceLinkStore(store),
        )
        val result = migrator.migrate("al:154587", "al:154587")
        assertEquals(0, result.watchProgressEntries)
        assertEquals(0, result.playbackStateEntries)
        assertEquals(0, result.episodeMetadataEntries)
        assertEquals(0, result.sourceLinkEntries)
    }

    @Test
    fun `migrate handles empty stores gracefully`() {
        val store = StubPreferenceStore()
        val migrator = ContentIdMigrator(
            WatchProgressStore(store),
            PlaybackStateStore(store),
            EpisodeMetadataCache(store),
            SourceLinkStore(store),
        )
        val result = migrator.migrate("al:154587", "al:999999")
        assertEquals(0, result.watchProgressEntries)
        assertEquals(0, result.playbackStateEntries)
        assertEquals(0, result.episodeMetadataEntries)
        assertEquals(0, result.sourceLinkEntries)
    }

    @Test
    fun `migrate re-keys watch progress entries with old content_id prefix`() {
        val store = StubPreferenceStore()
        val watchProgressStore = WatchProgressStore(store)

        watchProgressStore.save("al:154587", 1.0f, 100, 300, "Ep 1")
        watchProgressStore.save("al:154587", 2.0f, 200, 300, "Ep 2")
        watchProgressStore.save("al:999999", 1.0f, 50, 300, "Other Ep 1")

        val migrator = ContentIdMigrator(
            watchProgressStore,
            PlaybackStateStore(store),
            EpisodeMetadataCache(store),
            SourceLinkStore(store),
        )
        val result = migrator.migrate("al:154587", "al:111111")

        assertEquals(2, result.watchProgressEntries)
        assertEquals("Ep 1", watchProgressStore.get("al:111111", 1.0f)?.title)
        assertEquals("Ep 2", watchProgressStore.get("al:111111", 2.0f)?.title)
        assertEquals(null, watchProgressStore.get("al:154587", 1.0f))
        assertEquals(null, watchProgressStore.get("al:154587", 2.0f))
        assertEquals("Other Ep 1", watchProgressStore.get("al:999999", 1.0f)?.title)
    }

    @Test
    fun `migrate re-keys playback state entries with old content_id prefix`() {
        val store = StubPreferenceStore()
        val playbackStateStore = PlaybackStateStore(store)

        playbackStateStore.save("al:154587", 1.0f, "https://video.url")
        playbackStateStore.save("al:154587", 2.0f, "https://video2.url")
        playbackStateStore.save("al:999999", 1.0f, "https://other.url")

        val migrator = ContentIdMigrator(
            WatchProgressStore(store),
            playbackStateStore,
            EpisodeMetadataCache(store),
            SourceLinkStore(store),
        )
        val result = migrator.migrate("al:154587", "al:111111")

        assertEquals(2, result.playbackStateEntries)
        assertEquals("https://video.url", playbackStateStore.get("al:111111", 1.0f)?.videoUrl)
        assertEquals("https://video2.url", playbackStateStore.get("al:111111", 2.0f)?.videoUrl)
        assertEquals(null, playbackStateStore.get("al:154587", 1.0f))
    }

    @Test
    fun `migrate re-keys episode metadata entry`() {
        val store = StubPreferenceStore()
        val metadataCache = EpisodeMetadataCache(store)

        val metadata = mapOf(
            1 to EpisodeMetadata(
                animeId = 154587,
                episodeNumber = 1,
                title = "Episode 1",
                description = null,
                thumbnailUrl = null,
                airDate = null,
            )
        )
        metadataCache.save("al:154587", metadata)
        metadataCache.save("al:999999", metadata)

        val migrator = ContentIdMigrator(
            WatchProgressStore(store),
            PlaybackStateStore(store),
            metadataCache,
            SourceLinkStore(store),
        )
        val result = migrator.migrate("al:154587", "al:111111")

        assertEquals(1, result.episodeMetadataEntries)
        assertTrue(metadataCache.get("al:111111") != null)
        assertTrue(metadataCache.get("al:154587") == null)
        assertTrue(metadataCache.get("al:999999") != null)
    }

    @Test
    fun `migrate re-keys source link entry`() {
        val store = StubPreferenceStore()
        val sourceLinkStore = SourceLinkStore(store)

        sourceLinkStore.saveLink("al:154587", 123L, "https://anime.url", "Frieren")
        sourceLinkStore.saveLink("al:999999", 456L, "https://other.url", "Other")

        val migrator = ContentIdMigrator(
            WatchProgressStore(store),
            PlaybackStateStore(store),
            EpisodeMetadataCache(store),
            sourceLinkStore,
        )
        val result = migrator.migrate("al:154587", "al:111111")

        assertEquals(1, result.sourceLinkEntries)
        assertEquals("Frieren", sourceLinkStore.getLink("al:111111")?.animeTitle)
        assertEquals(null, sourceLinkStore.getLink("al:154587"))
        assertEquals("Other", sourceLinkStore.getLink("al:999999")?.animeTitle)
    }

    @Test
    fun `migrate handles unlinked-to-linked content_id change`() {
        val store = StubPreferenceStore()
        val watchProgressStore = WatchProgressStore(store)
        val oldContentId = "aniyomi:123:https://example.com/anime"

        watchProgressStore.save(oldContentId, 1.0f, 100, 300, "Ep 1")

        val migrator = ContentIdMigrator(
            watchProgressStore,
            PlaybackStateStore(store),
            EpisodeMetadataCache(store),
            SourceLinkStore(store),
        )
        val result = migrator.migrate(oldContentId, "al:154587")

        assertEquals(1, result.watchProgressEntries)
        assertEquals("Ep 1", watchProgressStore.get("al:154587", 1.0f)?.title)
        assertEquals(null, watchProgressStore.get(oldContentId, 1.0f))
    }
}

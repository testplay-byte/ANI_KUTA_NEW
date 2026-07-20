package com.testplaybyte.animeapp.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.testplaybyte.animeapp.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.libraryDataStore by preferencesDataStore("anime_library")

class LibraryRepository(private val context: Context) {
    private val KEY_ITEMS = stringPreferencesKey("items")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(LibraryItem.serializer())

    val items: Flow<List<LibraryItem>> = context.libraryDataStore.data.map { p ->
        p[KEY_ITEMS]?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() } ?: emptyList()
    }

    suspend fun add(anime: Anime, status: LibraryStatus = LibraryStatus.WATCHING) {
        context.libraryDataStore.edit { p ->
            val current = p[KEY_ITEMS]?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() } ?: emptyList()
            val item = LibraryItem(
                id = anime.id,
                title = anime.displayTitle,
                cover = anime.coverUrl,
                score = anime.averageScore,
                format = anime.format,
                episodes = anime.episodes,
                status = status,
                addedAt = System.currentTimeMillis(),
            )
            val next = listOf(item) + current.filter { it.id != anime.id }
            p[KEY_ITEMS] = json.encodeToString(serializer, next)
        }
    }

    suspend fun remove(ids: Set<Int>) {
        context.libraryDataStore.edit { p ->
            val current = p[KEY_ITEMS]?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() } ?: emptyList()
            p[KEY_ITEMS] = json.encodeToString(serializer, current.filter { it.id !in ids })
        }
    }

    suspend fun setStatus(id: Int, status: LibraryStatus) {
        context.libraryDataStore.edit { p ->
            val current = p[KEY_ITEMS]?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() } ?: emptyList()
            p[KEY_ITEMS] = json.encodeToString(serializer, current.map { if (it.id == id) it.copy(status = status) else it })
        }
    }

    suspend fun setStatusForIds(ids: Set<Int>, status: LibraryStatus) {
        context.libraryDataStore.edit { p ->
            val current = p[KEY_ITEMS]?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() } ?: emptyList()
            p[KEY_ITEMS] = json.encodeToString(serializer, current.map { if (it.id in ids) it.copy(status = status) else it })
        }
    }

    suspend fun clear() {
        context.libraryDataStore.edit { it.remove(KEY_ITEMS) }
    }
}

package com.testplaybyte.animeapp.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.testplaybyte.animeapp.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.historyDataStore by preferencesDataStore("anime_history")

class HistoryRepository(private val context: Context) {
    private val KEY_ITEMS = stringPreferencesKey("items")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(HistoryItem.serializer())

    val items: Flow<List<HistoryItem>> = context.historyDataStore.data.map { p ->
        p[KEY_ITEMS]?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() } ?: emptyList()
    }

    suspend fun add(anime: Anime) {
        context.historyDataStore.edit { p ->
            val current = p[KEY_ITEMS]?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() } ?: emptyList()
            val existing = current.find { it.id == anime.id }
            val (episode, progress) = if (existing != null) {
                val advance = 15 + (0..19).random()
                val newProgress = existing.progress + advance
                if (newProgress >= 100) Pair(existing.episode + 1, minOf(newProgress - 100, 80))
                else Pair(existing.episode, newProgress)
            } else {
                Pair(1, 10 + (0..29).random())
            }
            val item = HistoryItem(
                id = anime.id,
                title = anime.displayTitle,
                cover = anime.coverUrl,
                viewedAt = System.currentTimeMillis(),
                episode = episode,
                totalEpisodes = anime.episodes,
                progress = minOf(progress, 99),
                banner = anime.bannerImage ?: anime.coverUrl,
            )
            val next = (listOf(item) + current.filter { it.id != anime.id }).take(20)
            p[KEY_ITEMS] = json.encodeToString(serializer, next)
        }
    }

    suspend fun clear() {
        context.historyDataStore.edit { it.remove(KEY_ITEMS) }
    }
}

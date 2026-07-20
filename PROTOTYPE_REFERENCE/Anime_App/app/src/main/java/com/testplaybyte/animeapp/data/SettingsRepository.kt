package com.testplaybyte.animeapp.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.testplaybyte.animeapp.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore("anime_app_settings")

class SettingsRepository(private val context: Context) {
    private val KEY_DARK = booleanPreferencesKey("dark_theme")
    private val KEY_POSTER = stringPreferencesKey("poster_style")
    private val KEY_DENSITY = stringPreferencesKey("card_density")
    private val KEY_ANIM_SPEED = stringPreferencesKey("anim_speed")
    private val KEY_SINGLE_LINE = booleanPreferencesKey("single_line_titles")
    private val KEY_LIB_LAYOUT = stringPreferencesKey("lib_layout")
    private val KEY_LIB_COLUMNS = intPreferencesKey("lib_columns")
    private val KEY_LIB_TEXT = stringPreferencesKey("lib_text_placement")
    private val KEY_LIB_SHOW_FORMAT = booleanPreferencesKey("lib_show_format")
    private val KEY_LIB_SHOW_EP = booleanPreferencesKey("lib_show_episodes")
    private val KEY_LIB_EP_POS = stringPreferencesKey("lib_ep_position")

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { p ->
        AppSettings(
            darkTheme = p[KEY_DARK] ?: true,
            posterStyle = p[KEY_POSTER]?.let { runCatching { PosterStyle.valueOf(it) }.getOrNull() } ?: PosterStyle.ROUNDED,
            cardDensity = p[KEY_DENSITY]?.let { runCatching { CardDensity.valueOf(it) }.getOrNull() } ?: CardDensity.DEFAULT,
            animSpeed = p[KEY_ANIM_SPEED]?.let { runCatching { AnimSpeed.valueOf(it) }.getOrNull() } ?: AnimSpeed.NORMAL,
            singleLineTitles = p[KEY_SINGLE_LINE] ?: true,
            libraryLayout = p[KEY_LIB_LAYOUT]?.let { runCatching { LibraryLayout.valueOf(it) }.getOrNull() } ?: LibraryLayout.GRID,
            libraryColumns = p[KEY_LIB_COLUMNS] ?: 3,
            libraryTextPlacement = p[KEY_LIB_TEXT]?.let { runCatching { TextPlacement.valueOf(it) }.getOrNull() } ?: TextPlacement.BELOW,
            libraryShowFormat = p[KEY_LIB_SHOW_FORMAT] ?: true,
            libraryShowEpisodes = p[KEY_LIB_SHOW_EP] ?: true,
            libraryEpisodePosition = p[KEY_LIB_EP_POS]?.let { runCatching { EpisodePosition.valueOf(it) }.getOrNull() } ?: EpisodePosition.BOTTOM_RIGHT,
        )
    }

    suspend fun update(patch: AppSettings.() -> AppSettings) {
        context.settingsDataStore.edit { p ->
            val current = AppSettings(
                darkTheme = p[KEY_DARK] ?: true,
                posterStyle = p[KEY_POSTER]?.let { runCatching { PosterStyle.valueOf(it) }.getOrNull() } ?: PosterStyle.ROUNDED,
                cardDensity = p[KEY_DENSITY]?.let { runCatching { CardDensity.valueOf(it) }.getOrNull() } ?: CardDensity.DEFAULT,
                animSpeed = p[KEY_ANIM_SPEED]?.let { runCatching { AnimSpeed.valueOf(it) }.getOrNull() } ?: AnimSpeed.NORMAL,
                singleLineTitles = p[KEY_SINGLE_LINE] ?: true,
                libraryLayout = p[KEY_LIB_LAYOUT]?.let { runCatching { LibraryLayout.valueOf(it) }.getOrNull() } ?: LibraryLayout.GRID,
                libraryColumns = p[KEY_LIB_COLUMNS] ?: 3,
                libraryTextPlacement = p[KEY_LIB_TEXT]?.let { runCatching { TextPlacement.valueOf(it) }.getOrNull() } ?: TextPlacement.BELOW,
                libraryShowFormat = p[KEY_LIB_SHOW_FORMAT] ?: true,
                libraryShowEpisodes = p[KEY_LIB_SHOW_EP] ?: true,
                libraryEpisodePosition = p[KEY_LIB_EP_POS]?.let { runCatching { EpisodePosition.valueOf(it) }.getOrNull() } ?: EpisodePosition.BOTTOM_RIGHT,
            )
            val next = current.patch()
            p[KEY_DARK] = next.darkTheme
            p[KEY_POSTER] = next.posterStyle.name
            p[KEY_DENSITY] = next.cardDensity.name
            p[KEY_ANIM_SPEED] = next.animSpeed.name
            p[KEY_SINGLE_LINE] = next.singleLineTitles
            p[KEY_LIB_LAYOUT] = next.libraryLayout.name
            p[KEY_LIB_COLUMNS] = next.libraryColumns
            p[KEY_LIB_TEXT] = next.libraryTextPlacement.name
            p[KEY_LIB_SHOW_FORMAT] = next.libraryShowFormat
            p[KEY_LIB_SHOW_EP] = next.libraryShowEpisodes
            p[KEY_LIB_EP_POS] = next.libraryEpisodePosition.name
        }
    }
}

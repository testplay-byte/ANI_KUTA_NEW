package app.confused.anikuta.core.player.di

import app.confused.anikuta.core.player.PlaybackStateStore
import app.confused.anikuta.core.player.PlayerEpisodePreferences
import app.confused.anikuta.core.player.PlayerPreferences
import app.confused.anikuta.core.player.WatchProgressStore
import app.confused.anikuta.core.preferences.PreferenceStore
import org.koin.dsl.module

val playerModule = module {
    single { PlayerPreferences(get<PreferenceStore>()) }
    single { PlayerEpisodePreferences(get<PreferenceStore>()) }
    single { WatchProgressStore(get<PreferenceStore>()) }
    single { PlaybackStateStore(get<PreferenceStore>()) }
}

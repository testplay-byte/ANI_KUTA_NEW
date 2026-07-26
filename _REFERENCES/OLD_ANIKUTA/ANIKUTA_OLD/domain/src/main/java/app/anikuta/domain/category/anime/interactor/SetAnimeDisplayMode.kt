package app.anikuta.domain.category.anime.interactor

import app.anikuta.domain.library.model.LibraryDisplayMode
import app.anikuta.domain.library.service.LibraryPreferences

class SetAnimeDisplayMode(
    private val preferences: LibraryPreferences,
) {

    fun await(display: LibraryDisplayMode) {
        preferences.displayMode().set(display)
    }
}

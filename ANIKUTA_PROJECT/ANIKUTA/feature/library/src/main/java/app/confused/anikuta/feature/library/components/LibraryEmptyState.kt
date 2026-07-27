package app.confused.anikuta.feature.library.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.confused.anikuta.core.designsystem.component.EmptyState

/**
 * Empty-state wrapper for the library.
 *
 * Two distinct states:
 * - [isEmpty] == true → the user has nothing in their library at all.
 * - [isEmpty] == false → there are library entries but the current
 *   filter/search hides them all.
 *
 * Delegates the actual layout to the design system's [EmptyState].
 */
@Composable
fun LibraryEmptyState(
    isEmpty: Boolean,
    modifier: Modifier = Modifier,
) {
    if (isEmpty) {
        EmptyState(
            title = "Your library is empty",
            description = "Browse anime and add them to your library.",
            icon = Icons.AutoMirrored.Filled.MenuBook,
            modifier = modifier,
        )
    } else {
        EmptyState(
            title = "No anime found",
            description = "Try a different category or search query.",
            icon = Icons.Filled.SearchOff,
            modifier = modifier,
        )
    }
}

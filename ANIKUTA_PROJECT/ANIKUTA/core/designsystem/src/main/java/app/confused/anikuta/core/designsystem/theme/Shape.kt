package app.confused.anikuta.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * ANIKUTA shapes — corner radii used across the app.
 *
 * Key values (from DESIGN_LANGUAGE):
 * - 28dp: bottom nav pill (the floating bar).
 * - 16dp: large cards, dialogs.
 * - 12dp: standard cards, day-selector pills, airing rows.
 * - 8dp: small cards, chips.
 * - 6dp: buttons.
 */
val AnikutaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** The bottom nav pill shape (28dp — matches `Shapes.extraLarge`). */
val BottomNavPillShape = RoundedCornerShape(28.dp)

/** The day-selector pill shape (12dp). */
val DayPillShape = RoundedCornerShape(12.dp)

/** The active-nav-pill shape (50% — fully rounded). */
val ActiveNavPillShape = RoundedCornerShape(50)

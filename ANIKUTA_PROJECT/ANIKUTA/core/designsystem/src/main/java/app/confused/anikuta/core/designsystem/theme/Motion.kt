package app.confused.anikuta.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween

/**
 * ANIKUTA motion specs — animation durations and easing curves.
 *
 * Matches the prototype's animation style (tween 300ms, FastOutSlowInEasing).
 */
object Motion {
    /** Standard duration for most UI animations (color, size, visibility). */
    const val DurationStandard = 300

    /** Short duration for quick fades. */
    const val DurationShort = 200

    /** Very short duration for instant feedback. */
    const val DurationInstant = 100

    /** Standard easing for most animations. */
    val EasingStandard: Easing = FastOutSlowInEasing

    /** Emphasized easing for spatial transitions (enter/exit). */
    val EasingEmphasized: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    /** Helper: a standard tween spec. */
    fun <T> standardTween() = tween<T>(
        durationMillis = DurationStandard,
        easing = EasingStandard,
    )
}

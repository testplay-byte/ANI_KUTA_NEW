package app.anikuta.player

/**
 * ANI-KUTA PlayerEnums — minimal.
 *
 * Selective copy-paste from aniyomi's `PlayerEnums.kt` (D1): only the states a
 * minimal player needs. aniyomi's version adds StepMode, SeekDirection,
 * PlayerVideoAspect, AudioMode, etc. — deferred.
 *
 * Source: REFERENCE/app/.../ui/player/PlayerEnums.kt (reduced).
 */
enum class PlayerVideoAspect {
    /** Original aspect ratio (may letterbox). */
    ORIGINAL,
    /** Stretch to fill the screen. */
    STRETCH,
    /** Crop to fill the screen. */
    CROP,
}

enum class PlayerLoadingState {
    INITIALIZING,
    LOADING,
    READY,
    ERROR,
}

/**
 * The two display modes of the combined video player.
 *
 * - [MINIMIZED]: Video at top, episode info + server/version dropdowns + episodes list below.
 *   Portrait orientation. Video adapts to aspect ratio.
 * - [FULLSCREEN]: Video fills the entire screen (landscape). All controls overlaid on video.
 *
 * The video keeps playing during mode transitions — no reload/rebuffer.
 */
enum class PlayerMode {
    MINIMIZED,
    FULLSCREEN,
}

/**
 * The default view mode when the user opens the player.
 *
 * - [MINIMIZED]: Always open in minimized mode.
 * - [FULLSCREEN]: Always open in fullscreen mode.
 * - [ASK]: Show a prompt asking the user to choose (first time only, then remembers).
 */
enum class DefaultPlayerView {
    MINIMIZED,
    FULLSCREEN,
    ASK,
}

/**
 * Represents a video/audio/subtitle track from MPV's track-list.
 *
 * @param id The MPV track ID (used with sid/aid properties)
 * @param name Display name (title or language)
 * @param language Language code (e.g., "jpn", "eng") or null
 */
data class VideoTrack(
    val id: Int,
    val name: String,
    val language: String?,
)

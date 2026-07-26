package app.anikuta.notification

/**
 * Phase N-3 (Notifications) — Simple deep-link helper.
 *
 * When a new-episode notification is tapped, the PendingIntent launches
 * MainActivity. This object holds the "pending detail ID" that the NavGraph
 * reads on start to navigate to the detail page.
 *
 * This is intentionally simple — no complex deep-link URI parsing. The
 * notification sets [pendingDetailId] before launching the Intent; the NavGraph
 * reads + clears it in a LaunchedEffect on start.
 */
object NotificationDeepLink {
    /** Set by NotificationDispatcher before launching MainActivity. Read + cleared by NavGraph. */
    @Volatile
    var pendingDetailId: Int? = null

    /** Set by NotificationDispatcher for the "Watch" action. */
    @Volatile
    var pendingAutoPlayUrl: String? = null

    /** Read + clear — called by NavGraph on start. Returns the anilistId to navigate to, or null. */
    fun consumePendingNavigation(): Pair<Int, String?>? {
        val id = pendingDetailId ?: return null
        val url = pendingAutoPlayUrl
        pendingDetailId = null
        pendingAutoPlayUrl = null
        return id to url
    }
}

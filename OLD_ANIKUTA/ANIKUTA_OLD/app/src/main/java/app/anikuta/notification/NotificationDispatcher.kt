package app.anikuta.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import app.anikuta.MainActivity
import app.anikuta.R
import app.anikuta.data.notification.Notifications

/**
 * Phase N-3 (Notifications) — Builds and fires new-episode notifications.
 *
 * Two notification types:
 *  1. Extension-confirmed (CHANNEL_NEW_EPISODES) — "Episode X is available to watch"
 *     May distinguish SUB/DUB. This is the main, higher-importance notification.
 *  2. AniList-based (CHANNEL_NEW_EPISODES_ANILIST) — "Episode X has aired (AniList)"
 *     Does NOT distinguish sub/dub. Lower importance. Only sent in Mode 1 or 3.
 *
 * Grouping: if multiple new episodes drop at once, a summary notification is
 * posted with the count, and individual notifications are grouped under it.
 *
 * Quiet hours: if currently in quiet hours, notifications are still shown but
 * silently (no sound/vibration) — we can't easily defer delivery.
 *
 * Deep-linking: tapping a notification opens the detail page for that anime
 * via [NotificationDeepLink].
 */
class NotificationDispatcher(
    private val context: Context,
    private val prefs: NotificationPreferences,
) {

    companion object {
        private const val TAG = "NotificationDispatcher"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * Data for a single new-episode notification.
     */
    data class NewEpisodeNotification(
        val anilistId: Int,
        val title: String,
        val episodeNumber: Float,
        val episodeName: String?,
        val coverUrl: String?,
        val hasSub: Boolean,
        val hasDub: Boolean,
        val isAniListOnly: Boolean = false,  // true for Mode 1/3 AniList-aired notifications
    )

    /**
     * Fire notifications for a list of new episodes.
     * Handles grouping if there are multiple.
     */
    fun notifyNewEpisodes(notifications: List<NewEpisodeNotification>) {
        if (notifications.isEmpty()) return

        val isQuiet = prefs.isCurrentlyQuietHour(
            java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        )

        if (notifications.size == 1) {
            postSingleNotification(notifications.first(), isQuiet)
        } else {
            postGroupedNotifications(notifications, isQuiet)
        }

        Log.d(TAG, "notifyNewEpisodes: posted ${notifications.size} notification(s) (quiet=$isQuiet)")
    }

    /**
     * Post a single new-episode notification.
     */
    private fun postSingleNotification(notif: NewEpisodeNotification, isQuiet: Boolean) {
        val channelId = if (notif.isAniListOnly) {
            Notifications.CHANNEL_NEW_EPISODES_ANILIST
        } else {
            Notifications.CHANNEL_NEW_EPISODES
        }

        val contentText = buildContentText(notif)
        val bigText = buildBigText(notif)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(notif.title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(if (isQuiet) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        // Tap → open detail page
        val tapIntent = createDetailPendingIntent(notif.anilistId, autoPlayUrl = null)
        builder.setContentIntent(tapIntent)

        // "Watch" action button (only for extension-confirmed, not AniList-only)
        if (!notif.isAniListOnly) {
            val watchIntent = createDetailPendingIntent(notif.anilistId, autoPlayUrl = "auto")
            builder.addAction(
                android.R.drawable.ic_media_play,
                "Watch",
                watchIntent,
            )
        }

        val notifId = if (notif.isAniListOnly) {
            Notifications.ID_NEW_EPISODE_ANILIST_BASE + notif.anilistId
        } else {
            Notifications.ID_NEW_EPISODE_BASE + notif.anilistId
        }

        notificationManager.notify(notifId, builder.build())
    }

    /**
     * Post grouped notifications for multiple new episodes.
     */
    private fun postGroupedNotifications(notifications: List<NewEpisodeNotification>, isQuiet: Boolean) {
        // Post each individual notification
        for (notif in notifications) {
            postSingleNotification(notif, isQuiet)
        }

        // Post a summary notification
        val summaryText = "${notifications.size} new episodes available"
        val detailText = notifications.joinToString("\n") { "• ${it.title} — Ep ${formatEpisodeNumber(it.episodeNumber)}" }

        val summaryBuilder = NotificationCompat.Builder(context, Notifications.CHANNEL_NEW_EPISODES)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("New episodes available")
            .setContentText(summaryText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detailText))
            .setGroup("new_episodes")
            .setGroupSummary(true)
            .setPriority(if (isQuiet) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        // Tap → open the app (no specific anime for the summary)
        val tapIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        summaryBuilder.setContentIntent(tapIntent)

        notificationManager.notify(Notifications.ID_NEW_EPISODE_SUMMARY, summaryBuilder.build())
    }

    /**
     * Build the content text for a notification.
     */
    private fun buildContentText(notif: NewEpisodeNotification): String {
        val epNum = formatEpisodeNumber(notif.episodeNumber)
        return if (notif.isAniListOnly) {
            "Episode $epNum has aired"
        } else {
            val audioTag = when {
                notif.hasSub && notif.hasDub -> " (SUB + DUB)"
                notif.hasSub -> " (SUB)"
                notif.hasDub -> " (DUB)"
                else -> ""
            }
            "Episode $epNum available$audioTag"
        }
    }

    /**
     * Build the expanded big text for a notification.
     */
    private fun buildBigText(notif: NewEpisodeNotification): String {
        val epNum = formatEpisodeNumber(notif.episodeNumber)
        val sb = StringBuilder()
        sb.append("Episode $epNum")

        if (!notif.episodeName.isNullOrBlank()) {
            sb.append("\n${notif.episodeName}")
        }

        if (notif.isAniListOnly) {
            sb.append("\n\nAvailable on AniList — may not be watchable yet.")
        } else {
            val audioTags = mutableListOf<String>()
            if (notif.hasSub) audioTags.add("SUB")
            if (notif.hasDub) audioTags.add("DUB")
            if (audioTags.isNotEmpty()) {
                sb.append("\n\nAudio: ${audioTags.joinToString(", ")}")
            }
            sb.append("\n\nTap to watch.")
        }

        return sb.toString()
    }

    /**
     * Create a PendingIntent that opens the detail page for an anime.
     */
    private fun createDetailPendingIntent(anilistId: Int, autoPlayUrl: String?): PendingIntent {
        // Set the deep-link target
        NotificationDeepLink.pendingDetailId = anilistId
        NotificationDeepLink.pendingAutoPlayUrl = autoPlayUrl

        return PendingIntent.getActivity(
            context,
            anilistId,  // unique request code per anime
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Cancel a notification for a specific anime.
     */
    fun cancelNotification(anilistId: Int) {
        notificationManager.cancel(Notifications.ID_NEW_EPISODE_BASE + anilistId)
        notificationManager.cancel(Notifications.ID_NEW_EPISODE_ANILIST_BASE + anilistId)
    }

    /**
     * Format an episode number for display (e.g. 12.0 → "12", 12.5 → "12.5").
     */
    private fun formatEpisodeNumber(num: Float): String {
        return if (num == num.toInt().toFloat()) {
            num.toInt().toString()
        } else {
            num.toString()
        }
    }
}

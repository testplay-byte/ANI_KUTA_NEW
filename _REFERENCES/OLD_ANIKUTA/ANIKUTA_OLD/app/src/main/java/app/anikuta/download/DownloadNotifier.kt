package app.anikuta.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import app.anikuta.MainActivity
import app.anikuta.data.notification.Notifications
import app.anikuta.download.progress.formatBytes
import app.anikuta.download.progress.formatSpeed

/**
 * Manages download notifications: progress (foreground service),
 * error, and completion.
 *
 * Channels are created lazily on first use (and also in App.onCreate).
 *
 * The foreground service notification is updated frequently during download
 * to show overall progress. It uses [Notifications.ID_DOWNLOAD_PROGRESS]
 * (a single notification ID reused for all active downloads).
 *
 * Improvements:
 *  - Progress notification shows: anime title, episode name, percentage,
 *    size (downloaded/total), and speed — formatted cleanly
 *  - Multi-download mode shows a compact summary with overall progress
 *  - Complete notification includes quality info
 *  - Error notification shows the error + anime context
 */
class DownloadNotifier(
    private val context: Context,
) {
    companion object {
        private const val TAG = "DownloadNotifier"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Volatile
    private var channelsCreated = false

    fun createChannels() {
        if (channelsCreated) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            channelsCreated = true
            return
        }

        val channels = listOf(
            NotificationChannel(
                Notifications.CHANNEL_DOWNLOADER_PROGRESS,
                "Download progress",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Ongoing notification showing download progress"
                setShowBadge(false)
            },
            NotificationChannel(
                Notifications.CHANNEL_DOWNLOADER_ERROR,
                "Download errors",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Notifications when downloads fail"
            },
            NotificationChannel(
                Notifications.CHANNEL_DOWNLOADER_COMPLETE,
                "Download complete",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Notifications when downloads finish"
            },
        )

        channels.forEach { channel ->
            notificationManager.createNotificationChannel(channel)
        }
        channelsCreated = true
        Log.d(TAG, "createChannels: ✓ created ${channels.size} channels")
    }

    /**
     * Build the foreground service notification for active downloads.
     *
     * Single download: "Anime — Episode" + progress bar + percentage + size + speed
     * Multiple downloads: "Downloading N episodes" + overall progress bar + compact list
     */
    fun buildProgressNotification(
        activeDownloads: List<Download>,
    ): NotificationCompat.Builder {
        createChannels()

        val tapIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        if (activeDownloads.size == 1) {
            val dl = activeDownloads[0]
            val title = dl.animeTitle
            val content = buildString {
                append(dl.episodeName)
                append(" — ")
                append("${dl.progress}%")
                if (dl.totalSize > 0) {
                    append(" · ${formatBytes(dl.downloadedBytes)} / ${formatBytes(dl.totalSize)}")
                }
                if (dl.speed > 0) {
                    append(" · ${formatSpeed(dl.speed)}")
                }
            }

            return NotificationCompat.Builder(context, Notifications.CHANNEL_DOWNLOADER_PROGRESS)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText(content)
                .setProgress(100, dl.progress, dl.progress <= 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(tapIntent)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }

        // Multiple downloads
        val title = "Downloading ${activeDownloads.size} episodes"
        val overallProgress = if (activeDownloads.isNotEmpty()) {
            activeDownloads.sumOf { it.progress } / activeDownloads.size
        } else 0

        val bigText = activeDownloads.joinToString("\n") { dl ->
            buildString {
                append("• ${dl.episodeName} (${dl.progress}%)")
                if (dl.speed > 0) append(" — ${formatSpeed(dl.speed)}")
            }
        }

        return NotificationCompat.Builder(context, Notifications.CHANNEL_DOWNLOADER_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText("$overallProgress% overall")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setProgress(100, overallProgress, overallProgress <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(tapIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
    }

    fun updateProgress(activeDownloads: List<Download>) {
        if (activeDownloads.isEmpty()) return
        val notification = buildProgressNotification(activeDownloads).build()
        notificationManager.notify(Notifications.ID_DOWNLOAD_PROGRESS, notification)
    }

    fun cancelProgress() {
        notificationManager.cancel(Notifications.ID_DOWNLOAD_PROGRESS)
    }

    fun postError(download: Download) {
        createChannels()
        val notificationId = Notifications.ID_DOWNLOAD_ERROR_BASE + download.id.hashCode()
        val tapIntent = PendingIntent.getActivity(
            context,
            download.id.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val bigText = buildString {
            append("${download.animeTitle}")
            append(" — ${download.episodeName}")
            if (download.serverName.isNotBlank()) {
                append("\nServer: ${download.serverName}")
            }
            if (download.qualityLabel.isNotBlank()) {
                append("\nQuality: ${download.qualityLabel}")
            }
            append("\nError: ${download.error ?: "Unknown"}")
        }
        val notification = NotificationCompat.Builder(context, Notifications.CHANNEL_DOWNLOADER_ERROR)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Download failed: ${download.episodeName}")
            .setContentText(download.error ?: "Unknown error")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setAutoCancel(true)
            .setContentIntent(tapIntent)
            .build()
        notificationManager.notify(notificationId, notification)
        Log.d(TAG, "postError: ${download.episodeName}")
    }

    fun postComplete(download: Download) {
        createChannels()
        val notificationId = Notifications.ID_DOWNLOAD_COMPLETE_BASE + download.id.hashCode()
        val tapIntent = PendingIntent.getActivity(
            context,
            download.id.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val content = buildString {
            append("${download.animeTitle} — ${download.episodeName}")
            if (download.actualResolution.isNotBlank()) {
                append(" (${download.actualResolution})")
            }
            if (download.actualDurationMs > 0) {
                val mins = download.actualDurationMs / 60000
                val secs = (download.actualDurationMs % 60000) / 1000
                append(" ${mins}:${if (secs < 10) "0" else ""}$secs")
            }
        }
        val notification = NotificationCompat.Builder(context, Notifications.CHANNEL_DOWNLOADER_COMPLETE)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download complete")
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setAutoCancel(true)
            .setContentIntent(tapIntent)
            .build()
        notificationManager.notify(notificationId, notification)
        Log.d(TAG, "postComplete: ${download.episodeName}")
    }
}

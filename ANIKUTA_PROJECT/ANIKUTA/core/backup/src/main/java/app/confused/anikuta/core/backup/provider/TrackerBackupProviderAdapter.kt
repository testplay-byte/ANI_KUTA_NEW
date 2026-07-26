package app.confused.anikuta.core.backup.provider

import android.util.Log
import app.confused.anikuta.core.backup.BackupCategory
import app.confused.anikuta.core.backup.BackupEntry
import app.confused.anikuta.core.backup.BackupProvider
import app.confused.anikuta.core.backup.model.TrackerBackupModel
import app.confused.anikuta.core.backup.model.TrackerTrackItem
import app.confused.anikuta.core.tracker.TrackerBackupProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "AnikutaBackup"

/**
 * Adapter that wraps [TrackerBackupProvider] (defined in `:core:tracker`) into
 * the [BackupProvider] contract.
 *
 * The actual tracker data reading/writing (OAuth tokens + animetrack bindings)
 * is implemented by `TrackerBackupProviderImpl` in `:core:tracker` — that module
 * owns the tracker data and knows the internal pref keys + DB queries. This
 * adapter just bridges the two interfaces.
 *
 * The [TrackerBackupData] → [TrackerBackupModel] conversion ensures the backup
 * file format is independent of the tracker module's internal model (so the
 * backup schema stays stable if the tracker model changes).
 */
class TrackerBackupProviderAdapter(
    private val trackerBackupProvider: TrackerBackupProvider,
) : BackupProvider {

    override val id: String = BackupCategory.TRACKER.id

    override suspend fun export(): BackupEntry = withContext(Dispatchers.IO) {
        try {
            val data = trackerBackupProvider.export()
            val model = TrackerBackupModel(
                anilistToken = data.anilistToken,
                anilistUsername = data.anilistUsername,
                anilistUserId = data.anilistUserId.toLong(),
                malOAuthJson = data.malOAuthJson,
                malUsername = data.malUsername,
                bindings = data.bindings.map { track ->
                    TrackerTrackItem(
                        animeId = track.animeId,
                        trackerId = track.trackerId,
                        remoteId = track.remoteId,
                        remoteUrl = track.remoteUrl,
                        lastSeen = track.lastSeen,
                        score = track.score,
                        status = track.status,
                        totalEpisodes = track.totalEpisodes,
                        displayScore = track.displayScore,
                    )
                },
            )
            Log.i(TAG, "Tracker export: ${model.bindings.size} bindings, anilist=${model.anilistToken.isNotEmpty()}, mal=${model.malOAuthJson != null}")
            BackupEntry.Tracker(data = model)
        } catch (e: Exception) {
            Log.e(TAG, "Tracker export failed", e)
            BackupEntry.Tracker()
        }
    }

    override suspend fun import(entry: BackupEntry): Boolean = withContext(Dispatchers.IO) {
        require(entry is BackupEntry.Tracker) { "Expected Tracker entry, got ${entry.providerId}" }
        try {
            val data = app.confused.anikuta.core.tracker.TrackerBackupData(
                anilistToken = entry.data.anilistToken,
                anilistUsername = entry.data.anilistUsername,
                anilistUserId = entry.data.anilistUserId.toInt(),
                malOAuthJson = entry.data.malOAuthJson,
                malUsername = entry.data.malUsername,
                bindings = entry.data.bindings.map { item ->
                    app.confused.anikuta.core.tracker.AnimeTrack(
                        id = -1,
                        animeId = item.animeId,
                        trackerId = item.trackerId,
                        remoteId = item.remoteId,
                        remoteUrl = item.remoteUrl,
                        lastSeen = item.lastSeen,
                        score = item.score,
                        status = item.status,
                        totalEpisodes = item.totalEpisodes,
                        displayScore = item.displayScore,
                    )
                },
            )
            trackerBackupProvider.restore(data)
            Log.i(TAG, "Tracker import: ${data.bindings.size} bindings restored")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Tracker import failed", e)
            false
        }
    }
}

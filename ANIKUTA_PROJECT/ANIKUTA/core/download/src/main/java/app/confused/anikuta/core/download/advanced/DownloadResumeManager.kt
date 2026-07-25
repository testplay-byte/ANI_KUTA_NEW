package app.confused.anikuta.core.download.advanced

import app.confused.anikuta.core.download.DownloadLogger
import app.confused.anikuta.core.download.TempDownloadCache
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Manages resume metadata for the Advanced download method.
 *
 * For each download task, writes a JSON file (`resume.json` in the task's temp
 * dir) describing the chunk layout + per-chunk progress. On restart/resume,
 * reads this file + checks the chunk files on disk to determine where to
 * resume each chunk.
 *
 * **Resume flow:**
 * 1. Before starting a download, [loadResume] checks if a `resume.json` exists
 *    for the task. If so, it validates the chunk files on disk (their sizes
 *    must match the recorded `downloaded` bytes) and returns the resume state.
 * 2. During download, [saveResume] is called periodically (throttled) to
 *    persist the latest per-chunk progress.
 * 3. On completion, [clearResume] deletes the metadata + chunk files.
 *
 * **Corruption handling:** if the metadata is missing/corrupt, or a chunk file
 * is missing/smaller than expected, the affected chunk restarts from scratch.
 * This is safe — the chunk file is overwritten from the beginning.
 *
 * @param tempCache The temp cache (provides the task directory).
 */
class DownloadResumeManager(
    private val tempCache: TempDownloadCache,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** The resume metadata for a single download task. */
    @Serializable
    data class ResumeMetadata(
        val taskId: Long,
        val videoUrl: String,
        val totalBytes: Long,
        val chunkCount: Int,
        val chunks: List<ChunkProgress>,
    )

    /** Per-chunk progress (start byte, end byte, downloaded bytes). */
    @Serializable
    data class ChunkProgress(
        val index: Int,
        val start: Long,
        val end: Long,
        val downloaded: Long,
    )

    /** The resume file for a task. */
    private fun resumeFile(taskId: Long): File =
        File(tempCache.taskDir(taskId), "resume.json")

    /** The chunk file for a specific chunk index. */
    fun chunkFile(taskId: Long, chunkIndex: Int): File =
        File(tempCache.taskDir(taskId), "chunk_$chunkIndex.part")

    /**
     * Loads the resume metadata for [taskId], or null if none exists.
     * Validates chunk files on disk — if a chunk file is missing or smaller
     * than the recorded `downloaded`, resets that chunk's progress to 0.
     */
    fun loadResume(taskId: Long): ResumeMetadata? {
        val file = resumeFile(taskId)
        if (!file.exists()) return null
        return try {
            val metadata = json.decodeFromString(ResumeMetadata.serializer(), file.readText())
            // Validate chunk files on disk.
            val validatedChunks = metadata.chunks.map { chunk ->
                val chunkFile = chunkFile(taskId, chunk.index)
                val actualSize = if (chunkFile.exists()) chunkFile.length() else 0L
                if (actualSize < chunk.downloaded) {
                    // Chunk file is smaller than recorded — reset.
                    DownloadLogger.w("Resume: chunk ${chunk.index} file is smaller ($actualSize < ${chunk.downloaded}), resetting")
                    chunk.copy(downloaded = actualSize)
                } else {
                    chunk
                }
            }
            metadata.copy(chunks = validatedChunks)
        } catch (e: Exception) {
            DownloadLogger.w("Resume: failed to load metadata, starting fresh", e)
            null
        }
    }

    /**
     * Saves the resume metadata for [taskId]. Called periodically during
     * download (throttled by the caller).
     */
    fun saveResume(metadata: ResumeMetadata) {
        try {
            val file = resumeFile(metadata.taskId)
            file.writeText(json.encodeToString(ResumeMetadata.serializer(), metadata))
        } catch (e: Exception) {
            DownloadLogger.w("Resume: failed to save metadata (non-fatal)", e)
        }
    }

    /** Clears the resume metadata + all chunk files for [taskId]. */
    fun clearResume(taskId: Long) {
        try {
            resumeFile(taskId).delete()
            // Chunk files are cleaned up by TempDownloadCache.cleanupTask()
            // which deletes the entire task directory.
        } catch (e: Exception) {
            DownloadLogger.w("Resume: failed to clear metadata (non-fatal)", e)
        }
    }
}

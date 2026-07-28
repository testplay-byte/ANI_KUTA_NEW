package app.confused.anikuta.core.backup

import app.confused.anikuta.core.backup.model.BackupContainer
import java.io.InputStream
import java.io.OutputStream

/**
 * Contract for a backup file format (ANIKUTA zip, Aniyomi protobuf, etc.).
 *
 * A format knows how to:
 * - [write] a [BackupContainer] (+ optional cover images) to an [OutputStream].
 * - [read] a [BackupContainer] from an [InputStream].
 * - [detect] whether a given stream is in this format.
 *
 * Formats are pluggable — new formats can be added by implementing this
 * interface and registering in [BackupModule]. The [BackupManager] tries
 * each registered format's [detect] in order until one matches.
 *
 * All methods MUST be safe to call on [Dispatchers.IO].
 */
interface BackupFormat {

    /** The format type this implementation handles. */
    val type: BackupFormatType

    /**
     * Write a backup container to the output stream.
     *
     * @param container the fully-populated backup data.
     * @param covers optional cover images (anilistId → image bytes). May be empty.
     * @param output the destination stream (caller is responsible for closing).
     */
    suspend fun write(container: BackupContainer, covers: Map<Int, ByteArray>, output: OutputStream)

    /**
     * Read a backup container from the input stream.
     *
     * @param input the source stream (caller is responsible for closing).
     * @return the parsed container.
     * @throws BackupException if the stream is not a valid backup of this format.
     */
    suspend fun read(input: InputStream): BackupContainer

    /**
     * Read cover images from the backup (if the format supports bundled covers).
     *
     * @param input the source stream (caller is responsible for closing).
     * @return map of anilistId → image bytes. Empty if no covers or unsupported.
     */
    suspend fun readCovers(input: InputStream): Map<Int, ByteArray> = emptyMap()

    /**
     * Detect whether the input stream is in this format.
     *
     * Implementations should peek the first few bytes without consuming the
     * stream (use [InputStream.mark] / [InputStream.reset] or a buffered peek).
     *
     * @return true if this format can parse the stream.
     */
    fun detect(input: InputStream): Boolean
}

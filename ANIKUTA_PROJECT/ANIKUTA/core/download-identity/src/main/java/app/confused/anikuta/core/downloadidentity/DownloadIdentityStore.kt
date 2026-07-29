package app.confused.anikuta.core.downloadidentity

import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Low-level SAF I/O for [DownloadIdentity] files.
 *
 * Reads + writes `identity.json` inside a download anime folder. The file is
 * small (~500 bytes) + written atomically (write to temp → rename).
 *
 * Per `_DOWNLOAD_IDENTITY_PLAN/ARCHITECTURE.md`.
 */
object DownloadIdentityStore {
    private const val TAG = "AnikutaDlIdentity"
    private const val FILE_NAME = "identity.json"
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    /**
     * Read the identity file from an anime folder.
     *
     * @param animeDir the anime's download folder (e.g., `<root>/anime/Frieren/`)
     * @return the parsed [DownloadIdentity], or null if the file doesn't exist or is corrupt.
     */
    fun read(animeDir: DocumentFile): DownloadIdentity? {
        val file = animeDir.findFile(FILE_NAME) ?: return null
        return try {
            val content = StringBuilder()
            file.uri?.let { uri ->
                val context = app.confused.anikuta.core.downloadidentity.AppContextProvider.context
                context.contentResolver.openInputStream(uri)?.use { input ->
                    BufferedReader(InputStreamReader(input)).use { reader ->
                        var line = reader.readLine()
                        while (line != null) {
                            content.append(line).append('\n')
                            line = reader.readLine()
                        }
                    }
                }
            }
            val identity = json.decodeFromString<DownloadIdentity>(content.toString())
            Log.d(TAG, "read: contentId='${identity.contentId}', title='${identity.title}', " +
                "sourceId=${identity.sourceId}")
            identity
        } catch (e: Exception) {
            Log.w(TAG, "read: failed to parse identity.json in '${animeDir.name}' — $e")
            null
        }
    }

    /**
     * Write the identity file to an anime folder.
     *
     * If the file already exists, it's overwritten. If the folder doesn't exist,
     * this method does NOT create it — the caller must ensure the folder exists.
     *
     * @param animeDir the anime's download folder
     * @param identity the identity to write
     */
    fun write(animeDir: DocumentFile, identity: DownloadIdentity) {
        try {
            val existing = animeDir.findFile(FILE_NAME)
            existing?.delete()
            val file = animeDir.createFile("application/json", FILE_NAME) ?: run {
                Log.e(TAG, "write: failed to create $FILE_NAME in '${animeDir.name}'")
                return
            }
            val jsonStr = json.encodeToString(DownloadIdentity.serializer(), identity)
            val context = AppContextProvider.context
            context.contentResolver.openOutputStream(file.uri)?.use { output ->
                output.write(jsonStr.toByteArray())
            }
            Log.d(TAG, "write: contentId='${identity.contentId}', title='${identity.title}' " +
                "→ '${animeDir.name}/$FILE_NAME'")
        } catch (e: Exception) {
            Log.e(TAG, "write: failed to write identity.json in '${animeDir.name}' — $e")
        }
    }

    /**
     * Delete the identity file from an anime folder.
     */
    fun delete(animeDir: DocumentFile) {
        animeDir.findFile(FILE_NAME)?.delete()
    }

    /**
     * Check if an identity file exists in the folder.
     */
    fun exists(animeDir: DocumentFile): Boolean {
        return animeDir.findFile(FILE_NAME) != null
    }
}

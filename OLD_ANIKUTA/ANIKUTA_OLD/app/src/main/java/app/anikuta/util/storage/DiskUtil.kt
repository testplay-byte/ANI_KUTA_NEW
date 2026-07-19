package app.anikuta.util.storage

/**
 * Ported from aniyomi's DiskUtil. Sanitizes filenames for FAT/exFAT/ext4
 * compatibility (used for download directory names).
 */
object DiskUtil {

    /**
     * Builds a valid filename from the given string, replacing invalid characters
     * with underscores and truncating to 240 characters.
     */
    fun buildValidFilename(origName: String): String {
        val name = origName.trim('.', ' ')
        if (name.isEmpty()) {
            return "(invalid)"
        }
        val sb = StringBuilder(name.length)
        name.forEach { c ->
            if (isValidFatFilenameChar(c)) {
                sb.append(c)
            } else {
                sb.append('_')
            }
        }
        return sb.toString().take(240)
    }

    private fun isValidFatFilenameChar(c: Char): Boolean {
        if (0x00.toChar() <= c && c <= 0x1f.toChar()) {
            return false
        }
        return when (c) {
            '"', '*', '/', ':', '<', '>', '?', '\\', '|', 0x7f.toChar() -> false
            else -> true
        }
    }

    const val NOMEDIA_FILE = ".nomedia"
}

package app.confused.anikuta.core.backup

/**
 * Controls what gets included in a backup operation.
 *
 * @param categories the set of [BackupCategory.id]s to include.
 * @param format the output format ([BackupFormatType]).
 */
data class BackupOptions(
    val categories: Set<String> = BackupCategory.defaultSelection,
    val format: BackupFormatType = BackupFormatType.ANIKUTA,
) {
    /** Returns true if the given category id is selected. */
    fun includes(category: BackupCategory): Boolean = category.id in categories

    /** Returns true if the given provider id is selected. */
    fun includes(providerId: String): Boolean = providerId in categories

    companion object {
        /** Default options: all default-selected categories, ANIKUTA format. */
        val DEFAULT = BackupOptions()
    }
}

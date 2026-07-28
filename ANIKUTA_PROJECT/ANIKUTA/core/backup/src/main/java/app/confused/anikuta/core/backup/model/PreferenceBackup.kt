package app.confused.anikuta.core.backup.model

import kotlinx.serialization.Serializable

/**
 * Serializable wrapper for all app preferences.
 *
 * `PreferenceStore.getAll()` returns `Map<String, *>` with values of type
 * String, Int, Long, Float, Boolean, or Set<String>. We wrap each value in a
 * [PrefValue] sealed class so kotlinx-serialization can handle the
 * polymorphism cleanly.
 *
 * On restore, the [PreferencesBackupProvider] writes each value back via the
 * appropriate `PreferenceStore` setter based on its runtime type.
 */
@Serializable
data class PreferenceBackup(
    val entries: Map<String, PrefValue> = emptyMap(),
)

/**
 * A single preference value — sealed for type-safe polymorphic serialization.
 *
 * - [Str] — String values
 * - [Num] — Int and Long values (stored as Long for uniformity)
 * - [Dec] — Float values
 * - [Bool] — Boolean values
 * - [StrSet] — Set<String> values (used for things like hidden genres)
 */
@Serializable
sealed class PrefValue {
    @Serializable
    data class Str(val value: String) : PrefValue()

    @Serializable
    data class Num(val value: Long) : PrefValue()

    @Serializable
    data class Dec(val value: Float) : PrefValue()

    @Serializable
    data class Bool(val value: Boolean) : PrefValue()

    @Serializable
    data class StrSet(val value: Set<String>) : PrefValue()
}

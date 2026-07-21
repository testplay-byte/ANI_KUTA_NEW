package app.confused.anikuta.core.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.Flow

class AndroidPreferenceStore(
    context: Context,
    private val sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context),
) : PreferenceStore {

    private val keyFlow: Flow<String?> = sharedPreferences.keyFlow

    override fun getString(key: String, defaultValue: String): Preference<String> =
        AndroidPreference.StringPrimitive(sharedPreferences, keyFlow, key, defaultValue)

    override fun getLong(key: String, defaultValue: Long): Preference<Long> =
        AndroidPreference.LongPrimitive(sharedPreferences, keyFlow, key, defaultValue)

    override fun getInt(key: String, defaultValue: Int): Preference<Int> =
        AndroidPreference.IntPrimitive(sharedPreferences, keyFlow, key, defaultValue)

    override fun getFloat(key: String, defaultValue: Float): Preference<Float> =
        AndroidPreference.FloatPrimitive(sharedPreferences, keyFlow, key, defaultValue)

    override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> =
        AndroidPreference.BooleanPrimitive(sharedPreferences, keyFlow, key, defaultValue)

    override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> =
        AndroidPreference.StringSetPrimitive(sharedPreferences, keyFlow, key, defaultValue)

    override fun <T> getObject(
        key: String,
        defaultValue: T,
        serializer: (T) -> String,
        deserializer: (String) -> T,
    ): Preference<T> = AndroidPreference.Object(
        preferences = sharedPreferences,
        keyFlow = keyFlow,
        key = key,
        defaultValue = defaultValue,
        serializer = serializer,
        deserializer = deserializer,
    )

    override fun getAll(): Map<String, *> = sharedPreferences.all ?: emptyMap<String, Any>()
}

private val SharedPreferences.keyFlow: Flow<String?>
    get() = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key: String? ->
            trySend(key)
        }
        registerOnSharedPreferenceChangeListener(listener)
        awaitClose { unregisterOnSharedPreferenceChangeListener(listener) }
    }

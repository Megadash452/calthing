package me.marti.calprovexample

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit

fun Context.getAppPreferences(): SharedPreferences {
    return this.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
}

enum class PreferenceKey {
    SYNC_DIR_URI,
    FRAGMENT_CALS,
}

/** A generic *key-value pair* stored in disk using *`SharedPreferences`*.
 *
 * User ***MUST*** call **`initStore()`**, otherwise the value will not be saved to storage.
 *
 * The API for this class is meant to be similar to that of `MutableState<T>`.
 *
 * @property key The name of the preference as a string.
 * @property value The current value of the internal state.
 * Like in **`MutableState<T>`**, *reading* the value in a **`@Composable`** will subscribe to *writes* and trigger recomposition.
 * *Setting* the value wll save it to the *`SharedPreferences`* storage.
 * @see StringUserPreference
 * @see StringLikeUserPreference
 * @see IntUserPreference
 * @see BooleanUserPreference */
abstract class UserPreference<T>(
    protected val key: PreferenceKey,
) {
    protected open val state: MutableState<T?> = mutableStateOf(null)
    /** Used to access the preference. Has to be initialized when Activity.onCreate() is called. */
    protected var preferences: SharedPreferences? = null

    /** Load the *value* stored by *`SharedPreferences`* into the internal state.
     *
     * This is called after the preference is initialized in **`initStore`**
     * and **should not** be used anywhere else.
     *
     * Implementations should look something like this:
     * ```kt
     * override fun loadValue() {
     *     this.preferences?.getString(this.key.name, null)?.let { pref ->
     *         this.state.value = pref
     *     }
     * }
     * ``` */
    protected abstract fun loadValue()
    /** Save the internal *value* and store it with *`SharedPreferences`*.
     *
     * This is called before the value of **state** is written to
     * and **should not** be used anywhere else.
     *
     * Implementations should look something like this:
     * ```kt
     * override fun storeValue(value: T) {
     *     this.preferences?.edit {
     *         this.putString(key.name, value)
     *     }
     * }
     * ``` */
    protected abstract fun storeValue(value: T)

    /** Initialize the **`preferences`** to enable reading and writing to the storage.  */
    fun initStore(preferences: SharedPreferences) {
        if (this.preferences == null) {
            this.preferences = preferences
            // Load the value of the preference "key" only if it exists. Otherwise the state will stay null.
            // As a result, the default value passed into `preferences.get*` is never used.
            if (preferences.contains(this.key.name))
                this.loadValue()
        }
    }

    var value: T?
        get() = this.state.value
        set(value) {
            if (value == null)
                this.preferences?.edit {
                    this.remove(this@UserPreference.key.name)
                }
            else
                this.storeValue(value)

            this.state.value = value
        }
}

/** An user preference that is a Set of `T`, where `T` is serialized to a string when stored in *SharedPreferences*.
 *
 * Must call **`initStore()`**, otherwise the value will not be saved to storage.
 *
 * @param key The name of the preference as a string.
 * @param fromString A function applied to every stored String to convert it to a `T`.
 * */
class SetUserPreference<T>(
    private val key: PreferenceKey,
    private val fromString: (String) -> T
) {
    // Using mutableStateMapOf since the Set variant does not exist.
    private val state = mutableStateMapOf<T, Unit>()
    /** Used to access the preference. Has to be initialized when Activity.onCreate() is called. */
    private var preferences: SharedPreferences? = null

    /** Save the internal *state* and store it with *`SharedPreferences`*. */
    private fun storeState() {
        this.preferences?.edit {
            this.putStringSet(
                this@SetUserPreference.key.name,
                buildSet {
                    this.addAll(this@SetUserPreference.state.toList().map { pair -> pair.first.toString() })
                }
            )
        }
    }

    /** Initialize the **`preferences`** to enable reading and writing to the storage.  */
    fun initStore(preferences: SharedPreferences) {
        if (this.preferences == null) {
            this.preferences = preferences
            // Load the value of the preference "key" only if it exists. Otherwise the state will stay null.
            // As a result, the default value passed into `preferences.getStringSet()` is never used.
            if (preferences.contains(this.key.name)) {
                // Load the *value* stored by *`SharedPreferences`* into the internal state.
                this.preferences?.getStringSet(this.key.name, null)?.let { pref ->
                    pref.map { this.fromString(it) }.forEach {
                        this.state[it] = Unit
                    }
                }
            }
        }
    }

    fun getSet(): Set<T> {
        return buildSet {
            this.addAll(this@SetUserPreference.state.toList().map { pair -> pair.first })
        }
    }

    fun contains(element: T): Boolean {
        return this.state.containsKey(element)
    }
    fun add(element: T) {
        this.state[element] = Unit
        this.storeState()
    }
    fun remove(element: T) {
        this.state.remove(element)
        this.storeState()
    }
}

/** An user preference of a defined type `T` that is stored as a string in *SharedPreferences*.
 *
 * Must call **`initStore()`**, otherwise the value will not be saved to storage.
 *
 * @see UserPreference
 * @param key The name of the preference as a string.
 * @param fromString A simple function to convert the saved value (string) to `T` to load it to the internal state.
 * */
class StringLikeUserPreference<T>(
    key: PreferenceKey,
    private val fromString: (String) -> T
): UserPreference<T>(key) {
    override fun loadValue() {
        this.preferences?.getString(this.key.name, null)?.let { pref ->
            this.state.value = this.fromString(pref)
        }
    }

    override fun storeValue(value: T) {
        this.preferences?.edit {
            this.putString(this@StringLikeUserPreference.key.name, value.toString())
        }
    }
}

/** Store a *`String`* in user preferences.
 *
 * Must call **`initStore()`**, otherwise the value will not be saved to storage.
 *
 * @see UserPreference
 * @param key The name of the preference as a string.
 * */
class StringUserPreference(
    key: PreferenceKey
) : UserPreference<String>(key) {
    override fun loadValue() {
        this.preferences?.getString(this.key.name, null)?.let { pref ->
            this.state.value = pref
        }
    }
    override fun storeValue(value: String) {
        this.preferences?.edit {
            this.putString(this@StringUserPreference.key.name, value)
        }
    }
}

/** Store a *`Boolean`* in user preferences.
 *
 * Must call **`initStore()`**, otherwise the value will not be saved to storage.
 *
 * @see UserPreference
 * @param key The name of the preference as a string.
 * */
class BooleanUserPreference(
    key: PreferenceKey
) : UserPreference<Boolean>(key) {
    override fun loadValue() {
        this.preferences?.getBoolean(this.key.name, false)?.let { pref ->
            this.state.value = pref
        }
    }
    override fun storeValue(value: Boolean) {
        this.preferences?.edit {
            this.putBoolean(this@BooleanUserPreference.key.name, value)
        }
    }
}

/** Store a *`Integer`* in user preferences.
 *
 * Must call **`initStore()`**, otherwise the value will not be saved to storage.
 *
 * @see UserPreference
 * @param key The name of the preference as a string.
 * */
class IntUserPreference(
    key: PreferenceKey
) : UserPreference<Int>(key) {
    override fun loadValue() {
        this.preferences?.getInt(this.key.name, 0)?.let { pref ->
            this.state.value = pref
        }
    }
    override fun storeValue(value: Int) {
        this.preferences?.edit {
            this.putInt(this@IntUserPreference.key.name, value)
        }
    }
}

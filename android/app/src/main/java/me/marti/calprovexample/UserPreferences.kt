package me.marti.calprovexample

import android.content.SharedPreferences
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/** An user preference of a defined type `T` that is stored as a string in SharedPreferences.
 * Must call **`initStore()`**, otherwise the value will not be saved to storage.
 *
 * An object of this class *SHOULD* be stored as a property of the MainActivity Class
 * so that it can be used by its callbacks.
 *
 * The API for this class is similar to that of `MutableState<T>`.
 *
 * @param key The name of the preference as a string.
 * @param fromString A simple function to convert the saved value (string) to `T` to load it to the internal state.
 * */
class UserStringPreference<T>(
    private val key: String,
    private val fromString: (String) -> T
) {
    private val state: MutableState<T?> = mutableStateOf(null)
    /** Used to access the preference. Has to be initialized when Activity.onCreate() is called. */
    private var preferences: SharedPreferences? = null

    fun initStore(preferences: SharedPreferences) {
        this.preferences = preferences
        // Attempts to read the value stored on startup.
        val pref = preferences.getString(this.key, null)
        this.state.value = if (pref == null) null else {
            this.fromString(pref)
        }
    }

    var value: T?
        get() = this.state.value
        set(value) {
            val preferences = this.preferences
            if (preferences != null) {
                with(preferences.edit()) {
                    this.putString(this@UserStringPreference.key, value.toString())
                    this.apply()
                }
            }
            this.state.value = value
        }
}

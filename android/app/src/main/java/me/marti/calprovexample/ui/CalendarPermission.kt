package me.marti.calprovexample.ui

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.channels.Channel
import me.marti.calprovexample.R
import me.marti.calprovexample.launch
import java.lang.ref.WeakReference

private val PERMISSIONS = arrayOf("android.permission.READ_CALENDAR", "android.permission.WRITE_CALENDAR")

/** Check the results of launching the permission `requestLauncher`.
 * @return Whether all Permissions were **Granted**. */
private fun checkResults(results: Map<String, Boolean>): Boolean = results.all { (_, perm) -> perm }

/** The permission object that is used ot interact with the Content Provider. */
class CalendarPermissionScope internal constructor(val context: Context)

/** Base class for [CalendarPermission].
 *
 * Instances of this class MUST be initialized *before* the `Activity.onCreate()` method.
 *
 * Functions that require the permissions must be written as *Extension functions* that take [CalendarPermissionScope] as their receiver.
 * The only way to obtain the **`Scope`** is if the permission is *granted*,
 * so writing functions this way ensures they can only be called if the permission is granted.
 * The **`Scope`** contains the activity's `Context` for convenience.
 *
 * ## Rationale Dialog
 *
 * The system sometimes determines that the Activity should show a dialog explaining to the user
 * why the permissions being requested are needed.
 *
 * The **Rationale dialog** should be included in any activity that makes use of this class.
 * If the dialog is not included in the UI, and the system determines that the dialog should be shown,
 * the permissions will not be requested and the *UI flow will be blocked*.
 *
 * ## Example
 * ```kt
 * fun CalendarPermissionScope.getCalendarData() {
 *     ...
 * }
 *
 * class MyActivity : ComponentActivity() {
 *     private val calendarPermission = CalendarPermission(this)
 *
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         ...
 *         this.setContent {
 *              MyTheme {
 *                  ...
 *                  this.calendarPermission.RationaleDialog()
 *              }
 *         }
 *     }
 *
 *     private fun doCalendarThing(arg: String) {
 *         this.calendarPermission.launch {
 *             this.getCalendarData(arg)
 *             ...
 *         }
 *     }
 * }
 * ```
 * @param onPermGranted A function that always runs when the user Allows the permissions, regardless of the action being run.
 * @see CalendarPermission */
abstract class Permission(protected val activity: MainActivity, private val onPermGranted: (() -> Unit)?) {
    /** Use [MainActivity.registerForActivityResult] with [ActivityResultContracts.RequestMultiplePermissions]. */
    internal val requestLauncher: ActivityResultLauncher<Array<String>> = this.activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (checkResults(results))
            this.onPermGranted?.invoke()
        this.onPermRequested(results)
    }
    internal val dsl by lazy { CalendarPermissionScope(this.activity.baseContext) }

    /** The function that will be called when [requestLauncher] is launched.
     * Handles the allowing or denial of the permissions requested. */
    internal abstract fun onPermRequested(results: Map<String, Boolean>)

    /** Check if the app has the runtime permission to *read/write* device calendar. */
    fun hasPermission(): Boolean {
        return PERMISSIONS.all { perm ->
            this.activity.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED
        }
    }
    protected fun shouldShowDialog(): Boolean {
        return PERMISSIONS.all { perm ->
            this.activity.shouldShowRequestPermissionRationale(perm)
        }
    }
    protected fun showDeniedToast() = this.activity.showToast(activity.baseContext.getString(R.string.cal_perm_denied))
}

/** A multithreaded implementation of [Permission].
 *
 * Because most of these actions are expected to block the thread they run on,
 * this object crates a *new thread* to run all the actions so that the UI thread can keep running.
 *
 * ### Rationale Dialog
 *
 * This implementation uses [AsyncDialog] and [SuspendDialog] for the **Rationale Dialog**,
 * so ensure those are included in the composition.
 *
 * ## Bugs
 *
 * * Don't try using captured variables as *pseudo return values*.
 *   Here, `a` will always print null (except when it doesn't).
 * ```kt
 * fun doCalendarThing(arg: String) {
 *     var a = null
 *     this.calendarPermission.run {
 *         this.getCalendarData(arg)
 *         a = "Hi!!!"
 *         ...
 *     }
 *     println(a) // null
 * }
 * ```
 *
 * Doing that will not work, unless it is in a *[Composable] context*.
 * When the inner function returns (could be instantly or in a *few seconds*),
 * setting the value of `a` will trigger *recomposition* and render the text.
 * ```kt
 * @Composable
 * fun doCalendarThing(arg: String) {
 *     var a by remember { mutableStateOf(null) }
 *     this.calendarPermission.run {
 *         this.getCalendarData(arg)
 *         a = "Hi!!!"
 *         ...
 *     }
 *     if (a != null) Text(a) // Hi!!!
 * }
 * ```
 */
class CalendarPermission(
    activity: MainActivity,
    onPermGranted: (() -> Unit)?
): Permission(activity, onPermGranted) {
    private val channel = Channel<Boolean>()

    override fun onPermRequested(results: Map<String, Boolean>) {
        this.channel.trySend(checkResults(results))
    }

    /** Run the **action** by launching it in a *worker thread*,
     * only if the Activity has the required permissions.
     * Otherwise, an [Exception] will be thrown.
     *
     * Shows a [Dialog][SuspendDialog] while **action** runs if a **message** is passed.
     * @see launch */
    fun launchOrFail(msg: String? = null, action: suspend CalendarPermissionScope.() -> Unit) {
        if (this.hasPermission()) {
            if (msg == null)
                this.activity.calendarWorkThread.launch { action(this@CalendarPermission.dsl) }
            else
                this.activity.calendarWorkThread.launch(msg) { action(this@CalendarPermission.dsl) }
        } else
            throw Exception("Missing permissions to run action $action.")
    }

    /** Run a function that requires *Calendar Permissions* by launching it in a *worker thread*.
     *
     * If permission has not yet been granted, it will be requested, and then the **action** will be run.
     *
     * Shows a [Dialog][SuspendDialog] while **action** runs if a **message** is passed. */
    fun launch(msg: String? = null, action: suspend CalendarPermissionScope.() -> Unit) {
        this.activity.calendarWorkThread.launch {
            // TODO: Calling run is a workaround for now; should be independent from run
            this@CalendarPermission.run(msg, action)
        }
    }

    /** Run the **action** only if the Activity has the required permissions.
     * Otherwise, an [Exception] will be thrown.
     *
     * Otherwise, use [CalendarPermission.launchOrFail]. */
    suspend fun <T> runOrFail(action: suspend CalendarPermissionScope.() -> T): T {
        return if (this.hasPermission())
            action(this.dsl)
        else
            throw Exception("Missing permissions to run action $action.")
    }

    /** Run a function that requires *Calendar Permissions* in a suspend context,
     * waiting for the action to finish running and returning a value (`T`).
     *
     * If permission has not yet been granted, it will be requested, and then the **action** will be run.
     *
     * Shows a [Dialog][SuspendDialog] while **action** runs if a **message** is passed.
     * @return **`NULL`** if permissions were denied. */
    suspend fun <T> run(msg: String? = null, action: suspend CalendarPermissionScope.() -> T): T? {
        @Suppress("NAME_SHADOWING")
        val action = msg?.let { msg -> {
            SuspendDialog.show(msg)
            val result = action(this)
            SuspendDialog.close()
            result
        }} ?: action

        return if (this.hasPermission())
            action(this.dsl)
        else {
            if (this.shouldShowDialog()) {
                var response = false
                AsyncDialog.showDialog { close ->
                    CalendarRationaleDialog(
                        dismiss = { close() },
                        confirm = { response = true; close() }
                    )
                }

                if (!response)
                    return null
                // Proceed to request permission when user presses Allow in the Rationale Dialog
            }
            if (this.requestPermissionsAsync())
                action(this.dsl)
            else
                null
        }
    }

    /** Requests the permission and waits for it to be granted.
     * @return **`NULL`** if the permission was denied. */
    @Suppress("RedundantVisibilityModifier", "unused") // Used by Rust
    public suspend fun waitForPermission(): CalendarPermissionScope? {
        // Should use rationale dialog?
        return if (this.requestPermissionsAsync()) {
            this.dsl
        } else {
            null
        }
    }

    /** Gives access to the currently granted [permission][CalendarPermissionScope] (if any).
     * @return **`NULL`** if the app doesn't have the permission. */
    @Suppress("RedundantVisibilityModifier", "unused") // Used by Rust
    public fun usePermission(): CalendarPermissionScope? {
        return if (this.hasPermission())
            this.dsl
        else
            null
    }

    /** Returns whether the permissions have been granted and it is safe to proceed. */
    private suspend fun requestPermissionsAsync(): Boolean {
        Log.i("CalendarPermission", "Requesting Calendar permissions (async)...")
        this.requestLauncher.launch(PERMISSIONS)
        val result = this.channel.receive()
        if (!result) this.showDeniedToast()
        return result
    }
}

@Composable
fun CalendarRationaleDialog(dismiss: () -> Unit = {}, confirm: () -> Unit = {}) {
    AlertDialog(
        icon = { Icon(
            painterResource(R.drawable.baseline_edit_calendar_24),
            null,
            modifier = Modifier.size(36.dp),
        ) },
        title = { Text(stringResource(R.string.cal_perm_rationale_title)) },
        text = {
            Text(
                stringResource(R.string.cal_perm_rationale_body, stringResource(R.string.app_name))
            )
        },
        onDismissRequest = dismiss,
        confirmButton = {
            TextButton(onClick = confirm) {
                Text(stringResource(R.string.cal_perm_rationale_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = dismiss) {
                Text(stringResource(R.string.cal_perm_rationale_dismiss))
            }
        }
    )
}

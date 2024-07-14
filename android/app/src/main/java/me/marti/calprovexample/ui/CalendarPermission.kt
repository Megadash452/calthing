package me.marti.calprovexample.ui

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import me.marti.calprovexample.R

private val PERMISSIONS = arrayOf("android.permission.READ_CALENDAR", "android.permission.WRITE_CALENDAR")

/** Check the results of launching the permission `requestLauncher`.
 * @return Whether all Permissions were **Granted**. */
private fun checkResults(results: Map<String, Boolean>): Boolean = results.all { (_, perm) -> perm }

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
 * fun CalendarPermission.Dsl.getCalendarData() {
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
 *         this.calendarPermission.run {
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

    /** Run the **action** only if the Activity has the required permissions.
     * Otherwise, an [Exception] will be thrown.
     *
     * Should ONLY be used if already calling from a [worker thread][MainActivity.calendarWorkThread].
     * Otherwise, use [CalendarPermission.launchOrFail]. */
    fun <T> runOrFail(action: CalendarPermissionScope.() -> T): T {
        return if (this.hasPermission())
            action(this.dsl)
        else
            throw Exception("Missing permissions to run action $action.")
    }

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
    protected fun showDeniedToast() {} /* = this.activity.showToast(R.string.cal_perm_denied)*/

    // /** A **Dialog** explaining why the permissions are needed.
    //  *
    //  * Must be included in the **Activity**'s composition for it to be rendered. */
    // @Composable
    // abstract fun RationaleDialog()

    protected var suspendMessage: String? by mutableStateOf(null)
    /** Shows a Dialog with a [Spinner][CircularProgressIndicator] and a **message**.
     *
     * Like with the [RationaleDialog], the [SuspendDialog] must be included in the **Activity**'s composition for it to be rendered. */
    @Composable
    fun SuspendDialog() {
        this.suspendMessage?.let { text -> CalendarSuspendDialog(text = text) }
    }
}

/** A multithreaded implementation of [Permission].
 *
 * Because most of these actions are expected to block the thread they run on,
 * this object crates a *new thread* to run all the actions so that the UI thread can keep running.
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
    // /** System Calendar operations can block the main thread, so delegate them to another thread.
    //  *  Use **`calendarsThread.execute`** inside a *`calendarPermission.run`* block.
    //  *
    //  *  Using *Worker threads* instead of `AsyncTask` and `Loader`s because I understand it better.*/
    // private val workThread = Executors.newSingleThreadExecutor()
    //
    // private var currentAction: (CalendarPermissionScope.() -> Unit)? = null

    private val channel = Channel<Boolean>()

    override fun onPermRequested(results: Map<String, Boolean>) {
        this.channel.trySend(checkResults(results))
        // if (checkResults(results))
        //     currentAction?.let { action ->
        //         this.workThread.execute { runBlocking { action(this@CalendarPermission.dsl) } }
        //     } ?: run {
        //         throw Exception("CalendarPermission.currentAction was not set before requesting permission")
        //     }
        // else
        //     this.showDeniedToast()
    }

    /** Run the **action** by launching it in a *worker thread*,
     * only if the Activity has the required permissions.
     * Otherwise, an [Exception] will be thrown.
     * @see launch */
    fun launchOrFail(action: suspend CalendarPermissionScope.() -> Unit) {
        return if (this.hasPermission())
            this.activity.calendarWorkThread.execute { runBlocking { action(this@CalendarPermission.dsl) } }
        else
            throw Exception("Missing permissions to run action $action.")
    }

    /** Run a function that requires *Calendar Permissions* by launching it in a *worker thread*.
     * If permission has not yet been granted, it will be requested, and then the **action** will be run. */
    fun launch(action: suspend CalendarPermissionScope.() -> Unit) {
        // TODO: Calling run is a workaround for now; should be independent from run
        this.activity.calendarWorkThread.execute { runBlocking { this@CalendarPermission.run(action) } }
    }

    /** Calls [launch] with **action** and will also show a [Dialog][SuspendDialog] with a **message** while **action** runs. */
    fun launchWithMessage(msg: String, action: suspend CalendarPermissionScope.() -> Unit) {
        this.launch {
            this@CalendarPermission.suspendMessage = msg
            action(this)
            this@CalendarPermission.suspendMessage = null
        }
    }

    /** Run a function that requires *Calendar Permissions* in a suspend context,
     * waiting for the action to finish running and returning a value (`T`).
     *
     * If permission has not yet been granted, it will be requested, and then the **action** will be run.
     *
     * @return **`NULL`** if permissions were denied. */
    suspend fun <T> run(action: suspend CalendarPermissionScope.() -> T): T? {
        return if (this.hasPermission())
            action(this.dsl)
        else {
            if (this.shouldShowDialog()) {
                var response = false
                AsyncDialog.showDialog { close ->
                    PermissionRationaleDialog(
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

    /** Calls [run] with **action** and will also show a [Dialog][SuspendDialog] with a **message** while **action** runs. */
    suspend fun <T> runWithMessage(msg: String, action: suspend CalendarPermissionScope.() -> T): T? {
        return this.run {
            this@CalendarPermission.suspendMessage = msg
            val result = action(this)
            this@CalendarPermission.suspendMessage = null
            result
        }
    }

    // private fun requestPermissions() {
    //     Log.i("CalendarPermission", "Requesting Calendar permissions...")
    //     this.requestLauncher.launch(PERMISSIONS)
    // }
    /** Returns whether the permissions have been granted and it is safe to proceed. */
    private suspend fun requestPermissionsAsync(): Boolean {
        Log.i("CalendarPermission", "Requesting Calendar permissions (async)...")
        this.requestLauncher.launch(PERMISSIONS)
        val result = this.channel.receive()
        if (!result) this.showDeniedToast()
        return result
    }

    // private var dialogController by mutableStateOf(false)
    // @Composable
    // override fun RationaleDialog() {
    //     if (this.dialogController) {
    //         CalendarRationaleDialog(
    //             dismiss = {
    //                 this.dialogController = false
    //                 Log.d(null, "Calendar Permission Rationale Dialog dismissed")
    //             },
    //             confirm = {
    //                 this.dialogController = false
    //                 requestPermissions()
    //             }
    //         )
    //     }
    // }
}

// Keep it here to show the preview
@Composable
fun CalendarSuspendDialog(text: String, modifier: Modifier = Modifier) {
    // Set dismissOnBackPress to false so that the dialog doesn't eat the BackButton event
    Dialog({}) {
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.large,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    strokeCap = StrokeCap.Round,
                    strokeWidth = 5.dp
                )
                Spacer(Modifier.width(12.dp))
                Text(text)
            }
        }
    }
}
@Composable
fun PermissionRationaleDialog(dismiss: () -> Unit = {}, confirm: () -> Unit = {}) {
    AlertDialog(
        icon = {
            Icon(
                painterResource(R.drawable.baseline_edit_calendar_24),
                null,
                modifier = Modifier.size(36.dp),
            )
        },
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

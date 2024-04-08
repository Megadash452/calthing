package me.marti.calprovexample.ui

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
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
import me.marti.calprovexample.R
import java.util.concurrent.Executors

private val PERMISSIONS = arrayOf("android.permission.READ_CALENDAR", "android.permission.WRITE_CALENDAR")

/** Check the results of launching the permission `requestLauncher`.
 * @return Whether all Permissions were **Granted**. */
private fun checkResults(results: Map<String, Boolean>): Boolean = results.all { (_, perm) -> perm }

class CalendarPermissionScope internal constructor(val context: Context)

/** Base class for [CalendarPermission] and [AsyncCalendarPermission].
 *
 * Instances of this class MUST be initialized *before* the `Activity.onCreate()` method.
 *
 * Functions that require the permissions must written as *Extension functions* that take [CalendarPermissionScope] as their receiver.
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
 * @see CalendarPermission
 * @see AsyncCalendarPermission */
abstract class Permission(internal val activity: MainActivity) {
    /** Use [MainActivity.registerForActivityResult] with [ActivityResultContracts.RequestMultiplePermissions]. */
    internal abstract val requestLauncher: ActivityResultLauncher<Array<String>>
    internal open val dsl by lazy { CalendarPermissionScope(this.activity.baseContext) }

    /** Check if the app has the runtime permission to *read/write* device calendar. */
    fun hasPermission(): Boolean {
        return PERMISSIONS.all { perm ->
            this.activity.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED
        }
    }
    internal fun shouldShowDialog(): Boolean {
        return PERMISSIONS.all { perm ->
            this.activity.shouldShowRequestPermissionRationale(perm)
        }
    }
    internal fun showDeniedToast() {
        Toast.makeText(this.activity.baseContext, R.string.cal_perm_denied, Toast.LENGTH_SHORT).show()
    }

    /** A **Dialog** explaining why the permissions are needed.
     *
     * Must be included in the **Activity**'s composition for it to be rendered. */
    @Composable
    abstract fun RationaleDialog()
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
    activity: MainActivity
): Permission(activity) {
    /** System Calendar operations can block the main thread, so delegate them to another thread.
     *  Use **`calendarsThread.execute`** inside a *`calendarPermission.run`* block.
     *
     *  Using *Worker threads* instead of `AsyncTask` and `Loader`s because I understand it better.*/
    private val calendarThread = Executors.newSingleThreadExecutor()

    private var currentAction: (CalendarPermissionScope.() -> Unit)? = null

    override val requestLauncher = this.activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (checkResults(results))
            currentAction?.let { action ->
                this.calendarThread.execute { action(this.dsl) }
            } ?: run {
                throw Exception("CalendarPermission.currentAction was not set before requesting permission")
            }
        else
            this.showDeniedToast()
    }

    /** Run a function that requires *Calendar Permissions* in another thread. */
    fun run(action: CalendarPermissionScope.() -> Unit) {
        if (this.hasPermission())
            this.calendarThread.execute { action(this.dsl) }
        else {
            // Set the action that will be run after the user grants the permissions
            this.currentAction = action
            if (this.shouldShowDialog())
                this.dialogController = true
            else
                this.requestPermissions()
        }
    }

    private fun requestPermissions() {
        Log.i("CalendarPermission", "Requesting Calendar permissions...")
        this.requestLauncher.launch(PERMISSIONS)
    }

    private var dialogController by mutableStateOf(false)
    @Composable
    override fun RationaleDialog() {
        if (this.dialogController) {
            CalendarRationaleDialog(
                dismiss = {
                    this.dialogController = false
                    Log.d(null, "Calendar Permission Rationale Dialog dismissed")
                },
                confirm = {
                    this.dialogController = false
                    requestPermissions()
                }
            )
        }
    }
}

/** An async implementation of [Permission] using Kotlin's **Coroutines**. */
class AsyncCalendarPermission(
    activity: MainActivity
): Permission(activity) {
    private val channel = Channel<Boolean>()

    override val requestLauncher = this.activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        channel.trySend(checkResults(results))
    }

    /** Run a function that requires *Calendar Permissions* asynchronously, using Kotlin's *Coroutines*,
     * waiting for the action to finish running and returning its return value (`T`).  */
    suspend fun <T> run(action: suspend CalendarPermissionScope.() -> T): T? {
        return if (this.hasPermission())
            action(this.dsl)
        else {
            if (this.shouldShowDialog()) {
                this.dialogController = true
                // Wait for user to press Allow (true) or Deny (false)
                val response = this.channel.receive()
                this.dialogController = false
                if (!response)
                    return null
                // Proceed to request permission when Allow is pressed
            }
            if (this.requestPermissions())
                action(this.dsl)
            else
                null
        }
    }

    /** Calls [run] with **action** and will also show a [Dialog][SuspendDialog] with a **message** while **action** runs. */
    suspend fun <T> runWithMessage(msg: String, action: suspend CalendarPermissionScope.() -> T): T? {
        return this.run {
            this@AsyncCalendarPermission.suspendMessage = msg
            val result = action()
            this@AsyncCalendarPermission.suspendMessage = null
            result
        }
    }

    /** Returns whether the permissions have been granted and it is safe to proceed. */
    private suspend fun requestPermissions(): Boolean {
        Log.i("CalendarPermission", "Requesting Calendar permissions (async)...")
        this.requestLauncher.launch(PERMISSIONS)
        val result = this.channel.receive()
        if (!result) this.showDeniedToast()
        return result
    }

    private var dialogController by mutableStateOf(false)
    @Composable
    override fun RationaleDialog() {
        if (this.dialogController) {
            CalendarRationaleDialog(
                dismiss = { this.channel.trySend(false) },
                confirm = { this.channel.trySend(true) }
            )
        }
    }

    /** Shows a Dialog with a [Spinner][CircularProgressIndicator] and a **message** when is not **`NULL`**.
     *
     * Like with the [RationaleDialog], the [SuspendDialog] must be included in the **Activity**'s composition for it to be rendered. */
    private var suspendMessage: String? by mutableStateOf(null)
    @Composable
    fun SuspendDialog() {
        this.suspendMessage?.let { text -> CalendarSuspendDialog(text = text) }
    }
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
fun CalendarRationaleDialog(dismiss: () -> Unit = {}, confirm: () -> Unit = {}) {
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

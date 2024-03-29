package me.marti.calprovexample.ui

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.marti.calprovexample.R
import java.util.concurrent.Executors

private const val READ_CALENDAR_PERMISSION = "android.permission.READ_CALENDAR"
private const val WRITE_CALENDAR_PERMISSION = "android.permission.WRITE_CALENDAR"

/**
 * Run some action that requires calendar permissions safely.
 *
 * An instance of this classed must be initialized *before* the `Activity.onCreate()` method.
 *
 * Functions that require the permissions must take **`CalendarPermission.Dsl`** as their receiver.
 * The only way to obtain the **`Dsl`** is if the permission is granted,
 * so writing function this way ensures they can only be called if the permission is granted.
 * The **`Dsl`** contains the activity's `Context` for convenience.
 *
 * Because most of these actions are expected to block the thread they run on,
 * this object crates a *new thread* to run all the actions so that the UI thread can keep running.
 *
 * ## Rationale Dialog
 * The system sometimes determines that the Activity should show a dialog explaining to the user
 * why the permissions being requested are needed.
 *
 * The **Rationale dialog** should be included in any activity that makes use of this class.
 * If the dialog is not included in the UI, and the system determines that the dialog should be shown,
 * the permissions will not be requested and the UI flow will be blocked.
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
 *
 * ## Bugs
 * Don't use `calendarPermission.run` before `MainActivity.onCreate()` because it will never run.
 *
 * Don't do this, `a` will always print null (except when it doesn't).
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
 * This, however, ***will*** work:
 * ```kt
 * @Composable
 * fun doCalendarThing(arg: String) {
 *     var a by remember { mutableStateOf(null) }
 *     this.calendarPermission.run {
 *         this.getCalendarData(arg)
 *         a = "Hi!!!"
 *         ...
 *     }
 *     Text(a) // Hi!!!
 * }
 * ```
 * This can be used as a pseudo-return value for the function being run.
 */
class CalendarPermission(
    private val activity: MainActivity
) {
    class Dsl internal constructor(val context: Context)

    private lateinit var dsl: Dsl

    /** System Calendar operations can block the main thread, so delegate them to another thread.
     *  Use **`calendarsThread.execute`** inside a *`calendarPermission.run`* block.
     *
     *  Using *Worker threads* instead of `AsyncTask` and `Loader`s because I understand it better.*/
    private val calendarThread = Executors.newSingleThreadExecutor()

    private val requestLauncher = this.activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        for (permission in results)
            if (!permission.value) {
                Toast.makeText(
                    this.activity.baseContext,
                    R.string.cal_perm_denied,
                    Toast.LENGTH_SHORT
                ).show()
                return@registerForActivityResult
            }

        currentAction?.let { action ->
            this.calendarThread.execute { action(this.dsl) }
        } ?: run {
            throw Exception("CalendarPermission.currentAction was not set before requesting permission")
        }
    }

    fun run(action: Dsl.() -> Unit) {
        // Initialize the Dsl on the first call of this function
        if (!this::dsl.isInitialized) {
            this.dsl = Dsl(this.activity.baseContext)
        }

        if (this.hasPermission()) {
            this.calendarThread.execute { action(this.dsl) }
        } else {
            // Set the action that will be run after the user grants the permissions
            currentAction = action
            if (
                this.activity.shouldShowRequestPermissionRationale(READ_CALENDAR_PERMISSION)
                || this.activity.shouldShowRequestPermissionRationale(WRITE_CALENDAR_PERMISSION)
            ) {
                dialogController.value = true
            } else {
                requestPermissions()
            }
        }
    }

    /** Check if the app has the runtime permission to *read/write* device calendar. */
    fun hasPermission(): Boolean {
        return this.activity.checkSelfPermission(READ_CALENDAR_PERMISSION) == PackageManager.PERMISSION_GRANTED
            && this.activity.checkSelfPermission(WRITE_CALENDAR_PERMISSION) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        Log.d(null, "Requesting Calendar permissions...")
        this.requestLauncher.launch(
            arrayOf(
                READ_CALENDAR_PERMISSION,
                WRITE_CALENDAR_PERMISSION
            )
        )
    }

    private val dialogController = mutableStateOf(false)
    private var currentAction: (Dsl.() -> Unit)? = null

    @Composable
    fun RationaleDialog() {
        if (this.dialogController.value) {
            CalendarRationaleDialog(
                dismiss = {
                    this.dialogController.value = false
                    Log.d(null, "Calendar Permission Rationale Dialog dismissed")
                },
                confirm = {
                    this.dialogController.value = false
                    requestPermissions()
                }
            )
        }
    }
}

// Keep it here to show the preview
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

package me.marti.calprovexample.ui

import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import me.marti.calprovexample.R

private const val READ_CALENDAR_PERMISSION = "android.permission.READ_CALENDAR"
private const val WRITE_CALENDAR_PERMISSION = "android.permission.WRITE_CALENDAR"

/**
 * Run some action that requires calendar permissions safely,
 * guaranteeing that the action will not be run unless the activity has the required permissions,
 * and requests the necessary permissions if it doesn't.
 *
 * The **Rationale dialog** should be included in any activity that makes use of this class.
 * If the dialog is not included in the UI, and the system determines that the dialog should be shown,
 * the permissions will not be requested and the UI flow will be blocked.
 *
 * ## Example
 * ```kt
 * class MyActivity : {
 *     private val val calendarActionManager = CalendarPermission(this) { ... }
 *
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         ...
 *
 *         this.setContent {
 *             MyTheme {
 *                 ...
 *                 this.calendarActionManager.RationaleDialog()
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * @param activity the activity the action will be run on.
 * @param action the action that will be run if the activity has the required permissions.
 */
// TODO: find better name for this class
class CalendarPermission(
    private val activity: ComponentActivity,
    private val action: () -> Unit
) {
    private val dialogController = mutableStateOf(false)

    private val calendarPermissionRequestLauncher = this.activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    )
    { results ->
        var canRun = true
        for (permission in results)
            if (!permission.value) {
                Toast.makeText(
                    this.activity.baseContext,
                    R.string.cal_perm_denied,
                    Toast.LENGTH_SHORT
                ).show()
                canRun = false
                break
            }

        if (canRun)
            this.action()
    }

    /**
     * Run the action given when constructing this object.
     */
    fun runAction() {
        if (this.hasPermission()) {
            this.action()
        } else if (ActivityCompat.shouldShowRequestPermissionRationale(
                this.activity,
                READ_CALENDAR_PERMISSION
            )
        || ActivityCompat.shouldShowRequestPermissionRationale(
                this.activity,
                WRITE_CALENDAR_PERMISSION
            )
        ) {
            this.dialogController.value = true
        } else {
            this.requestPermissions()
        }
    }

    /** Check if the app has the runtime permission to *read/write* device calendar. */
    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this.activity.baseContext, READ_CALENDAR_PERMISSION) == PackageManager.PERMISSION_GRANTED
        && ContextCompat.checkSelfPermission(this.activity.baseContext, WRITE_CALENDAR_PERMISSION) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        Log.d(null, "Requesting Calendar permissions...")
        this.calendarPermissionRequestLauncher.launch(arrayOf(
            READ_CALENDAR_PERMISSION,
            WRITE_CALENDAR_PERMISSION
        ))
    }

    @Composable
    fun RationaleDialog() {
        // The if statement "subscribes" to the value, and this code is run every time the value changes.
        if (this.dialogController.value) {
            CalendarRationaleDialog(
                onConfirm = {
                    this.dialogController.value = false
                    this.requestPermissions()
                },
                onDismiss = {
                    this.dialogController.value = false
                    Log.d(null, "Calendar Permission Rationale Dialog dismissed")
                }
            )
        }
    }
}

@Composable
fun CalendarRationaleDialog(onConfirm: () -> Unit = {}, onDismiss: () -> Unit = {}) {
    AlertDialog(
        icon = {
            Icon(
                painterResource(R.drawable.baseline_edit_calendar_24),
                null,
                modifier = Modifier.size(36.dp),
            )
        },
        title = { Text(stringResource(R.string.cal_perm_rationale_title)) },
        text = { Text(stringResource(R.string.cal_perm_rationale_body, stringResource(R.string.app_name))) },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                content = { Text(stringResource(R.string.cal_perm_rationale_confirm)) }
            )
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                content = { Text(stringResource(R.string.cal_perm_rationale_dismiss)) }
            )
        }
    )
}

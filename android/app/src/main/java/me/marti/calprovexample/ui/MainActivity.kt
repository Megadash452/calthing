package me.marti.calprovexample.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.marti.calprovexample.BooleanUserPreference
import me.marti.calprovexample.Color
import me.marti.calprovexample.GroupedList
import me.marti.calprovexample.NonEmptyList
import me.marti.calprovexample.PreferenceKey
import me.marti.calprovexample.R
import me.marti.calprovexample.StringLikeUserPreference
import me.marti.calprovexample.calendar.AllData
import me.marti.calprovexample.calendar.ExternalUserCalendar
import me.marti.calprovexample.calendar.InternalUserCalendar
import me.marti.calprovexample.calendar.copyFromDevice
import me.marti.calprovexample.calendar.deleteCalendar
import me.marti.calprovexample.calendar.editCalendar
import me.marti.calprovexample.calendar.externalUserCalendars
import me.marti.calprovexample.calendar.internalUserCalendars
import me.marti.calprovexample.calendar.newCalendar
import me.marti.calprovexample.calendar.toggleSync
import me.marti.calprovexample.getAppPreferences
import me.marti.calprovexample.ui.theme.CalProvExampleTheme

class MainActivity : ComponentActivity() {
    private val calendarPermission = CalendarPermission(this)

    // -- Hoisted States for compose
    /** The path/URI where the synced .ics files are stored in shared storage.
      * Null if the user hasn't selected a directory. */
    private val syncDir = StringLikeUserPreference(PreferenceKey.SYNC_DIR_URI) { uri -> uri.toUri() }
    /** Calendars are grouped by Account Name.
     * **`Null`** if the user hasn't granted permission (this can't be represented by empty because the user could have no calendars in the device).
     *
     * Since the list is *`MutableState`*, to edit data of an element it must be **replaced** with another (use [InternalUserCalendar.copy]). */
    @SuppressLint("MutableCollectionMutableState")
    private var userCalendars: MutableState<SnapshotStateList<InternalUserCalendar>?> = mutableStateOf(null)

    /** Register for the intent that lets the user pick a directory where Syncthing (or some other service) will store the .ics files. */
    private val dirSelectIntent = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) {
            println("User cancelled the file picker.")
        } else {
            println("User selected $uri for synced .ics files.")
            this.syncDir.value = uri
            // Preserve access to the directory. Otherwise, access would be revoked when app is closed.
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

            // TODO: use DocumentContract instead (performance)
            val dir = DocumentFile.fromTreeUri(this.baseContext, uri)!!
            println("Files in ${uri.path}:")
            for (file in dir.listFiles()) {
                println("\t${file.name}")
            }

            // println("Calendars on device:")
            // for (cal in userCalendars(this.baseContext)!!) {
            //     println("\t$cal")
            // }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must set navigationBarStyle to remove the scrim.
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.light(0, 0))
        super.onCreate(savedInstanceState)

        val preferences = this.baseContext.getAppPreferences()
        this.syncDir.initStore(preferences)
        val fragmentCals = BooleanUserPreference(PreferenceKey.FRAGMENT_CALS)
        fragmentCals.initStore(preferences)

        Log.d(null, "Initializing Main Activity")

        // Populate the list of synced calendars, but only if the user had allowed it before.
        if (this.calendarPermission.hasPermission())
            this.setUserCalendars()

        this.setContent {
            CalProvExampleTheme {
                val navController = rememberNavController()

                NavHost(navController, startDestination = NavDestination.Main.route) {
                    this.composable(NavDestination.Main.route) {
                        // Open a secondary item in this screen, such as the FAB or a dialog
                        var openAction: Actions? by rememberSaveable { mutableStateOf(null) }
                        val snackBarHost = SnackBarHost.create()
                        MainContent(
                            navigateTo = { dest -> navController.navigate(dest.route) },
                            snackBarState = snackBarHost.state
                        ) {
                            this.tabWithFab(
                                icon = Icons.Default.DateRange,
                                title = "Calendars",
                                fab = ExpandableFab(
                                    icon = Icons.Default.Add,
                                    description = "Add/New Calendar",
                                    actions = NonEmptyList(
                                        ExpandableFab.Action(Icons.Default.Create, "New blank calendar")
                                            { openAction = Actions.NewCalendar },
                                        ExpandableFab.Action(R.drawable.rounded_calendar_add_on_24, "Device calendar")
                                            { openAction = Actions.CopyCalendar },
                                        ExpandableFab.Action(R.drawable.rounded_upload_file_24, "Import from file")
                                            { openAction = Actions.ImportFile },
                                    )
                                ),
                            ) { modifier ->
                                Calendars(
                                    modifier = modifier,
                                    calendars = userCalendars.value,
                                    hasSelectedDir = syncDir.value != null,
                                    selectDirClick = { this@MainActivity.selectSyncDir() },
                                    calPermsClick =  { this@MainActivity.setUserCalendars() },
                                    syncCalendarSwitch = { id, sync -> this@MainActivity.toggleSyncCalendar(id, sync) },
                                    editCalendar = { id, name, color ->
                                        openAction = Actions.EditCalendar(id, name, color)
                                    },
                                    deleteCalendar = { id -> this@MainActivity.deleteCalendar(id, snackBarHost) }
                                )
                            }

                            this.tab(icon = Icons.Default.AccountCircle, title = "Contacts") { modifier ->
                                Text("Contacts section", modifier = modifier)
                            }
                        }

                        // Show a dialog for the current action the user selected
                        when (openAction) {
                            Actions.NewCalendar -> NewCalendarAction(
                                close = { openAction = null },
                                submit = { name, color -> this@MainActivity.newCalendar(name, color) }
                            )
                            is Actions.EditCalendar -> {
                                val data = openAction as Actions.EditCalendar
                                EditCalendarAction(
                                    name = data.name,
                                    color = data.color,
                                    close = { openAction = null },
                                    submit = { newName, newColor -> this@MainActivity.editCalendar(data.id, newName, newColor) }
                                )
                            }
                            Actions.CopyCalendar -> {
                                // Get the Calendars in the device the user can copy
                                var calendars by remember { mutableStateOf<GroupedList<String, ExternalUserCalendar>?>(null) }
                                var error by remember { mutableStateOf(false) }
                                this@MainActivity.calendarPermission.run {
                                    this.externalUserCalendars()?.let { cals ->
                                        calendars = cals.map { cal -> ExternalUserCalendar(cal,
                                            // Find the calendar owned by this app (internal) that copied this calendar's data (if any).
                                            userCalendars.value?.find { iCal -> cal.id == iCal.importedFrom }?.name
                                        ) }
                                            .groupBy { cal -> cal.accountName }
                                    } ?: run {
                                        error = true
                                    }
                                }

                                if (calendars != null) {
                                    CopyCalendarAction(
                                        calendars = calendars!!,
                                        close = { openAction = null },
                                        submit = { id -> this@MainActivity.copyCalendars(id) }
                                    )
                                } else if (error) {
                                    Toast.makeText(this@MainActivity.baseContext, "Could not query user calendars", Toast.LENGTH_SHORT).show()
                                    openAction = null
                                }
                            }
                            Actions.ImportFile -> { /* TODO */ }
                            null -> {}
                        }
                    }
                    this.composable(NavDestination.Settings.route) {
                        SettingsContent(
                            navUpClick = { navController.navigateUp() },
                            settings = listOf(
                                { BooleanSetting(
                                    name = "Fragment Calendars",
                                    summary = "Store data about each Calendar in a separate .ics file",
                                    value = fragmentCals.value ?: false,
                                    onClick = { checked -> fragmentCals.value = checked }
                                ) },
                                { DirSetting(
                                    name = "Syncing Directory",
                                    value = this@MainActivity.syncDir.value,
                                    selectClick = { this@MainActivity.selectSyncDir() }
                                ) }
                            )
                        )
                    }
                    this.composable(NavDestination.Debug.route) {
                        val data = remember { AllData(this@MainActivity.baseContext) }
                        DebugContent(
                            navUpClick = { navController.navigateUp() },
                            data = data,
                        )
                    }
                }
                this.calendarPermission.RationaleDialog()
            }
        }
    }

    private fun setUserCalendars() {
        this.calendarPermission.run {
            this.internalUserCalendars()?.also { cals ->
                // Group calendars by Account Name
                userCalendars.value = cals.toMutableStateList()
            } ?: run {
                println("Couldn't get user calendars")
            }
        }
    }

    private fun newCalendar(name: String, color: Color) {
        this.calendarPermission.run {
            this.newCalendar(name, color)?.let { newCal ->
                // Add the Calendar to the list
                userCalendars.value?.add(newCal)
            }
        }
    }

    private fun editCalendar(id: Long, newName: String, newColor: Color) {
        this.calendarPermission.run {
            if (this.editCalendar(id, newName, newColor)) {
                userCalendars.value?.let { calendars ->
                    // ?: throw Exception("Could not find Calendar with ID=$id in `userCalendars`")
                    val i = calendars.indexOfFirst { cal -> cal.id == id }
                    calendars[i] = calendars[i].copy(
                        name = newName,
                        color = newColor
                    )
                }
            }
        }
    }

    private fun toggleSyncCalendar(id: Long, sync: Boolean) {
        this.calendarPermission.run {
            if (this.toggleSync(id, sync)) {
                userCalendars.value?.let { calendars ->
                    val i = calendars.indexOfFirst { cal -> cal.id == id }
                    calendars[i] = calendars[i].copy(sync = sync)
                }
            }
        }
    }

    private fun deleteCalendar(id: Long, snackBarHost: SnackBarHost) {
        val calendars = userCalendars.value ?: run {
            Log.e("UI deleteCalendar", "Trying to delete Calendar when 'userCalendars' is NULL")
            return
        }
        // Keep "deleted" Calendar so that it can be restored later
        val deleted = calendars.let { cals ->
            // Remove the deleted Calendar from the list
            val i = cals.indexOfFirst { cal -> cal.id == id }
            if (i == -1) {
                Log.e("UI deleteCalendar", "Trying to delete Calendar that doesn't exist")
                return
            }
            Pair(i, cals.removeAt(i))
        }
        val msg = "Calendar deleted"

        snackBarHost.scope.launch {
            // If there is already a snack-bar with similar msg, dismiss it to show this one.
            snackBarHost.state.currentSnackbarData?.let { current ->
                if (current.visuals.message == msg)
                    current.dismiss()
            }
            val result = snackBarHost.state.showSnackbar(
                message = msg,
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            when (result) {
                // Restore Calendar when user presses "Undo"
                SnackbarResult.ActionPerformed -> {
                    val (idx, calendar) = deleted
                    if (idx < calendars.size)
                        calendars.add(idx, calendar)
                    else
                        calendars.add(calendar)
                }
                // Only really delete calendar after notif is dismissed.
                SnackbarResult.Dismissed -> this@MainActivity.calendarPermission.run {
                    this.deleteCalendar(id)
                }
            }
        }
    }

    private fun copyCalendars(ids: List<Long>) {
        this.calendarPermission.run {
            this.copyFromDevice(ids)?.let { newCals ->
                // Add the Calendars to the list
                userCalendars.value?.addAll(newCals)
            }
        }
    }

    private fun selectSyncDir() {
        // The ACTION_OPEN_DOCUMENT_TREE Intent can optionally take an URI where the file picker will open to.
        dirSelectIntent.launch(null)
    }
}

private class SnackBarHost(
    val scope: CoroutineScope,
    val state: SnackbarHostState
) {
    companion object {
        @Composable
        fun create(
            scope: CoroutineScope = rememberCoroutineScope(),
            state: SnackbarHostState = remember { SnackbarHostState() }
        ) = SnackBarHost(scope, state)
    }
}

package me.marti.calprovexample.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
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
import androidx.compose.material3.Text
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import java.util.concurrent.Executors
import me.marti.calprovexample.BooleanUserPreference
import me.marti.calprovexample.Color
import me.marti.calprovexample.GroupedList
import me.marti.calprovexample.NonEmptyList
import me.marti.calprovexample.PreferenceKey
import me.marti.calprovexample.R
import me.marti.calprovexample.SetUserPreference
import me.marti.calprovexample.StringLikeUserPreference
import me.marti.calprovexample.calendar.AllData
import me.marti.calprovexample.calendar.UserCalendarListItem
import me.marti.calprovexample.calendar.copyFromDevice
import me.marti.calprovexample.calendar.deleteCalendar
import me.marti.calprovexample.calendar.newCalendar
import me.marti.calprovexample.calendar.userCalendars
import me.marti.calprovexample.getAppPreferences
import me.marti.calprovexample.ui.theme.CalProvExampleTheme

class MainActivity : ComponentActivity() {
    private val calendarPermission = CalendarPermission(this)

    // -- Hoisted States for compose
    /** The path/URI where the synced .ics files are stored in shared storage.
      * Null if the user hasn't selected a directory. */
    private val syncDir = StringLikeUserPreference(PreferenceKey.SYNC_DIR_URI) { uri -> uri.toUri() }
    /** Calendars are grouped by Account Name.
      * **`Null`** if the user hasn't granted permission (this can't be represented by empty because the user could have no calendars in the device). */
    @SuppressLint("MutableCollectionMutableState")
    private var userCalendars: MutableState<SnapshotStateList<UserCalendarListItem>?> = mutableStateOf(null)

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

        val syncedCals = SetUserPreference(PreferenceKey.SYNCED_CALS) { id -> id.toLong() }
        syncedCals.initStore(preferences)
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
                        MainContent(navigateTo = { dest -> navController.navigate(dest.route) }) {
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
                                    groupedCalendars = userCalendars.value?.groupBy { cal -> cal.accountName },
                                    hasSelectedDir = syncDir.value != null,
                                    selectDirClick = { this@MainActivity.selectSyncDir() },
                                    calPermsClick =  { this@MainActivity.setUserCalendars() },
                                    calIsSynced = { id -> syncedCals.contains(id) },
                                    onCalSwitchClick = { id, checked -> if (checked) syncedCals.add(id) else syncedCals.remove(id) },
                                    deleteCalendar = { id ->
                                        this@MainActivity.deleteCalendar(id)
                                    }
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
                            Actions.CopyCalendar -> {
                                // Get the Calendars in the device the user can copy
                                var calendars by remember { mutableStateOf<GroupedList<String, UserCalendarListItem>?>(null) }
                                var error by remember { mutableStateOf(false) }
                                this@MainActivity.calendarPermission.run {
                                    calendarsThread.submit {
                                        this.userCalendars(false)?.let { cals ->
                                            calendars = cals.groupBy { cal -> cal.accountName }
                                        } ?: run {
                                            error = true
                                        }
                                    }
                                }

                                if (calendars != null) {
                                    CopyCalendarAction(
                                        calendars = calendars!!,
                                        close = { openAction = null },
                                        submit = { id -> this@MainActivity.copyCalendars(id) }
                                    )
                                } else if (error) {
                                    Text("Could not query user calendars")
                                } else {
                                    Text("Waiting for Calendar Permission...")
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

    /** System Calendar operations can block the main thread, so delegate them to another thread.
     *  Use **`calendarsThread.execute`** inside a *`calendarPermission.run`* block.
     *
     *  Using *Worker threads* instead of `AsyncTask` and `Loader`s because I understand it better.*/
    // TODO: might create a DSL, and make the CalendarPermissionDSL depend on that DSL.
    // TODO: or might put the Executor in the Calendar permission?
    private val calendarsThread = Executors.newSingleThreadExecutor()

    private fun setUserCalendars() {
        this.calendarPermission.run {
            calendarsThread.execute {
                this.userCalendars()?.also { cals ->
                    // Group calendars by Account Name
                    userCalendars.value = cals.toMutableStateList()
                } ?: run {
                    println("Couldn't get user calendars")
                }
            }
        }
    }

    private fun newCalendar(name: String, color: Color) {
        this.calendarPermission.run {
            calendarsThread.execute {
                this.newCalendar(name, color)?.let { newCal ->
                    // Add the Calendar to the list
                    userCalendars.value?.add(newCal) ?: run {
                        this@MainActivity.setUserCalendars()
                    }
                }
            }
        }
    }

    private fun deleteCalendar(id: Long) {
        this.calendarPermission.run {
            calendarsThread.execute {
                if (this.deleteCalendar(id)) {
                    // Remove the deleted Calendar from the list
                    userCalendars.value?.removeIf { cal -> cal.id == id }  ?: run {
                        this@MainActivity.setUserCalendars()
                    }
                }
            }
        }
    }

    private fun copyCalendars(ids: List<Long>) {
        this.calendarPermission.run {
            calendarsThread.execute {
                this.copyFromDevice(ids)?.let { newCals ->
                    // Add the Calendars to the list
                    userCalendars.value?.addAll(newCals) ?: run {
                        this@MainActivity.setUserCalendars()
                    }
                }
            }
        }
    }

    private fun selectSyncDir() {
        // The ACTION_OPEN_DOCUMENT_TREE Intent can optionally take an URI where the file picker will open to.
        dirSelectIntent.launch(null)
    }
}

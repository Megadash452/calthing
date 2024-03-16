package me.marti.calprovexample.ui

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
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import me.marti.calprovexample.AllData
import me.marti.calprovexample.BooleanUserPreference
import me.marti.calprovexample.Color
import me.marti.calprovexample.NonEmptyList
import me.marti.calprovexample.PreferenceKey
import me.marti.calprovexample.R
import me.marti.calprovexample.SetUserPreference
import me.marti.calprovexample.StringLikeUserPreference
import me.marti.calprovexample.UserCalendarListItem
import me.marti.calprovexample.getAppPreferences
import me.marti.calprovexample.ui.theme.CalProvExampleTheme
import me.marti.calprovexample.userCalendars

/** A **`List<T>`** grouped by values **`G`**, which are members of **`T`**. */
typealias GroupedList<G, T> = Map<G, List<T>>

class MainActivity : ComponentActivity() {
    private val calendarPermission = CalendarPermission(this)

    // -- Hoisted States for compose
    /** The path/URI where the synced .ics files are stored in shared storage.
      * Null if the user hasn't selected a directory. */
    private val syncDir = StringLikeUserPreference(PreferenceKey.SYNC_DIR_URI) { uri -> uri.toUri() }
    /** Calendars are grouped by Account Name.
      * **`Null`** if the user hasn't granted permission (this can't be represented by empty because the user could have no calendars in the device). */
    private var userCalendars: MutableState<GroupedList<String, UserCalendarListItem>?> = mutableStateOf(null)

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
            this.getUserCalendars()

        this.setContent {
            CalProvExampleTheme {
                val navController = rememberNavController()

                NavHost(navController, startDestination = NavDestination.Main.route) {
                    this.composable(NavDestination.Main.route) {
                        // Open a secondary item in this screen, such as the FAB or a dialog
                        var openItem by rememberSaveable { mutableStateOf("") }
                        MainContent(navigateTo = { dest -> navController.navigate(dest.route) }) {
                            this.tabWithFab(
                                icon = Icons.Default.DateRange,
                                title = "Calendars",
                                fab = ExpandableFab(
                                    icon = Icons.Default.Add,
                                    description = "Add/New Calendar",
                                    actions = NonEmptyList(
                                        first = ExpandableFab.Action(Icons.Default.Create, "New blank calendar")
                                            { openItem = "newCalendar" },
                                        ExpandableFab.Action(R.drawable.rounded_calendar_add_on_24, "Device calendar") { /* TODO */ },
                                        ExpandableFab.Action(R.drawable.rounded_upload_file_24, "Import from file") { /* TODO */ },
                                    )
                                ),
                            ) { modifier ->
                                Calendars(
                                    modifier = modifier,
                                    groupedCalendars = userCalendars.value,
                                    hasSelectedDir = syncDir.value != null,
                                    selectDirClick = { this@MainActivity.selectSyncDir() },
                                    calPermsClick =  { this@MainActivity.getUserCalendars() },
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

                        when (openItem) {
                            "newCalendar" -> NewCalendarAction(
                                close = { openItem = "" },
                                submit = { name, color ->
                                    this@MainActivity.newCalendar(name, color)
                                    navController.navigateUp()
                                }
                            )
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

    private fun getUserCalendars() {
        this.calendarPermission.run {
            this.userCalendars()?.also { cals ->
                // Group calendars by Account Name
                userCalendars.value = cals.groupBy { cal -> cal.accountName }
            } ?: run {
                println("Couldn't get user calendars")
            }
        }
    }

    private fun newCalendar(name: String, color: Color) {
        this.calendarPermission.run {
            this.newCalendar(name, color)
            // TODO: add calendar to userCalendars without reQuerying
            this@MainActivity.getUserCalendars()
        }
    }

    private fun deleteCalendar(id: Long) {
        this.calendarPermission.run {
            this.deleteCalendar(id)
            // TODO: remove calendar from userCalendars without reQuerying
            this@MainActivity.getUserCalendars()
        }
    }

    private fun selectSyncDir() {
        // The ACTION_OPEN_DOCUMENT_TREE Intent can optionally take an URI where the file picker will open to.
        dirSelectIntent.launch(null)
    }
}

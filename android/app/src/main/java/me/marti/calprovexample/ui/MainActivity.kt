package me.marti.calprovexample.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import me.marti.calprovexample.BooleanUserPreference
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
    // -- Hoisted States for compose
    /** The path/URI where the synced .ics files are stored in shared storage.
      * Null if the user hasn't selected a directory. */
    private val syncDir = StringLikeUserPreference(PreferenceKey.SYNC_DIR_URI) { uri -> uri.toUri() }
    /** Calendars are grouped by Account Name.
      * **`Null`** if the user hasn't granted permission (this can't be represented by empty because the user could have no calendars in the device). */
    private var userCalendars: MutableState<GroupedList<String, UserCalendarListItem>?> = mutableStateOf(null)

    private val getUserCalendars = this.withCalendarPermission {
        userCalendars(this.baseContext)?.also { cals ->
            // Group calendars by Account Name
            userCalendars.value = cals.groupBy { cal -> cal.accountName }
        } ?: run {
            println("Couldn't get user calendars")
        }
    }
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
        if (getUserCalendars.hasPermission())
            getUserCalendars.runAction()

        // FABs can be placed on the content of each navigation destination.
        val fabs = mapOf(
            Pair(NavDestination.Calendars.route, ExpandableFab(
                icon = Icons.Default.Add,
                description = "Add/New Calendar",
                actions = NonEmptyList(
                    first = ExpandableFab.Action(Icons.Default.Create, "New blank calendar") { /* TODO */ },
                    ExpandableFab.Action(R.drawable.rounded_calendar_add_on_24, "Device calendar") { /*TODO*/ },
                    ExpandableFab.Action(R.drawable.rounded_upload_file_24, "Import from file") { /*TODO*/ },
                )
            )),
        )

        this.setContent {
            CalProvExampleTheme {
                var actionsExpanded by remember { mutableStateOf(false) }
                val navController = rememberNavController()
                val navBackStack by navController.currentBackStackEntryAsState()

                Scaffold(
                    contentWindowInsets = WindowInsets.systemBars,
                    floatingActionButton = {
                        AnimatedContent(
                            targetState = navBackStack?.destination?.route,
                            label = "FAB transition animations"
                        ) { dest ->
                            fabs[dest]?.let { fab ->
                                ExpandableFloatingActionButtons(
                                    expanded = actionsExpanded,
                                    expand = { actionsExpanded = true },
                                    data = fab
                                )
                            }
                        }
                    },
                    bottomBar = {
                        NavBar(
                            items = NavDestination.entries,
                            controller = navController,
                            onClick = { actionsExpanded = false }
                        )
                    }
                ) { paddingValues ->
                    NavHost(
                        modifier = Modifier.padding(paddingValues),
                        navController = navController,
                        startDestination = NavDestination.entries[0].route,
                    ) {
                        this.composable(NavDestination.Calendars.route) {
                            Calendars(
                                modifier = Modifier.fillMaxSize(),
                                groupedCalendars = this@MainActivity.userCalendars.value,
                                hasSelectedDir = this@MainActivity.syncDir.value != null,
                                selectDirClick = { this@MainActivity.selectSyncDir() },
                                calPermsClick = { this@MainActivity.getUserCalendars.runAction() },
                                calIsSynced = { id -> syncedCals.contains(id) },
                                onCalSwitchClick = { id, checked -> if (checked) syncedCals.add(id) else syncedCals.remove(id) },
                            )
                        }
                        this.composable(NavDestination.Contacts.route) {
                            Text("Contacts Page", modifier = Modifier.fillMaxSize())
                        }
                        this.composable(NavDestination.Settings.route) {
                            Settings(
                                modifier = Modifier.fillMaxSize(),
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
                                ))
                        }
                    }
                    ExpandedFabBackgroundOverlay(expanded = actionsExpanded) { actionsExpanded = false }
                }
                WithCalendarPermission.RationaleDialog()
            }
        }
    }

    private fun selectSyncDir() {
        // The ACTION_OPEN_DOCUMENT_TREE Intent can optionally take an URI where the file picker will open to.
        dirSelectIntent.launch(null)
    }
}

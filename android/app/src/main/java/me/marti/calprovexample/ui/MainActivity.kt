package me.marti.calprovexample.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import me.marti.calprovexample.UserCalendarListItem
import me.marti.calprovexample.UserStringPreference
import me.marti.calprovexample.ui.theme.CalProvExampleTheme
import me.marti.calprovexample.userCalendars

class MainActivity : ComponentActivity() {
    // -- Hoisted States for compose
    /** The path/URI where the synced .ics files are stored in shared storage.
      * Null if the user hasn't selected a directory. */
    private val filesUri = UserStringPreference("files_uri") { s -> s.toUri() }
    /** Calendars are grouped by Account Name.
      * Null if the user hasn't granted permission (this can't be represented by empty because the user could have no calendars in the device). */
    private var userCalendars: MutableState<Map<String, List<UserCalendarListItem>>?> = mutableStateOf(null)

    // TODO: better name
    private val calendarQueryManager = CalendarPermission(this) {
        val cals = userCalendars(this.baseContext)
        // queryCalendar(this.baseContext)
        if (cals == null) {
            println("Couldn't get user calendars")
        } else {
            // Group calendars by Account Name
            userCalendars.value = cals.groupBy { cal -> cal.accountName }
        }
    }
    // Register for the intent that lets the user pick a directory where Syncthing (or some other service) will store the .ics files.
    private val dirSelectIntent = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) {
            println("User cancelled the file picker.")
        } else {
            println("User selected $uri for synced .ics files.")
            this.filesUri.value = uri
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
        val preferences = this.getPreferences(Context.MODE_PRIVATE)
        this.filesUri.initStore(preferences)

        Log.d(null, "Initializing Main Activity")

        // Populate the list of synced calendars, but only if the user had allowed it before.
        if (calendarQueryManager.hasPermission())
            calendarQueryManager.runAction()

        // /** A list of screens that are rendered as the main content (depending on the selected tab) of the app. */
        // val tabItems: Array<TabItem> = arrayOf(
        //     TabItem(
        //         icon = { Icon(Icons.Default.DateRange, null) },
        //         title = "Calendars",
        //     ) { modifier ->
        //         Calendars(
        //             modifier = modifier,
        //             groupedCalendars = userCalendars.value,
        //             hasSelectedDir = hasSelectedDir.value,
        //         )
        //     },
        //     TabItem(
        //         icon = { Icon(Icons.Default.AccountCircle, null) },
        //         title = "Contacts",
        //     ) { modifier ->
        //         Text("hiii!!!!", modifier = modifier)
        //     },
        //     TabItem(
        //         icon = { Icon(Icons.Default.Settings, null) },
        //         title = "Settings",
        //     ) { modifier ->
        //         Text("Settings page", modifier = modifier)
        //     },
        // )

        this.setContent {
            CalProvExampleTheme {
                val navController = rememberNavController()

                Scaffold(
                    contentWindowInsets = WindowInsets.systemBars,
                    bottomBar = {
                        NavBar(
                            items = tabItems.map { item -> item.toTabBarItem() },
                            controller = navController
                        )
                    }
                ) { paddingValues ->
                    NavHost(navController, startDestination = "calendars", modifier = Modifier.padding(paddingValues)) {
                        this.composable("calendars") {

                        }
                    }
                }
                this.calendarQueryManager.RationaleDialog()
            }
        }
    }
}

@Composable
fun NavBar(
    modifier: Modifier = Modifier,
    items: List<Pair<@Composable () -> Unit, String>>,
    controller: NavController
) {
    NavigationBar(modifier) {
        items.forEachIndexed { index, item ->
            NavigationBarItem(
                icon = item.first,
                label = { Text(item.second) },
                selected = selectedItem.intValue == index,
                onClick = {
                    controller.navigate("next") { this.launchSingleTop = true }
                },
            )
        }
    }
}


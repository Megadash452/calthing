package me.marti.calprovexample.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.documentfile.provider.DocumentFile
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import me.marti.calprovexample.UserCalendarListItem
import me.marti.calprovexample.ui.theme.CalProvExampleTheme
import me.marti.calprovexample.userCalendars

class MainActivity : ComponentActivity() {
    // -- Hoisted States for compose
    /** The path/URI where the synced .ics files are stored in shared storage.
      * Null if the user hasn't selected a directory. */
    private var filesUri: MutableState<Uri?> = mutableStateOf(null)
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

        Log.d(null, "Initializing Main Activity")

        // Populate the list of synced calendars, but only if the user had allowed it before.
        if (calendarQueryManager.hasPermission())
            calendarQueryManager.runAction()

        this.setContent {
            CalProvExampleTheme {
                val navController = rememberNavController()

                NavHost(navController, startDestination = NavDestinationItem.Main.route) {
                    this.composable(NavDestinationItem.Main.route) {
                        MainContent(
                            settingsClick = { navController.navigate(NavDestinationItem.Settings.route) },
                            groupedCalendars = userCalendars.value,
                            hasSelectedDir = filesUri.value != null,
                            selectDirClick = {
                                // The ACTION_OPEN_DOCUMENT_TREE Intent can optionally take an URI where the file picker will open to.
                                dirSelectIntent.launch(null)
                            },
                            calPermsClick =  { calendarQueryManager.runAction() }
                        )
                    }
                    this.composable(NavDestinationItem.Settings.route) {
                        Scaffold(
                            contentWindowInsets = WindowInsets.systemBars,
                            topBar = { SimpleTopBar(
                                title = NavDestinationItem.Settings.title,
                                navUpClick = {

                                }
                            ) }
                        ) { paddingValues ->
                            Text("Settings Page", modifier = Modifier.padding(paddingValues))
                        }
                    }
                }
                this.calendarQueryManager.RationaleDialog()
            }
        }
    }
}

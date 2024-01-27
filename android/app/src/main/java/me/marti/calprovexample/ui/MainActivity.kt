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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.PlainTooltipBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.compose.ui.res.vectorResource
import me.marti.calprovexample.Color
import me.marti.calprovexample.R
import me.marti.calprovexample.UserCalendarListItem
import me.marti.calprovexample.ui.theme.CalProvExampleTheme
import me.marti.calprovexample.userCalendars

private const val OUTER_PADDING = 10
private const val MIDDLE_PADDING = 8
private const val LIST_ITEM_SPACING = 4

class MainActivity : ComponentActivity() {
    // The path/URI where the synced .ics files are stored in shared storage.
    private var filesUri: Uri? = null

    // -- Hoisted States for compose
    // Calendars are grouped by Account Name.
    // Null if the user hasn't granted permission (this can't be represented by empty because the user could have no calendars in the device).
    private var userCalendars: MutableState<Map<String, List<UserCalendarListItem>>?> = mutableStateOf(null)
    // Tells if the user has selected a directory in shared storage where to sync.
    // TODO: should move to a "single source of truth", but can't use mutable state.
    private var hasSelectedDir: MutableState<Boolean> = mutableStateOf(false)

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
            this.filesUri = uri
            this.hasSelectedDir.value = true
            // Preserve access to the directory. Otherwise, access would be revoked when app is closed.
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

            // TODO: use DocumentContract instead (performance)
            val dir = DocumentFile.fromTreeUri(this.baseContext, uri)!!
            println("Files in ${uri.path}:")
            for (file in dir.listFiles()) {
                println("\t${file.name}")
            }

            println("Calendars on device:")
            for (cal in userCalendars(this.baseContext)!!) {
                println("\t$cal")
            }
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
                Scaffold(
                    contentWindowInsets = WindowInsets.systemBars,
                    bottomBar = {
                        NavBar(
                            items = arrayOf(
                                Pair({ Icon(Icons.Default.DateRange, null) }, "Calendars"),
                                Pair({ Icon(Icons.Default.AccountCircle, null) }, "Contacts"),
                                Pair({ Icon(Icons.Default.Settings, null) }, "Settings"),
                            )
                        )
                    }
                ) { paddingValues ->
                    Greeting(
                        Modifier.padding(paddingValues),
                        groupedCalendars = this.userCalendars.value,
                        hasSelectedDir = this.hasSelectedDir.value,
                    )
                }
                this.calendarQueryManager.RationaleDialog()
            }
        }
    }

    /**
     * The starting screen for the Main Activity.
     *
     * @param hasSelectedDir Shows the user a button to select a sync dir if false.
     * @param groupedCalendars All the calendars the user has on their device. The calendars are grouped by Account Name.
     *                         Pass in **null** if the app doesn't have permission to read device calendars
     */
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun Greeting(modifier: Modifier = Modifier, hasSelectedDir: Boolean = false, groupedCalendars: Map<String, List<UserCalendarListItem>>?) {
        Surface(
            modifier = modifier
                .padding(OUTER_PADDING.dp)
                .fillMaxWidth(),
            tonalElevation = 2.dp,
            shadowElevation = 5.dp,
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(Modifier.padding(MIDDLE_PADDING.dp)) {
                Text("Calendars:", style = MaterialTheme.typography.titleLarge)

                Column(
                    Modifier.padding(MIDDLE_PADDING.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!hasSelectedDir) {
                        Text("Please select a directory where to sync Calendars and Contacts.")
                        IconTextButton(
                            icon = painterResource(R.drawable.round_folder_24),
                            text = "Select",
                            onclick = {
                                // The ACTION_OPEN_DOCUMENT_TREE Intent can optionally take an URI where the file picker will open to.
                                dirSelectIntent.launch(null)
                            }
                        )
                        Divider(Modifier.padding(vertical = (LIST_ITEM_SPACING * 4).dp))
                    }

                    if (groupedCalendars == null) {
                        Text("Please allow ${stringResource(R.string.app_name)} to read and write yo your device's calendar")
                        IconTextButton(
                            icon = painterResource(R.drawable.outline_sync_24),
                            text = "Sync",
                            onclick = { calendarQueryManager.runAction() }
                        )
                    } else {
                        LazyColumn(
                            Modifier
                                .clip(MaterialTheme.shapes.small),
                            verticalArrangement = Arrangement.spacedBy(LIST_ITEM_SPACING.dp)
                        ) {
                            groupedCalendars.forEach { (accountName, calGroup) ->
                                this.stickyHeader {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        tonalElevation = 0.dp,
                                        shape = MaterialTheme.shapes.small,
                                    ) {
                                        Text(
                                            text = accountName,
                                            modifier = Modifier
                                                .padding(2.dp)
                                                .padding(start = 4.dp),
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                    }
                                }
                                this.items(calGroup) { cal -> CalendarListItem(cal) }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun NavBar(modifier: Modifier = Modifier, items: Array<Pair<@Composable () -> Unit, String>>) {
        // remember which item is selected
        var selected by remember { mutableIntStateOf(0) }

        NavigationBar(modifier) {
            items.forEachIndexed { index, item ->
                NavigationBarItem(
                    selected = index == selected,
                    label = { Text(item.second) },
                    icon = item.first,
                    onClick = {
                        selected = index
                        // TODO: switch screens
                    },
                )
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun CalendarListItem(cal: UserCalendarListItem) {
        var isChecked by remember { mutableStateOf(false) }

        ListItem(
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .fillMaxWidth(),
            tonalElevation = 3.dp,
            headlineContent = { Text(cal.name) },
            supportingContent = {
                // Don't show any status if the user has not selected this calendar for syncing
                if (isChecked) {
                    Text(
                        "Status...",
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            },
            leadingContent = {
                PlainTooltipBox(
                    tooltip = { Text("This calendar is synced") }
                ) {
                    Icon(
                        modifier = Modifier.tooltipAnchor(),
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Calendars list item",
                        tint = androidx.compose.ui.graphics.Color(cal.color.R.toInt(), cal.color.G.toInt(), cal.color.B.toInt())
                    )
                }
            },
            trailingContent = {
                Switch(
                    checked = isChecked,
                    onCheckedChange = { checked -> isChecked = checked }
                )
            },
        )
    }

    @Preview(showBackground = true)
    @Composable
    fun GreetingPreview() {
        val acc = "me@mydomain.me"

        CalProvExampleTheme {
            this.Greeting(
                hasSelectedDir = true,
                groupedCalendars = arrayOf(
                    UserCalendarListItem(
                        name = "Personal",
                        accountName = acc,
                        color = Color("cd58bb")
                    ),
                    UserCalendarListItem(
                        name = "Friend",
                        accountName = "Friend",
                        color = Color("58cdc9")
                    ),
                    UserCalendarListItem(
                        name = "Work",
                        accountName = acc,
                        color = Color("5080c8")
                    )
                ).groupBy { cal -> cal.accountName }
            )
        }
    }
    @Preview
    @Composable
    fun NavBarPreview() {
        CalProvExampleTheme {
            this.NavBar(
                items = arrayOf(
                    Pair({ Icon(Icons.Default.DateRange, null) }, "Calendars"),
                    Pair({ Icon(Icons.Default.AccountCircle, null) }, "Contacts"),
                    Pair({ Icon(Icons.Default.Settings, null) }, "Settings"),
                )
            )
        }
    }
    @Preview(showBackground = true)
    @Composable
    fun GreetingNoPermPreview() {
        CalProvExampleTheme {
            this.Greeting(groupedCalendars = null)
        }
    }
    @Preview
    @Composable
    fun CalendarPermissionRationaleDialogPreview() {
        CalProvExampleTheme {
            this.calendarQueryManager.RationaleDialog(true)
        }
    }
}

/** A Button that has an icon and text*/
@Composable
fun IconTextButton(modifier: Modifier = Modifier, icon: Painter, text: String, onclick: () -> Unit) {
    Button(onclick, modifier, contentPadding = PaddingValues(start = 16.dp, end = 24.dp)) {
        Icon(icon, null, modifier = Modifier.size(18.dp))
        Text(text, modifier.padding(start = 8.dp))
    }
}

// @Composable
// fun GenericDialog(
//     icon: ImageVector,
//     title: String,
//     text: String,
//     visibilityController: MutableState<Boolean>,
//     confirmText: String = "Confirm",
//     onConfirm: () -> Unit,
//     dismissText: String = "Dismiss",
//     onDismiss: () -> Unit,
// ) {
//     val dismiss: () -> Unit = {
//         visibilityController.value = false
//         onDismiss()
//     }
//
//     if (visibilityController.value) {
//         AlertDialog(
//             icon = {
//                 Icon(icon, contentDescription = "Example Icon")
//             },
//             title = {
//                 Text(title)
//             },
//             text = {
//                 Text(text)
//             },
//             onDismissRequest = dismiss,
//             confirmButton = {
//                 TextButton(
//                     onClick = {
//                         visibilityController.value = false
//                         onConfirm()
//                     },
//                     content = {
//                         Text(confirmText)
//                     }
//                 )
//             },
//             dismissButton = {
//                 TextButton(
//                     onClick = dismiss,
//                     content = {
//                         Text(dismissText)
//                     }
//                 )
//             }
//         )
//     }
// }

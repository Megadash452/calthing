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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import me.marti.calprovexample.Color
import me.marti.calprovexample.R
import me.marti.calprovexample.UserCalendarListItem
import me.marti.calprovexample.ui.theme.CalProvExampleTheme
import me.marti.calprovexample.userCalendars
import androidx.compose.ui.graphics.Color as ComposeColor

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

            // println("Calendars on device:")
            // for (cal in userCalendars(this.baseContext)!!) {
            //     println("\t$cal")
            // }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        // Must set navigationBarStyle to remove the scrim.
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.light(0, 0))
        super.onCreate(savedInstanceState)

        Log.d(null, "Initializing Main Activity")

        // Populate the list of synced calendars, but only if the user had allowed it before.
        if (calendarQueryManager.hasPermission())
            calendarQueryManager.runAction()

        // A list of screens that can be rendered one at a time, and can be switched using tab functionality.
        // The composable function is passed in the modifier with the required "swipe-able" setup.
        val tabScreens: Array<@Composable (Modifier) -> Unit> = arrayOf(
            { modifier -> Greeting(
                modifier = modifier,
                groupedCalendars = userCalendars.value,
                hasSelectedDir = hasSelectedDir.value,
            ) },
            { modifier -> Text("hiii!!!!", modifier = modifier) }
        )

        this.setContent {
            CalProvExampleTheme {
                val containerColor = MaterialTheme.colorScheme.primaryContainer
                val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
                val selectedTab = remember { mutableIntStateOf(0) }

                /* FIXME: the content colors of the TopBar and TabBar are only in sync when using MediumTopAppBar.
                 * At that point just put the TabBar inside the TopBar (but that doesn't look good either) */
                // Hoist up the animation state from inside the TopAppBar to control both the TopBar and TabBar.
                // -- Adapted from TopAppBar -> SingleRowTopAppBar:
                // > Obtain the container color from the TopAppBarColors using the `overlapFraction`. This
                // > ensures that the colors will adjust whether the app bar behavior is pinned or scrolled.
                // > This may potentially animate or interpolate a transition between the container-color and the
                // > container's scrolled-color according to the app bar's scroll state.
                val isScrolled = scrollBehavior.state.overlappedFraction <= 0.01f
                val topBarContainerColor by animateColorAsState(
                    targetValue = if (isScrolled) ComposeColor(0) else containerColor,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "topAppBarContainerColorAnimation"
                )

                Scaffold(
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentWindowInsets = WindowInsets.systemBars,
                    topBar = { TopBar(
                        title = { Text(stringResource(R.string.app_name), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        // title = { TabBar(selectedTab = selectedTab, containerColor = ComposeColor(0) },
                        containerColor = topBarContainerColor,
                        scrollBehavior = scrollBehavior
                    ) }
                ) { paddingValues ->
                    Column(modifier = Modifier.padding(paddingValues)) {
                        TabBar(selectedTab = selectedTab, containerColor = topBarContainerColor)

                        // TODO: add anchoredDraggable modifier
                        tabScreens[selectedTab.intValue](Modifier)
                    }
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
        Column(
            modifier.padding(MIDDLE_PADDING.dp),
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
                HorizontalDivider(Modifier.padding(vertical = (LIST_ITEM_SPACING * 4).dp))
            }

            if (groupedCalendars == null) {
                Text("Please allow ${stringResource(R.string.app_name)} to read and write yo your device's calendar")
                IconTextButton(
                    icon = painterResource(R.drawable.outline_sync_24),
                    text = "Sync",
                    onclick = { calendarQueryManager.runAction() }
                )
            } else {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    tonalElevation = 1.dp,
                    shadowElevation = 5.dp
                ) {
                    LazyColumn(
                        Modifier
                            .padding(4.dp)
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
                                        color = MaterialTheme.colorScheme.secondary,
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

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun TopBar(
        modifier: Modifier = Modifier,
        containerColor: ComposeColor = MaterialTheme.colorScheme.background,
        scrollBehavior: TopAppBarScrollBehavior? = null,
        title: @Composable () -> Unit,
    ) {
        val contentColor = MaterialTheme.colorScheme.primary

        MediumTopAppBar(
            modifier = modifier,
            scrollBehavior = scrollBehavior,
            title = { title() },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = containerColor,
                scrolledContainerColor = containerColor,
                titleContentColor = contentColor,
                navigationIconContentColor = contentColor,
                actionIconContentColor = contentColor,
            ),
            actions = {
                IconButton(onClick = { /*TODO*/ }) {
                    Icon(Icons.Default.Settings, "Settings")
                }
            }
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun TabBar(
        modifier: Modifier = Modifier,
        containerColor: ComposeColor =  MaterialTheme.colorScheme.primaryContainer,
        selectedTab: MutableIntState = mutableIntStateOf(0),
    ) {
        val tabs = arrayOf(
            Pair(Icons.Default.DateRange, "Calendars"),
            Pair(Icons.Default.AccountCircle, "Contacts")
        )

        PrimaryTabRow(
            modifier = modifier,
            selectedTabIndex = selectedTab.intValue,
            containerColor = containerColor,
        ) {
            tabs.forEachIndexed { i, tab ->
                // Tab(
                //     icon = { Icon(tab.first, null) },
                //     text = { Text(tab.second, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp) },
                //     selectedContentColor = MaterialTheme.colorScheme.primary,
                //     unselectedContentColor = MaterialTheme.colorScheme.onSurface,
                //     selected = selectedTab.intValue == i,
                //     onClick = { selectedTab.intValue = i }
                // )

                // Use the regular Tab when there is more than 2 tabs.
                LeadingIconTab(
                    icon = { Icon(tab.first, null) },
                    text = { Text(tab.second, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp) },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurface,
                    selected = selectedTab.intValue == i,
                    onClick = { selectedTab.intValue = i }
                )
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun CalendarListItem(cal: UserCalendarListItem) {
        // FIXME: LazyColumn resets state when it hides the item on scroll.
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
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    state = rememberTooltipState(),
                    tooltip = {
                        this.PlainTooltip {
                            Text("This calendar is synced")
                        }
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Calendars list item",
                        tint = ComposeColor(cal.color.R.toInt(), cal.color.G.toInt(), cal.color.B.toInt())
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
    @OptIn(ExperimentalMaterial3Api::class)
    @Preview(showBackground = true)
    @Composable
    fun TopBarPreview() {
        CalProvExampleTheme {
            Column {
                TopBar(title = { Text("Title") } )
                TabBar()
                // TabBar(onTabSelect = { tab -> selectedTab = tab })
            }
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

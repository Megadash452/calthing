package me.marti.calprovexample.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import me.marti.calprovexample.NonEmptyList
import me.marti.calprovexample.R
import me.marti.calprovexample.UserCalendarListItem
import me.marti.calprovexample.ui.theme.CalProvExampleTheme
import androidx.compose.ui.graphics.Color as ComposeColor

private const val OUTER_PADDING = 8
private const val LIST_PADDING = 4
private const val LIST_ITEM_ELEVATION = 1
private const val LIST_ITEM_SPACING = 4
private const val PREVIEW_WIDTH = 300

private val TOP_BAR_COLOR = ComposeColor(0)
private val TOP_BAR_SCROLLER_COLOR
    @Composable get() = MaterialTheme.colorScheme.primaryContainer
private val TOP_BAR_CONTENT_COLOR
    @Composable get() = MaterialTheme.colorScheme.primary


/** The `Main` content of the app.
 * Contains the scaffolding with the `TopBar` and the `Calendars` and `Contacts` components.
 *
 * @param navigateTo The logic behind the navigation. Takes in a *destination* to navigate to. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(
    modifier: Modifier = Modifier,
    navigateTo: (NavDestination) -> Unit = {},
    hasSelectedDir: Boolean = false,
    selectDirClick: () -> Unit = {},
    groupedCalendars: GroupedList<String, UserCalendarListItem>?,
    addCalendar: () -> Unit = {},
    calPermsClick: () -> Unit = {},
    calIsSynced: (Long) -> Boolean,
    onCalSwitchClick: (Long, Boolean) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var actionsExpanded by remember { mutableStateOf(false) }
    val selectedTab = rememberSaveable { mutableIntStateOf(0) }
    val tabController = remember { TabNavController(
        selectedIdx = selectedTab,
        tabs = listOf(
            PrimaryTabNavDestinationWithFab(
                icon = Icons.Default.DateRange,
                fabActions = NonEmptyList(
                    first = ExpandableFABAction(Icons.Default.Create, "New blank calendar", addCalendar),
                    ExpandableFABAction(R.drawable.rounded_calendar_add_on_24, "Device calendar") { /*TODO*/ },
                    ExpandableFABAction(R.drawable.rounded_upload_file_24, "Import from file") { /*TODO*/ },
                ),
                title = "Calendars",
            ) { modifier ->
                Calendars(
                    modifier = modifier,
                    groupedCalendars = groupedCalendars,
                    hasSelectedDir = hasSelectedDir,
                    selectDirClick = selectDirClick,
                    calPermsClick =  calPermsClick,
                    calIsSynced = calIsSynced,
                    onCalSwitchClick = onCalSwitchClick
                )
            },
            PrimaryTabNavDestination(
                icon = Icons.Default.AccountCircle,
                title = "Contacts",
            ) { modifier -> Text("Contacts section", modifier = modifier) },
        )
    ) }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.systemBars,
        floatingActionButton = {
            // AnimatedVisibility(visible = tabController.selectedTab is PrimaryTabNavDestinationWithFab) {
            (tabController.selectedTab as? PrimaryTabNavDestinationWithFab)?.let { tabWithFab ->
                ExpandableFloatingActionButtons(
                    expanded = actionsExpanded,
                    expand = { actionsExpanded = true },
                    description = "Add/New Calendar",
                    icon = Icons.Default.Add,
                    actions = tabWithFab.fabActions
                )
            }
        },
    ) { paddingValues ->
        Column(Modifier.padding(bottom = paddingValues.calculateBottomPadding())) {
            TopBar(
                title = stringResource(R.string.app_name),
                scrollBehavior = scrollBehavior,
                tabController = tabController,
                actions = {
                    IconButton(onClick = { navigateTo(NavDestination.Debug) }) {
                        Icon(Icons.Default.Build, "Debug")
                    }
                    IconButton(onClick = { navigateTo(NavDestination.Settings) }) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                }
            )
            // TODO: add anchoredDraggable modifier
            tabController.SelectedContent()
        }
        ExpandedFabBackgroundOverlay(expanded = actionsExpanded) { actionsExpanded = false }
    }
}


private val topBarTitle = @Composable { title: String -> Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
private val topBarNavigationIcon = @Composable { navUpClick: (() -> Unit)? ->
    if (navUpClick != null) {
        IconButton(onClick = navUpClick) {
            Icon(Icons.AutoMirrored.Default.ArrowBack, "Navigation Button Up")
        }
    }
}
/** The `TopBar` is the top-most element of the UI, and nothing should be rendered above it.
 * It shows the **title** of the main content being rendered.
 *
 * See the overload that takes a **`TabController`* for a `TopBar` with a `TabBar`.
 *
 * @param title The title of the content currently being shown.
 * @param actions Action IconButtons that appear on the right side of the `TopBar`.
 * @param navUpClick An optional BackButton that navigates to the previous `NavDestination` will be shown if this is not *`NULL`*. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    title: String,
    actions: @Composable (RowScope.() -> Unit) = {},
    navUpClick: (() -> Unit)? = null,
) {
    /* When there is no TabController, use a simple TopAppBar.
     * No Need to reduce its size as it already gives enough space for content. */
    TopAppBar(
        modifier = modifier,
        scrollBehavior = scrollBehavior,
        title = { topBarTitle(title) },
        navigationIcon = { topBarNavigationIcon(navUpClick) },
        actions = actions,
    )
}
/** The `TopBar` is the top-most element of the UI, and nothing should be rendered above it.
 * It shows the **title** of the main content being rendered, and optionally any navigation (including a `TabBar`).
 * The `TopBar` will reduce its *height* on scroll to make more space for the content while also showing the `TabBar`.
 *
 * See the first overload for a `TopBar` *without* a `TabBar`.
 *
 * @param title The title of the content currently being shown.
 * @param actions Action IconButtons that appear on the right side of the `TopBar`.
 * @param navUpClick An optional BackButton that navigates to the previous `NavDestination` will be shown if this is not *`NULL`*.
 * @param tabController An optional set of *`Tabs`* to show a **`TabBar`** for. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T: TabNavDestination> TopBar(
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    title: String,
    actions: @Composable (RowScope.() -> Unit) = {},
    navUpClick: (() -> Unit)? = null,
    tabController: TabNavController<T>
) {
    /* FIXME: the content colors of the TopBar and TabBar are only in sync when using MediumTopAppBar.
             * At that point just put the TabBar inside the TopBar (but that doesn't look good either) */
    // Hoist up the animation state from inside the TopAppBar to control both the TopBar and TabBar.
    // -- Adapted from TopAppBar -> SingleRowTopAppBar:
    // > Obtain the container color from the TopAppBarColors using the `overlapFraction`. This
    // > ensures that the colors will adjust whether the app bar behavior is pinned or scrolled.
    // > This may potentially animate or interpolate a transition between the container-color and the
    // > container's scrolled-color according to the app bar's scroll state.
    val isScrolled = if (scrollBehavior == null) {
        false
    } else {
        scrollBehavior.state.overlappedFraction <= 0.01f
    }
    val containerColor by animateColorAsState(
        targetValue = if (isScrolled) TOP_BAR_COLOR else TOP_BAR_SCROLLER_COLOR,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "topAppBarContainerColorAnimation"
    )

    Column(modifier = modifier) {
        /* When there is a TabController, use a MediumTopAppBar that changes size on scroll
         * and gives more room for content while still showing the TabBar. */
        MediumTopAppBar(
            scrollBehavior = scrollBehavior,
            title = { topBarTitle(title) },
            navigationIcon = { topBarNavigationIcon(navUpClick) },
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = containerColor,
                scrolledContainerColor = containerColor,
                titleContentColor = TOP_BAR_CONTENT_COLOR,
                navigationIconContentColor = TOP_BAR_CONTENT_COLOR,
                actionIconContentColor = TOP_BAR_CONTENT_COLOR,
            ),
        )

        TabBar(
            containerColor = containerColor,
            controller = tabController
        )
    }
}

/** The `TabBar` is responsible for switching the content view using the controller on the user's request.
 *
 * The tab bar should be rendered right under the `TopBar` in composition. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T: TabNavDestination> TabBar(
    modifier: Modifier = Modifier,
    containerColor: Color =  MaterialTheme.colorScheme.primaryContainer,
    controller: TabNavController<T>
) {
    @Composable
    fun TabTitle(title: String) {
        Text(
            title,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
        )
    }

    PrimaryTabRow(
        modifier = modifier,
        selectedTabIndex = controller.selectedIdx.intValue,
        containerColor = containerColor,
        divider = { HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.surfaceVariant) },
        // indicator = { TabRowDefaults.PrimaryIndicator() }
    ) {
        // Is not NULL only when tabs are PrimaryTabNavDestination
        val getIcon: (@Composable (T) -> Unit)? =
            if (controller.tabs.all { it is PrimaryTabNavDestination }) { tab: T ->
                Icon((tab as PrimaryTabNavDestination).icon, null, modifier = Modifier.size(24.dp))
            } else {
                null
            }
        // When there are more than 2 Tabs, or the caller decides to use SimpleTabs (no icon), use regular Tab Composable.
        val composeTab: @Composable (Int, T) -> Unit = if (controller.tabs.size <= 2 && getIcon != null) { i, tab ->
            LeadingIconTab(
                icon = { getIcon(tab) },
                text = { TabTitle(tab.title) },
                selectedContentColor = TOP_BAR_CONTENT_COLOR,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                selected = controller.selectedIdx.intValue == i,
                onClick = { controller.selectedIdx.intValue = i }
            )
        } else { i, tab ->
            androidx.compose.material3.Tab(
                icon = if (getIcon == null) null else { { getIcon(tab) } },
                text = { TabTitle(tab.title) },
                selectedContentColor = TOP_BAR_CONTENT_COLOR,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                selected = controller.selectedIdx.intValue == i,
                onClick = { controller.selectedIdx.intValue = i }
            )
        }
        controller.tabs.forEachIndexed{ i, tab -> composeTab(i, tab) }
    }
}

/** Used in **`ExpandableFloatingActionButtons`** */
class ExpandableFABAction(
    val icon: @Composable () -> Unit,
    val label: String,
    val onClick: () -> Unit
) {
    constructor(icon: ImageVector, label: String, onClick: () -> Unit):
            this({ Icon(icon, null) }, label, onClick)
    constructor(@DrawableRes icon: Int, label: String, onClick: () -> Unit):
            this({ Icon(painterResource(icon), null) }, label, onClick)
}

/** A `FloatingActionButton` that can be expanded to show a set of multiple *sub actions*.
 *
 * When the main `FAB` (*collapsed mode*) is pressed, `Small FAB`s will expand upwards
 * and the main `FAB` (*expanded mode*) will change to the first sub-action.
 * When in *expanded mode*, an overlay will darken all content with an **overlay** behind the
 * `FAB`s to place the focus on the `FAB`s.
 *
 * Also when in *expanded mode*, clicking the darkening overlay will revert the state to *collapsed mode*.
 * Clicking the back button (or doing the back gesture, depending on system settings) should also
 * revert the state to *collapsed mode*, but this part is **TODO: *not yet implemented***.
 *
 * See the `Scaffold` in **`MainContent()`** for an example of how to use this Composable.
 * @see MainContent
 * @see ExpandedFabBackgroundOverlay
 *
 * @param expanded Whether the state of the buttons is in *expanded* or *collapsed mode*.
 * @param expand A function called when the main `FAB` is clicked in *collapsed mode*,
 *               will set the value of **expanded** to `true`.
 * @param description The overall *description* of all the actions to show in a `Tooltip`.
 * @param icon The icon shown in the `FAB` in *collapsed mode*.
 * @param actions The sub-actions shown in *expanded mode*. They are rendered *from the bottom up*.
 *                The first **action** in the list will replace the main `FAB` in *expanded mode*.
 *                The rest of the actions will compose `Small FAB`s above the fist `FAB`.
 * */
@Composable
private fun ExpandableFloatingActionButtons(
    modifier: Modifier = Modifier,
    expanded: Boolean,
    expand: () -> Unit = {},
    description: String? = null,
    icon: ImageVector,
    actions: NonEmptyList<ExpandableFABAction>
) {
    var fabWidth by remember { mutableStateOf(0.dp) }
    val mainFabColor by animateColorAsState(
        if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
        label = "Main FAB Container Color Animation"
    )
    val spacing = 12.dp
    @Composable
    fun ActionLabel(text: String) {
        AnimatedVisibility(visible = expanded) {
            Text(text, fontWeight = FontWeight.SemiBold)
        }
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        // Sub actions are shown from the bottom up, so it is reversed
        remember { actions.rest.reversed() }.forEach { action ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing)) {
                ActionLabel(action.label)
                AnimatedVisibility(visible = expanded, enter = expandIn(), exit = shrinkOut()) {
                    Box(Modifier.width(fabWidth), contentAlignment = Alignment.Center) {
                        SmallFloatingActionButton(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.primary,
                            onClick = action.onClick,
                            content = action.icon
                        )
                    }
                }
            }
        }
        Row(modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing)) {
            ActionLabel(actions.first.label)
            val mainFab = @Composable {
                FloatingActionButton(
                    containerColor = mainFabColor,
                    // When actions are expanded, the FAB will change to one of the actions
                    onClick = if (expanded) actions.first.onClick else expand
                ) {
                    Crossfade(targetState = expanded, label = "Calendars FAB Expanded") { expanded ->
                        if (expanded)
                            actions.first.icon()
                        else
                            Icon(icon, description)
                    }
                }
            }
            val density = LocalDensity.current
            Box(modifier = Modifier.onGloballyPositioned {
                fabWidth = with(density) { it.size.width.toDp() }
            }) {
                if (description != null)
                    PlainTooltipBox(
                        tooltipContent = { Text(description) },
                        content = mainFab
                    )
                else
                    mainFab()
            }
        }
    }
}
/** Dialog-like overlay that darkens the content of the screen to place focus on the `FAB`.
 * The overlay sits between the *content* of the screen and the `FAB`.
 * The overlay will block all input for the content, but not for the `FAB`.
 *
 * The overlay is shown when the `FAB`s are in *expanded mode*, and hidden when in *collapsed mode*.
 * When the overlay is **clicked**, it will set the **expanded** value to `false`.
 *
 * See the content of the `Scaffold` in **`MainContent`** for an example of how to use this overlay.
 * @see ExpandableFloatingActionButtons */
@Composable
private fun ExpandedFabBackgroundOverlay(modifier: Modifier = Modifier, expanded: Boolean, collapse: () -> Unit) {
    AnimatedVisibility(modifier = modifier, visible = expanded, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xc3000000))
                .clickable(onClick = collapse)
        )
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
private fun Calendars(
    modifier: Modifier = Modifier,
    hasSelectedDir: Boolean = false,
    selectDirClick: () -> Unit = {},
    groupedCalendars: GroupedList<String, UserCalendarListItem>?,
    calPermsClick: () -> Unit = {},
    calIsSynced: (Long) -> Boolean = { false },
    onCalSwitchClick: (Long, Boolean) -> Unit = { _, _ -> }
) {
    Column(modifier = modifier) {
        if (!hasSelectedDir) {
            Column(
                modifier = Modifier.padding(OUTER_PADDING.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Please select a directory where to sync Calendars and Contacts.")
                IconTextButton(
                    icon = painterResource(R.drawable.round_folder_24),
                    text = "Select",
                    onclick = selectDirClick
                )
            }
            HorizontalDivider(Modifier.padding(vertical = (LIST_ITEM_SPACING * 2).dp))
        }

        if (groupedCalendars == null) {
            Column(
                modifier = Modifier.padding(OUTER_PADDING.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Please allow ${stringResource(R.string.app_name)} to read and write yo your device's calendar")
                IconTextButton(
                    icon = painterResource(R.drawable.outline_sync_24),
                    text = "Sync",
                    onclick = calPermsClick
                )
            }
        } else {
            LazyColumn(
                Modifier
                    .padding(horizontal = LIST_PADDING.dp).padding(top = LIST_PADDING.dp)
                    .clip(MaterialTheme.shapes.small),
                verticalArrangement = Arrangement.spacedBy(LIST_ITEM_SPACING.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
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
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }
                    this.items(calGroup, key = { cal -> cal.id }) { cal ->
                        CalendarListItem(
                            cal = cal,
                            isSynced = calIsSynced(cal.id),
                            onSwitchClick = { checked -> onCalSwitchClick(cal.id, checked) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarListItem(cal: UserCalendarListItem, isSynced: Boolean = false, onSwitchClick: (Boolean) -> Unit = {}) {
    ListItem(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .fillMaxWidth(),
        tonalElevation = LIST_ITEM_ELEVATION.dp,
        headlineContent = { Text(cal.name) },
        supportingContent = {
            // Don't show any status if the user has not selected this calendar for syncing
            if (isSynced) {
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
                tooltipContent = { Text("This calendar is synced") },
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = "Calendars list item",
                    tint = cal.color.toColor()
                )
            }
        },
        trailingContent = {
            Switch(
                checked = isSynced,
                onCheckedChange = onSwitchClick
            )
        },
    )
}


/** The top-level composable for the settings page.
 * Contains the scaffolding and the TopBar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    modifier: Modifier = Modifier,
    navUpClick: () -> Unit = {},
    settings: List<@Composable () -> Unit>,
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.systemBars,
        topBar = { TopBar(
            title = "Settings",
            navUpClick = navUpClick
        ) }
    ) { paddingValues ->
        Settings(modifier = Modifier.padding(paddingValues), settings)
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

/** The new `TooltipBox` is more verbose than the Plain/RichTooltipBox in the previous version... */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlainTooltipBox(modifier: Modifier = Modifier, tooltipContent: @Composable () -> Unit, content: @Composable () -> Unit) {
    TooltipBox(
        modifier = modifier,
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        state = rememberTooltipState(),
        tooltip = { this.PlainTooltip {
            tooltipContent()
        } },
        content = content
    )
}


@Preview(showBackground = true, widthDp = PREVIEW_WIDTH)
@Composable
fun CalendarsPreview() {
    val acc = "me@mydomain.me"

    CalProvExampleTheme {
        Calendars(
            hasSelectedDir = true,
            groupedCalendars = arrayOf(
                UserCalendarListItem(
                    id = 0,
                    name = "Personal",
                    accountName = acc,
                    color = me.marti.calprovexample.Color("cd58bb")
                ),
                UserCalendarListItem(
                    id = 1,
                    name = "Friend",
                    accountName = "Friend",
                    color = me.marti.calprovexample.Color("58cdc9")
                ),
                UserCalendarListItem(
                    id = 2,
                    name = "Work",
                    accountName = acc,
                    color = me.marti.calprovexample.Color("5080c8")
                )
            ).groupBy { cal -> cal.accountName }
        )
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, widthDp = PREVIEW_WIDTH)
@Composable
fun TopBarPreview() {
    CalProvExampleTheme {
        Column {
            TopBar(
                title = "Title",
                tabController = TabNavController(tabs = listOf(
                    PrimaryTabNavDestination(Icons.Default.DateRange, "Calendars") {},
                    PrimaryTabNavDestination(Icons.Default.AccountCircle, "Contacts") {}
                ))
            )
        }
    }
}
@Preview(showBackground = true, widthDp = PREVIEW_WIDTH)
@Composable
fun SettingsPreview() {
    CalProvExampleTheme {
        Settings(settings = listOf(
            { BooleanSetting(name = "example", summary = "Example boolean setting with summary") },
            { DirSetting(name = "Syncing Directory", value = "content://com.android.calendar/primary%3ASyncthing".toUri()) }
        ))
    }
}
@Preview(showBackground = true, widthDp = PREVIEW_WIDTH)
@Composable
fun GreetingNoPermPreview() {
    CalProvExampleTheme {
        Calendars(groupedCalendars = null)
    }
}
@Preview(widthDp = PREVIEW_WIDTH)
@Composable
fun CalendarPermissionRationaleDialogPreview() {
    CalProvExampleTheme {
        CalendarRationaleDialog()
    }
}

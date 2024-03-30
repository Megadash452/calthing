package me.marti.calprovexample.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Delete
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
import me.marti.calprovexample.GroupedList
import me.marti.calprovexample.NonEmptyList
import me.marti.calprovexample.R
import me.marti.calprovexample.calendar.UserCalendarListItem
import me.marti.calprovexample.ui.theme.CalProvExampleTheme
import androidx.compose.ui.graphics.Color
import me.marti.calprovexample.calendar.InternalUserCalendar
import me.marti.calprovexample.tryMap
import me.marti.calprovexample.Color as InternalColor

const val OUTER_PADDING = 8
private const val LIST_PADDING = 4
private const val LIST_ITEM_ELEVATION = 1
private const val LIST_ITEM_SPACING = 4
private const val PREVIEW_WIDTH = 300

private val TOP_BAR_COLOR = Color(0)
private val TOP_BAR_SCROLLER_COLOR
    @Composable get() = MaterialTheme.colorScheme.primaryContainer
private val TOP_BAR_CONTENT_COLOR
    @Composable get() = MaterialTheme.colorScheme.primary


/** The `Main` screen of the App. Contains the necessary `Scaffold`.
 * Can show different **content** but this keeps it consistent.
 *
 * The different **content** are separated into *tabs*.
 * Add a **tab** for a content using the DSL **[MainContentScope]**.
 *
 * Example:
 * ```kt
 * MainContent(...) {
 *     this.tab(title = "Tab1") { modifier -> Text("Hi i i :3") }
 * }
 * ```
 *
 * @param navigateTo The logic behind the navigation. Takes in a *destination* to navigate to. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(
    modifier: Modifier = Modifier,
    navigateTo: (NavDestination) -> Unit = {},
    content: MainContentScope.() -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var actionsExpanded by remember { mutableStateOf(false) }
    val tabController = TabNavController(
        selectedIdx = rememberSaveable { mutableIntStateOf(0) },
        tabs = remember {
            // Build the set of tabs that the caller wants to compose
            MainContentScope().apply { this.content() }.tabs
        }
    )

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.systemBars,
        floatingActionButton = {
            AnimatedContent(
                targetState = (tabController.selectedTab as? PrimaryTabNavDestinationWithFab)?.fab,
                label = "FAB transition animation"
            ) { fab ->
                if (fab != null) {
                    ExpandableFloatingActionButtons(
                        expanded = actionsExpanded,
                        expand = { actionsExpanded = true },
                        collapse = { actionsExpanded = false },
                        data = fab
                    )
                }
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
/** A DSL to add **content** to the `MainContent` composable.
 * Use one of the public `tab` functions to add the `Tab` data and the *content*.
 *
 * **Parameters** for the functions:
 *  - ***`title`***: (*Required*) The title of the `Tab` in the `TopBar`.
 *  - ***`content`***: (*Required*) The content that will be shown when this tab is selected.
 *  - ***`icon`***: (*Optional*) An icon that will appear next to (or above) the *title* in the `Tab`.
 *  - ***`fab`***: (*Optional*) A `FloatingActionButton` that will render with the *content* of this tab.
 *                 Must call **`this.tabWithFab()`** for this parameter.
 *
 * @see MainContent */
@Suppress("unused", "MemberVisibilityCanBePrivate")
class MainContentScope {
    internal var tabs: MutableList<TabNavDestination> = mutableListOf()

    // Add tab without an icon
    fun tab(title: String, content: @Composable (Modifier) -> Unit) {
        this.tabs.add(SimpleTabNavDestination(title, content))
    }

    // Add tabs with icons
    fun tab(icon: ImageVector, title: String, content: @Composable (Modifier) -> Unit) =
        this.tab({ Icon(icon, null) }, title, content)
    fun tab(@DrawableRes icon: Int, title: String, content: @Composable (Modifier) -> Unit) =
        this.tab({ Icon(painterResource(icon), null) }, title, content)
    fun tab(icon: @Composable () -> Unit, title: String, content: @Composable (Modifier) -> Unit) {
        this.tabs.add(PrimaryTabNavDestination(icon, title, content))
    }

    // Add Tab with a FloatingActionButton
    fun tabWithFab(icon: ImageVector, title: String, fab: ExpandableFab, content: @Composable (Modifier) -> Unit) =
        this.tabWithFab({ Icon(icon, null) }, title, fab, content)
    fun tabWithFab(@DrawableRes icon: Int, title: String, fab: ExpandableFab, content: @Composable (Modifier) -> Unit) =
        this.tabWithFab({ Icon(painterResource(icon), null) }, title, fab, content)
    fun tabWithFab(icon: @Composable () -> Unit, title: String, fab: ExpandableFab, content: @Composable (Modifier) -> Unit) {
        this.tabs.add(PrimaryTabNavDestinationWithFab(icon, title, fab, content))
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
    fun TabTitle(title: String)
        = Text(title, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)

    PrimaryTabRow(
        modifier = modifier,
        selectedTabIndex = controller.selectedIdx.intValue,
        containerColor = containerColor,
        divider = { HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.surfaceVariant) },
    ) {
        // Show Icon only if ALL Tabs have icons, or if there are no more than 2 tabs.
        controller.tabs.tryMap {
            if (controller.tabs.size <= 2)
                it as? PrimaryTabNavDestination
            else
                null
        }?.let { tabs ->
            // T has icons
            tabs.forEachIndexed { i, tab ->
                LeadingIconTab(
                    icon = tab.icon,
                    text = { TabTitle(tab.title) },
                    selectedContentColor = TOP_BAR_CONTENT_COLOR,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selected = controller.selectedIdx.intValue == i,
                    onClick = { controller.selectedIdx.intValue = i }
                )
            }
        } ?: run {
            // T doesn't have icons
            controller.tabs.forEachIndexed { i, tab ->
                androidx.compose.material3.Tab(
                    text = { TabTitle(tab.title) },
                    selectedContentColor = TOP_BAR_CONTENT_COLOR,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selected = controller.selectedIdx.intValue == i,
                    onClick = { controller.selectedIdx.intValue = i }
                )
            }
        }
    }
}

/** Used in **`ExpandableFloatingActionButtons`**.
 * @see ExpandableFloatingActionButtons
 * @param description The overall *description* of all the actions, to show in a `Tooltip`.
 * @param icon The icon shown in the `FAB` in *collapsed mode*.
 * @param actions The sub-actions shown in *expanded mode*. They are rendered *from the bottom up*.
 *                The first **action** in the list will replace the main `FAB` in *expanded mode*.
 *                The rest of the actions will compose `Small FAB`s above the first `FAB`. */
// Primary constructor is private to overload constructors with different icon variants.
@Suppress("unused")
class ExpandableFab private constructor(
    val icon: @Composable () -> Unit,
    val description: String?,
    val actions: NonEmptyList<Action>
) {
    constructor(icon: ImageVector, description: String, actions: NonEmptyList<Action>):
            this({ Icon(icon, description) }, description, actions)
    constructor(@DrawableRes icon: Int, description: String, actions: NonEmptyList<Action>):
            this({ Icon(painterResource(icon), description) }, description, actions)

    class Action(
        val icon: @Composable () -> Unit,
        val label: String,
        val onClick: () -> Unit
    ) {
        constructor(icon: ImageVector, label: String, onClick: () -> Unit):
                this({ Icon(icon, null) }, label, onClick)
        constructor(@DrawableRes icon: Int, label: String, onClick: () -> Unit):
                this({ Icon(painterResource(icon), null) }, label, onClick)
    }
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
 * @param collapse A function called when an Action Button is clicked in *expanded mode*,
 *                 will set the value of **expanded** to `false`.
 * @param data The data for the `FloatingActionButton`. */
@Composable
private fun ExpandableFloatingActionButtons(
    modifier: Modifier = Modifier,
    expanded: Boolean,
    expand: () -> Unit = {},
    collapse: () -> Unit = {},
    data: ExpandableFab
) {
    var fabWidth by remember { mutableStateOf(0.dp) }
    val spacing = 12.dp
    @Composable
    fun ActionLabel(text: String) {
        AnimatedVisibility(visible = expanded) {
            Text(text, fontWeight = FontWeight.SemiBold)
        }
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        // Sub actions are shown from the bottom up, so it is reversed
        remember { data.actions.rest.reversed() }.forEach { action ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing)) {
                ActionLabel(action.label)
                // FIXME: shadows are very glitchy when animating (for both the big and small fabs) (probably has to do with AnimatedVisibility clipping its content during animation)
                // RowScope (this) has its own AnimatedVisibility which has slightly different animations.
                this.AnimatedVisibility(visible = expanded, enter = expandIn(), exit = shrinkOut()) {
                    Box(Modifier.width(fabWidth), contentAlignment = Alignment.Center) {
                        SmallFloatingActionButton(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary,
                            onClick = {
                                collapse()
                                action.onClick()
                            },
                            content = action.icon
                        )
                    }
                }
            }
        }
        Row(modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing)) {
            ActionLabel(data.actions.first.label)
            val mainFab = @Composable {
                val density = LocalDensity.current
                FloatingActionButton(
                    modifier = Modifier.onGloballyPositioned {
                        fabWidth = with(density) { it.size.width.toDp() }
                    },
                    containerColor = animateColorAsState(
                        if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                        label = "Main FAB Container Color Animation"
                    ).value,
                    // When actions are expanded, the FAB will change to one of the actions
                    onClick = if (expanded) { {
                        collapse()
                        data.actions.first.onClick()
                    } } else
                        expand
                ) {
                    Crossfade(targetState = expanded, label = "Calendars FAB Expanded") { expanded ->
                        if (expanded)
                            data.actions.first.icon()
                        else
                            data.icon()
                    }
                }
            }

            if (data.description != null && !expanded)
                PlainTooltipBox(
                    tooltipContent = { Text(data.description) },
                    content = mainFab
                )
            else
                mainFab()
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
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)) // 0xe1000000
                .clickable(onClick = collapse)
        )
    }
}

/**
 * The starting screen for the Main Activity.
 *
 * @param hasSelectedDir Shows the user a button to select a sync dir if `false`.
 * @param groupedCalendars
 *   A list of Calendars that the user has created/imported to sync with this App.
 *   The list is grouped by *`accountName`* // TODO: will ungroup this in the future since all calendars passed in will have the same accountName.
 *   If **`NULL`** is passed in, a Button to request Calendar Permissions will be shown.
 * @param calPermsClick
 *   When the app doesn't have Calendar Permissions, [groupedCalendars] will be **`NULL`**
 *   and this will show a Button that will call *this function* when clicked to *request the permission*.
 * @param syncCalendarSwitch
 *   Runs when the user toggles the **Sync** switch for a Calendar.
 *   #### Arguments
 *   1. (*`Long`*): The **ID** of the Calendar.
 *   2. (*`Boolean`*): Whether the switch is *checked* (should enable *syncing* for this Calendar).
 * @param editCalendar
 *   Runs when the user clicks the **Edit** Button for a Calendar.
 *   #### Arguments
 *   1. (*`Long`*): The **ID** of the Calendar.
 *   2. (*`String`*): The current **name** of the Calendar.
 *   3. (*`Color`*): The current **color** of the Calendar.
 * @param deleteCalendar
 *   Runs when the user clicks the **Delete** Button for a Calendar.
 *   #### Arguments
 *   1. (*`Long`*): The **ID** of the Calendar. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Calendars(
    modifier: Modifier = Modifier,
    hasSelectedDir: Boolean = false,
    selectDirClick: () -> Unit = {},
    groupedCalendars: GroupedList<String, InternalUserCalendar>?,
    calPermsClick: () -> Unit = {},
    syncCalendarSwitch: (Long, Boolean) -> Unit = { _, _ -> },
    editCalendar: (Long, String, InternalColor) -> Unit = { _, _, _ -> },
    deleteCalendar: (Long) -> Unit = {}
) {
    @Composable
    fun InfoColumn(content: @Composable ColumnScope.() -> Unit) {
        Column(
            modifier = Modifier.padding(OUTER_PADDING.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }

    Column(modifier = modifier) {
        if (!hasSelectedDir) {
            InfoColumn {
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
            InfoColumn {
                Text("Please allow ${stringResource(R.string.app_name)} to read and write yo your device's calendar")
                IconTextButton(
                    icon = painterResource(R.drawable.outline_sync_24),
                    text = "Sync",
                    onclick = calPermsClick
                )
            }
        } else if (groupedCalendars.isEmpty()) {
            InfoColumn {
                Text("There are currently no calendars.")
                Text("Try importing one by clicking the \"Add\" Button.")
            }
        } else {
            // Items in this list can be expanded to show more actions. Value is the id of the expanded item.
            var expandedItem: Long? by rememberSaveable { mutableStateOf(null) }
            LazyColumn(
                Modifier.padding(top = LIST_PADDING.dp),
                verticalArrangement = Arrangement.spacedBy(LIST_ITEM_SPACING.dp),
                contentPadding = PaddingValues(bottom = 80.dp, start = LIST_PADDING.dp, end = LIST_PADDING.dp)
            ) {
                groupedCalendars.forEach { (accountName, calGroup) ->
                    this.stickyHeader { StickyHeader(text = accountName) }

                    this.items(calGroup, key = { cal -> cal.id }) { cal ->
                        CalendarListItem(
                            cal = cal,
                            isSynced = cal.sync,
                            expanded = expandedItem == cal.id,
                            expandedToggle = {
                                // if user clicks on the expandedItem, it will be collapsed
                                expandedItem = if (expandedItem == cal.id) null else cal.id
                            },
                            onSwitchClick = { checked -> syncCalendarSwitch(cal.id, checked) },
                            editClick = { editCalendar(cal.id, cal.name, cal.color) },
                            deleteClick = { deleteCalendar(cal.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarListItem(
    cal: UserCalendarListItem,
    isSynced: Boolean = false,
    expanded: Boolean = false,
    expandedToggle: () -> Unit = {},
    onSwitchClick: (Boolean) -> Unit = {},
    editClick: () -> Unit = {},
    deleteClick: () -> Unit = {},
) {
    ListItem(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .fillMaxWidth()
            .clickable(onClick = expandedToggle),
        tonalElevation = LIST_ITEM_ELEVATION.dp,
        headlineContent = { Text(cal.name) },
        supportingContent = {
            Column {
                // Don't show any status if the user has not selected this calendar for syncing
                if (isSynced) {
                    Text(
                        "Status...",
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                AnimatedVisibility(visible = expanded) {
                    Spacer(Modifier.size(4.dp))
                    HorizontalDivider()
                    Row {
                        val buttonSize = 36.dp
                        IconButton(modifier = Modifier.size(buttonSize), onClick = editClick) {
                            Icon(Icons.Default.Create, "Edit Calendar")
                        }
                        IconButton(modifier = Modifier.size(buttonSize), onClick = deleteClick) {
                            Icon(Icons.Default.Delete, "Delete Calendar", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
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


/** The sticky header used in the LazyColumn to list the user's calendars. */
@Composable
fun StickyHeader(modifier: Modifier = Modifier, leadingContent: @Composable () -> Unit = {}, text: String) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        tonalElevation = 0.dp,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            Modifier
                .padding(2.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            leadingContent()
            Text(
                text = text,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall
            )
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

/** The new `TooltipBox` is more verbose than the Plain/RichTooltipBox in the previous version...  */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlainTooltipBox(modifier: Modifier = Modifier, tooltipContent: @Composable () -> Unit, content: @Composable () -> Unit) {
    TooltipBox(
        modifier = modifier,
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        state = rememberTooltipState(),
        tooltip = { this.PlainTooltip(content = tooltipContent) },
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
            ).map { cal -> InternalUserCalendar(cal, false, null) }
                .groupBy { cal -> cal.accountName }
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
                    PrimaryTabNavDestination({ Icon(Icons.Default.DateRange, null) }, "Calendars") {},
                    PrimaryTabNavDestination({ Icon(Icons.Default.AccountCircle, null) }, "Contacts") {}
                ))
            )
        }
    }
}
@Preview(showBackground = true, widthDp = 250, heightDp = 200)
@Composable
fun ExpandedFabPreview() {
    CalProvExampleTheme {
        Box(contentAlignment = Alignment.BottomEnd) {
            ExpandedFabBackgroundOverlay(expanded = true) { }
            ExpandableFloatingActionButtons(
                modifier = Modifier.padding(end = 10.dp, bottom = 10.dp),
                expanded = true,
                data = ExpandableFab(
                    icon = Icons.Default.Add,
                    description = "Action Buttons",
                    actions = NonEmptyList(
                        ExpandableFab.Action(Icons.Default.Create, "Bottom action") { },
                        ExpandableFab.Action(R.drawable.rounded_calendar_add_on_24, "Middle action") { },
                        ExpandableFab.Action(R.drawable.rounded_upload_file_24, "Top action") { },
                    )
                )
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

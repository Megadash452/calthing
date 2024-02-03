package me.marti.calprovexample.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.PlainTooltipBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
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
import me.marti.calprovexample.R
import androidx.compose.ui.graphics.Color as ComposeColor
import me.marti.calprovexample.UserCalendarListItem
import me.marti.calprovexample.ui.theme.CalProvExampleTheme

private const val OUTER_PADDING = 8
private const val MIDDLE_PADDING = 4
private const val LIST_ITEM_SPACING = 4
private const val PREVIEW_WIDTH = 300


/** The `Main` content of the app.
 * Contains the scaffolding with the `TopBar` and the `Calendars` and `Contacts` components. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(
    modifier: Modifier = Modifier,
    settingsClick: () -> Unit = {},
    hasSelectedDir: Boolean = false,
    selectDirClick: () -> Unit = {},
    groupedCalendars: Map<String, List<UserCalendarListItem>>?,
    calPermsClick: () -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    @Suppress("NAME_SHADOWING")
    val tabController = TabNavController(
        selectedIdx = rememberSaveable { mutableIntStateOf(0) },
        tabs = listOf(
            TabNavDestination(
                icon = Icons.Default.DateRange,
                title = "Calendars",
            ) { modifier ->
                Calendars(
                    modifier = modifier,
                    groupedCalendars = groupedCalendars,
                    hasSelectedDir = hasSelectedDir,
                    selectDirClick = selectDirClick,
                    calPermsClick =  calPermsClick
                )
            },
            TabNavDestination(
                icon = Icons.Default.AccountCircle,
                title = "Contacts",
            ) { modifier -> Text("Contacts section", modifier = modifier) },
        )
    )

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            TopBar(
                title = { Text(stringResource(R.string.app_name), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                // title = { TabBar(containerColor = ComposeColor(0), controller = tabController) },
                scrollBehavior = scrollBehavior,
                tabController = tabController,
                settingsClick = settingsClick
            )
        }
    ) { paddingValues ->
        // TODO: add anchoredDraggable modifier
        tabController.Content(modifier = Modifier.padding(paddingValues))
    }
}


/** The TopBar used in the settings page. Only has the title and the Navigation Up button.
 *
 * @param navUpClick The action run when the button to *navigate up* the NavBackStack is clicked. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleTopBar(modifier: Modifier = Modifier, title: String, navUpClick: () -> Unit = {}) {
    TopAppBar(
        modifier = modifier,
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = navUpClick) {
                Icon(Icons.Default.ArrowBack, "Navigation Button Up")
            }
        }
    )
}

/** The `TopBar` is the top-most element of the UI, and nothing should be rendered above it.
 * It shows the **title** of the main content being rendered, and any navigation (including a `TabBar`).
 *
 * @param title The title of the content currently being shown.
 * @param settingsClick What happens when the settings button is clicked. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    title: @Composable () -> Unit,
    settingsClick: () -> Unit = {},
    tabController: TabNavController
) {
    val containerColor = MaterialTheme.colorScheme.primaryContainer
    val contentColor = MaterialTheme.colorScheme.primary

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
    val topBarContainerColor by animateColorAsState(
        targetValue = if (isScrolled) ComposeColor(0) else containerColor,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "topAppBarContainerColorAnimation"
    )

    Column(modifier = modifier) {
        MediumTopAppBar(
            scrollBehavior = scrollBehavior,
            title = { title() },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = topBarContainerColor,
                scrolledContainerColor = topBarContainerColor,
                titleContentColor = contentColor,
                navigationIconContentColor = contentColor,
                actionIconContentColor = contentColor,
            ),
            actions = {
                IconButton(onClick = settingsClick) {
                    Icon(Icons.Default.Settings, "Settings")
                }
            }
        )

        TabBar(
            containerColor = topBarContainerColor,
            controller = tabController
        )
    }
}

/** The `TabBar` is responsible for switching the content view using the controller on the user's request.
 *
 * The tab bar should be rendered right under the `TopBar` in composition.
 *
 * FIXME: This should be using PrimaryTabRow, but that is not available yet. Use primary tab row when 1.2.0 becomes stable. */
@Composable
private fun TabBar(
    modifier: Modifier = Modifier,
    containerColor: Color =  MaterialTheme.colorScheme.primaryContainer,
    controller: TabNavController
) {
    val iconSize = 24.dp
    val density = LocalDensity.current
    // The width of the indicator will change depending on the content of the selected tab.
    // Value is set by onGloballyPositioned modifier of Tab's text.
    val indicatorWidth = remember { mutableStateOf(0.dp) }

    TabRow(
        modifier = modifier,
        selectedTabIndex = controller.selectedIdx.intValue,
        containerColor = containerColor,
        divider = { Divider(thickness = 1.dp, color = MaterialTheme.colorScheme.surfaceVariant) },
        indicator = { tabPositions ->
            val currentTabPosition = tabPositions[controller.selectedIdx.intValue]
            val width = indicatorWidth.value
            val indicatorOffset by animateDpAsState(
                targetValue = currentTabPosition.left,
                animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
                label = "TabIndicatorPosition"
            )
            Spacer(
                Modifier
                    .wrapContentSize(Alignment.BottomStart)
                    // Center the indicator on the tab
                    .offset(x = indicatorOffset + (currentTabPosition.width - width) / 2)
                    .requiredHeight(3.dp)
                    .requiredWidth(width)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(3.0.dp)
                    )
            )
        }
    ) {
        controller.tabs.forEachIndexed { i, tab ->
            // Tab(
            //     icon = { Icon(tab.icon, null) },
            //     text = {
            //         Text(
            //             tab.title,
            //             modifier = if (controller.selectedTab.intValue == i) {
            //                 Modifier.onGloballyPositioned {
            //                     indicatorWidth.value = with(density) {
            //                         // The width of the indicator is the of the text or the icon, whichever is greater.
            //                         max(it.size.width.toDp(), iconSize)
            //                     }
            //                 }
            //             } else { Modifier },
            //             fontWeight = FontWeight.SemiBold,
            //             letterSpacing = 0.5.sp
            //         )
            //     },
            //     selectedContentColor = MaterialTheme.colorScheme.primary,
            //     unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            //     selected = controller.selectedTab.intValue == i,
            //     onClick = { controller.selectedTab.intValue = i }
            // )

            // Use the regular Tab when there is more than 2 tabs.
            LeadingIconTab(
                icon = { Icon(tab.icon, null, modifier = Modifier.size(iconSize)) },
                text = {
                    Text(
                        tab.title,
                        modifier = if (controller.selectedIdx.intValue == i) {
                            Modifier.onGloballyPositioned {
                                indicatorWidth.value = with(density) {
                                    // The width of the indicator is the width of the icon + padding + width of text.
                                    iconSize + 8.dp + it.size.width.toDp()
                                }
                            }
                        } else { Modifier },
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                selected = controller.selectedIdx.intValue == i,
                onClick = { controller.selectedIdx.intValue = i }
            )
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
private fun Calendars(
    modifier: Modifier = Modifier,
    hasSelectedDir: Boolean = false,
    selectDirClick: () -> Unit = {},
    groupedCalendars: Map<String, List<UserCalendarListItem>>?,
    calPermsClick: () -> Unit = {},
) {
    Column(modifier.padding(OUTER_PADDING.dp)) {
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
            Divider(Modifier.padding(vertical = (LIST_ITEM_SPACING * 2).dp))
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
            Surface(
                modifier = Modifier
                    .fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                tonalElevation = 1.dp,
                shadowElevation = 5.dp
            ) {
                LazyColumn(
                    Modifier
                        .padding(MIDDLE_PADDING.dp)
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
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
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
private fun CalendarListItem(cal: UserCalendarListItem) {
    var isChecked by rememberSaveable { mutableStateOf(false) }

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
                tooltip = { Text("This calendar is synced") },
            ) {
                Icon(
                    modifier = Modifier.tooltipAnchor(),
                    imageVector = Icons.Default.DateRange,
                    contentDescription = "Calendars list item",
                    tint = cal.color.toColor()
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


/** A Button that has an icon and text*/
@Composable
fun IconTextButton(modifier: Modifier = Modifier, icon: Painter, text: String, onclick: () -> Unit) {
    Button(onclick, modifier, contentPadding = PaddingValues(start = 16.dp, end = 24.dp)) {
        Icon(icon, null, modifier = Modifier.size(18.dp))
        Text(text, modifier.padding(start = 8.dp))
    }
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
                    name = "Personal",
                    accountName = acc,
                    color = me.marti.calprovexample.Color("cd58bb")
                ),
                UserCalendarListItem(
                    name = "Friend",
                    accountName = "Friend",
                    color = me.marti.calprovexample.Color("58cdc9")
                ),
                UserCalendarListItem(
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
                title = { Text("Title") },
                tabController = TabNavController(tabs = listOf(
                    TabNavDestination(Icons.Default.DateRange, "Calendars") {},
                    TabNavDestination(Icons.Default.AccountCircle, "Contacts") {}
                ))
            )
        }
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

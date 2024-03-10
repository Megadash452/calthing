package me.marti.calprovexample.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import me.marti.calprovexample.NonEmptyList
import me.marti.calprovexample.R
import me.marti.calprovexample.UserCalendarListItem
import me.marti.calprovexample.ui.theme.CalProvExampleTheme

const val OUTER_PADDING = 8
private const val MIDDLE_PADDING = 4
private const val LIST_ELEVATION = 1
private const val LIST_ITEM_ELEVATION = 3
private const val LIST_ITEM_SPACING = 4
private const val PREVIEW_WIDTH = 300

/** Used in **`ExpandableFloatingActionButtons`**.
 * @see ExpandableFloatingActionButtons
 * @param description The overall *description* of all the actions, to show in a `Tooltip`.
 * @param icon The icon shown in the `FAB` in *collapsed mode*.
 * @param actions The sub-actions shown in *expanded mode*. They are rendered *from the bottom up*.
 *                The first **action** in the list will replace the main `FAB` in *expanded mode*.
 *                The rest of the actions will compose `Small FAB`s above the first `FAB`. */
// Primary constructor is private to overload constructors with different icon variants.
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
 * @see ExpandedFabBackgroundOverlay
 *
 * @param expanded Whether the state of the buttons is in *expanded* or *collapsed mode*.
 * @param expand A function called when the main `FAB` is clicked in *collapsed mode*,
 *               will set the value of **expanded** to `true`.
 * @param data The data for the `FloatingActionButton`. */
@Composable
fun ExpandableFloatingActionButtons(
    modifier: Modifier = Modifier,
    expanded: Boolean,
    expand: () -> Unit = {},
    data: ExpandableFab
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
        remember { data.actions.rest.reversed() }.forEach { action ->
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
            ActionLabel(data.actions.first.label)
            val mainFab = @Composable {
                FloatingActionButton(
                    containerColor = mainFabColor,
                    // When actions are expanded, the FAB will change to one of the actions
                    onClick = if (expanded) data.actions.first.onClick else expand
                ) {
                    Crossfade(targetState = expanded, label = "Calendars FAB Expanded") { expanded ->
                        if (expanded)
                            data.actions.first.icon()
                        else
                            data.icon()
                    }
                }
            }
            val density = LocalDensity.current
            Box(modifier = Modifier.onGloballyPositioned {
                fabWidth = with(density) { it.size.width.toDp() }
            }) {
                if (data.description != null)
                    PlainTooltipBox(
                        tooltipContent = { Text(data.description) },
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
fun ExpandedFabBackgroundOverlay(modifier: Modifier = Modifier, expanded: Boolean, collapse: () -> Unit) {
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
fun Calendars(
    modifier: Modifier = Modifier,
    hasSelectedDir: Boolean = false,
    selectDirClick: () -> Unit = {},
    groupedCalendars: GroupedList<String, UserCalendarListItem>?,
    calPermsClick: () -> Unit = {},
    calIsSynced: (Long) -> Boolean = { false },
    onCalSwitchClick: (Long, Boolean) -> Unit = { _, _ -> }
) {
    Column(modifier.padding(OUTER_PADDING.dp)) {
        Header("Calendars")

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
            Surface(
                modifier = Modifier
                    .fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                tonalElevation = LIST_ELEVATION.dp,
                shadowElevation = 5.dp
            ) {
                LazyColumn(
                    Modifier
                        .padding(MIDDLE_PADDING.dp)
                        .clip(MaterialTheme.shapes.small),
                    verticalArrangement = Arrangement.spacedBy(LIST_ITEM_SPACING.dp),
                    contentPadding = PaddingValues(bottom = 64.dp)
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
        tooltip = { this.PlainTooltip(content = tooltipContent) },
        content = content
    )
}

@Composable
fun Header(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        modifier = modifier.padding(bottom = OUTER_PADDING.dp),
        fontWeight = FontWeight.Medium,
        style = MaterialTheme.typography.titleLarge
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
@Preview(showBackground = true, widthDp = PREVIEW_WIDTH)
@Composable
fun NavBarPreview() {
    CalProvExampleTheme {
        NavBar()
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

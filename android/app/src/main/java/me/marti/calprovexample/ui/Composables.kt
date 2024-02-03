package me.marti.calprovexample.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltipBox
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.marti.calprovexample.R
import me.marti.calprovexample.UserCalendarListItem
import me.marti.calprovexample.ui.theme.CalProvExampleTheme

private const val OUTER_PADDING = 8
private const val MIDDLE_PADDING = 4
private const val LIST_ITEM_SPACING = 4
private const val PREVIEW_WIDTH = 300


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
    groupedCalendars: Map<String, List<UserCalendarListItem>>?,
    calPermsClick: () -> Unit = {},
) {
    Column(modifier.padding(OUTER_PADDING.dp)) {
        Text(
            "Calendars",
            modifier = Modifier.padding(bottom = OUTER_PADDING.dp),
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.titleLarge
        )

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
                tonalElevation = 2.dp,
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
@Preview(showBackground = true, widthDp = PREVIEW_WIDTH)
@Composable
fun NavBarPreview() {
    CalProvExampleTheme {
        NavBar(items = NavDestinationItem.All)
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

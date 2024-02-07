package me.marti.calprovexample.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState

data class NavDestinationItem (
    val icon: ImageVector,
    val title: String,
) {
    companion object {
        val Calendars = NavDestinationItem(
            icon = Icons.Default.DateRange,
            title = "Calendars"
        )
        val Contacts = NavDestinationItem(
            icon = Icons.Default.AccountCircle,
            title = "Contacts"
        )
        val Settings = NavDestinationItem(
            icon = Icons.Default.Settings,
            title = "Settings",
        )

        val All = listOf(Calendars, Contacts, Settings)
    }

    /** convert the user-readable **title** into a path segment string for NavHost. */
    val route: String
        get() = this.title.lowercase().replace(' ', '-')
}

@Composable
fun NavBar(
    modifier: Modifier = Modifier,
    items: List<NavDestinationItem> = NavDestinationItem.All,
    controller: NavController? = null
) {
    val backStack by controller?.currentBackStackEntryAsState() ?: remember { mutableStateOf(null) }

    NavigationBar(modifier) {
        items.forEachIndexed { i, item ->
            NavigationBarItem(
                icon = { Icon(item.icon, null) },
                label = { Text(item.title) },
                // When there is no controller the first item is always selected
                selected = if (backStack != null) backStack!!.destination.route == item.route else i == 0,
                onClick = {
                    controller?.navigate(item.route) {
                        this.popUpTo(controller.graph.startDestinationId)
                        this.restoreState = true
                        this.launchSingleTop = true
                    }
                },
            )
        }
    }
}

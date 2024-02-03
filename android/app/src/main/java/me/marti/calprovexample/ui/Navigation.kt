package me.marti.calprovexample.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

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


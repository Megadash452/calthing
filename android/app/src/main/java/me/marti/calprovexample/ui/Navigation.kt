package me.marti.calprovexample.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/** Information about a tab in a TabBar or NavBar and the content it is tied to.
 * @param content The content composable is passed a modifier. */
data class TabNavDestination(
    val icon: ImageVector,
    val title: String,
    val content: @Composable (Modifier) -> Unit,
)

class TabNavController(
    val selectedIdx: MutableIntState = mutableIntStateOf(0),
    val tabs: List<TabNavDestination>
) {
    /** Render the content of the selected Tab. */
    @Composable
    fun Content(modifier: Modifier = Modifier) {
        this.tabs[this.selectedIdx.intValue].content(modifier)
    }
}

class NavDestinationItem (
    val title: String,
    val content: @Composable (Modifier) -> Unit,
) {
    companion object {
        val Main = NavDestinationItem(
            title = "Main"
        ) { modifier ->

        }

        val Settings = NavDestinationItem(
            title = "Settings",
        ) { modifier ->
            Text("Settings Page", modifier = modifier)
        }
    }

    /** convert the user-readable **title** into a path segment string for NavHost. */
    val route: String
        get() = this.title.lowercase().replace(' ', '-')
}


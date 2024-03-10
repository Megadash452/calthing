package me.marti.calprovexample.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/** Information about a tab in a TabBar or NavBar and the content it is tied to.
 * @param content The content composable is passed a modifier.
 * @see PrimaryTabNavDestination
 * @see SimpleTabNavDestination */
open class TabNavDestination(
    val title: String,
    val content: @Composable (Modifier) -> Unit
)

/** Data of a Primary Tab, with the **icon** above **title**. */
open class PrimaryTabNavDestination(
    val icon: ImageVector,
    title: String,
    content: @Composable (Modifier) -> Unit,
) : TabNavDestination(title, content)
class PrimaryTabNavDestinationWithFab(
    icon: ImageVector,
    title: String,
    val fab: ExpandableFab,
    content: @Composable (Modifier) -> Unit,
) : PrimaryTabNavDestination(icon, title, content)
class SimpleTabNavDestination(
    title: String,
    content: @Composable (Modifier) -> Unit,
) : TabNavDestination(title, content)


/** A **`Tab Controller`** that can be shown with a `TopBar` and handles the switching of tab content.
 * Rendering is implemented in the *`TabBar` Composable*.
 * @property tabs All elements of tabs must be of the same type, or the code will be broken.
 *                Tabs can be created from *`Primary` (icon)* or *`Simple` (no icon)* Tab Destinations.
 * @see PrimaryTabNavDestination
 * @see SimpleTabNavDestination */
class TabNavController<out T: TabNavDestination>(
    val selectedIdx: MutableIntState = mutableIntStateOf(0),
    val tabs: List<T>
) {
    val selectedTab: T
        get() = this.tabs[this.selectedIdx.intValue]
    /** Render the content of the selected Tab. */
    @Composable
    fun SelectedContent(modifier: Modifier = Modifier) {
        this.selectedTab.content(modifier)
    }
}

enum class NavDestination {
    Main, Settings, Debug,
    NewCalendar;

    /** convert the user-readable **title** into a path segment string for NavHost. */
    val route: String
        get() = this.name.lowercase().replace(' ', '-')
}


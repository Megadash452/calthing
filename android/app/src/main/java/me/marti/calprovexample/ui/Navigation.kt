package me.marti.calprovexample.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.compose.rememberNavController
import me.marti.calprovexample.R

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
    // /** Returns the tab stored at the **selectedIdx** of **tabs**. */
    // val selectedTab: TabNavDestination
    //     get() = this.tabs[this.selectedIdx.intValue]

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


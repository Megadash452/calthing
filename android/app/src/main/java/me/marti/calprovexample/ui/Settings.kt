package me.marti.calprovexample.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import me.marti.calprovexample.R

@Composable
fun Settings(modifier: Modifier = Modifier, settings: List<@Composable () -> Unit>) {
    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        this.items(settings) { setting ->
            setting()
        }
    }
}

@Composable
fun BooleanSetting(
    modifier: Modifier = Modifier,
    name: String,
    value: Boolean = false,
    onClick: (Boolean) -> Unit = {}
) {
    ListItem(
        modifier = modifier,
        headlineContent = { Text(name) },
        trailingContent = {
            Switch(checked = value, onCheckedChange = onClick)
        }
    )
}

/** The setting represents a *directory* in the user's shared storage.
 * @param name The name of the preference in human readable terms. */
@Composable
fun DirSetting(
    modifier: Modifier = Modifier,
    name: String,
    value: Uri? = null,
    selectClick: () -> Unit = {}
) {
    ListItem(
        modifier = modifier,
        leadingContent = { Icon(painterResource(R.drawable.round_folder_24), "Directory") },
        headlineContent = { Text(name) },
        supportingContent = {
            if (value != null)
                Text(readableUri(value), modifier = Modifier.padding(start = 8.dp))
        },
        trailingContent = {
            Button(onClick = selectClick) {
                Text("Select")
            }
        }
    )
}

/** converts an URI into a human readable file path. */
private fun readableUri(uri: Uri): String {
    return uri.lastPathSegment!!
}

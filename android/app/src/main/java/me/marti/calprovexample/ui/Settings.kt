package me.marti.calprovexample.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.marti.calprovexample.R

private const val TITLE_SUBTITLE_SPACING = 6
private const val SUBTITLE_INDENT = 4

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
    summary: String? = null,
    value: Boolean = false,
    onClick: (Boolean) -> Unit = {}
) {
    ListItem(
        modifier = modifier,
        headlineContent = { Text(name, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = TITLE_SUBTITLE_SPACING.dp)) },
        supportingContent = { if (summary != null) Text(summary, modifier = Modifier.padding(start = SUBTITLE_INDENT.dp)) },
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
        headlineContent = { Text(name, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = TITLE_SUBTITLE_SPACING.dp)) },
        supportingContent = {
            if (value != null)
                Text(readableUri(value), modifier = Modifier.padding(start = SUBTITLE_INDENT.dp))
        },
        trailingContent = {
            IconTextButton(
                icon = painterResource(R.drawable.round_folder_24),
                text = "Select",
                onclick = selectClick
            )
            // Button(onClick = selectClick) {
            //     Text("Select")
            // }
        }
    )
}

/** converts an URI into a human readable file path. */
private fun readableUri(uri: Uri): String {
    val split = (uri.lastPathSegment ?: "/").split(':', limit = 2)
    val base = if (split[0] == "primary") "Internal" else split[0]

    return if (split.size == 1)
        base
    else
        "$base/${split[1]}"
}

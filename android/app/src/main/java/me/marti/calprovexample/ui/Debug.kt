package me.marti.calprovexample.ui

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.marti.calprovexample.AllData
import me.marti.calprovexample.ui.theme.CalProvExampleTheme

/** @param navUpClick An optional BackButton that navigates to the previous `NavDestination` will be shown if this is not *`NULL`*. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugContent(modifier: Modifier = Modifier, navUpClick: (() -> Unit) = {}, data: AllData?) {
    @Suppress("NAME_SHADOWING")
    val tabController = TabNavController(
        selectedIdx = rememberSaveable { mutableIntStateOf(0) },
        tabs = listOf(
            SimpleTabNavDestination(
                title = "Calendars",
            ) { modifier -> Data(modifier = modifier, data = data?.calendars?.data ?: listOf(), getNext = { data?.calendars?.queryNext() }) },
            SimpleTabNavDestination(
                title = "Events",
            ) { modifier -> Data(modifier = modifier, data = data?.events?.data ?: listOf(), getNext = { data?.events?.queryNext() }) },
            SimpleTabNavDestination(
                title = "Reminders",
            ) { modifier -> Data(modifier = modifier, data = data?.reminders?.data ?: listOf(), getNext = { data?.reminders?.queryNext() }) },
            SimpleTabNavDestination(
                title = "Attendees",
            ) { modifier -> Data(modifier = modifier, data = data?.attendees?.data ?: listOf(), getNext = { data?.attendees?.queryNext() }) },
        )
    )

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.systemBars,
        topBar = { TopBar(
            title = "Debug",
            tabController = tabController,
            navUpClick = navUpClick
        ) }
    ) { paddingValues ->
        // TODO: add anchoredDraggable modifier
        tabController.SelectedContent(modifier = Modifier.padding(paddingValues))
    }
}

@Composable
private fun Data(modifier: Modifier = Modifier, data: List<Map<String, String>>, getNext: () -> Unit = {}) {
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(vertical = 6.dp, horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        this.items(data) { cal ->
            Surface(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).horizontalScroll(rememberScrollState()), tonalElevation = 10.dp) {
                Column(modifier = Modifier.padding(4.dp)) {
                    cal.forEach { (key, value) ->
                        Row {
                            Text("$key:", textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(" $value", maxLines = 1)
                        }
                    }
                }
            }
        }
        this.item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Button(onClick = getNext) {
                    Text("Load Next")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ContentPreview() {
    CalProvExampleTheme {
        Data(data = listOf(
            mapOf(Pair("1", "Hi"), Pair("2", "Hello"), Pair("3", "wharr")),
            mapOf(Pair("1", "World"), Pair("2", "People"), Pair("3", "More")),
        ))
    }
}

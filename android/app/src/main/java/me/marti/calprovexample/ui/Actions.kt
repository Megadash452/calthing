package me.marti.calprovexample.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.marti.calprovexample.Color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewCalendarAction(modifier: Modifier = Modifier, navUpClick: () -> Unit, submit: (String, Color) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var color by rememberSaveable { mutableStateOf("000000") }
    var nameError by rememberSaveable { mutableStateOf(false) }
    var colorError by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopBar(
                title = "Create new Calendar",
                navUpClick = navUpClick
            )
        }
    ) { paddingValues ->
        Column(Modifier.padding(paddingValues).padding(OUTER_PADDING.dp)) {
            OutlinedTextField(
                value = name,
                label = { Text("Name") },
                isError = nameError,
                supportingText = if (nameError) {{
                    Text("Name can't be blank")
                }} else null,
                singleLine = true,
                onValueChange = { text -> name = text }
            )
            OutlinedTextField(
                value = color,
                prefix = { Text("0x") },
                label = { Text("Color") },
                isError = colorError,
                supportingText = if (colorError) {{
                    Text("color must be in Hexadecimal RGB format")
                }} else null,
                singleLine = true,
                onValueChange = { text -> color = text }
            )
            Button(onClick = {
                val colorVal = try {
                    Color(color)
                } catch (e: Exception) {
                    null
                }

                nameError = name.isBlank()
                colorError = colorVal == null

                if (!(nameError || colorError))
                    submit(name, colorVal!!)
            }) {
                Text("Submit")
            }
        }
    }
}
package me.marti.calprovexample.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import me.marti.calprovexample.Color
import me.marti.calprovexample.UserCalendarListItem
import me.marti.calprovexample.ui.theme.CalProvExampleTheme

enum class Actions {
    NewCalendar, CopyCalendar, ImportFile
}

@Composable
fun NewCalendarAction(modifier: Modifier = Modifier, close: () -> Unit, submit: (String, Color) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var color by rememberSaveable { mutableIntStateOf(0x68acef) }
    var nameError by rememberSaveable { mutableStateOf(false) }
    var pickColor by rememberSaveable { mutableStateOf(false) }
    val onSubmit = {
        nameError = name.isBlank()

        if (!nameError) {
            close()
            submit(name, Color(color))
        }
    }

    if (pickColor) {
        ColorPickerDialog(
            color = Color(color),
            dismiss = { pickColor = false }
        ) { newColor -> color = newColor.toColor().toArgb() }
    } else {
        AlertDialog(
            modifier = modifier,
            title = { Text("Create new Calendar") },
            onDismissRequest = close,
            dismissButton = {
                TextButton(onClick = close) { Text("Cancel") }
            },
            confirmButton = {
                FilledTonalButton(onClick = onSubmit, enabled = !nameError) { Text("Create") }
            },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Box(Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color(color).toColor())
                            .border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            .clickable { pickColor = true }
                        )
                    }
                    Spacer(Modifier.size(8.dp))
                    TextField(
                        value = name,
                        label = { Text("Name") },
                        isError = nameError,
                        supportingText = if (nameError) {{
                            Text("Name can't be blank")
                        }} else null,
                        singleLine = true,
                        onValueChange = { text -> name = text }
                    )
                }
            }
        )
    }
}

/** The dialog for when the user wants to import an existing Calendar from the device to be synced.
 * @param submit Function that runs when the "Select" button is clicked.
 *               Argument is a list of Calendar IDs.
 *               User can select multiple calendars, aka a `List<Long>` */
@Composable
fun CopyCalendarAction(modifier: Modifier = Modifier, calendars: GroupedList<String, UserCalendarListItem>, close: () -> Unit, submit: (List<Long>) -> Unit) {
    val selectedIds = remember { mutableStateMapOf<Long, Unit>() }

    AlertDialog(
        modifier = modifier,
        title = { Text("Select Calendars to copy") },
        onDismissRequest = close,
        dismissButton = {
            TextButton(onClick = close) { Text("Cancel") }
        },
        confirmButton = {
            FilledTonalButton(enabled = !selectedIds.isEmpty(), onClick = {
                close()
                submit(selectedIds.toList().map { (id, _) -> id  })
            }) { Text("Select") }
        },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                calendars.forEach { (accName, group) ->
                    this.items(group) { cal ->
                        Calendar(
                            color = cal.color, name = cal.name,
                            selected = selectedIds.containsKey(cal.id),
                            onClick = {
                                // Toggle the checkbox
                                if (selectedIds.remove(cal.id) == null)
                                    selectedIds[cal.id] = Unit
                            }
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun Calendar(modifier: Modifier = Modifier, color: Color, name: String, selected: Boolean, onClick: () -> Unit) {
    // Color of the ListItem container when it is selected
    val selectedColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(if (selected) selectedColor else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(
                    color = if (selected) MaterialTheme.colorScheme.surface else selectedColor
                ),
                onClick = onClick
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                modifier = Modifier.scale(1.2f),
                checked = selected,
                onCheckedChange = { onClick() },
                colors = CheckboxDefaults.colors().copy(
                    checkedBoxColor = color.toColor(),
                )
            )
            Text(name, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ColorPickerDialog(
    modifier: Modifier = Modifier,
    color: Color,
    dismiss: () -> Unit = {},
    colorConfirm: (Color) -> Unit = {},
) {
    // TODO: make Color class savable
    // FIXME: UI breaks in landscape mode
    val controller = rememberColorPickerController()
    var localColor by remember { mutableStateOf(color) }
    // The value of the RGB Hex string the user inputted
    var textColor by remember { mutableStateOf(localColor.toHex()) }
    // A possible parsing error with the textColor
    var colorError by remember { mutableStateOf<String?>(null) }
    println("color: $color")
    println("localColor: $localColor")

    AlertDialog(
        modifier = modifier,
        onDismissRequest = dismiss,
        confirmButton = {
            TextButton(
                enabled = colorError == null,
                onClick = {
                    colorConfirm(localColor)
                    dismiss()
                }
            ) {
                Text("Done")
            }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
        title = { Text("Pick Color", style = MaterialTheme.typography.labelMedium) },
        text = {
            Column {
                /* onColorChanged is called with White (why white?) on first render,
                 * unintentionally changing the initial color.
                 * This variable prevents that from happening */
                var firstRender = remember { true } // don't make mutableState
                HsvColorPicker(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    controller = controller,
                    initialColor = color.toColor(),
                    onColorChanged = { env ->
                        if (firstRender) {
                            firstRender = false
                            controller.selectByColor(color.toColor(), true)
                        } else {
                            println("picked color wheel: ${env.color}")
                            localColor = Color(env.color.toArgb())
                            textColor = localColor.toHex()
                            // The color wheel will always have a valid color
                            colorError = null
                        }
                    }
                )
                Spacer(Modifier.size(6.dp))
                BrightnessSlider(modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp), controller = controller)
                Spacer(Modifier.size(12.dp))

                Row {
                    OutlinedTextField(
                        value = textColor,
                        prefix = { Text("#") },
                        label = { Text("RGB") },
                        isError = colorError != null,
                        supportingText = colorError?.let {{ Text(it) }},
                        singleLine = true,
                        onValueChange = {
                            textColor = it
                            colorError = try {
                                // Constructing color from user string can result in an error
                                @Suppress("NAME_SHADOWING")
                                val color = Color(it)
                                localColor = color
                                controller.selectByColor(color.toColor(), true)
                                println("Good color")
                                null
                            } catch (e: NumberFormatException) {
                                e.message ?: "Invalid format"
                            }
                        }
                    )
                }
            }
        }
    )
}

@Preview
@Composable
private fun NewCalendarPreview() {
    CalProvExampleTheme {
        NewCalendarAction(close = {}) { _, _ -> }
    }
}
@Preview
@Composable
private fun CopyCalendarPreview() {
    val acc = "me@mydomain.me"

    CalProvExampleTheme {
        CopyCalendarAction(
            calendars = arrayOf(
                UserCalendarListItem(
                    id = 0,
                    name = "Personal",
                    accountName = acc,
                    color = Color("cd58bb")
                ),
                UserCalendarListItem(
                    id = 1,
                    name = "Friend",
                    accountName = "Friend",
                    color = Color("58cdc9")
                ),
                UserCalendarListItem(
                    id = 2,
                    name = "Work",
                    accountName = acc,
                    color = Color("5080c8")
                )
            ).groupBy { cal -> cal.accountName },
            close = {}, submit = {}
        )
    }
}
@Preview
@Composable
private fun ColorPickerPreview() {
    CalProvExampleTheme {
        ColorPickerDialog(color = Color("ff0000"))
    }
}

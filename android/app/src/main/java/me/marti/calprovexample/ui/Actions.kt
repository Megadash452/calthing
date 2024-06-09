package me.marti.calprovexample.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.TriStateCheckbox
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import me.marti.calprovexample.Color
import me.marti.calprovexample.GroupedList
import me.marti.calprovexample.calendar.ExternalUserCalendar
import me.marti.calprovexample.ui.NameCheck.Companion.checkError
import me.marti.calprovexample.ui.theme.CalProvExampleTheme

// Sum-type-like enum
@Suppress("ConvertObjectToDataObject")
sealed class Actions private constructor() {
    object NewCalendar: Actions()
    class EditCalendar(val id: Long, val name: String, val color: Color): Actions()
    object CopyCalendar: Actions()
    class ImportFileExists(val name: String): Actions()
}

/** Show a dialog to **Create** the info of a new Calendar for this App.
 *
 * @param close Stop showing the dialog.
 * @param submit Function that runs when the "Select" button is clicked.
 * Takes the display *name* and *color* of the new Calendar.
 * [close] is always called before *submitting*.
 *
 * @see EditCalendarAction */
@Composable
fun NewCalendarAction(modifier: Modifier = Modifier, close: () -> Unit, submit: (String, Color) -> Unit) {
    EditCalendarAction(
        modifier = modifier,
        title = { Text("Create new Calendar") },
        submitButtonContent = { Text("Create") },
        name = "",
        nameChecks = listOf(NameCheck.BlankCheck),
        close = close,
        submit = submit
    )
}

/** Struct to hold data about checking the validity of a String.
 *
 * @param check Determines if a String is valid.
 * @param error A message ot display if the String is **not** valid. */
class NameCheck(val check: (String) -> Boolean, val error: String) {
    companion object {
        // Some common checks
        val BlankCheck = NameCheck({ name -> name.isNotBlank() }, "Name can't be blank")

        /** Perform all the checks on the `name`. Returns [error] of the [NameCheck] that failed. */
        fun List<NameCheck>.checkError(name: String): String? {
            // Find the first check that fails
            val result = this.indexOfFirst { check -> !check.check(name) }
            return if (result >= 0)
                this[result].error
            else
                null // All checks passed
        }
    }
}

/** Show a dialog to **Edit** the info of an existing Calendar that is owned by this App.
 *
 * Is used to *edit* an existing one, or to *create* a new one in [NewCalendarAction].
 *
 * @param title The text of the title header of the Dialog.
 * @param submitButtonContent The text for the *submit* button.
 * @param submitButtonContent The text for the *cancel* button.
 * @param color The initial *color* of the Calendar being edited.
 * @param name The initial display *name* of the Calendar being edited.
 * @param nameChecks A set of checks to determine if the *new name* is valid.
 * @param close Stop showing the dialog.
 * @param submit Handle the data submitted by the user. [close] is always called before this.
 *
 * @see NewCalendarAction */
@Composable
fun EditCalendarAction(
    modifier: Modifier = Modifier,
    submitButtonContent: @Composable RowScope.() -> Unit = { Text("Save") },
    cancelButtonContent: @Composable RowScope.() -> Unit = { Text("Cancel") },
    color: Color = Color(DEFAULT_CALENDAR_COLOR),
    title: @Composable () -> Unit,
    name: String,
    nameChecks: List<NameCheck>,
    close: () -> Unit,
    submit: (String, Color) -> Unit
) {
    // The new name of the Calendar. Is the string argument of the `submit` function
    @Suppress("NAME_SHADOWING")
    var name by rememberSaveable { mutableStateOf(name) }
    // The error message to display. Disables the Submit button. Null if there is no error.
    var nameError: String? by remember { mutableStateOf(null) }
    @Suppress("NAME_SHADOWING")
    var color by rememberSaveable { mutableIntStateOf(color.toColor().toArgb()) }
    // Whether to show ColorPicker or the Edit dialog
    var pickColor by rememberSaveable { mutableStateOf(false) }
    // Does all the checks before calling `submit`
    val onSubmit = {
        nameError = nameChecks.checkError(name)
        if (nameError == null) {
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
            title = title,
            onDismissRequest = close,
            dismissButton = {
                TextButton(onClick = close, content = cancelButtonContent)
            },
            confirmButton = {
                FilledTonalButton(onClick = onSubmit, enabled = nameError == null, content = submitButtonContent)
            },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // A circle showing the color for the Calendar
                    Box(Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color(color).toColor())
                        .border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        .clickable { pickColor = true }
                    )
                    Spacer(Modifier.size(8.dp))
                    TextField(
                        value = name,
                        label = { Text("Name") },
                        isError = nameError != null,
                        supportingText = nameError?.let { error -> { Text(error) } },
                        singleLine = true,
                        onValueChange = {
                            text -> name = text
                            nameError = nameChecks.checkError(text)
                        }
                    )
                }
            }
        )
    }
}

/** The dialog for when the user wants to import an existing Calendar from the device to be synced.
 * @param submit Function that runs when the "Select" button is clicked.
 * Argument is a list of Calendar IDs.
 * User can select multiple calendars, aka a `List<Long>`
 * [close] is always called before *submitting*. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CopyCalendarAction(modifier: Modifier = Modifier, calendars: GroupedList<String, ExternalUserCalendar>, close: () -> Unit, submit: (List<Long>) -> Unit) {
    // The IDs of the Calendars selected to be copied
    val selectedIds = remember { mutableStateMapOf<Long, Unit>() }

    AlertDialog(
        modifier = modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.75).dp),
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
        text = if (calendars.isEmpty()) {{
            Text("This device has no calendars to import", modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }} else {{
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                calendars.forEach { (accountName, group) ->
                    this.stickyHeader { StickyHeader(text = accountName, leadingContent = {
                        // Calendars that have importedTo set cannot be selected
                        val selectable = group.filter { cal -> cal.importedTo == null }
                        // Mapping of whether some, all, or none of the group's calendars are in selectedIds.
                        val selected = selectable.map { cal -> selectedIds.containsKey(cal.id) }
                        TriStateCheckbox(
                            modifier = Modifier.size(24.dp),
                            enabled = selectable.isNotEmpty(), // There are no items to select
                            state =
                                if (selectable.isEmpty())
                                    ToggleableState.Off
                                // All items of the group are selected if all their IDs are in the selectedIds Set.
                                else if (selected.all { it }) // All items are selected
                                    ToggleableState.On
                                else if (selected.contains(true)) // Any items are selected
                                    ToggleableState.Indeterminate
                                else ToggleableState.Off,
                            onClick = {
                                // Deselect all if all are selected
                                if (selected.all { it })
                                    selectable.forEach { cal -> selectedIds.remove(cal.id) }
                                // Otherwise, Select all.
                                else
                                    selectable.forEach { cal -> selectedIds[cal.id] = Unit }
                            }
                        )
                    }) }
                    this.items(group) { cal ->
                        Calendar(
                            color = cal.color, name = cal.name,
                            selected = selectedIds.containsKey(cal.id),
                            importedTo = cal.importedTo,
                            onClick = {
                                // Toggle the checkbox
                                if (selectedIds.remove(cal.id) == null)
                                    selectedIds[cal.id] = Unit
                            }
                        )
                    }
                }
            }
        }}
    )
}

@Composable
private fun Calendar(modifier: Modifier = Modifier, color: Color, name: String, selected: Boolean, importedTo: String? = null, onClick: () -> Unit) {
    val enabled = importedTo == null
    val content = @Composable {
        Row(modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .let {
                if (enabled) {
                    val selectedColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)
                    it.background(if (selected) selectedColor else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = rememberRipple(
                                color = if (selected) MaterialTheme.colorScheme.surface else selectedColor
                            ),
                            onClick = onClick
                        )
                } else it
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    modifier = Modifier.scale(1.2f),
                    enabled = enabled,
                    checked = selected,
                    onCheckedChange = { onClick() },
                    colors = CheckboxDefaults.colors().copy(
                        checkedBoxColor = color.toColor(),
                    )
                )
                if (enabled)
                    Text(name, style = MaterialTheme.typography.bodyMedium)
                else
                    Text(name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
            }
        }
    }

    if (enabled) {
        content()
    } else {
        // Show a tooltip if calendar is already imported.
        PlainTooltipBox(
            tooltipContent = { Text("Already imported to '$importedTo'") },
            content = content
        )
    }
}

/** A dialog that prompts the user whether to **rename** a calendar being imported,
 * or **overwrite** the existing calendar when there is an import conflict.
 *
 * @param name The name of the calendar that was attempted import.
 * @param appDir The path of the app's internal directory. Is used to check whether the user is trying to use another name that already exists.
 * @param rename The user decided to assign the import a different name. Import the file with that name.
 * @param overwrite The user decided to remove the existing calendar and import the new one. */
@Composable
fun ImportFileExistsAction(
    modifier: Modifier = Modifier,
    name: String,
    appDir: String,
    rename: (String, Color) -> Unit,
    overwrite: () -> Unit,
    close: () -> Unit
) {
    var isRename by remember { mutableStateOf(false) }

    if (isRename) {
        EditCalendarAction(
            title = { Text("Rename import") },
            submitButtonContent = { Text("Rename") },
            cancelButtonContent = { Text("Back") },
            name = name,
            nameChecks = listOf(
                NameCheck.BlankCheck,
                NameCheck({ new -> new != name }, "Name must be different"),
                // TODO? Maybe use the content provider to check this
                NameCheck({ new -> !java.io.File("$appDir/calendars/$new.ics").exists() }, "Another calendar with this name exists")
            ),
            close = { isRename = false },
            submit = { newName, color ->
                close()
                rename(newName, color)
            },
        )
    } else { // Initial dialog
        AlertDialog(
            modifier = modifier,
            onDismissRequest = close,
            text = { Text("A calendar already exists with the name \"$name\". Rename imported calendar?") },
            confirmButton = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = close) { Text("Cancel") }
                    Row {
                        TextButton(onClick = { isRename = true }) { Text("Rename") }
                        TextButton(onClick = {
                            close()
                            overwrite()
                        }) { Text("Overwrite") }
                    }
                }
            },
        )
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
                            localColor = Color(env.color.toArgb())
                            textColor = localColor.toHex()
                            // The color wheel will always have a valid color
                            colorError = null
                        }
                    }
                )
                Spacer(Modifier.size(6.dp))
                BrightnessSlider(
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    controller = controller
                )
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
                ExternalUserCalendar(
                    id = 0,
                    name = "Personal",
                    accountName = acc,
                    color = Color("cd58bb"),
                    importedTo = null
                ),
                ExternalUserCalendar(
                    id = 1,
                    name = "Friend",
                    accountName = "Friend",
                    color = Color("58cdc9"),
                    importedTo = "Other Friend"
                ),
                ExternalUserCalendar(
                    id = 2,
                    name = "Work",
                    accountName = acc,
                    color = Color("5080c8"),
                    importedTo = null
                )
            ).groupBy { cal -> cal.accountName },
            close = {}, submit = {}
        )
    }
}
@Preview
@Composable
private fun ImportFilePreview() {
    CalProvExampleTheme {
        ImportFileExistsAction(
            name = "MyCalendar",
            appDir = "/",
            rename = { _, _ -> }, overwrite = {},
            close = {}
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

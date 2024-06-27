package me.marti.calprovexample.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.core.net.toUri
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import me.marti.calprovexample.BooleanUserPreference
import me.marti.calprovexample.Color
import me.marti.calprovexample.DavSyncRs
import me.marti.calprovexample.ImportFileExists
import me.marti.calprovexample.NonEmptyList
import me.marti.calprovexample.PreferenceKey
import me.marti.calprovexample.R
import me.marti.calprovexample.StringLikeUserPreference
import me.marti.calprovexample.UserPreference
import me.marti.calprovexample.calendar.AllData
import me.marti.calprovexample.calendar.ExternalUserCalendar
import me.marti.calprovexample.calendar.InternalUserCalendar
import me.marti.calprovexample.calendar.copyExternalCalendar
import me.marti.calprovexample.calendar.deleteCalendarByName
import me.marti.calprovexample.calendar.editCalendar
import me.marti.calprovexample.calendar.externalUserCalendars
import me.marti.calprovexample.calendar.internalUserCalendars
import me.marti.calprovexample.calendar.newCalendar
import me.marti.calprovexample.createFiles
import me.marti.calprovexample.deleteFiles
import me.marti.calprovexample.externalFile
import me.marti.calprovexample.finishImportOverwrite
import me.marti.calprovexample.finishImportRename
import me.marti.calprovexample.getAppPreferences
import me.marti.calprovexample.importFiles
import me.marti.calprovexample.internalFile
import me.marti.calprovexample.openFd
import me.marti.calprovexample.ui.theme.CalProvExampleTheme
import me.marti.calprovexample.writeCalendarDataToFile
import me.marti.calprovexample.writeColorToCalendarFile
import me.marti.calprovexample.writeFileDataToCalendar
import java.io.IOException
import java.io.File as Path

const val DEFAULT_CALENDAR_COLOR = 0x68acef
const val CALENDAR_DOCUMENT_MIME_TYPE = "text/calendar"

class MainActivity : ComponentActivity() {
    val calendarPermission = CalendarPermission(this)
    val asyncCalendarPermission = AsyncCalendarPermission(this)
    private val snackBarState = SnackbarHostState()

    // -- Hoisted States for compose
    /** The path/URI where the synced .ics files are stored in shared storage.
      * Null if the user hasn't selected a directory.
     *
     * This URI can be used by the [Documents Content Provider][android.provider.DocumentsProvider]
     * by using the [DocumentsContract]. */
    val syncDir: UserPreference<Uri> = StringLikeUserPreference(PreferenceKey.SYNC_DIR_URI) { uri -> uri.toUri() }
        .apply {
            this.value?.let {
                // TODO: test if app still has access at startup
            }
        }
    /** Holds the list of the App's Calendars that will be displayed to the user.
     * **`Null`** if the user hasn't granted permission (this can't be represented by empty because the user could have no calendars in the device).
     *
     * Since the list is *`MutableState`*, to edit data of an element it must be **replaced** with another (use [InternalUserCalendar.copy]). */
    @SuppressLint("MutableCollectionMutableState")
    var userCalendars: MutableState<MutableCalendarsList?> = mutableStateOf(null)

    /** Channel for sending messages between *[dirSelectIntent]* and the UI.
     *  true if the user selected the directory.
     *  false if the user canceled. */
    private val dirSelectChannel = Channel<Boolean>()
    /** Register for the intent that lets the user pick a directory where Syncthing (or some other service) will store the .ics files. */
    private val dirSelectIntent = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) {
            println("OpenDir: User cancelled the file picker.")
            dirSelectChannel.trySend(false)
            return@registerForActivityResult
        }
        // Preserve access to the directory. Otherwise, access would be revoked when app is closed.
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        @Suppress("NAME_SHADOWING")
        // Convert Tree URI to an URI that can be used by the DocumentsProvider
        val uri = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))

        val file = this.openFd(uri) ?: run {
            dirSelectChannel.trySend(false)
            return@registerForActivityResult
        }
        DavSyncRs.initialize_sync_dir(file.fd)
        file.close()

        // Now that syncDir is initialized, create the files for calendars stored in the provider
        if (this.calendarPermission.hasPermission())
            // TODO: also import files in syncDir (use user to resolve conflicts too)
            this.calendarPermission.run {
                for (cal in this.internalUserCalendars()!!) {
                    try {
                        this@MainActivity.createFiles("${cal.name}.ics", cal.color, cal.id)
                    } catch (e: Exception) {
                        Log.e("dirSelectIntent", "Error creating files for \"${cal.name}.ics\" in newly-selected syncDir")
                    }
                }
            }
        else
            Log.w("dirSelectIntent", "Can't create files for calendars stored in the provider; no calendar permission")

        this.syncDir.value = uri
        dirSelectChannel.trySend(true)
        println("User selected $uri for synced .ics files.")
        // If nothing works, recheck DocumentsContract and DocumentFile
    }

    /** Channel for sending messages between *[importFilesIntent] handler* and the UI.
     *
     * The value is only NOT NULL when there is a conflict that requires user input. */
    var importChannel: Channel<ImportFileExists?>? = null
    /** Register for file picker intent. */
    // TODO: Use ActivityResultContracts.GetContent instead
    private val importFilesIntent = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        this.importFiles(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must set navigationBarStyle to remove the scrim.
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.light(0, 0))
        super.onCreate(savedInstanceState)

        val preferences = this.baseContext.getAppPreferences()
        this.syncDir.initStore(preferences)
        val fragmentCals = BooleanUserPreference(PreferenceKey.FRAGMENT_CALS)
        fragmentCals.initStore(preferences)

        // Clear recycle bin
        Path("${this.filesDir}/deleted/").deleteRecursively()

        Log.d(null, "Initializing Main Activity")

        // Populate the list of synced calendars, but only if the user had allowed it before.
        if (this.calendarPermission.hasPermission())
            this.userCalendars.value = MutableCalendarsList(this)

        this.setContent {
            CalProvExampleTheme {
                val navController = rememberNavController()

                NavHost(navController, startDestination = NavDestination.Main.route) {
                    this.composable(NavDestination.Main.route) {
                        /** Open a secondary item in this screen, such as the FAB or a dialog */
                        var openAction: Actions? by rememberSaveable { mutableStateOf(null) }
                        val asyncScope = rememberCoroutineScope()

                        MainContent(
                            navigateTo = { dest -> navController.navigate(dest.route) },
                            snackBarState = snackBarState
                        ) {
                            this.tabWithFab(
                                icon = Icons.Default.DateRange,
                                title = "Calendars",
                                fab = ExpandableFab(
                                    icon = Icons.Default.Add,
                                    description = "Add/New Calendar",
                                    actions = NonEmptyList(
                                        ExpandableFab.Action(Icons.Default.Create, "New blank calendar")
                                            { openAction = Actions.NewCalendar },
                                        ExpandableFab.Action(R.drawable.rounded_calendar_add_on_24, "Device calendar") {
                                            asyncScope.launch {
                                                // FIXME: "Skipped 41 frames!  The application may be doing too much work on its main thread."
                                                // Get the Calendars in the device the user can copy
                                                val calendars = this@MainActivity.asyncCalendarPermission.runWithMessage("Searching for calendars") {
                                                    this.externalUserCalendars()
                                                } ?: run {
                                                    this@MainActivity.showToast("Error getting calendars")
                                                    Log.e("CopyCalendar", "Could not get external calendars, either because of error or permissions denied")
                                                    openAction = null
                                                    return@launch
                                                }
                                                AsyncDialog.showDialog { close ->
                                                    CopyCalendarAction(
                                                        calendars = calendars.map { cal ->
                                                            ExternalUserCalendar(cal,
                                                                // Find the calendar owned by this app (internal) that copied this calendar's data (if any).
                                                                userCalendars.value?.find { iCal -> cal.id == iCal.importedFrom }?.name
                                                            )
                                                        }.groupBy { cal -> cal.accountName },
                                                        close = { close(); openAction = null },
                                                        submit = { selectedCals -> userCalendars.value?.copyFromExternal(selectedCals, asyncScope) }
                                                    )
                                                }
                                            }
                                        },
                                        ExpandableFab.Action(R.drawable.rounded_upload_file_24, "Import from file") {
                                            asyncScope.launch {
                                                // Select synDir before importing because it will be expected to Not be NULL.
                                                if (syncDir.value == null) {
                                                    // Tell the user they will select syncDir
                                                    if (!AsyncDialog.show("Select sync directory")) {
                                                        this@MainActivity.showToast("Import canceled")
                                                        return@launch
                                                    }
                                                    dirSelectChannel.tryReceive() // clear channel
                                                    this@MainActivity.selectSyncDir()
                                                    if (!dirSelectChannel.receive()) {
                                                        // Cancel the import if the user canceled selecting the directory
                                                        this@MainActivity.showToast("Import canceled")
                                                        return@launch
                                                    }
                                                    // Tell the user they will select the file to import
                                                    AsyncDialog.showNoCancel("Select file to import")
                                                }
                                                importChannel = Channel(Channel.BUFFERED)
                                                // The ACTION_OPEN_DOCUMENT Intent takes the MIME Types of files that can be opened
                                                importFilesIntent.launch(arrayOf(CALENDAR_DOCUMENT_MIME_TYPE))
                                                importChannel!!.let { importChannel ->
                                                    for (data in importChannel) {
                                                        if (data == null)
                                                            continue
                                                        AsyncDialog.showDialog { close ->
                                                            ImportFileExistsAction(
                                                                name = data.name,
                                                                rename = { newName-> this@MainActivity.finishImportRename(data.fileUri, "$newName.ics") },
                                                                overwrite = { this@MainActivity.finishImportOverwrite(data.fileUri) },
                                                                close = close
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                    )
                                ),
                            ) { modifier ->
                                Calendars(
                                    modifier = modifier,
                                    calendars = userCalendars.value,
                                    hasSelectedDir = syncDir.value != null,
                                    selectDirClick = { this@MainActivity.selectSyncDir() },
                                    calPermsClick = {
                                        this@MainActivity.calendarPermission.run {
                                            userCalendars.value = MutableCalendarsList(this@MainActivity)
                                        }
                                    },
                                    syncCalendarSwitch = { id, sync -> userCalendars.value?.edit(id) { it.copy(sync = sync) } },
                                    editCalendar = { id, name, color -> openAction = Actions.EditCalendar(id, name, color) },
                                    deleteCalendar = { name -> this@MainActivity.deleteCalendar(name, asyncScope) }
                                )
                            }

                            this.tab(icon = Icons.Default.AccountCircle, title = "Contacts") { modifier ->
                                Text("Contacts section", modifier = modifier)
                            }
                        }

                        // Show a dialog for the current action the user selected
                        when (openAction) {
                            Actions.NewCalendar -> NewCalendarAction(
                                close = { openAction = null },
                                submit = { name, color -> userCalendars.value?.add(CalendarData(name, color)) }
                            )
                            is Actions.EditCalendar -> {
                                val data = openAction as Actions.EditCalendar
                                EditCalendarAction(
                                    title = { Text("Edit Calendar") },
                                    color = data.color,
                                    name = data.name,
                                    close = { openAction = null },
                                    submit = { newName, newColor -> userCalendars.value?.edit(data.id) { it.copy(name = newName, color = newColor) } }
                                )
                            }
                            null -> {}
                        }
                    }
                    this.composable(NavDestination.Settings.route) {
                        SettingsContent(
                            navUpClick = { navController.navigateUp() },
                            settings = listOf(
                                { BooleanSetting(
                                    name = "Fragment Calendars",
                                    summary = "Store data about each Calendar in a separate .ics file",
                                    value = fragmentCals.value ?: false,
                                    onClick = { checked -> fragmentCals.value = checked }
                                ) },
                                { DirSetting(
                                    name = "Syncing Directory",
                                    value = this@MainActivity.syncDir.value,
                                    selectClick = { this@MainActivity.selectSyncDir() }
                                ) }
                            )
                        )
                    }
                    this.composable(NavDestination.Debug.route) {
                        val data = remember { AllData(this@MainActivity.baseContext) }
                        DebugContent(
                            navUpClick = { navController.navigateUp() },
                            data = data,
                        )
                    }
                }
                this.calendarPermission.RationaleDialog()
                this.asyncCalendarPermission.RationaleDialog()
                this.asyncCalendarPermission.SuspendDialog()
                AsyncDialog.Dialog()
            }
        }
    }

    /** Delete the calendar and show a **snack-bar** that can undo the deletion.
     *
     * Moves the deleted calendar to the `"deleted"` directory (like a recycle bin) before fully deleting it.
     * The file is only fully deleted after the snack-bar is dismissed or app is reopened. */
    private fun deleteCalendar(name: String, asyncScope: CoroutineScope) {
        val calendars = this.userCalendars.value ?: run {
            Log.e("UI deleteCalendar", "Trying to delete Calendar when 'userCalendars' is NULL")
            return
        }

        calendars.removeAt(calendars.indexOfFirst { cal -> cal.name == name })

        asyncScope.launch {
            val msg = "Calendar deleted"

            // If there is already a 'Deleted Calendar' snack-bar, dismiss it to show this one.
            snackBarState.currentSnackbarData?.let { current ->
                if (current.visuals.message == msg)
                    current.dismiss()
            }

            when (snackBarState.showSnackbar(
                message = msg,
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )) {
                // Restore Calendar when user presses "Undo"
                SnackbarResult.ActionPerformed -> calendars.restore(name)
                // Fully delete Calendar by deleting file in recycle bin
                SnackbarResult.Dismissed -> {
                    try {
                        Path("${this@MainActivity.filesDir.path}/deleted/calendars/$name.ics").delete()
                    } catch (e: Exception) {
                        false
                    }.let {
                        if (!it)
                            Log.e("deleteFiles", "Error deleting file in internal directory.")
                    }
                }
            }
        }
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun showToast(text: String) = Toast.makeText(this.baseContext, text, Toast.LENGTH_SHORT).show()
    fun showToast(@StringRes resId: Int) = Toast.makeText(this.baseContext, resId, Toast.LENGTH_SHORT).show()

    private fun selectSyncDir() {
        // The ACTION_OPEN_DOCUMENT_TREE Intent can optionally take an URI where the file picker will open to.
        this.dirSelectIntent.launch(null)
    }
}

data class CalendarData(
    val name: String,
    val color: Color,
)

class MutableCalendarsList(
    private val activity: MainActivity,
): List<InternalUserCalendar> {
    private val list: SnapshotStateList<InternalUserCalendar> = mutableStateListOf()
    init {
        if (activity.calendarPermission.hasPermission())
            activity.calendarPermission.run {
                this.internalUserCalendars()?.also { cals ->
                    this@MutableCalendarsList.list.addAll(cals)
                } ?: Log.e("MutableCalendarsList", "Error querying Content Provider for Local Calendars")
            }
        else
            Log.e("MutableCalendarsList", "Missing Calendar permission to initialize User Calendars list")
    }

    override val size: Int
        get() = this.list.size

    /** Import a list of Calendars *not owned by this app* that the user chose.
     *
     * This Function first checks that none of the Calendars have conflicting names with Calendars already in the list.
     * If they do, a **dialog** will prompt the user whether they want to ***rename, overwrite, or cancel import***. */
    internal fun copyFromExternal(calendars: List<ExternalUserCalendar>, asyncScope: CoroutineScope) {
        asyncScope.launch {
            activity.asyncCalendarPermission.runWithMessage("Copying calendars") {
                for (tmpCal in calendars) {
                    // Check whether the selected calendar would have conflict with another calendar
                    val cal = if (this@MutableCalendarsList.find { cal -> cal.name == tmpCal.name } == null)
                        tmpCal
                    else {
                        // In case of conflict, ask user whether to rename, overwrite, or don't import at all
                        var finalName: String? = null
                        AsyncDialog.showDialog { close ->
                            ImportFileExistsAction(
                                name = tmpCal.name,
                                rename = { newName -> finalName = newName },
                                overwrite = {
                                    // TODO: could show snack-bar with undo action
                                    this@MutableCalendarsList.remove(tmpCal.name)
                                    finalName = tmpCal.name
                                },
                                close = close
                            )
                        }

                        // The calendar is omitted when finalName is NULL because the user canceled the import
                        finalName?.let {
                            name -> tmpCal.copy(name = name)
                        } ?: continue
                    }

                    this.copyExternalCalendar(cal)?.let { newCal ->
                        // Create the files
                        activity.createFiles("${newCal.name}.ics", newCal.color, newCal.id)
                        writeCalendarDataToFile(newCal.name)
                        // Add the Calendar to the list
                        this@MutableCalendarsList.list.add(newCal)
                    } ?: throw Exception("Error copying calendar ${cal.name}")
                }
            }
        }
    }

    override fun get(index: Int): InternalUserCalendar = this.list[index]

    // operator fun set(index: Int, element: CalendarData): CalendarData {
    // }

    fun edit(id: Long, editor: (InternalUserCalendar) -> InternalUserCalendar) {
        this.edit(this.indexOfFirst { it.id == id }, editor)
    }
    fun edit(index: Int, editor: (InternalUserCalendar) -> InternalUserCalendar) {
        val old = this[index]
        val new = editor(old)
        val syncDir = activity.syncDir.value ?: throw Exception("syncDir is NULL; can't delete external file")

        if (old.id != new.id)
            throw Exception("Can't manually change the ID of a Calendar")

        // Rename files
        if (old.name != new.name) {
            // Check if name is unique
            if (this.indexOfFirst { e -> e.name == new.name } != -1)
                throw Exception("An element with name \"${new.name}\" already exists in the list")
            // Rename internal file
            val file = activity.internalFile("${old.name}.ics")
            java.nio.file.Files.move(
                file.toPath(),
                file.resolveSibling(new.name).toPath()
            )
            // Rename external file
            DocumentsContract.renameDocument(activity.contentResolver,
                externalFile(syncDir, "${old.name}.ics"), new.name
            ) ?: throw Exception("Error renaming file in external directory from \"${old.name}\" to \"${new.name}\"")
        }
        // Change color in files
        if (old.color != new.color)
            writeColorToCalendarFile(old.name, new.color)

        // Change data in the Content Provider
        if (activity.calendarPermission.hasPermission())
            activity.calendarPermission.run {
                if (!this.editCalendar(old.id, new.name, new.color, new.sync))
                    throw Exception("Error editing Calendar in Content Provider")
            }
        else
            throw Exception("Missing permission to create Calendar in Content Provider")

        // Change data in the list
        this.list[index] = new
    }

    // fun addAll(elements: Collection<CalendarData>): Boolean = elements.all { this.add(it) }
    // fun addAll(index: Int, elements: Collection<CalendarData>): Boolean {
    //     var i = index
    //     return elements.all { element -> this.add(i++, element); true }
    // }
    fun add(element: CalendarData): Boolean { this.add(this.size, element); return true }
    /** Inserts an [element] into the list at some [index].
     * @throws IndexOutOfBoundsException when [index] falls outside the range *0 <= i <= [size]*.
     * Note that when index == size is equivalent to calling [add] (element), but not if index > size.
     * @throws Exception if the [element] could not be added because of some internal error.
     * (e.g. creating files, content provider, etc.) */
    @Throws(IndexOutOfBoundsException::class)
    fun add(index: Int, element: CalendarData) {
        val name = element.name
        val fileName = "$name.ics"

        // Check if name is unique
        if (this.indexOfFirst { e -> e.name == name } != -1)
            throw Exception("An element with name \"$name\" already exists in the list")

        // Create calendar files
        activity.createFiles(fileName, element.color)
        // Create entry in Content Provider
        if (activity.calendarPermission.hasPermission())
            activity.calendarPermission.run {
                this.newCalendar(name, element.color)?.let { newCal ->
                    this@MutableCalendarsList.run {
        // Add Calendar to the list
                        if (index > this.size)
                            throw IndexOutOfBoundsException("Can't insert at index $index in a list with ${this.size} elements")
                        else if (index == this.size)
                            this.list.add(newCal)
                        else
                            this.list.add(index, newCal)
                    }
                } ?: throw Exception("Error creating new calendar")
            }
        else
            throw Exception("Missing permission to create Calendar in Content Provider")
    }

    // fun removeAll(elements: Collection<InternalUserCalendar>): Boolean = elements.all { this.remove(it) }
    // fun remove(element: InternalUserCalendar): Boolean =
    //     try {
    //         this.removeAt(this.indexOf(element)); true
    //     } catch (e: Exception) {
    //         Log.e("MutableCalendarsList", "Error removing element from list: $e"); false
    //     }
    fun removeAll(names: Collection<String>) = names.forEach { name -> this.remove(name) }
    fun remove(name: String) {
        try {
            this.removeAt(this.indexOfFirst { it.name == name })
        } catch (e: Exception) {
            Log.e("MutableCalendarList", "Error removing element \"$name\" from list: $e")
        }
    }
    fun clear() { this.onEachIndexed { i, _ -> try { this.removeAt(i) } catch (_: Exception) {} } }
    /** Removes an element from the list that is found at some [index].
     * @return The element that was removed
     * @throws IndexOutOfBoundsException when [index] falls outside the range *0 <= i < [size]*
     * @throws Exception if the element could not be removed because of some internal error.
     * (e.g. deleting files, content provider, etc.) */
    @Throws(IndexOutOfBoundsException::class)
    fun removeAt(index: Int): InternalUserCalendar {
        val name = this[index].name
        val fileName = "$name.ics"

        // Put this first in case there is a bug where the file doesn't exist, the entry will no longer be showed.
        // Delete the Calendar from the Content Provider
        if (activity.calendarPermission.hasPermission())
            activity.calendarPermission.run {
                if (!this.deleteCalendarByName(name))
                    throw Exception("Calendar \"$name\" not removed from Content Provider.")
            }
        else
            throw Exception("Missing permission to delete Calendar from Content Provider")

        // Copy internal file to recycle bin before deleting everything
        try {
            activity.internalFile(fileName)
                .copyTo(Path("${activity.filesDir}/deleted/calendars/$fileName"), true)
        } catch (e: Exception) {
            throw IOException("Error copying \"$fileName\" to \"deleted/calendars\" directory: $e")
        }
        // Delete the Calendar files
        // FIXME: crashes if fileName has spaces or symbols
        activity.deleteFiles(fileName)

        // Delete the Calendar from the list
        return this.list.removeAt(index)
    }

    /** Moves a file that was moved to the "Recycle Bin" back to its respective directories,
     * and adds it back to the Content Provider and the calendars list.
     * @throws Exception if an error occurs while working with the files or Content Provider. */
    internal fun restore(name: String) {
        val fileName = "$name.ics"
        val syncDir = activity.syncDir.value ?: throw Exception("syncDir is NULL; can't delete external file")
        val deleted = Path("${activity.filesDir.path}/deleted/calendars/$fileName")
        if (!deleted.exists())
            throw Exception("Tried to restore a calendar file that does not exist in the recycle bin")

        // Copy file to internal dir
        deleted.copyTo(activity.internalFile(fileName))
        // TODO: what if a calendar with the same name was created while this one was in recycle bin?
        // Copy file to external dir
        DocumentsContract.copyDocument(activity.contentResolver,
            deleted.toUri(),
            externalFile(syncDir, fileName),
        )
        // File in recycle bin is no longer needed
        deleted.delete()
        // Create entry in Content Provider,
        activity.calendarPermission.run {
            this.newCalendar(name, Color(DEFAULT_CALENDAR_COLOR))?.let { newCal ->
                // Add the Calendar to the list
                this@MutableCalendarsList.list.add(newCal)
            } ?: throw Exception("Error creating new calendar")
        }
        // ,and parse the file's content
        writeFileDataToCalendar(name)
    }

    override fun isEmpty(): Boolean = this.list.isEmpty()

    override fun iterator(): MutableIterator<InternalUserCalendar> = this.list.iterator()
    override fun listIterator(): MutableListIterator<InternalUserCalendar> = this.list.listIterator()
    override fun listIterator(index: Int): MutableListIterator<InternalUserCalendar> = this.list.listIterator(index)
    override fun subList(fromIndex: Int, toIndex: Int): MutableList<InternalUserCalendar> = throw NotImplementedError("WILL NOT IMPLEMENT")
    // fun retainAll(elements: Collection<InternalUserCalendar>): Boolean = throw NotImplementedError("WILL NOT IMPLEMENT")
    override fun lastIndexOf(element: InternalUserCalendar): Int = this.list.lastIndexOf(element)
    override fun indexOf(element: InternalUserCalendar): Int = this.list.indexOf(element)
    override fun containsAll(elements: Collection<InternalUserCalendar>): Boolean = this.list.containsAll(elements)
    override fun contains(element: InternalUserCalendar): Boolean = this.list.contains(element)
}

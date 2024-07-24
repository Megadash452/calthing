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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import me.marti.calprovexample.ElementExistsException
import me.marti.calprovexample.ILLEGAL_FILE_CHARACTERS
import me.marti.calprovexample.ImportFileResult
import me.marti.calprovexample.MutableMapList
import me.marti.calprovexample.NonEmptyList
import me.marti.calprovexample.PreferenceKey
import me.marti.calprovexample.R
import me.marti.calprovexample.StringLikeUserPreference
import me.marti.calprovexample.UserPreference
import me.marti.calprovexample.ValueEditor
import me.marti.calprovexample.calendar.AllData
import me.marti.calprovexample.calendar.ExternalUserCalendar
import me.marti.calprovexample.calendar.InternalUserCalendar
import me.marti.calprovexample.calendar.copyExternalCalendar
import me.marti.calprovexample.calendar.deleteCalendarByName
import me.marti.calprovexample.calendar.editCalendar
import me.marti.calprovexample.calendar.externalUserCalendars
import me.marti.calprovexample.calendar.getData
import me.marti.calprovexample.calendar.internalUserCalendars
import me.marti.calprovexample.calendar.newCalendar
import me.marti.calprovexample.calendar.writeFileDataToCalendar
import me.marti.calprovexample.copyToExternalFile
import me.marti.calprovexample.createFiles
import me.marti.calprovexample.deleteFiles
import me.marti.calprovexample.externalFile
import me.marti.calprovexample.fileName
import me.marti.calprovexample.fileNameWithoutExtension
import me.marti.calprovexample.getAppPreferences
import me.marti.calprovexample.internalFile
import me.marti.calprovexample.launch
import me.marti.calprovexample.mergeDirs
import me.marti.calprovexample.openFd
import me.marti.calprovexample.ui.theme.CalProvExampleTheme
import me.marti.calprovexample.writeCalendarDataToFile
import me.marti.calprovexample.writeColorToCalendarFile
import java.io.IOException
import java.util.concurrent.Executors
import java.io.File as Path

const val DEFAULT_CALENDAR_COLOR = 0x68acef
const val CALENDAR_DOCUMENT_MIME_TYPE = "text/calendar"

class MainActivity : ComponentActivity() {
    private val onCalendarPermGranted: () -> Unit = {
        Log.d("MainActivity", "Calendar Permission granted")

        // Add files that are not in the content provider
        this.calendarPermission.launchOrFail("Syncing Calendars") {
            val internalFiles = Path("${this@MainActivity.filesDir.path}/calendars/").listFiles()
                ?: return@launchOrFail
            val calendars = this.internalUserCalendars()!!.map { it.name }
            for (file in internalFiles.filter { !calendars.contains(it.name) })
                this.writeFileDataToCalendar(fileNameWithoutExtension(file.name), this@MainActivity.filesDir)
            // Init list if it's not already
            println("sync list with content provider")
            userCalendars.value?.syncWithProvider() ?: run {
                userCalendars.value = MutableCalendarsList(this@MainActivity)
            }
        }
    }
    val calendarPermission = CalendarPermission(this, this.onCalendarPermGranted)
    private val snackBarState = SnackbarHostState()
    private lateinit var snackBarAsyncScope: CoroutineScope
    internal val calendarWorkThread = Executors.newSingleThreadExecutor()

    // -- Hoisted States for compose
    /** The path/URI where the synced .ics files are stored in shared storage.
      * Null if the user hasn't selected a directory.
     *
     * This URI can be used by the [Documents Content Provider][android.provider.DocumentsProvider]
     * by using the [DocumentsContract]. */
    val syncDir: UserPreference<Uri> = StringLikeUserPreference(PreferenceKey.SYNC_DIR_URI) { uri -> uri.toUri() }
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
    private val dirSelectIntent = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
        this.calendarWorkThread.launch("Syncing directories") {
            if (treeUri == null) {
                println("OpenDir: User cancelled the file picker.")
                dirSelectChannel.trySend(false)
                return@launch
            }
            // Preserve access to the directory. Otherwise, access would be revoked when app is closed.
            contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            // Convert Tree URI to an URI that can be used by the DocumentsProvider
            val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))

            this.openFd(uri)?.use { file ->
                // Create calendar and contacts dirs in internal and external directories.
                DavSyncRs.initialize_dirs(file.fd, this.filesDir.path)
                // // // Copy files from internal to external, and vice versa, resolving conflicts with user
                // DavSyncRs.merge_dirs(file.fd, this.filesDir.path)
            } ?: run {
                dirSelectChannel.trySend(false)
                return@launch
            }

            this.mergeDirs(uri)

            this.syncDir.value = uri
            dirSelectChannel.trySend(true)
            println("User selected $uri for synced .ics files.")
            // If nothing works, recheck DocumentsContract and DocumentFile
        }
    }

    /** Register for file picker intent. */
    private val importFilesIntent = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isEmpty()) {
            println("OpenFiles: User cancelled the file picker.")
            return@registerForActivityResult
        }

        this.calendarWorkThread.launch("Importing files") {
            for (uri in uris)
                this.userCalendars.value?.addFile(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must set navigationBarStyle to remove the scrim.
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.light(0, 0))
        super.onCreate(savedInstanceState)
        Log.d(null, "Initializing Main Activity")

        val fragmentCals = BooleanUserPreference(PreferenceKey.FRAGMENT_CALS)

        val preferences = this.baseContext.getAppPreferences()
        this.syncDir.initStore(preferences)
        fragmentCals.initStore(preferences)

        // Check if still has access to external directory
        this.syncDir.value?.let { uri ->
            if (this.contentResolver.persistedUriPermissions.find {
                it.uri == DocumentsContract.buildTreeDocumentUri(uri.authority, DocumentsContract.getDocumentId(uri))
            } == null)
                this.syncDir.value = null
        }

        // Clear recycle bin
        Path("${this.filesDir}/deleted/").deleteRecursively()

        // Populate the list of synced calendars, but only if the user had allowed it before.
        if (this.calendarPermission.hasPermission())
            userCalendars.value = MutableCalendarsList(this@MainActivity)

        this.setContent {
            CalProvExampleTheme {
                val navController = rememberNavController()

                NavHost(navController, startDestination = NavDestination.Main.route) {
                    this.composable(NavDestination.Main.route) {
                        /** Open a secondary item in this screen, such as the FAB or a dialog */
                        var openAction: Actions? by rememberSaveable { mutableStateOf(null) }
                        val asyncScope = rememberCoroutineScope()
                        snackBarAsyncScope = asyncScope

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
                                    actions = this@MainActivity.calendarFabActions(asyncScope)
                                ),
                            ) { modifier ->
                                Calendars(
                                    modifier = modifier,
                                    calendars = userCalendars.value,
                                    hasSelectedDir = syncDir.value != null,
                                    selectDirClick = { this@MainActivity.selectSyncDir() },
                                    calPermsClick = {
                                        this@MainActivity.calendarPermission.launch {
                                            userCalendars.value = MutableCalendarsList(this@MainActivity)
                                        }
                                    },
                                    syncCalendarSwitch = { id, sync -> userCalendars.value?.edit(id) { this.sync = sync } },
                                    editCalendar = { id, name, color -> openAction = Actions.EditCalendar(id, name, color) },
                                    deleteCalendar = { name -> userCalendars.value?.remove(name) }
                                )
                            }

                            this.tab(icon = Icons.Default.AccountCircle, title = "Contacts") { modifier ->
                                Text("Contacts section", modifier = modifier)
                            }
                        }

                        // Show a dialog for the current action the user selected
                        when (openAction) {
                            is Actions.EditCalendar -> {
                                val data = openAction as Actions.EditCalendar
                                EditCalendarAction(
                                    title = { Text("Edit Calendar") },
                                    color = data.color,
                                    name = data.name,
                                    close = { openAction = null },
                                    submit = { newName, newColor -> userCalendars.value?.edit(data.id) { name = newName; color = newColor } }
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
                // FIXME: Race Condition: SuspendDialog can show on top of AsyncDialog, blocking it unintentionally
                AsyncDialog.Dialog()
                SuspendDialog.Dialog()
            }
        }
    }

    /** Defines the actions of the [ExpandableFab] */
    private fun calendarFabActions(asyncScope: CoroutineScope) = NonEmptyList(
        ExpandableFab.Action(Icons.Default.Create, "New blank calendar") {
            this@MainActivity.calendarPermission.launch {
                AsyncDialog.showDialog { close ->
                    NewCalendarAction(
                        close = close,
                        submit = { name, color ->
                            userCalendars.value?.add(CalendarEditor(name, color))
                        }
                    )
                }
            }
        },
        ExpandableFab.Action(R.drawable.rounded_calendar_add_on_24, "Device calendar") {
            this.calendarWorkThread.launch {
                // Get the Calendars in the device the user can copy
                val calendars = this@MainActivity.calendarPermission.run("Searching for calendars") {
                    this.externalUserCalendars()
                } ?: run {
                    this@MainActivity.showToast("Error getting calendars")
                    Log.e("CopyCalendar", "Could not get external calendars, either because of error or permissions denied")
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
                        close = close,
                        submit = { selectedCals -> userCalendars.value?.copyFromExternal(selectedCals) }
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
                // The ACTION_OPEN_DOCUMENT Intent takes the MIME Types of files that can be opened
                importFilesIntent.launch(CALENDAR_DOCUMENT_MIME_TYPE)
            }
        },
    )

    fun showToast(text: String) = this.runOnUiThread {
        Toast.makeText(this.baseContext, text, Toast.LENGTH_SHORT).show()
    }

    /** Shows a snack-bar with a **message** and an "Undo" button.
     * Automatically dismisses other snack-bars with the same message.
     *
     * @param onResult Is called when the snackbar's "Undo" button is pressed or it is dismissed either by the user or the timer. */
    fun showSnackbar(msg: String, onResult: suspend (SnackbarResult) -> Unit) {
        this.snackBarAsyncScope.launch {
            // If there is already a snack-bar with the same message, dismiss it to show this one.
            this@MainActivity.snackBarState.currentSnackbarData?.let { current ->
                if (current.visuals.message == msg)
                    current.dismiss()
            }

            val result = this@MainActivity.snackBarState.showSnackbar(
                message = msg,
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            onResult(result)
        }
    }

    private fun selectSyncDir() {
        // The ACTION_OPEN_DOCUMENT_TREE Intent can optionally take an URI where the file picker will open to.
        this.dirSelectIntent.launch(null)
    }
}

/** Used for editing calendar in [MutableCalendarsList.edit]. */
class CalendarEditor(
    var name: String,
    var color: Color,
    var sync: Boolean = false
): ValueEditor<InternalUserCalendar> {
    constructor(cal: InternalUserCalendar) : this(
        name = cal.name,
        color = cal.color,
        sync = cal.sync
    )
    override fun editFrom(value: InternalUserCalendar) {
        this.name = value.name
        this.color = value.color
        this.sync = value.sync
    }
}

class MutableCalendarsList(
    private val activity: MainActivity
): MutableMapList<String, InternalUserCalendar, CalendarEditor> {
    private val list = mutableStateListOf<InternalUserCalendar>()
    init {
        activity.calendarPermission.launchOrFail {
            this@MutableCalendarsList.syncWithProvider()
        }
    }

    /** Read the App's calendars stored by the ContentProvider.
     * This should be called either to *initialize* the list or
     * *update* the list after files have been modified.
     *
     * Use ONLY if calling from a [worker thread][MainActivity.calendarWorkThread] */
    internal suspend fun syncWithProvider() {
        activity.calendarPermission.runOrFail {
            this.internalUserCalendars()?.also { cals ->
                this@MutableCalendarsList.list.clear()
                this@MutableCalendarsList.list.addAll(cals)
            } ?: Log.e("MutableCalendarsList", "Error querying Content Provider for Local Calendars")
        }
    }

    private fun calDeletedSnackbar(name: String) {
        activity.showSnackbar("Calendar deleted") { result ->
            when (result) {
                // Restore Calendar when user presses "Undo"
                SnackbarResult.ActionPerformed -> this.restore(name)
                // Fully delete Calendar by deleting file in recycle bin
                SnackbarResult.Dismissed -> this.finishRemove(name)
            }
        }
    }

    /** Import a list of Calendars *not owned by this app* that the user chose.
     *
     * This Function first checks that none of the Calendars have conflicting names with Calendars already in the list.
     * If they do, a **dialog** will prompt the user whether they want to ***rename, overwrite, or cancel import***. */
    internal fun copyFromExternal(calendars: List<ExternalUserCalendar>) {
        activity.calendarPermission.launchOrFail("Copying calendars") {
            for (tmpCal in calendars) {
                val cal =
                    // Check whether the selected calendar would have conflict with another calendar
                    if (this@MutableCalendarsList.containsKey(tmpCal.name)) {
                        // In case of conflict, ask user whether to rename, overwrite, or don't import at all
                        var finalName: String? = null
                        AsyncDialog.showDialog { close ->
                            ImportFileExistsAction(
                                name = tmpCal.name,
                                rename = { newName -> finalName = newName },
                                overwrite = {
                                    finalName = tmpCal.name
                                    this@MutableCalendarsList.remove(tmpCal.name)
                                    this@MutableCalendarsList.calDeletedSnackbar(tmpCal.name)
                                },
                                close = close
                            )
                        }
                        // The calendar is omitted when finalName is NULL because the user canceled the import
                        finalName?.let { name -> tmpCal.copy(name = name) } ?: continue
                    }
                    // Check for Illegal characters and replace them
                    else if (!tmpCal.name.all { c -> !ILLEGAL_FILE_CHARACTERS.contains(c) }) {
                        tmpCal.copy(name = tmpCal.name.replace(
                            Regex("[${ILLEGAL_FILE_CHARACTERS.joinToString(separator = "") { it.toString() }}]"),
                            Regex.escapeReplacement("_")
                        ))
                    } else
                        tmpCal

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

    /** Like [MutableMapList.edit], but uses the [ID][id] of the calendar as the key instead of the **name**. */
    fun edit(id: Long, editor: CalendarEditor.() -> Unit) {
        this.edit(
            this.find { cal -> cal.id == id }?.name
                ?: throw NoSuchElementException("There is no calendar with ID \"$id\""),
            editor
        )
    }
    override fun edit(key: String, editor: CalendarEditor.() -> Unit) {
        val index = this.indexOfFirst { cal -> cal.name == key }
        if (index == -1)
            throw NoSuchElementException("There is no calendar named \"$key\"")
        val old = this[index]
        val new = CalendarEditor(old)
        editor(new)

        activity.calendarWorkThread.launch {
            // Rename files
            if (old.name != new.name) {
                // Check if name is unique
                if (this.indexOfFirst { e -> e.name == new.name } != -1)
                    throw Exception("An element with name \"${new.name}\" already exists in the list")
                // Rename internal file
                val file = activity.internalFile("${old.name}.ics")
                java.nio.file.Files.move(
                    file.toPath(),
                    file.resolveSibling("${new.name}.ics").toPath()
                )
                // Rename external file
                activity.syncDir.value?.also { syncDir ->
                    DocumentsContract.renameDocument(
                        activity.contentResolver,
                        externalFile(syncDir, "${old.name}.ics"), "${new.name}.ics"
                    )
                        ?: throw Exception("Error renaming file in external directory from \"${old.name}.ics\" to \"${new.name}.ics\"")
                } ?: run {
                    Log.w("MutableCalendarList.edit", "syncDir is NULL; can't rename external file")
                    return@launch
                }
            }
            // Change color in files
            if (old.color != new.color)
                writeColorToCalendarFile(old.name, new.color)

            // Change data in the Content Provider
            activity.calendarPermission.runOrFail {
                if (!this.editCalendar(old.id, new.name, new.color, new.sync))
                    throw Exception("Error editing Calendar in Content Provider")
            }

            // Change data in the list
            this.list[index] = old.copy(name = new.name, color = new.color, sync = new.sync)
        }
    }

    override fun add(element: CalendarEditor) {
        activity.calendarWorkThread.launch {
            val name = element.name
            val fileName = "$name.ics"

            // Check if name is unique
            if (this.indexOfFirst { e -> e.name == name } != -1)
                throw ElementExistsException(element.name)

            // Create calendar files
            activity.createFiles(fileName, element.color)
            // Create entry in Content Provider
            activity.calendarPermission.runOrFail {
                this.newCalendar(name, element.color)?.let { newCal ->
                    // Add Calendar to the list
                    this@MutableCalendarsList.list.add(newCal)
                } ?: throw Exception("Error creating new calendar")
                // Fill data in Content Provider
                this.writeFileDataToCalendar(name, activity.filesDir)
            }
        }
    }

    /** Add a Calendar from a file in Shared Storage.
     * @throws Exception if the Calendar could not be added because of some internal error.
     * (e.g. creating files, content provider, etc.) */
    internal fun addFile(fileUri: Uri) {
        activity.calendarWorkThread.launch {
            val syncDir = activity.syncDir.value ?: run {
                Log.w("createFiles", "syncDir is NULL; Can't add external file; Will add it later")
                return@launch
            }
            val openFdError = "Error opening file picked by user. See log."
            val importErrorToast = "Error importing file"

            // Import to internal file first
            val result: ImportFileResult = activity.openFd(fileUri)?.use { file ->
                DavSyncRs.importFileInternal(file.fd, fileUri.fileName()!!, activity.filesDir.path)
            } ?: throw Exception(openFdError)

            val name = when (result) {
                is ImportFileResult.Error -> {
                    activity.showToast(importErrorToast)
                    return@launch
                }
                is ImportFileResult.FileExists -> {
                    // In case of conflict, ask user whether to rename, overwrite, or don't import at all
                    var finalName: String = result.calName
                    var choice = 0
                    AsyncDialog.showDialog { close ->
                        ImportFileExistsAction(
                            name = result.calName,
                            rename = { newName-> finalName = newName; choice = 1 },
                            overwrite = { choice = 2 },
                            close = close
                        )
                    }
                    when (choice) {
                        /* Canceled */0 -> return@launch
                        /* Rename */1 -> { }
                        /* Overwrite */2 -> {
                        this.removeBlocking(finalName)
                        this.calDeletedSnackbar(finalName)
                    }
                    }

                    // Retry import under new conditions
                    activity.openFd(fileUri)?.use { file ->
                        when (DavSyncRs.importFileInternal(file.fd, "$finalName.ics", activity.filesDir.path)) {
                            is ImportFileResult.Error -> {
                                activity.showToast(importErrorToast)
                                return@launch
                            }
                            is ImportFileResult.FileExists -> {
                                Log.e("finishImport (Rename)", "Tried to import file under different name, but still another file exists.")
                                activity.showToast(importErrorToast)
                                return@launch
                            }
                            is ImportFileResult.Success -> {}
                        }
                    } ?: throw Exception(openFdError)

                    finalName
                }
                is ImportFileResult.Success -> result.calName
            }
            val fileName = "$name.ics"

            // TODO: check if file is in syncDir. if it's not, create external file, otherwise don't
            // Create external file
            activity.copyToExternalFile(fileName, syncDir)
            // Create entry in Content Provider
            activity.calendarPermission.runOrFail {
                // Color doesn't matter, as it will be assigned in writeFileDataToCalendar
                this.newCalendar(name, Color(0)) ?: throw Exception("Error creating Calendar from File")
                this.writeFileDataToCalendar(name, activity.filesDir)
                // Add Calendar to the list
                this@MutableCalendarsList.list.add(this.getData(name)
                    ?: throw Exception("Error getting data of newly added Calendar")
                )
            }
        }
    }

    /** Removes a calendar with **name** from the list.
     *
     * Puts calendar in a "recycle bin" so that it can be restored later.
     *
     * @return The element that was removed
     * @throws NoSuchElementException if there is no calendar with this [name].
     * @throws Exception if the element could not be removed because of some internal error.
     * (e.g. deleting files, content provider, etc.) */
    @Throws(NoSuchElementException::class)
    private suspend fun removeBlocking(name: String) {
        val index = this.indexOfFirst { cal -> cal.name == name }
        if (index == -1)
            throw NoSuchElementException("There is no calendar named \"$name\"")
        val fileName = "$name.ics"

        // Delete the Calendar from the list
        this.list.removeAt(index)

        // Put this first in case there is a bug where the file doesn't exist, the entry will no longer be showed.
        // Delete the Calendar from the Content Provider
        activity.calendarPermission.runOrFail {
            if (!this.deleteCalendarByName(name))
                throw Exception("Calendar \"$name\" not removed from Content Provider.")
        }

        // Copy internal file to recycle bin before deleting everything
        try {
            activity.internalFile(fileName)
                .copyTo(Path("${activity.filesDir}/deleted/calendars/$fileName"), true)
        } catch (e: Exception) {
            throw IOException("Error copying \"$fileName\" to \"deleted/calendars\" directory: $e")
        }
        // Delete the Calendar files
        activity.deleteFiles(fileName)
    }

    /** Fully delete the Calendar, that is, delete the copy of the file that was moved to the "recycle bin". */
    @Suppress("RedundantSuspendModifier")
    private suspend fun finishRemove(name: String) {
        try {
            Path("${activity.filesDir.path}/deleted/calendars/$name.ics").delete()
        } catch (e: Exception) {
            false
        }.let {
            if (!it) Log.e("finishRemove", "Error deleting file in internal directory.")
        }
    }

    /** Moves a file that was moved to the "Recycle Bin" back to its respective directories,
     * and adds it back to the Content Provider and the calendars list.
     * @throws Exception if an error occurs while working with the files or Content Provider. */
    private suspend fun restore(name: String) {
        val fileName = "$name.ics"
        // The file in the "recycle bin"
        val deleted = Path("${activity.filesDir.path}/deleted/calendars/$fileName")
        // The file in internal app storage that will be restored
        val targetFile = activity.internalFile(fileName)
        if (!deleted.exists())
            throw Exception("Tried to restore a calendar file that does not exist in the recycle bin")

        // A calendar with the same name could exist, because the one in recycle bin was overwritten.
        this.find { cal -> cal.name == name }?.let {
            this.remove(name)
        }

        // Copy file to internal dir
        deleted.copyTo(targetFile)
        // Copy file to external dir
        activity.copyToExternalFile(fileName, activity.syncDir.value ?: run {
            Log.w("MutableCalendarList.restore", "syncDir is NULL; can't create external file")
            return
        })
        // File in recycle bin is no longer needed
        deleted.delete()
        // Create entry in Content Provider,
        activity.calendarPermission.runOrFail {
            this.newCalendar(name, Color(DEFAULT_CALENDAR_COLOR))?.let { newCal ->
                // Add the Calendar to the list
                this@MutableCalendarsList.list.add(newCal)
            } ?: throw Exception("Error creating new calendar")
            // ,and parse the file's content
            this.writeFileDataToCalendar(name, activity.filesDir)
        }
    }

    // MutableMap and List overrides

    override val size: Int get() = this.list.size
    override fun get(index: Int): InternalUserCalendar = this.list[index]
    override fun isEmpty(): Boolean = this.list.isEmpty()
    override fun iterator(): Iterator<InternalUserCalendar>  = this.list.iterator()
    override fun listIterator(): ListIterator<InternalUserCalendar>  = this.list.listIterator()
    override fun listIterator(index: Int): ListIterator<InternalUserCalendar>  = this.list.listIterator(index)
    override fun subList(fromIndex: Int, toIndex: Int): List<InternalUserCalendar>  = this.list.subList(fromIndex, toIndex)
    override fun lastIndexOf(element: InternalUserCalendar): Int  = this.list.lastIndexOf(element)
    override fun indexOf(element: InternalUserCalendar): Int  = this.list.indexOf(element)
    override fun contains(element: InternalUserCalendar): Boolean = this.list.contains(element)
    override fun containsAll(elements: Collection<InternalUserCalendar>): Boolean  = this.list.containsAll(elements)

    override fun clear() {
        activity.calendarWorkThread.launch { this.list.forEach { cal ->  this.removeBlocking(cal.name) } }
    }
    override fun remove(key: String): InternalUserCalendar? {
        if (!this.containsKey(key))
            return null
        activity.calendarWorkThread.launch { this.removeBlocking(key) }
        this.calDeletedSnackbar(key)
        return this[key]
    }
}

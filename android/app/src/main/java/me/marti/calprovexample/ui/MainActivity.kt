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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
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
import me.marti.calprovexample.calendar.UserCalendarListItem
import me.marti.calprovexample.calendar.copyFromDevice
import me.marti.calprovexample.calendar.deleteCalendarByName
import me.marti.calprovexample.calendar.editCalendar
import me.marti.calprovexample.calendar.externalUserCalendars
import me.marti.calprovexample.calendar.internalUserCalendars
import me.marti.calprovexample.calendar.newCalendar
import me.marti.calprovexample.calendar.toggleSync
import me.marti.calprovexample.createFiles
import me.marti.calprovexample.deleteFiles
import me.marti.calprovexample.finishImportOverwrite
import me.marti.calprovexample.finishImportRename
import me.marti.calprovexample.getAppPreferences
import me.marti.calprovexample.importFiles
import me.marti.calprovexample.join
import me.marti.calprovexample.openFd
import me.marti.calprovexample.ui.theme.CalProvExampleTheme
import java.io.File as Path

const val DEFAULT_CALENDAR_COLOR = 0x68acef
const val CALENDAR_DOCUMENT_MIME_TYPE = "text/calendar"

class MainActivity : ComponentActivity() {
    val calendarPermission = CalendarPermission(this)
    private val asyncCalendarPermission = AsyncCalendarPermission(this)

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
    var userCalendars: MutableState<SnapshotStateList<InternalUserCalendar>?> = mutableStateOf(null)

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
                    this@MainActivity.createFiles("${cal.name}.ics", cal.color, cal.id)
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
            this.initUserCalendars()

        this.setContent {
            CalProvExampleTheme {
                val navController = rememberNavController()

                NavHost(navController, startDestination = NavDestination.Main.route) {
                    this.composable(NavDestination.Main.route) {
                        /** Open a secondary item in this screen, such as the FAB or a dialog */
                        var openAction: Actions? by rememberSaveable { mutableStateOf(null) }
                        val asyncScope = rememberCoroutineScope()
                        val snackBarState = remember { SnackbarHostState() }

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
                                        ExpandableFab.Action(R.drawable.rounded_calendar_add_on_24, "Device calendar")
                                            { openAction = Actions.CopyCalendar },
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
                                                                rename = { newName, _ -> this@MainActivity.finishImportRename(data.fileUri, "$newName.ics") },
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
                                    calPermsClick =  { this@MainActivity.initUserCalendars() },
                                    syncCalendarSwitch = { id, sync -> this@MainActivity.toggleSyncCalendar(id, sync) },
                                    editCalendar = { id, name, color -> openAction = Actions.EditCalendar(id, name, color) },
                                    deleteCalendar = { name -> this@MainActivity.deleteCalendar(name, asyncScope, snackBarState) }
                                )
                            }

                            this.tab(icon = Icons.Default.AccountCircle, title = "Contacts") { modifier ->
                                Text("Contacts section", modifier = modifier)
                            }
                        }

                        // Show a dialog for the current action the user selected
                        when (openAction) {
                            // TODO: don't allow user to use a name of a calendar that already exists
                            Actions.NewCalendar -> NewCalendarAction(
                                close = { openAction = null },
                                submit = { name, color -> this@MainActivity.newCalendar(name, color) }
                            )
                            is Actions.EditCalendar -> {
                                val data = openAction as Actions.EditCalendar
                                // TODO: don't allow user to use a name of a calendar that already exists
                                EditCalendarAction(
                                    title = { Text("Edit Calendar") },
                                    color = data.color,
                                    name = data.name,
                                    close = { openAction = null },
                                    submit = { newName, newColor -> this@MainActivity.editCalendar(data.id, newName, newColor) }
                                )
                            }
                            Actions.CopyCalendar -> {
                                // Get the Calendars in the device the user can copy
                                var calendars by remember { mutableStateOf<List<UserCalendarListItem>?>(null) }
                                // FIXME: small lag on UI when running LaunchedEffect and asyncScope.launch
                                LaunchedEffect(true) {
                                    this@MainActivity.asyncCalendarPermission.runWithMessage("Searching for calendars") {
                                        calendars = this.externalUserCalendars()
                                        if (calendars == null) {
                                            this@MainActivity.showToast("Could not query user calendars")
                                            openAction = null
                                        }
                                    } ?: run {
                                        // Close if couldn't get calendars, because of error or perm denial
                                        openAction = null
                                    }
                                }

                                // TODO: If another calendar with he same name exists, ask user to rename this one

                                calendars?.let { cals ->
                                    CopyCalendarAction(
                                        calendars = cals.map { cal ->
                                            ExternalUserCalendar(cal,
                                                // Find the calendar owned by this app (internal) that copied this calendar's data (if any).
                                                userCalendars.value?.find { iCal -> cal.id == iCal.importedFrom }?.name
                                            )
                                        }.groupBy { cal -> cal.accountName },
                                        close = { openAction = null },
                                        submit = { ids -> this@MainActivity.copyCalendars(ids, asyncScope) }
                                    )
                                }
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

    private fun initUserCalendars() {
        this.calendarPermission.run {
            this.internalUserCalendars()?.also { cals ->
                // Group calendars by Account Name
                userCalendars.value = cals.toMutableStateList()
            } ?: run {
                println("Couldn't get user calendars")
            }
        }
    }

    private fun newCalendar(name: String, color: Color) {
        this.calendarPermission.run {
            this.newCalendar(name, color)?.let { newCal ->
                // Add the Calendar to the list
                userCalendars.value?.add(newCal)
            } ?: return@run

            this@MainActivity.createFiles("$name.ics", color)
        }
    }

    private fun editCalendar(id: Long, newName: String, newColor: Color) {
        this.calendarPermission.run {
            if (this.editCalendar(id, newName, newColor)) {
                userCalendars.value?.let { calendars ->
                    // ?: throw Exception("Could not find Calendar with ID=$id in `userCalendars`")
                    val i = calendars.indexOfFirst { cal -> cal.id == id }
                    calendars[i] = calendars[i].copy(
                        name = newName,
                        color = newColor
                    )
                }

                // TODO: rename file and change color in the file
            }
        }
    }

    private fun toggleSyncCalendar(id: Long, sync: Boolean) {
        this.calendarPermission.run {
            if (this.toggleSync(id, sync)) {
                userCalendars.value?.let { calendars ->
                    val i = calendars.indexOfFirst { cal -> cal.id == id }
                    calendars[i] = calendars[i].copy(sync = sync)
                }
            }
        }
    }

    /** Delete the calendar and show a **snack-bar** that can undo the deletion.
     *
     * Moves the deleted calendar to the `"deleted"` directory (like a recycle bin) before fully deleting it.
     * The file is only fully deleted after the snack-bar is dismissed or app is reopened. */
    private fun deleteCalendar(name: String, asyncScope: CoroutineScope, snackBarState: SnackbarHostState) {
        val calendars = userCalendars.value ?: run {
            Log.e("UI deleteCalendar", "Trying to delete Calendar when 'userCalendars' is NULL")
            return
        }
        val syncDir = this.syncDir.value ?: run {
            Log.e("UI deleteCalendar", "No syncDir :(")
            return
        }
        val fileName = "$name.ics"
        val msg = "Calendar deleted"

        fun Path.copyTo(path: Path): Path? {
            return try {
                java.io.File("${this@MainActivity.filesDir}/calendars/$fileName")
                    .copyTo(path, true)
            } catch (e: Exception) {
                Log.e("UI deleteCalendar", "Error copying \"${this.name}\" to \"$path\" directory: $e")
                null
            }
        }

        // Copy internal file to recycle bin before deleting everything
        val deleted = Path("${this.filesDir}/calendars/$fileName")
            .copyTo(Path("${this.filesDir}/deleted/calendars/$fileName"))
            ?: return
        // Delete the Calendar from the list
        val i = calendars.indexOfFirst { cal -> cal.name == name }
        if (i == -1) {
            Log.e("UI deleteCalendar", "Trying to delete Calendar that doesn't exist")
            return
        }
        calendars.removeAt(i)
        // Delete the Calendar files
        this.deleteFiles(fileName)

        asyncScope.launch {
            // Delete the Calendar from the Content Provider
            this@MainActivity.asyncCalendarPermission.run {
                this.deleteCalendarByName(name)
            }?.run {
                Log.w("UI deleteCalendar", "Calendar \"$name\" not removed from Content Provider.")
            } ?: run {
                Log.e("UI deleteCalendar", "Calendar permission denied")
                return@launch
            }

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
                SnackbarResult.ActionPerformed -> {
                    // Copy file internal dir
                    deleted.copyTo(Path("${this@MainActivity.filesDir}/calendars/$fileName"))
                        ?: return@launch
                    // Copy file to external dir
                    DocumentsContract.copyDocument(this@MainActivity.contentResolver,
                        deleted.toUri(),
                        syncDir.join("calendars/$fileName")!!,
                    )
                    // File in recycle bin is no longer needed
                    deleted.delete()
                    // Create entry in Content Provider, and parse the file's content
                    // TODO: parse content
                    this@MainActivity.asyncCalendarPermission.run {
                        this.newCalendar(name, Color(DEFAULT_CALENDAR_COLOR))?.let { newCal ->
                            // Add the Calendar to the list
                            userCalendars.value?.add(newCal)
                        } ?: return@run
                    }
                }
                // Fully delete Calendar by deleting file in recycle bin
                SnackbarResult.Dismissed -> {
                    try {
                        deleted.delete()
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

    private fun copyCalendars(ids: List<Long>, asyncScope: CoroutineScope) {
        asyncScope.launch {
            this@MainActivity.asyncCalendarPermission.runWithMessage("Copying calendars") {
                // kotlinx.coroutines.delay(500)
                this.copyFromDevice(ids)?.let { newCals ->
                    // Add the Calendars to the list
                    userCalendars.value?.addAll(newCals)
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

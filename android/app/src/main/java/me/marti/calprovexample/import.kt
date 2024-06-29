/** Functions that import external files that the user chooses to the app's directories and Android's content providers. */
package me.marti.calprovexample

import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import me.marti.calprovexample.ui.CALENDAR_DOCUMENT_MIME_TYPE
import me.marti.calprovexample.ui.CalendarData
import me.marti.calprovexample.ui.DEFAULT_CALENDAR_COLOR
import me.marti.calprovexample.ui.MainActivity

/** A conflict occurred while importing a file and requires user intervention.
 * Contains data that will be used to show a dialog.
 * @param name The name of the file (without extension) that was being imported.
 * @param fileUri */
class ImportFileExists(val name: String, val fileUri: Uri)

/** Handle the import of files chosen by the user.
 *
 * Sends a message on the [MainActivity.importChannel]
 *
 * @return Data to be sent the import channel to show the dialog. */
fun MainActivity.importFiles(uris: List<Uri>) {
    // Channel must be closed after returning from this function to release the thread from the loop
    fun close() = importChannel.trySend(null)

    if (uris.isEmpty()) {
        println("OpenFiles: User cancelled the file picker.")
        close()
        return
    }

    val syncDir = this.syncDir.value ?: run {
        Log.e("importFiles", "'syncDir' is NULL. This should NEVER happen")
        this.showToast("UNEXPECTED: 'syncDir' is NULL in importFilesIntent")
        close()
        return
    }

    for (uri in uris) {
        if (uri.path == null)
            continue
        println("User picked file: $uri")

        val fileName = uri.fileName()!!
        val result: ImportFileResult = this.openFd(uri)?.use { file ->
            DavSyncRs.importFileInternal(file.fd, fileName, this.filesDir.path)
        } ?: continue

        // Send message to importChannel for every attempt
        when (result) {
            is ImportFileResult.Error -> this.showToast("Error importing file")
            is ImportFileResult.FileExists -> importChannel.trySend(ImportFileExists(result.calName, uri))
            is ImportFileResult.Success -> this.importExternal(fileName, syncDir)
        }
    }

    // close channel when done with all files
    close()
}

/** Deletes the file created in the internal directory in the case that importing to external syncDir fails or user cancels. */
private fun MainActivity.abortImport(fileName: String) {
    Log.d("abortImport", "Abort: Deleting internal file.")
    try {
        java.io.File("${this.filesDir.path}/${destinationDir(fileName)}/$fileName").delete()
    } catch(e: Exception) {
        Log.e("abortImport", "Error deleting internal file:\n$e")
    }
}
/** Creates the file in the external **syncDir** and writes the contents from the respective file in internal app storage.
 * Helper function to avoid repeating code */
fun MainActivity.importExternal(fileName: String, syncDir: Uri) {
    // THIS IS THE ONLY WAY TO CREATE A FILE IN EXTERNAL STORAGE, EVEN IF YOU HAVE WRITE PERMISSIONS. WHY!!!!
    // AND I CANT OPEN A FILE IN THE DIRECTORY, EVEN IF I HAVE THE FILE DESCRIPTOR
    val externalFileUri = DocumentsContract.createDocument(this.contentResolver,
        syncDir.join(destinationDir(fileName))!!,
        CALENDAR_DOCUMENT_MIME_TYPE,
        fileNameWithoutExtension(fileName)
    ) ?: run {
        abortImport(fileName)
        return
    }

    this.openFd(externalFileUri, "w")?.use { externalFile ->
        DavSyncRs.importFileExternal(externalFile.fd, fileName, this.filesDir.path)
    }?.let {
        if (it) Unit else null // Run abort code if result is false
    } ?: run {
        abortImport(fileName)
        return
    }

    this.addImportedCalendar(fileNameWithoutExtension(fileName))
}

/** Finish importing a file that caused a conflict (because a file with that name already exists),
 * but the user chose to **rename** the new import file.
 * @param fileUri The uri of the file to import.
 * @param newFileName must include the extension (e.g. `"name.ics"`). Also, newName must be unique from all other calendars. */
fun MainActivity.finishImportRename(fileUri: Uri, newFileName: String) {
    val syncDir = this.syncDir.value ?: run {
        Log.e("importFiles", "'syncDir' is NULL. This should NEVER happen")
        this.showToast("UNEXPECTED: 'syncDir' is NULL in importFilesIntent")
        return
    }

    // Try again with different name
    when (this.openFd(fileUri)?.let { file ->
        DavSyncRs.importFileInternal(file.fd, newFileName, this.filesDir.path)
    }) {
        null -> {}
        is ImportFileResult.Error -> this.showToast("Error importing file")
        is ImportFileResult.FileExists -> {
            Log.e("finishImport (Rename)", "Tried to import file under different name, but still another file exists with the new name.")
            this.showToast("Error importing file")
        }
        is ImportFileResult.Success -> this.importExternal(newFileName, syncDir)
    }
}
/** Finish importing a file that caused a conflict (because a file with that name already exists),
 * but the user chose to **overwrite** the existing file. */
fun MainActivity.finishImportOverwrite(fileUri: Uri) {
    val syncDir = this.syncDir.value ?: run {
        Log.e("finishImport (Overwrite)", "'syncDir' is NULL. This should NEVER happen")
        this.showToast("UNEXPECTED: 'syncDir' is NULL in importFilesIntent")
        return
    }
    // Get the name of the file to be imported ("name.ics") from the URI
    val fileName = fileUri.fileName()!!
    val name = fileNameWithoutExtension(fileName)

    // TODO: show snack-bar to undo overwrite import (merge with MainActivity.deleteCalendar)

    // Delete calendar from view list
    this.userCalendars.value?.remove(name)

    // Import files normally
    when (this.openFd(fileUri)?.use { file ->
        DavSyncRs.importFileInternal(file.fd, fileName, this.filesDir.path)
    }) {
        null -> {}
        is ImportFileResult.Error -> {
            this.showToast("Error importing file")
        }
        is ImportFileResult.FileExists -> {
            Log.e("finishImport (Overwrite)", "Tried to import file after deleting conflicts, but still another file exists with the same name.")
            this.showToast("Error importing file")
        }
        is ImportFileResult.Success -> this.importExternal(fileName, syncDir)
    }
}

/** Delete the files from the internal directory and the external sync directory.
 * Only tries to delete if syncDir is initialized, otherwise does nothing.
 *
 * Automatically detects whether file is in *`calendars`* or *`contacts`*.
 * @param fileName must include the extension (e.g. `"name.ics"`).
 *
 * @throws Exception if couldn't delete any of the files for whatever reason. */
fun MainActivity.deleteFiles(fileName: String) {
    val syncDir = this.syncDir.value ?: throw Exception("syncDir is NULL; can't delete external file")
    val dest = destinationDir(fileName)

    // Delete from App's internal storage
    try {
        if (!this.internalFile(fileName).delete())
            throw Exception("Unknown Reason")
    } catch (e: Exception) {
        throw Exception("Error deleting file in Internal Directory: $e")
    }
    // Delete from syncDir in shared storage
    try {
        // FIXME: pass the fileName to deleteDocument without having to encode it in the URI, because that removes spaces and symbols.
        if (!DocumentsContract.deleteDocument(this.contentResolver, syncDir.join("$dest/$fileName")!!))
            throw Exception("Unknown Reason")
    } catch (e: Exception) {
        throw Exception("Error deleting file in External Directory: $e")
    }
}

/** Adds the newly imported calendar to the content provider and triggers a recomposition with [userCalendars][MainActivity.userCalendars]. */
private fun MainActivity.addImportedCalendar(name: String, color: Color = Color(DEFAULT_CALENDAR_COLOR)) {
    this.userCalendars.value?.add(CalendarData(name, color))
    writeFileDataToCalendar(name)
    // DavSyncRs.parse_file(this.baseContext.filesDir.path, result.calName)
}

/** Create the files in internal and external storage for a new Calendar the user created.
 * Only creates the files if syncDir is initialized, otherwise does nothing.
 *
 * Automatically detects whether file is in *`calendars`* or *`contacts`*.
 * @param fileName must include the extension (e.g. `"name.ics"`).
 * @param id If the ID of a calendar is given, it will write all its data in the ContentProvider to the files that were created.
 * @throws Exception if couldn't create any files for whatever reason.
 * @throws IllegalArgumentException if the **fileName** contains [illegal characters][ILLEGAL_FILE_CHARACTERS]. */
fun MainActivity.createFiles(fileName: String, color: Color, id: Long? = null) {
    val syncDir = this.syncDir.value ?: throw Exception("syncDir is NULL; can't delete external file")
    val dest = destinationDir(fileName)
    val name = fileNameWithoutExtension(fileName)

    // Check for illegal characters
    for (c in fileName)
        if (ILLEGAL_FILE_CHARACTERS.contains(c))

    // Create file in App's internal storage
    try {
        val file = this.internalFile(fileName).toPath()
        java.nio.file.Files.createDirectories(file.parent)
        java.nio.file.Files.createFile(file)
    } catch (e: Exception) {
        throw Exception("Error creating file in Internal Directory: $e")
    }
    // Create file in syncDir in shared storage
    try {
        DocumentsContract.createDocument(this.contentResolver,
            syncDir.join(dest)!!,
            CALENDAR_DOCUMENT_MIME_TYPE,
            name
        ) ?: throw Exception("Unknown Reason")
    } catch (e: Exception) {
        throw Exception("Error creating file in External Directory: $e")
    }

    writeColorToCalendarFile(name, color)

    id?.let { _ ->
        writeCalendarDataToFile(name)
    }
}

fun writeColorToCalendarFile(name: String, color: Color) {
    // TODO()
}
fun writeCalendarDataToFile(name: String) {
    // TODO()
}
fun writeFileDataToCalendar(name: String) {
    // TODO()
}
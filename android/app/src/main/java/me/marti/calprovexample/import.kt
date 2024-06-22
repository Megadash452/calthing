/** Functions that import external files that the user chooses to the app's directories and Android's content providers. */
package me.marti.calprovexample

import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import me.marti.calprovexample.calendar.deleteCalendarByName
import me.marti.calprovexample.calendar.newCalendar
import me.marti.calprovexample.ui.CALENDAR_DOCUMENT_MIME_TYPE
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
    this.importChannel?.let { importChannel ->
        // Channel must be closed after returning from this function to release the thread from the loop
        val close = {
            importChannel.close()
            this.importChannel = null
        }

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
                // TODO: maybe use null as a stream terminator instead of resetting the channel??
                is ImportFileResult.FileExists -> importChannel.trySend(ImportFileExists(result.calName, uri))
                is ImportFileResult.Success -> this.importExternal(fileName, syncDir)
            }
        }

        // close channel when done with all files
        close()
    }
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
/** Creates the file in the external **syncDir** and writes the contents.
 * Helper function to avoid repeating code */
private fun MainActivity.importExternal(fileName: String, syncDir: Uri) {
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
    this.userCalendars.value?.let { cals ->
        val i = cals.indexOfFirst { cal -> cal.name == name }
        if (i == -1) {
            Log.e("finishImport (Overwrite)", "Trying to delete Calendar that doesn't exist")
            return
        }
        cals.removeAt(i)
    }
    // Delete Calendar from content provider
    this.calendarPermission.run {
        this.deleteCalendarByName(name)
    }
    // Delete Calendar from filesystem
    this.deleteFiles(fileName)

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
 * @param fileName must include the extension (e.g. `"name.ics"`). */
fun MainActivity.deleteFiles(fileName: String) {
    this.syncDir.value?.let { syncDir ->
        val dest = destinationDir(fileName)
        // Delete from App's internal storage
        java.io.File("${this.filesDir.path}/$dest/$fileName").delete()
        // Delete from syncDir in shared storage
        DocumentsContract.deleteDocument(this.contentResolver, syncDir.join("$dest/$fileName")!!)
    }
}

/** Adds the newly imported calendar to the content provider and triggers a recomposition with [userCalendars][MainActivity.userCalendars]. */
private fun MainActivity.addImportedCalendar(name: String, color: Color = Color(DEFAULT_CALENDAR_COLOR)) {
    this.calendarPermission.run {
        this.newCalendar(name, color)?.let { newCal ->
            // Add the Calendar to the list
            userCalendars.value?.add(newCal) ?: run {
                userCalendars.value = mutableStateListOf(newCal)
            }
        } ?: return@run
    }
    // TODO: parse file to add data to content provider
    // DavSyncRs.parse_file(this.baseContext.filesDir.path, result.calName)
}

/** Create the files in internal and external storage for a new Calendar the user created.
 * Only creates the files if syncDir is initialized, otherwise does nothing.
 *
 * Automatically detects whether file is in *`calendars`* or *`contacts`*.
 * @param fileName must include the extension (e.g. `"name.ics"`).
 * @param id If the ID of a calendar is given, it will write all its data in the ContentProvider to the files that were created. */
fun MainActivity.createFiles(fileName: String, color: Color, id: Long? = null) {
    this.syncDir.value?.let { syncDir ->
        val dest = destinationDir(fileName)
        // Create file in App's internal storage
        java.io.File("${this.filesDir.path}/$dest/$fileName").createNewFile()
        // Create file in syncDir in shared storage
        DocumentsContract.createDocument(this.contentResolver,
            syncDir.join(dest)!!,
            CALENDAR_DOCUMENT_MIME_TYPE,
            fileNameWithoutExtension(fileName)
        )

        // TODO: write color data to files

        id?.let { id ->
            // TODO: write calendar data to files
        }
    }
}

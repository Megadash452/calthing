/** Functions that import external files that the user chooses to the app's directories and Android's content providers. */
package me.marti.calprovexample

import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import me.marti.calprovexample.calendar.newCalendar
import me.marti.calprovexample.ui.Actions
import me.marti.calprovexample.ui.CALENDAR_DOCUMENT_MIME_TYPE
import me.marti.calprovexample.ui.DEFAULT_CALENDAR_COLOR
import me.marti.calprovexample.ui.MainActivity

/** Handle the import of files chosen by the user.
 * @return Data to be sent the import channel to show the dialog. */
fun MainActivity.importFiles(uris: List<Uri>): Actions.ImportFileExists? {
    if (uris.isEmpty()) {
        println("OpenFiles: User cancelled the file picker.")
        return null
    }

    val syncDir = this.syncDir.value ?: run {
        Log.e("importFiles", "'syncDir' is NULL. This should NEVER happen")
        this.showToast("UNEXPECTED: 'syncDir' is NULL in importFilesIntent")
        return null
    }

    for (uri in uris) {
        if (uri.path == null)
            continue
        println("User picked file: $uri")

        val fileName = uri.fileName()!!
        val result: ImportFileResult = this.openFd(uri)?.use { file ->
            DavSyncRs.importFileInternal(file.fd, fileName, this.filesDir.path)
        } ?: continue

        // TODO: send message to importChannel for every attempt, instead of returning only the first
        return when (result) {
            is ImportFileResult.Error -> {
                this.showToast("Error importing file")
                null
            }
            is ImportFileResult.FileExists -> Actions.ImportFileExists(result.calName, uri)
            is ImportFileResult.Success -> {
                this.importExternal(fileName, syncDir)
                null
            }
        }
    }

    return null
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
    // Import to external sync dir

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

    when (this.openFd(fileUri)?.use { file ->
        DavSyncRs.importFileInternal(file.fd, newFileName, this.filesDir.path)
    }) {
        null -> {}
        is ImportFileResult.Error -> {
            this.showToast("Error importing file")
        }
        is ImportFileResult.FileExists -> {
            Log.e("finishImport (Rename)", "Tried to import file under different name, but still another file exists with the new name.")
            this.showToast("Error importing file")
        }
        is ImportFileResult.Success -> this.importExternal(newFileName, syncDir)
    }
}
/** Finish importing a file that caused a conflict (because a file with that name already exists),
 * but the user chose to **overwrite** the existing file.
 * @param fileName must include the extension (e.g. `"name.ics"`). */
fun MainActivity.finishImportOverwrite(fileUri: Uri, fileName: String) {
    TODO()
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
 * @param fileName must include the extension (e.g. `"name.ics"`).*/
fun MainActivity.createFiles(fileName: String, color: Color) {
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

        // TODO: write color data to file
    }
}

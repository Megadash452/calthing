/** Functions that import external files that the user chooses to the app's directories and Android's content providers. */
package me.marti.calprovexample

import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import me.marti.calprovexample.ui.CALENDAR_DOCUMENT_MIME_TYPE
import me.marti.calprovexample.ui.DEFAULT_CALENDAR_COLOR
import me.marti.calprovexample.ui.MainActivity


/** Deletes the file created in the internal directory in the case that importing to external syncDir fails or user cancels. */
fun MainActivity.abortImport(fileName: String) {
    Log.d("abortImport", "Abort: Deleting internal file.")
    try {
        java.io.File("${this.filesDir.path}/${destinationDir(fileName)}/$fileName").delete()
    } catch(e: Exception) {
        Log.e("abortImport", "Error deleting internal file:\n$e")
    }
}

/** Handle the import of files chosen by the user.
 * @return See [MainActivity.importChannel] for an explanation of the return type. */
fun MainActivity.importFiles(uris: List<Uri>): String? {
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

        return when (result) {
            is ImportFileResult.Error -> {
                abortImport(fileName)
                this.showToast("Error importing file")
                null
            }
            is ImportFileResult.FileExists -> result.calName
            is ImportFileResult.Success -> {
                // Import to external sync dir

                // THIS IS THE ONLY WAY TO CREATE A FILE IN EXTERNAL STORAGE, EVEN IF YOU HAVE WRITE PERMISSIONS. WHY!!!!
                // AND I CANT OPEN A FILE IN THE DIRECTORY, EVEN IF I HAVE THE FILE DESCRIPTOR
                val externalFileUri = DocumentFile.fromTreeUri(this.baseContext, syncDir)
                    ?.findFile(destinationDir(fileName))
                    ?.createFile(CALENDAR_DOCUMENT_MIME_TYPE, fileName)
                    ?.uri
                    ?: run {
                        abortImport(fileName)
                        return null
                    }
                this.openFd(externalFileUri, "w")?.use { externalFile ->
                    DavSyncRs.importFileExternal(externalFile.fd, fileName, this.filesDir.path)
                }?.let {
                    if (it) Unit else null // Run abort code if result is false
                } ?: run {
                    abortImport(fileName)
                    return null
                }

                this.addImportedCalendar(result.calName)
                null
            }
        }
    }

    return null
}

/** Finish importing a file that caused a conflict (because a file with that name already exists),
 * but the user chose to **rename** the new import file.
 * @param uri The uri of the file to import.
 * @param newFileName must include the extension (e.g. `"name.ics"`). Also, newName must be unique from all other calendars.
 * @param color The dialog prompting the user for the new name also lets them choose a color. */
fun MainActivity.finishImportRename(uri: Uri, newFileName: String, color: Color) {
    val dest = destinationDir(newFileName)
    val syncDir = this.syncDir.value ?: run {
        Log.e("importFiles", "'syncDir' is NULL. This should NEVER happen")
        this.showToast("UNEXPECTED: 'syncDir' is NULL in importFilesIntent")
        return
    }

    // TODO: call DavSYncRs.importFile

    // Create file in internal directory
    java.io.File("${this.filesDir.path}/$dest/$newFileName").createNewFile()
    // Create file in external sync directory
    DocumentFile.fromTreeUri(this.baseContext, syncDir)
        ?.findFile(destinationDir(newFileName))
        ?.createFile(CALENDAR_DOCUMENT_MIME_TYPE, newFileName)
}
/** Finish importing a file that caused a conflict (because a file with that name already exists),
 * but the user chose to **overwrite** the existing file.
 * @param fileName must include the extension (e.g. `"name.ics"`). */
fun MainActivity.finishImportOverwrite(fileName: String) {
    TODO()
}

/** Delete the files from the internal directory and the external sync directory.
 * Automatically detects whether file is in *`calendars`* or *`contacts`*.
 * @param fileName must include the extension (e.g. `"name.ics"`). */
fun MainActivity.deleteImportedFiles(fileName: String) {
    val dest = destinationDir(fileName)
    // Delete from App's internal storage
    java.io.File("${this.filesDir.path}/$dest/$fileName").delete()
    // Delete from syncDir in shared storage
    this.syncDir.value?.let { syncDir ->
        DocumentsContract.deleteDocument(this.contentResolver, syncDir.join("$dest/$fileName")!!)
    }
}

/** Adds the newly imported calendar to the content provider and triggers a recomposition with [userCalendars][MainActivity.userCalendars]. */
private fun MainActivity.addImportedCalendar(name: String, color: Color = Color(DEFAULT_CALENDAR_COLOR)) {
    this.newCalendar(name, color)
    // TODO: parse file to add data to content provider
    // DavSyncRs.parse_file(this.baseContext.filesDir.path, result.calName)
}
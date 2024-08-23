/** Functions that import external files that the user chooses to the app's directories and Android's content providers. */
package me.marti.calprovexample

import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import me.marti.calprovexample.calendar.writeFileDataToCalendar
import me.marti.calprovexample.jni.DavSyncRs
import me.marti.calprovexample.ui.CALENDAR_DOCUMENT_MIME_TYPE
import me.marti.calprovexample.ui.MainActivity
import java.io.File as Path


/** Deletes the file created in the internal directory in the case that importing to external syncDir fails or user cancels. */
internal fun MainActivity.abortImport(fileName: String) {
    Log.d("abortImport", "Abort: Deleting internal file.")
    try {
        Path("${this.filesDir.path}/${destinationDir(fileName)}/$fileName").delete()
    } catch (e: Exception) {
        false
    }.let {
        if (!it) Log.e("abortImport", "Error deleting file in internal directory.")
    }
}

/** Copy the contents of a real file in the file system to a file in shared storage (uses content provider).
 * Creates the file with the same *file name*.
 *
 *  @param fileName The name of the file (e.g. `"calendar.ics"`). The path of the file will be derived from its *file extension*.
 *  @param syncDir Directory containing external files. See [MainActivity.syncDir]. */
fun MainActivity.copyToExternalFile(fileName: String, syncDir: Uri) {
    // THIS IS THE ONLY WAY TO CREATE A FILE IN EXTERNAL STORAGE, EVEN IF YOU HAVE WRITE PERMISSIONS. WHY!!!!
    // AND I CANT OPEN A FILE IN THE DIRECTORY, EVEN IF I HAVE THE FILE DESCRIPTOR
    val externalFileUri = DocumentsContract.createDocument(this.contentResolver,
        syncDir.join(destinationDir(fileName)),
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
}

/** Delete the files from the internal directory and the external sync directory.
 * Only tries to delete if syncDir is initialized, otherwise does nothing.
 *
 * Automatically detects whether file is in *`calendars`* or *`contacts`*.
 * @param fileName must include the extension (e.g. `"name.ics"`).
 *
 * @throws Exception if couldn't delete any of the files for whatever reason. */
fun MainActivity.deleteFiles(fileName: String) {
    val syncDir = this.syncDir.value ?: run {
        Log.w("deleteFiles", "syncDir is NULL; Can't delete external file")
        return
    }
    val dest = destinationDir(fileName)

    // Delete from App's internal storage
    try {
        if (!this.internalFile(fileName).delete())
            throw Exception("Unknown Reason")
    } catch (e: Exception) {
        throw Exception("Error deleting file in Internal Directory: $e")
    }
    // Delete from syncDir in shared storage.
    // Failure to delete external file should not cause exception
    try {
        DocumentFile.fromTreeUri(this.baseContext, syncDir)!!.findFile(dest)!!.findFile(fileName)?.delete()
            ?: Log.w("deleteFiles", "Tried deleting external file \"$fileName\", but it did not exist.")
    } catch (e: Exception) {
        Log.e("deleteFiles", "Error deleting external file: $e")
    }
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
    val syncDir = this.syncDir.value ?: run {
        Log.w("createFiles", "syncDir is NULL; Can't add external file; Will add it later")
        return
    }
    val dest = destinationDir(fileName)
    val name = fileNameWithoutExtension(fileName)

    // Check for illegal characters
    for (c in fileName)
        if (ILLEGAL_FILE_CHARACTERS.contains(c))
            throw IllegalArgumentException("File name can't contain the following characters: [${ILLEGAL_FILE_CHARACTERS.joinToString { "'$it'" }}]")

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
        val dir = DocumentFile.fromTreeUri(this.baseContext, syncDir)!!.findFile(dest)!!
        if (dir.findFile(fileName) != null)
            Log.w("createFiles", "Tried to create file \"$fileName\" in external directory, but file already exists.")
        else
            dir.createFile(CALENDAR_DOCUMENT_MIME_TYPE, name)
    } catch (e: Exception) {
        throw Exception("Error creating file in External Directory: $e")
    }

    writeColorToCalendarFile(name, color)

    id?.let { _ ->
        writeCalendarDataToFile(name)
    }
}

fun MainActivity.mergeDirs(syncDir: Uri) {
    val internalFiles = Path("${this.filesDir.path}/calendars/").listFiles()!!
    val externalFiles = DocumentFile.fromTreeUri(this.baseContext, syncDir)!!.findFile("calendars")!!.listFiles()
    // Find the files that are in one directory but not in the other, and copy them to the other.
    val copyToInternal = externalFiles.filter { file -> !internalFiles.map { it.name }.contains(file.name!!) }
    val copyToExternal = internalFiles.filter { file -> !externalFiles.map { it.name!! }.contains(file.name) }
    val filesToMerge = internalFiles.map { it.name }.filter { file -> externalFiles.map { it.name!! }.contains(file) }
    // Copy the files
    for (file in copyToInternal) {
        this.openFd(file.uri)?.use { fileFd ->
            try { DavSyncRs.importFileInternal(fileFd.fd, file.name!!, this.filesDir.path) }
            catch (e: Exception) {
                Log.e("mergeDirs", "Error copying external file to internal dir: $e")
            }
        }
    }
    for (file in copyToExternal)
        this.copyToExternalFile(file.name, syncDir)
    // Check if common files are different, and ask user whether to accept incoming or keep internal
    for (fileName in filesToMerge) {
        val internalFile = Path("${this.filesDir.path}/calendars/$fileName").bufferedReader().use { it.readText() }
        val externalFile = this.contentResolver.openInputStream(syncDir.join("calendars/$fileName"))!!.use { it -> it.bufferedReader().use { it.readText() } }
        if (internalFile != externalFile)
            TODO("Show dialog asking user whether to keep current or accept incoming (launch in other thread)")
    }
    // Add calendars from external directory to Content Provider
    // Calendars in internal dir are should already be in the Content Provider, so no need to do this for copyToExternal too.
    if (this.calendarPermission.hasPermission())
        this.calendarPermission.launchOrFail {
            for (file in copyToInternal)
                writeFileDataToCalendar(fileNameWithoutExtension(file.name!!), this@mergeDirs.filesDir)
            this@mergeDirs.userCalendars.value?.syncWithProvider()
        }
}

fun writeColorToCalendarFile(name: String, color: Color) {
    // TODO()
}
fun writeCalendarDataToFile(name: String) {
    // TODO()
}
/** Functions that import external files that the user chooses to the app's directories and Android's content providers. */
package me.marti.calprovexample

import android.util.Log
import androidx.documentfile.provider.DocumentFile
import me.marti.calprovexample.ui.CALENDAR_DOCUMENT_MIME_TYPE
import me.marti.calprovexample.ui.MainActivity


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


fun writeColorToCalendarFile(name: String, color: Color) {
    // TODO()
}
fun writeCalendarDataToFile(name: String) {
    // TODO()
}
package me.marti.calprovexample.jni

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import me.marti.calprovexample.ElementExistsException
import me.marti.calprovexample.calendar.DisplayCalendarProjection
import me.marti.calprovexample.calendar.InternalUserCalendar
import me.marti.calprovexample.calendar.getCursor
import me.marti.calprovexample.fileNameWithoutExtension
import kotlin.jvm.Throws

/** Rust functions that can be called from Java.
 * All the extern functions declared in this class are defined in `project root/rust/src/lib.rs` */
@Suppress("FunctionName")
object DavSyncRs {
    init { System.loadLibrary("davsync") }

    /** Initialize the **internal** and **external** directories by creating all necessary sub-directories (e.g. calendars and contacts directories).
     * @param externalDirFd The *file descriptor* for the directory in shared storage the user picked to sync files.
     * @param appDir The internal directory where all of this app's files are stored. */
    external fun initialize_dirs(externalDirFd: Int, appDir: String)

    external fun merge_dirs(externalDirUri: Uri, appDir: String, context: Context)

    private external fun import_file_internal(fileFd: Int, fileName: String, appDir: String): Byte
    /** Copy file's content into the internal *app's directory*.
     *
     * After a *successful* call to this function,
     * the caller should create a file in the external directory and call [`import_file_external()`].
     *
     * ### Parameters
     * @param fileFd The *unowned* file descriptor of the file to be imported, and **file_name** is its name (including extension).
     * @param appDir The internal directory where all of this app's files are stored.
     *   Pass in the base directory regardless of whether the file should go in *calendars* or *contacts*.
     *
     * @return Returns `false` if the file couldn't be imported because a file with that name already exists in the local directory. */
    fun importFileInternal(fileFd: Int, fileName: String, appDir: String): ImportFileResult {
        val calName = fileNameWithoutExtension(fileName)

        when (try {
            import_file_internal(fileFd, fileName, appDir).toInt()
        } catch (e: Exception) {
            Log.e("importFileInternal", "Error importing file. Thrown exception:\n$e")
            return ImportFileResult.Error
        }) {
            1 -> {
                Log.d("importFileInternal", "file '${fileName}' imported successfully")
                return ImportFileResult.Success(calName)
            }
            2 -> {
                Log.d("importFileInternal", "'${fileName}' is already imported. Overwrite?")
                return ImportFileResult.FileExists(calName)
            }
            else -> throw IllegalStateException("*prowler sfx*")
        }
    }

    private external fun import_file_external(externalFileFd: Int, fileName: String, appDir: String)
    /** Write the contents of the file already imported in the *internal directory* to the new file created in *sync directory* (external). */
    fun importFileExternal(externalFileFd: Int, fileName: String, appDir: String): Boolean {
        return try {
            import_file_external(externalFileFd, fileName, appDir)
            true
        } catch (e: Exception) {
            Log.e(null, "Error importing file to external storage. Thrown exception:\n$e")
            false
        }
    }

    /** Create a new Calendar entry in the Content Provider by reading the contents of a calendar file.
     * This function will find the file in the provided [app directory][appDir].
     *
     * @throws ElementExistsException if a calendar with that **name** already exists. */
    @Throws(ElementExistsException::class)
    external fun new_calendar_from_file(context: Context, name: String, appDir: String): InternalUserCalendar
}

@Suppress("unused")
object DavSyncRsHelpersKt {
    fun checkUniqueName(contentResolver: ContentResolver, name: String): Boolean? {
        // Check that a calendar with this name doesn't already exist
        return contentResolver.acquireContentProviderClient(CalendarContract.CONTENT_URI)?.use { client ->
            client.getCursor<DisplayCalendarProjection>(CalendarContract.Calendars.CONTENT_URI,
                "${DisplayCalendarProjection.DISPLAY_NAME.column} = ?",
                arrayOf(name)
            )?.use { cursor ->
                // If there already exists a calendar with this name,
                cursor.moveToFirst()
            }
        }
    }
}

/** Result from calling Native function [DavSyncRs.importFileInternal].
 *
 * [code] is the return code from the Native function.
 * `calName` is the name of the imported Calendar as it should appear in the Content Provider.
 * If [code] is:
 * * **`0`**, there was an error and an Exception was was thrown. TODO: might just put the error in the String without throwing.
 * * **`1`**, the file was imported successfully.
 * * **`2`**, an imported calendar with that name already exists, ask user to *overwrite* or *pick another name*. */
@Suppress("ConvertObjectToDataObject")
sealed class ImportFileResult {
    object Error: ImportFileResult()
    class Success(val calName: String): ImportFileResult()
    class FileExists(val calName: String): ImportFileResult()
}

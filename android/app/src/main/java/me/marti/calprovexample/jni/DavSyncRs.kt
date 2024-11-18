package me.marti.calprovexample.jni

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import me.marti.calprovexample.Color
import me.marti.calprovexample.calendar.DisplayCalendarProjection
import me.marti.calprovexample.calendar.getCursor
import me.marti.calprovexample.ui.CalendarPermissionScope
import me.marti.calprovexample.ui.DEFAULT_CALENDAR_COLOR
import me.marti.calprovexample.ui.MainActivity

/** Rust functions that can be called from Java.
 * All the extern functions declared in this class are defined in `project root/rust/src/lib.rs` */
@Suppress("FunctionName")
object DavSyncRs {
    init { System.loadLibrary("davsync") }

    /** Initialize the **internal** and **external** directories by creating all necessary sub-directories (e.g. calendars and contacts directories).
     * @param externalDirUri The *Uri* for the directory in shared storage the user picked to sync files.  */
    external fun initialize_dirs(context: Context, externalDirUri: Uri)

    external fun merge_dirs(activity: MainActivity, externalDirUri: Uri)

    /** Copy file's content into the internal *app's directory*.
     *
     * After a *successful* call to this function,
     * the caller should create a file in the external directory and call [`import_file_external()`].
     *
     * @param fileUri is the *Document Uri* of the file to be imported.
     * @param fileName If not `NULL`, the file will be imported with this name instead of the *fileName* of [fileUri].
     *
     * @return Returns [ImportFileResult.FileExists] if the file couldn't be imported because a file with that name already exists in the local directory. */
    external fun import_file_internal(context: Context, fileUri: Uri, fileName: String? = null): ImportFileResult

    /** Copy a file named **file_name** from the *internal directory* to the **external directory** in Shared Storage. */
    external fun import_file_external(context: Context, fileName: String, externalDirUri: Uri)

    /** Create the files in internal and external storage for a new Calendar the user created.
     *
     * If [externalDirUri] is **`NULL`**, only the file in app storage will be created.
     * @param fileName is the name of the file that will be created in each directory (e.g. `"name.ics"`). */
    external fun create_calendar_files(context: Context, fileName: String, color: Color, externalDirUri: Uri?)

    // /** Create a new Calendar entry in the Content Provider by reading the contents of a calendar file.
    //  * This function will find the file in the [internal directory][Context.getFilesDir].
    //  *
    //  * @throws ElementExistsException if a calendar with that **name** already exists. */
    // @Throws(ElementExistsException::class)
    // external fun new_calendar_from_file(context: Context, name: String): InternalUserCalendar

    external fun write_calendar_data_to_file(name: String)
    external fun write_file_data_to_calendar(perm: CalendarPermissionScope, name: String, color: Color = Color(DEFAULT_CALENDAR_COLOR))
    external fun write_color_to_calendar_file(name: String, color: Color)
}

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

/** Result from calling Native function [DavSyncRs.import_file_internal].
 *
 * [code] is the return code from the Native function.
 * `calName` is the name of the imported Calendar as it should appear in the Content Provider.
 * If [code] is:
 * * **`0`**, there was an error and an Exception was was thrown.
 * * **`1`**, the file was imported successfully.
 * * **`2`**, an imported calendar with that name already exists, ask user to *overwrite* or *pick another name*. */
@Suppress("ConvertObjectToDataObject")
sealed class ImportFileResult {
    object Error: ImportFileResult()
    class Success(val calName: String): ImportFileResult()
    class FileExists(val calName: String): ImportFileResult()
}

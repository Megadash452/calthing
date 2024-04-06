package me.marti.calprovexample.calendar

import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.core.database.getStringOrNull

/** Create a **cursor** to query the data of a *Content Provider*.
 *
 * Only use this function if it will be the only *operation* done on the *Content Provider*.
 * If there will be more operations (e.g. create another cursor, insert, etc.),
 * create a **client** with [`context.contentResolver.acquireContentProviderClient()`][android.content.ContentResolver.acquireContentProviderClient]
 * and call [`client.getCursor()`][ContentProviderClient.getCursor], and also do all the other operations with this **client**.
 *
 * The cursor must be [closed][ContentProviderClient.close] when it is no longer needed.
 *
 * ### Params
 * See [`ContentResolver.query()`][android.content.ContentResolver.query] for description of parameters.
 *
 * @param P The **[Projection][ProjectionEntry]** enum.
 * Replaces the **projection** parameter of [android.content.ContentResolver.query]. */
internal inline fun <reified P> Context.getCursor(uri: Uri, selection: String = "", selectionArgs: Array<String> = arrayOf(), sort: String = ""): Cursor?
where P: Enum<P>, P: ProjectionEntry
    = openCursor { this.contentResolver.query(uri, projectionArray<P>(), selection, selectionArgs, sort) }

/** Create a **cursor** to query the data of a *Content Provider*.
 *
 *  When this is the only *operation* done on the *Content Provider*, there is no need to create a **client**,
 *  just use [`context.getCursor()`][Context.getCursor].
 *
 *  The cursor must be [closed][ContentProviderClient.close] when it is no longer needed.
 *
 * ### Params
 * See [`ContentResolver.query()`][android.content.ContentResolver.query] for description of parameters.
 *
 * @param P The **[Projection][ProjectionEntry]** enum.
 * Replaces the **projection** parameter of [android.content.ContentResolver.query]. */
internal inline fun <reified P> ContentProviderClient.getCursor(uri: Uri, selection: String = "", selectionArgs: Array<String> = arrayOf(), sort: String = ""): Cursor?
where P: Enum<P>, P: ProjectionEntry
    = openCursor { this.query(uri, projectionArray<P>(), selection, selectionArgs, sort) }

/** Consolidate the 2 getCursor functions. */
private fun openCursor(create: () -> Cursor?): Cursor? {
    return try {
        // No need to use the CursorLoader since all these functions are run in a separate thread.
        val cur = create()
        if (cur == null)
            Log.e("getCursor", "Returned cursor was null")
        cur
    } catch(e: Exception) {
        Log.e("getCursor", "Exception occurred while loading calendar query cursor:\n$e")
        null
    }
}

/** Update data of a specific Calendar.
 *
 * @param id The *ID* of the Calendar.
 * @param accountName If not **`NULL`**, will update data **as sync adapter**.
 * @param values The updated data.
 *
 * @return Whether the update was successful. */
internal fun Context.updateCalendar(id: Long, accountName: String? = null, values: ContentValues): Boolean {
    val uri = CalendarContract.Calendars.CONTENT_URI.withId(id)
    val success = this.contentResolver.update(
        if (accountName != null)
            uri.asSyncAdapter(accountName)
        else uri,
        values, null, null
    ) != 0

    if (!success)
        Log.e("updateCalendar", "Failed to update Calendar with ID=$id")

    return success
}

/** Get a client for general operations on the Calendar *Content Provider*
 *
 * The client must be [closed][ContentProviderClient.close] when it is no longer needed. */
internal fun Context.getClient(): ContentProviderClient
    = this.contentResolver.acquireContentProviderClient(CalendarContract.CONTENT_URI)
        ?: throw Exception("Device does not have a System Calendars Content Provider.")


internal fun Uri.asSyncAdapter(accountName: String): Uri {
    return this.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, accountName)
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
        .build()
}
internal fun Uri.withId(id: Long): Uri = ContentUris.withAppendedId(this, id)

// This is used for showing debug data about calendars in the app
class AllData(context: Context) {
    val calendars: Data = Data(context, CalendarContract.Calendars.CONTENT_URI)
    val events: Data = Data(context, CalendarContract.Events.CONTENT_URI)
    val reminders: Data = Data(context, CalendarContract.Reminders.CONTENT_URI)
    val attendees: Data = Data(context, CalendarContract.Attendees.CONTENT_URI)

    class Data(context: Context, uri: Uri) {
        private var cursor = initializeCursor(context, uri)
        val data: SnapshotStateList<Map<String, String>> = mutableStateListOf()

        fun queryNext() {
            this.data.add(query(cursor))
        }

        companion object {
            private fun initializeCursor(context: Context, uri: Uri): Cursor {
                return context.getCursor<EmptyProjection>(uri) ?: throw Exception("Cant get query cursor")
            }
            private fun query(cursor: Cursor): Map<String, String> {
                cursor.moveToNext()
                val map = mutableMapOf<String, String>()
                // Get the field values
                for (i in 0 ..< cursor.columnCount)
                    map[cursor.getColumnName(i)] = cursor.getStringOrNull(i) ?: ""
                return map
            }
        }
    }
}

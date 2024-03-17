package me.marti.calprovexample

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.graphics.toArgb
import androidx.core.database.getStringOrNull
import androidx.loader.content.CursorLoader
import me.marti.calprovexample.ui.CalendarPermission

const val ACCOUNT_TYPE = "marti.CalProv"
const val ACCOUNT_NAME = "myuser"

/** Outputs a list of all calendars that are synced on the user has on the device with the calendar provider.
 * @param internal If `true`, the returned list will only contain Calendars created by the app.
 *                 Otherwise it will exclude these calendars*/
fun CalendarPermission.Dsl.userCalendars(internal: Boolean = true): List<UserCalendarListItem>? {
    // Whether teh cursor will include (=) or exclude (!=) calendars with ACCOUNT_TYPE
    val op = if (internal) "=" else "!="
    val cur = this.context.getCursor(
        CalendarContract.Calendars.CONTENT_URI, UserCalendarListItem.Projection,
        "((${CalendarContract.Calendars.ACCOUNT_TYPE} $op ?))", arrayOf(ACCOUNT_TYPE)
    ) ?: return null

    val result = List(cur.count) {
        cur.moveToNext()
        UserCalendarListItem(
            id = cur.getLong(UserCalendarListItem.Projection.ID.ordinal),
            name = cur.getString(UserCalendarListItem.Projection.DISPLAY_NAME.ordinal),
            accountName = cur.getString(UserCalendarListItem.Projection.ACCOUNT_NAME.ordinal),
            // The stored color is a 32bit ARGB, but the alpha is ignored.
            color = Color(cur.getInt(UserCalendarListItem.Projection.COLOR.ordinal)),
        )
    }

    cur.close()
    return result
}

data class UserCalendarListItem(
    val id: Long,
    val name: String,
    val accountName: String,
    val color: Color,
) {
    enum class Projection(val s: String) {
        ID(CalendarContract.Calendars._ID),
        DISPLAY_NAME(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME),
        ACCOUNT_NAME(CalendarContract.Calendars.ACCOUNT_NAME),
        COLOR(CalendarContract.Calendars.CALENDAR_COLOR);

        companion object : QueryProjection {
            override fun projectionArray(): Array<String> {
                return Projection.entries.toList().map { it.s }.toTypedArray()
            }
        }
    }
}

fun CalendarPermission.Dsl.newCalendar(name: String, color: Color) {
    val newCalUri = this.context.contentResolver.insert(asSyncAdapter(CalendarContract.Calendars.CONTENT_URI), ContentValues().apply {
        // Required
        this.put(CalendarContract.Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
        this.put(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
        this.put(CalendarContract.Calendars.OWNER_ACCOUNT, ACCOUNT_NAME)
        this.put(CalendarContract.Calendars.NAME, name) // Don't really know what this is for
        this.put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, name)
        this.put(CalendarContract.Calendars.CALENDAR_COLOR, color.toColor().toArgb())
        this.put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
        // Not required, but recommended
        this.put(CalendarContract.Calendars.SYNC_EVENTS, 1)
        this.put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, "America/New_York") // TODO: get value from rust library
        this.put(CalendarContract.Calendars.ALLOWED_REMINDERS,
            "${CalendarContract.Reminders.METHOD_DEFAULT}," +
            "${CalendarContract.Reminders.METHOD_ALERT}," +
            "${CalendarContract.Reminders.METHOD_ALARM}"
        )
        this.put(CalendarContract.Calendars.ALLOWED_AVAILABILITY,
            "${CalendarContract.Events.AVAILABILITY_BUSY}," +
            "${CalendarContract.Events.AVAILABILITY_FREE}," +
            "${CalendarContract.Events.AVAILABILITY_TENTATIVE}"
        )
        this.put(CalendarContract.Calendars.ALLOWED_ATTENDEE_TYPES, CalendarContract.Attendees.TYPE_NONE)
    })
    Toast.makeText(context, "$newCalUri", Toast.LENGTH_SHORT).show()
}

fun CalendarPermission.Dsl.deleteCalendar(id: Long) {
    // Events are automatically deleted with the calendar
    // TODO: show confirmation to delete
    // TODO: show snack-bar with undo button
    // TODO: delete only if sync adapter (UI will not show button if cant delete)
    val calName = this.context.getCursor(
        ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, id),
        UserCalendarListItem.Projection,
    )?.let { cursor ->
        cursor.moveToFirst()
        val name = cursor.getString(UserCalendarListItem.Projection.DISPLAY_NAME.ordinal)
        cursor.close()
        name
    } ?: "UNKNOWN"

    this.context.contentResolver.delete(
        asSyncAdapter(ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, id)),
        null, null
    )
    Toast.makeText(context, "Deleted Calendar \"$calName\"", Toast.LENGTH_SHORT).show()
}

/** Copy a set of Calendars created by other apps in the device so that they can be synced with this app. */
fun CalendarPermission.Dsl.copyFromDevice(ids: List<Long>) {
    val client = this.context.contentResolver.acquireContentProviderClient(CalendarContract.CONTENT_URI) ?: return
    val cursor = this.context.getCursor(
        CalendarContract.Calendars.CONTENT_URI, CopyCalendarsProjection,
        // Select the calendars that are in the id list
        /* FIXME: Using selectionArgs to pass the IDs didn't work.
        *   Tried this but also didn't work: https://stackoverflow.com/questions/7418849/in-clause-and-placeholders.
        *   SQL injection shouldn't be possible here, but who knows... */
        selection = "${CalendarContract.Calendars._ID} IN (${ids.joinToString(separator = ",")})",
    ) ?: run {
        client.close()
        return
    }

    if (cursor.count != ids.size) {
        Log.e("copyFromDevice", "Invalid IDs. Passed in ${ids.size} IDs, but cursor only has ${cursor.count} rows.")
        cursor.close()
        client.close()
        return
    }

    // Copy each calendar returned selected with the cursor
    while (cursor.moveToNext()) {
        // TODO: prevent the calendar from being copied more than once
        // Load the data about this calendar to memory
        val data = ContentValues().apply {
            CopyCalendarsProjection.entries.forEach { entry ->
                cursor.getStringOrNull(entry.ordinal)?.let { this.put(entry.column, it) }
            }
        }
        data.put(CalendarContract.Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
        data.put(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
        data.put(CalendarContract.Calendars.OWNER_ACCOUNT, ACCOUNT_NAME)
        // data.put(CalendarContract.Calendars._SYNC_ID, ???)
        // Write the calendar data to the content provider
        client.insert(asSyncAdapter(CalendarContract.Calendars.CONTENT_URI), data)
    }

    // TODO: do the same thing for events

    cursor.close()
    client.close()
}
private enum class CopyCalendarsProjection(val column: String) {
    DISPLAY_NAME(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME),
    COLOR(CalendarContract.Calendars.CALENDAR_COLOR),
    // COLOR_KEY(CalendarContract.Calendars.CALENDAR_COLOR_KEY), // Including color key causes an error
    MAX_REMINDERS(CalendarContract.Calendars.MAX_REMINDERS),
    ALLOWED_REMINDERS(CalendarContract.Calendars.ALLOWED_REMINDERS),
    ALLOWED_AVAILABILITY(CalendarContract.Calendars.ALLOWED_AVAILABILITY),
    ALLOWED_ATTENDEE_TYPES(CalendarContract.Calendars.ALLOWED_ATTENDEE_TYPES),
    CAN_MODIFY_TIME_ZONE(CalendarContract.Calendars.CAN_MODIFY_TIME_ZONE),
    CAN_ORGANIZER_RESPOND(CalendarContract.Calendars.CAN_ORGANIZER_RESPOND),
    CAN_PARTIALLY_UPDATE(CalendarContract.Calendars.CAN_PARTIALLY_UPDATE),
    TIME_ZONE(CalendarContract.Calendars.CALENDAR_TIME_ZONE),
    ACCESS_LEVEL(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL),
    LOCATION(CalendarContract.Calendars.CALENDAR_LOCATION),
    IS_PRIMARY(CalendarContract.Calendars.IS_PRIMARY),
    SYNC_EVENTS(CalendarContract.Calendars.SYNC_EVENTS),
    VISIBLE(CalendarContract.Calendars.VISIBLE);

    companion object : QueryProjection {
        override fun projectionArray(): Array<String> {
            return CopyCalendarsProjection.entries.toList().map { it.column }.toTypedArray()
        }
    }
}

// This is used for showing debug data about calendars in the app
class AllData(context: Context) {
    val calendars: Data = Data(context, CalendarContract.Calendars.CONTENT_URI, EmptyProjection)
    val events: Data = Data(context, CalendarContract.Events.CONTENT_URI, EmptyProjection)
    val reminders: Data = Data(context, CalendarContract.Reminders.CONTENT_URI, EmptyProjection)
    val attendees: Data = Data(context, CalendarContract.Attendees.CONTENT_URI, EmptyProjection)

    class Data(context: Context, uri: Uri, projection: QueryProjection) {
        private var cursor = initializeCursor(context, uri, projection)
        val data: SnapshotStateList<Map<String, String>> = mutableStateListOf()

        fun queryNext() {
            this.data.add(query(cursor))
        }

        companion object {
            private fun initializeCursor(context: Context, uri: Uri, projection: QueryProjection): Cursor {
                return context.getCursor(uri, projection) ?: throw Exception("Cant get query cursor")
                // val client = context.contentResolver.acquireContentProviderClient(CalendarContract.Calendars.CONTENT_URI)!!
                // client.close()
                // cur.close()
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

/** The projection that is passed to a `CursorLoader` to tell it what columns we want to read.
 * Should be implemented by the **`companion object`** of an enum whose entries are the projection. */
interface QueryProjection {
    /** Convert the projection object into a String Array to pass into `CursorLoader`. */
    fun projectionArray(): Array<String>
}
/** A projection that will get all the columns. Equivalent to **`SELECT * FROM Calendars`** */
private open class EmptyProjection: QueryProjection {
    override fun projectionArray(): Array<String> = arrayOf()
    companion object: EmptyProjection()
}

private fun Context.getCursor(uri: Uri, projection: QueryProjection, selection: String = "", selectionArgs: Array<String> = arrayOf(), sort: String = ""): Cursor? {
    val loader = CursorLoader(
        this,
        uri,
        projection.projectionArray(),
        selection,
        selectionArgs,
        sort
    )

    return try {
        val cur = loader.loadInBackground()
        if (cur == null)
            println("Returned cursor was null")
        cur
    } catch(e: Exception) {
        Log.e("getCursor", "Exception occurred while loading calendar query cursor:\n$e")
        null
    }
}

fun asSyncAdapter(uri: Uri): Uri {
    return uri.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
        .build()
}

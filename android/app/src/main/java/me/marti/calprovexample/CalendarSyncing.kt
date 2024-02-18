package me.marti.calprovexample

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import android.widget.Toast
import androidx.loader.content.CursorLoader

/** Outputs a list of all calendars that are synced on the user has on the device with the calendar provider. */
fun userCalendars(context: Context): List<UserCalendarListItem>? {
    val cur = context.getCursor(
        CalendarContract.Calendars.CONTENT_URI, UserCalendarListItem.Projection
    ) ?: return null

    return List(cur.count) {
        cur.moveToNext()
        UserCalendarListItem(
            id = cur.getLong(UserCalendarListItem.Projection.ID.ordinal),
            name = cur.getString(UserCalendarListItem.Projection.DISPLAY_NAME.ordinal),
            accountName = cur.getString(UserCalendarListItem.Projection.ACCOUNT_NAME.ordinal),
            // The stored color is a 32bit ARGB, but the alpha is ignored.
            color = Color(cur.getInt(UserCalendarListItem.Projection.COLOR.ordinal)),
        )
    }
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

fun queryAllData(context: Context) {
    // Run query
    val cur = context.getCursor(CalendarContract.Calendars.CONTENT_URI, AllCalendarsProjection) ?: return

    // val client = context.contentResolver.acquireContentProviderClient(CalendarContract.Calendars.CONTENT_URI)!!
    // client.close()

    val columns = cur.columnCount
    while (cur.moveToNext()) {
        // Get the field values
        for (i in 0 ..< columns) {
            if (i != 0)
                print("\t")
            println("${cur.getColumnName(i)}: ${cur.getString(i)}")
        }

        // Do something with the values...
    }

    Toast.makeText(context, "Printed calendar query results", Toast.LENGTH_SHORT).show()

    cur.close()
}

private enum class AllCalendarsProjection(val s: String) {
    ID(CalendarContract.Calendars._ID),
    DISPLAY_NAME(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME),
    ACCOUNT_NAME(CalendarContract.Calendars.ACCOUNT_NAME),
    ACCOUNT_TYPE(CalendarContract.Calendars.ACCOUNT_TYPE),
    OWNER_ACCOUNT(CalendarContract.Calendars.OWNER_ACCOUNT),
    ACCESS_LEVEL(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL),
    ALLOWED_AVAILABILITY(CalendarContract.Calendars.ALLOWED_AVAILABILITY),
    COLOR(CalendarContract.Calendars.CALENDAR_COLOR),
    COLOR_KEY(CalendarContract.Calendars.CALENDAR_COLOR_KEY),
    TIME_ZONE(CalendarContract.Calendars.CALENDAR_TIME_ZONE),
    IS_PRIMARY(CalendarContract.Calendars.IS_PRIMARY),
    DIRTY(CalendarContract.Calendars.DIRTY),
    SYNC_EVENTS(CalendarContract.Calendars.SYNC_EVENTS),
    VISIBLE(CalendarContract.Calendars.VISIBLE);

    companion object : QueryProjection {
        override fun projectionArray(): Array<String> {
            return AllCalendarsProjection.entries.toList().map { it.s }.toTypedArray()
        }
    }
}

/** The projection that is passed to a `CursorLoader` to tell it what columns we want to read.
 * Should be implemented by the **`companion object`** of an enum whose entries are the projection. */
interface QueryProjection {
    /** Convert the projection object into a String Array to pass into `CursorLoader`. */
    fun projectionArray(): Array<String>
}
private fun Context.getCursor(uri: Uri, projection: QueryProjection, selection: String = "", selectionArgs: Array<String> = arrayOf()): Cursor? {
    val loader = CursorLoader(
        this,
        uri,
        projection.projectionArray(),
        selection,
        selectionArgs,
        null
    )

    return try {
        val cur = loader.loadInBackground()
        if (cur == null)
            println("Returned cursor was null")
        cur
    } catch(e: Exception) {
        println("Exception occurred while loading calendar query cursor: $e")
        null
    }
}

fun asSyncAdapter(uri: Uri): Uri {
    return uri.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .build()
}

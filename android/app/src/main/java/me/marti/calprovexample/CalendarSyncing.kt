package me.marti.calprovexample

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import android.widget.Toast
import androidx.loader.content.CursorLoader

/** Outputs a list of all calendars that are synced on the user has on the device with the calendar provider.
 */
fun userCalendars(context: Context): Array<UserCalendarListItem>? {
    val PROJECTION = arrayOf(
        CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
        CalendarContract.Calendars.ACCOUNT_NAME,
        CalendarContract.Calendars.CALENDAR_COLOR,
    )
    val PROJECTION_DISPLAY_NAME = 0
    val PROJECTION_ACCOUNT_NAME = 1
    val PROJECTION_COLOR = 2

    val cur = getCursor(context,
        projection = PROJECTION,
        "", arrayOf()
    ) ?: return null

    return Array(cur.count) {
        cur.moveToNext()
        UserCalendarListItem(
            name = cur.getString(PROJECTION_DISPLAY_NAME),
            accountName = cur.getString(PROJECTION_ACCOUNT_NAME),
            // The stored color is a 32bit ARGB, but the alpha is ignored.
            color = Color(cur.getInt(PROJECTION_COLOR)),
        )
    }
}

class UserCalendarListItem(
    val name: String,
    val accountName: String,
    val color: Color,
)

fun queryCalendar(context: Context) {
    println("Querying user calendar...")
    // Run query
    val loader = CursorLoader(
        context,
        CalendarContract.Calendars.CONTENT_URI,
        EVENT_PROJECTION,
        "",
        arrayOf(),
        null
    )

    val cur = try {
        loader.loadInBackground()
    } catch(e: Exception) {
        println("Exception occurred while loading calendar query cursor: $e")
        null
    }

    if (cur == null) {
        println("Returned cursor was null")
        return
    }

    // context.contentResolver.acquireContentProviderClient(CalendarContract.Calendars.CONTENT_URI)?.release()

    while (cur.moveToNext()) {
        // Get the field values
        for (i in EVENT_PROJECTION.indices) {
            println("${cur.getColumnName(i)}: ${cur.getString(i)}")
        }

        // Do something with the values...
    }

    Toast.makeText(context, "Printed calendar query results", Toast.LENGTH_SHORT).show()

    cur.close()
}

private fun getCursor(context: Context, projection: Array<String>, selection: String, selectionArgs: Array<String>): Cursor? {
    val loader = CursorLoader(
        context,
        CalendarContract.Calendars.CONTENT_URI,
        projection,
        selection,
        selectionArgs,
        null
    )

    return try {
        loader.loadInBackground()
    } catch(e: Exception) {
        println("Exception occurred while loading calendar query cursor: $e")
        null
    }
}

// Projection array. Creating indices for this array instead of doing dynamic lookups improves performance.
private val EVENT_PROJECTION: Array<String> = arrayOf(
    CalendarContract.Calendars._ID,
    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
    CalendarContract.Calendars.ACCOUNT_NAME,
    CalendarContract.Calendars.ACCOUNT_TYPE,
    CalendarContract.Calendars.OWNER_ACCOUNT,
    CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
    CalendarContract.Calendars.CALENDAR_COLOR,
    CalendarContract.Calendars.CALENDAR_COLOR_KEY,
    CalendarContract.Calendars.CALENDAR_TIME_ZONE,
    CalendarContract.Calendars.IS_PRIMARY,
    CalendarContract.Calendars.SYNC_EVENTS,
    CalendarContract.Calendars.VISIBLE,
)
// The indices for the projection array above.
private const val PROJECTION_ID_INDEX = 0
private const val PROJECTION_DISPLAY_NAME_INDEX = 1
private const val PROJECTION_ACCOUNT_NAME_INDEX = 2
private const val PROJECTION_ACCOUNT_TYPE_INDEX = 3
private const val PROJECTION_OWNER_ACCOUNT_INDEX = 4
private const val PROJECTION_ACCESS_LEVEL_INDEX = 5
private const val PROJECTION_COLOR_INDEX = 6
private const val PROJECTION_COLOR_KEY_INDEX = 7
private const val PROJECTION_TIME_ZONE_INDEX = 8
private const val PROJECTION_IS_PRIMARY_INDEX = 9
private const val PROJECTION_SYNC_EVENTS_INDEX = 10
private const val PROJECTION_VISIBLE_INDEX = 11

fun asSyncAdapter(uri: Uri): Uri {
    return uri.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .build()
}

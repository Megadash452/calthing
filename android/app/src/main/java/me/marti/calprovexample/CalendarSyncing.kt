package me.marti.calprovexample

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.loader.content.CursorLoader
import kotlin.enums.EnumEntries

/** Outputs a list of all calendars that are synced on the user has on the device with the calendar provider. */
fun userCalendars(context: Context): List<UserCalendarListItem>? {
    val cur = context.getCursor(
        CalendarContract.Calendars.CONTENT_URI, UserCalendarListItem.Projection
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
                    map[cursor.getColumnName(i)] = cursor.getString(i) ?: ""
                return map
            }
        }
    }

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
    MUTATORS(CalendarContract.Calendars.MUTATORS),
    SYNC_EVENTS(CalendarContract.Calendars.SYNC_EVENTS),
    VISIBLE(CalendarContract.Calendars.VISIBLE);

    companion object : QueryProjection {
        override fun projectionArray(): Array<String> {
            return AllCalendarsProjection.entries.toList().map { it.s }.toTypedArray()
        }
    }
}

private enum class EmptyProjection(val s: String) {
    ;

    companion object : QueryProjection {
        override fun projectionArray(): Array<String> {
            return EmptyProjection.entries.toList().map { it.s }.toTypedArray()
        }
    }
}

/** The projection that is passed to a `CursorLoader` to tell it what columns we want to read.
 * Should be implemented by the **`companion object`** of an enum whose entries are the projection. */
interface QueryProjection {
    /** Convert the projection object into a String Array to pass into `CursorLoader`. */
    fun projectionArray(): Array<String>
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
        println("Exception occurred while loading calendar query cursor: $e")
        null
    }
}

fun asSyncAdapter(uri: Uri, accName: String, accType: String): Uri {
    return uri.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, accName)
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, accType)
        .build()
}

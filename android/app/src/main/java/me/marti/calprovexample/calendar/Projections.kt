package me.marti.calprovexample.calendar

import android.content.ContentValues
import android.database.Cursor
import android.provider.CalendarContract

/** Some Calendars owned by this App are created by *importing* (copying) calendars created by other apps.
 *  To prevent the user from copying the calendar more than once,
 *  this *General-Purpose* field is set to the **ID** of the calendar it was copied from in the *ContentProvider*.
 *
 *  The value of this field will be **`NULL`** if calendar was *not copied* from another calendar in the device. */
const val IMPORTED_FROM_COLUMN = CalendarContract.Calendars.CAL_SYNC1

/** The projection that is passed to a `CursorLoader` to tell it what columns we want to read.
 * This is the `SELECT` part of the query.
 * Should be implemented by the **`companion object`** of an enum whose entries are the columns. */
interface QueryProjection {
    /** Convert the projection object into a String Array to pass into `CursorLoader`. */
    fun projectionArray(): Array<String>
}

/** A projection that will get all the columns. Equivalent to **`SELECT *`** */
internal open class EmptyProjection: QueryProjection {
    override fun projectionArray(): Array<String> = arrayOf()
    companion object: EmptyProjection()
}

// Gets only the data required to display Calendar Info
enum class DisplayCalendarProjection(val s: String) {
    ID(CalendarContract.Calendars._ID),
    DISPLAY_NAME(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME),
    ACCOUNT_NAME(CalendarContract.Calendars.ACCOUNT_NAME),
    COLOR(CalendarContract.Calendars.CALENDAR_COLOR),
    /** Used only with Calendars owned by this app (internal). See [IMPORTED_FROM_COLUMN]. */
    IMPORTED_FROM(IMPORTED_FROM_COLUMN);

    companion object : QueryProjection {
        override fun projectionArray(): Array<String> {
            return DisplayCalendarProjection.entries.toList().map { it.s }.toTypedArray()
        }
    }
}

internal enum class CopyCalendarsProjection(val column: String) {
    ID(CalendarContract.Calendars._ID),
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

        fun loadCursorData(cursor: Cursor): ContentValues {
            val data = ContentValues()
            CopyCalendarsProjection.entries.forEach { entry ->
                when (cursor.getType(entry.ordinal)) {
                    // Use the cursor.get* variants that have the largest size to avoid errors.
                    Cursor.FIELD_TYPE_INTEGER -> data.put(entry.column, cursor.getLong(entry.ordinal))
                    Cursor.FIELD_TYPE_FLOAT -> data.put(entry.column, cursor.getDouble(entry.ordinal))
                    Cursor.FIELD_TYPE_STRING -> data.put(entry.column, cursor.getString(entry.ordinal))
                    Cursor.FIELD_TYPE_BLOB -> data.put(entry.column, cursor.getBlob(entry.ordinal))
                    Cursor.FIELD_TYPE_NULL -> {} // Put nothing
                }
            }
            return data
        }
    }
}
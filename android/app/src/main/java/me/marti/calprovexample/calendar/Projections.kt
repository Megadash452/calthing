@file:OptIn(ExperimentalStdlibApi::class)
@file:Suppress("unused")

package me.marti.calprovexample.calendar

import android.content.ContentValues
import android.database.Cursor
import android.provider.CalendarContract
import androidx.core.database.getBlobOrNull
import androidx.core.database.getDoubleOrNull
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import kotlin.enums.enumEntries

/** Some Calendars owned by this App are created by *importing* (copying) calendars created by other apps.
 *  To prevent the user from copying the calendar more than once,
 *  this *General-Purpose* field is set to the **ID** of the calendar it was copied from in the *ContentProvider*.
 *
 *  The value of this field will be **`NULL`** if calendar was *not copied* from another calendar in the device. */
const val IMPORTED_FROM_COLUMN = CalendarContract.Calendars.CAL_SYNC1

/** The projection that is passed to a `CursorLoader` to tell it what columns we want to read.
 * This is the `SELECT` part of the query.
 * Should be implemented by the **`companion object`** of an enum whose entries are the columns.
 *
 * The enums in question must implement [ProjectionEntry].
 * @sample DisplayCalendarProjection */
// Methods for this interface are defined below. Cant be defined here because of 'reified' shenanigans...
interface QueryProjection<P>
where P : Enum<P>, P: ProjectionEntry

/** This interface is implemented by **all Projection enums**.
 * Implementors should have a *Companion Object* that implements [QueryProjection]. */
interface ProjectionEntry {
    /** The string value of the *column name* as it exists in memory. */
    val column: String
}

/** Convert the projection object into a String Array to pass into `CursorLoader`. */
internal inline fun <reified P> projectionArray(): Array<String>
where P: Enum<P>, P: ProjectionEntry {
    return enumEntries<P>().toList().map { entry -> entry.column }.toTypedArray()
}

/** Load the data of the current *row* of the cursor into [ContentValues] using the *columns* of a **projection**. */
// Chose to user QueryProjection receiver to make it more readable since this will only be used in external calls,
// unlike projectionArray() which is used in utility functions to map between a Projection Enum and an actual projection string.
@Suppress("UnusedReceiverParameter")
internal inline fun <reified P> QueryProjection<P>.loadCursorData(cursor: Cursor): ContentValues
where P: Enum<P>, P: ProjectionEntry {
    val data = ContentValues()
    enumEntries<P>().forEach { entry ->
        when (cursor.getType(entry.ordinal)) {
            // Use the cursor.get* variants that have the largest size to avoid errors.
            Cursor.FIELD_TYPE_INTEGER -> cursor.getLongOrNull(entry.ordinal)?.let { data.put(entry.column, it) }
            Cursor.FIELD_TYPE_FLOAT -> cursor.getDoubleOrNull(entry.ordinal)?.let { data.put(entry.column, it) }
            Cursor.FIELD_TYPE_STRING -> cursor.getStringOrNull(entry.ordinal)?.let { data.put(entry.column, it) }
            Cursor.FIELD_TYPE_BLOB -> cursor.getBlobOrNull(entry.ordinal)?.let { data.put(entry.column, it) }
            Cursor.FIELD_TYPE_NULL -> {} // Put nothing
        }
    }
    return data
}

/** A projection that will get all the columns. Equivalent to **`SELECT *`** */
internal enum class EmptyProjection: ProjectionEntry {
    ;
    companion object: QueryProjection<EmptyProjection>
}

// Gets only the data required to display Calendar Info
internal enum class DisplayCalendarProjection(override val column: String): ProjectionEntry {
    ID(CalendarContract.Calendars._ID),
    DISPLAY_NAME(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME),
    ACCOUNT_NAME(CalendarContract.Calendars.ACCOUNT_NAME),
    COLOR(CalendarContract.Calendars.CALENDAR_COLOR),
    SYNC(CalendarContract.Calendars.SYNC_EVENTS),
    /** Used only with Calendars owned by this app (internal). See [IMPORTED_FROM_COLUMN]. */
    IMPORTED_FROM(IMPORTED_FROM_COLUMN);

    companion object : QueryProjection<DisplayCalendarProjection>
}

internal enum class CopyCalendarsProjection(override val column: String): ProjectionEntry {
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

    companion object : QueryProjection<CopyCalendarsProjection>
}

internal class CopyEventsProjection {
    // val entries
}

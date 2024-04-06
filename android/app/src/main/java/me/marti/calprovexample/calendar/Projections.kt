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

/** A skeleton for the members of a Projection that can be used to *query* the Calendar ContentProvider.
 *
 * Projections are the `SELECT` part of a query.
 * Projections are **enum classes** whose entries are the **columns** to be loaded.
 * They are passed as the *Generic Type* to a [CursorLoader][getCursor] to tell it what columns we want to read.
 *
 * This interface is implemented by **all Projection enums**.
 * @sample DisplayCalendarProjection */
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
internal inline fun <reified P> Cursor.loadRowData(): ContentValues
where P: Enum<P>, P: ProjectionEntry {
    val data = ContentValues()
    enumEntries<P>().forEach { entry ->
        when (this.getType(entry.ordinal)) {
            // Use the cursor.get* variants that have the largest size to avoid errors.
            Cursor.FIELD_TYPE_INTEGER -> this.getLongOrNull(entry.ordinal)?.let { data.put(entry.column, it) }
            Cursor.FIELD_TYPE_FLOAT -> this.getDoubleOrNull(entry.ordinal)?.let { data.put(entry.column, it) }
            Cursor.FIELD_TYPE_STRING -> this.getStringOrNull(entry.ordinal)?.let { data.put(entry.column, it) }
            Cursor.FIELD_TYPE_BLOB -> this.getBlobOrNull(entry.ordinal)?.let { data.put(entry.column, it) }
            Cursor.FIELD_TYPE_NULL -> {} // Put nothing
        }
    }
    return data
}

/** A projection that will get all the columns. Equivalent to **`SELECT *`** */
internal enum class EmptyProjection: ProjectionEntry {;}

// Gets only the data required to display Calendar Info
internal enum class DisplayCalendarProjection(override val column: String): ProjectionEntry {
    ID(CalendarContract.Calendars._ID),
    DISPLAY_NAME(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME),
    ACCOUNT_NAME(CalendarContract.Calendars.ACCOUNT_NAME),
    COLOR(CalendarContract.Calendars.CALENDAR_COLOR),
    SYNC(CalendarContract.Calendars.SYNC_EVENTS),
    /** Used only with Calendars owned by this app (internal). See [IMPORTED_FROM_COLUMN]. */
    IMPORTED_FROM(IMPORTED_FROM_COLUMN),
}

internal enum class CopyCalendarsProjection(override val column: String): ProjectionEntry {
    ID(CalendarContract.Calendars._ID), // Excluded from copied data. Only used to get Events
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
    VISIBLE(CalendarContract.Calendars.VISIBLE),
}

internal enum class CopyEventsProjection(override val column: String): ProjectionEntry {
    ID(CalendarContract.Events._ID), // Excluded from copied data. Only used to get Reminders and Attendees
    // CALENDAR_ID(CalendarContract.Events.CALENDAR_ID),
    COLOR(CalendarContract.Events.EVENT_COLOR),
    TITLE(CalendarContract.Events.TITLE),
    ORGANIZER(CalendarContract.Events.ORGANIZER),
    LOCATION(CalendarContract.Events.EVENT_LOCATION),
    DESCRIPTION(CalendarContract.Events.DESCRIPTION),
    TIMEZONE(CalendarContract.Events.EVENT_TIMEZONE),
    END_TIMEZONE(CalendarContract.Events.EVENT_END_TIMEZONE),
    DATE_START(CalendarContract.Events.DTSTART),
    DATE_END(CalendarContract.Events.DTEND),
    DURATION(CalendarContract.Events.DURATION),
    ALL_DAY(CalendarContract.Events.ALL_DAY),
    R_RULE(CalendarContract.Events.RRULE),
    R_DATE(CalendarContract.Events.RDATE),
    EX_RULE(CalendarContract.Events.EXRULE),
    EX_DATE(CalendarContract.Events.EXDATE),
    // TODO: If event is an exception, find the ID of the copied event for which this will become an exception.
    // ORIGINAL_ID(CalendarContract.Events.ORIGINAL_ID),
    // ORIGINAL_SYNC_ID(CalendarContract.Events.ORIGINAL_SYNC_ID),
    // ORIGINAL_INSTANCE_TIME(CalendarContract.Events.ORIGINAL_INSTANCE_TIME),
    // ORIGINAL_ALL_DAY(CalendarContract.Events.ORIGINAL_ALL_DAY),
    ACCESS_LEVEL(CalendarContract.Events.ACCESS_LEVEL),
    AVAILABILITY(CalendarContract.Events.AVAILABILITY),
    GUESTS_CAN_MODIFY(CalendarContract.Events.GUESTS_CAN_MODIFY),
    GUESTS_CAN_INVITE_OTHERS(CalendarContract.Events.GUESTS_CAN_INVITE_OTHERS),
    GUESTS_CAN_SEE_GUESTS(CalendarContract.Events.GUESTS_CAN_SEE_GUESTS),
    // CalendarContract.Events.CUSTOM_APP_PACKAGE
    // CalendarContract.Events.CUSTOM_APP_URI
    // CalendarContract.Events.UID_2445
}

internal enum class CopyRemindersProjection(override val column: String): ProjectionEntry {
    // ID(CalendarContract.Reminders._ID),
    // EVENT_ID(CalendarContract.Reminders.EVENT_ID),
    METHOD(CalendarContract.Reminders.METHOD),
    MINUTES(CalendarContract.Reminders.MINUTES),
}

internal enum class CopyAttendeesProjection(override val column: String): ProjectionEntry {
    // ID(CalendarContract.Attendees._ID),
    // EVENT_ID(CalendarContract.Attendees.EVENT_ID),
    ATTENDEE_NAME(CalendarContract.Attendees.ATTENDEE_NAME),
    ATTENDEE_EMAIL(CalendarContract.Attendees.ATTENDEE_EMAIL),
    ATTENDEE_RELATIONSHIP(CalendarContract.Attendees.ATTENDEE_RELATIONSHIP),
    ATTENDEE_TYPE(CalendarContract.Attendees.ATTENDEE_TYPE),
    ATTENDEE_STATUS(CalendarContract.Attendees.ATTENDEE_STATUS),
    ATTENDEE_IDENTITY(CalendarContract.Attendees.ATTENDEE_IDENTITY),
    ATTENDEE_ID_NAMESPACE(CalendarContract.Attendees.ATTENDEE_ID_NAMESPACE),
}
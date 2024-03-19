package me.marti.calprovexample.calendar

import android.provider.CalendarContract

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

internal enum class CopyCalendarsProjection(val column: String) {
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
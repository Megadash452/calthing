package me.marti.calprovexample.calendar

import android.content.ContentUris
import android.content.ContentValues
import android.provider.CalendarContract
import android.util.Log
import android.widget.Toast
import androidx.compose.ui.graphics.toArgb
import androidx.core.database.getStringOrNull
import me.marti.calprovexample.Color
import me.marti.calprovexample.ui.CalendarPermission

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

/** @return Returns the basic data about the Calendar so it can be added to the *`userCalendars`* list.
 *          **`Null`** if adding the calendar failed. */
fun CalendarPermission.Dsl.newCalendar(name: String, color: Color): UserCalendarListItem? {
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
        this.put(
            CalendarContract.Calendars.ALLOWED_REMINDERS,
            "${CalendarContract.Reminders.METHOD_DEFAULT}," +
            "${CalendarContract.Reminders.METHOD_ALERT}," +
            "${CalendarContract.Reminders.METHOD_ALARM}"
        )
        this.put(
            CalendarContract.Calendars.ALLOWED_AVAILABILITY,
            "${CalendarContract.Events.AVAILABILITY_BUSY}," +
            "${CalendarContract.Events.AVAILABILITY_FREE}," +
            "${CalendarContract.Events.AVAILABILITY_TENTATIVE}"
        )
        this.put(CalendarContract.Calendars.ALLOWED_ATTENDEE_TYPES, CalendarContract.Attendees.TYPE_NONE)
    }) ?: run {
        Toast.makeText(context, "Failed to add calendar \"$name\"", Toast.LENGTH_SHORT).show()
        return@newCalendar null
    }
    Toast.makeText(context, "$newCalUri", Toast.LENGTH_SHORT).show()

    return UserCalendarListItem(
        id = ContentUris.parseId(newCalUri),
        name = name,
        accountName = ACCOUNT_NAME,
        color = color
    )
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

/** Copy a set of Calendars created by other apps in the device so that they can be synced with this app.
 * @returns A list of calendar data of the Calendars that were successfully added.
 *          **`Null`** if there was an error setting up the contentProvider cursor. */
fun CalendarPermission.Dsl.copyFromDevice(ids: List<Long>): List<UserCalendarListItem>? {
    val successCals = mutableListOf<UserCalendarListItem>()
    val client = this.context.contentResolver.acquireContentProviderClient(CalendarContract.CONTENT_URI) ?: return null
    val cursor = this.context.getCursor(
        CalendarContract.Calendars.CONTENT_URI, CopyCalendarsProjection,
        // Select the calendars that are in the id list
        /* FIXME: Using selectionArgs to pass the IDs didn't work.
        *   Tried this but also didn't work: https://stackoverflow.com/questions/7418849/in-clause-and-placeholders.
        *   SQL injection shouldn't be possible here, but who knows... */
        selection = "${CalendarContract.Calendars._ID} IN (${ids.joinToString(separator = ",")})",
    ) ?: run {
        client.close()
        return null
    }

    if (cursor.count != ids.size) {
        Log.e("copyFromDevice", "Invalid IDs. Passed in ${ids.size} IDs, but cursor only has ${cursor.count} rows.")
        cursor.close()
        client.close()
        return null
    }

    // Copy each calendar returned selected with the cursor
    while (cursor.moveToNext()) {
        // TODO: prevent the calendar from being copied more than once
        // Load the data about this calendar to memory
        val data = ContentValues().apply {
            CopyCalendarsProjection.entries.forEach { entry ->
                // TODO: user proper types
                cursor.getStringOrNull(entry.ordinal)?.let { this.put(entry.column, it) }
            }
        }
        data.put(CalendarContract.Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
        data.put(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
        data.put(CalendarContract.Calendars.OWNER_ACCOUNT, ACCOUNT_NAME)
        // data.put(CalendarContract.Calendars._SYNC_ID, ???)
        // Write the calendar data to the content provider
        client.insert(asSyncAdapter(CalendarContract.Calendars.CONTENT_URI), data)?.let { newCal ->
            successCals.add(UserCalendarListItem(
                id = ContentUris.parseId(newCal),
                name = data.getAsString(CopyCalendarsProjection.DISPLAY_NAME.column),
                accountName = ACCOUNT_NAME,
                // color = data[CalendarContract.Calendars.CALENDAR_COLOR]
                color = Color(data.getAsInteger(CopyCalendarsProjection.COLOR.column))
            ))
        }
    }

    // TODO: do the same thing for events

    cursor.close()
    client.close()
    return successCals
}

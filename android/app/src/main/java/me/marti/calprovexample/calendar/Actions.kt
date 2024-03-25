package me.marti.calprovexample.calendar

import android.content.ContentUris
import android.content.ContentValues
import android.provider.CalendarContract
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import me.marti.calprovexample.Color
import me.marti.calprovexample.R
import me.marti.calprovexample.ui.CalendarPermission

data class UserCalendarListItem(
    val id: Long,
    val name: String,
    val accountName: String,
    val color: Color
)
/** Outputs a list of all calendars that are synced on the user has on the device with the calendar provider.
 * @param internal If `true`, the returned list will only contain Calendars *created by this app*.
 *                 Otherwise it will exclude these calendars*/
fun CalendarPermission.Dsl.userCalendars(internal: Boolean = true): List<UserCalendarListItem>? {
    // Calendars created by this app are those with LOCAL account type and this app's account name
    // Whether the cursor will include or exclude calendars owned by this App
    val op = if (internal) "" else "NOT"
    val cur = this.context.getCursor(
        CalendarContract.Calendars.CONTENT_URI, DisplayCalendarProjection,
        "$op ((${CalendarContract.Calendars.ACCOUNT_TYPE} = ?) AND (${CalendarContract.Calendars.ACCOUNT_NAME} = ?))",
        arrayOf(CalendarContract.ACCOUNT_TYPE_LOCAL, this.context.getString(R.string.account_name))
    ) ?: return null

    val result = List(cur.count) {
        cur.moveToNext()
        UserCalendarListItem(
            id = cur.getLong(DisplayCalendarProjection.ID.ordinal),
            name = cur.getString(DisplayCalendarProjection.DISPLAY_NAME.ordinal),
            accountName = cur.getString(DisplayCalendarProjection.ACCOUNT_NAME.ordinal),
            // The stored color is a 32bit ARGB, but the alpha is ignored.
            color = Color(cur.getInt(DisplayCalendarProjection.COLOR.ordinal)),
        )
    }

    cur.close()
    return result
}

/** @return Returns the basic data about the Calendar so it can be added to the *`userCalendars`* list.
 *          **`Null`** if adding the calendar failed. */
fun CalendarPermission.Dsl.newCalendar(name: String, color: Color): UserCalendarListItem? {
    val accountName = this.context.getString(R.string.account_name)

    val newCalUri = this.context.contentResolver.insert(CalendarContract.Calendars.CONTENT_URI.asSyncAdapter(accountName), ContentValues().apply {
        // Required
        this.put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
        this.put(CalendarContract.Calendars.ACCOUNT_NAME, accountName)
        this.put(CalendarContract.Calendars.OWNER_ACCOUNT, accountName)
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
        Log.e("newCalendar", "Failed to add calendar \"$name\"")
        return@newCalendar null
    }

    return UserCalendarListItem(
        id = ContentUris.parseId(newCalUri),
        name = name,
        accountName = accountName,
        color = color
    )
}

/** Delete a Calendar from the System ContentProvider.
 * @return **`true`** if the Calendar was successfully deleted, **`false`** if it wasn't. */
fun CalendarPermission.Dsl.deleteCalendar(id: Long): Boolean {
    // Events are automatically deleted with the calendar
    // TODO: show confirmation to delete
    // TODO: show snack-bar with undo button
    // TODO: delete only if sync adapter (UI will not show button if cant delete)
    val client = this.context.getClient()
    val calName = client.getCursor(
        CalendarContract.Calendars.CONTENT_URI.withId(id),
        DisplayCalendarProjection,
    )?.let { cursor ->
        cursor.moveToFirst()
        val name = cursor.getString(DisplayCalendarProjection.DISPLAY_NAME.ordinal)
        cursor.close()
        name
    } ?: "UNKNOWN"

    val success = client.delete(
       CalendarContract.Calendars.CONTENT_URI
           .withId(id)
           .asSyncAdapter(this.context.getString(R.string.account_name)),
       null, null
    ).run {
        this != 0
    }
    client.close()

    if (success)
        Log.i("deleteCalendar", "Deleted Calendar \"$calName\"")
    else
        Log.e("deleteCalendar", "Failed to delete Calendar \"$calName\"")

    return success
}

/** Copy a set of Calendars created by other apps in the device so that they can be synced with this app.
 *  Also copies all the Events, Reminders, and Attendees.
 *
 *  Some data from the copied calendar is changed (like `ACCOUNT_NAME`) before it is inserted into the *New Calendar*.
 *
 * @returns A list of calendar data of the Calendars that were successfully added.
 *          **`Null`** if there was an error setting up the contentProvider cursor. */
fun CalendarPermission.Dsl.copyFromDevice(ids: List<Long>): List<UserCalendarListItem>? {
    val accountName = this.context.getString(R.string.account_name)
    val successCals = mutableListOf<UserCalendarListItem>()
    val client = this.context.contentResolver.acquireContentProviderClient(CalendarContract.CONTENT_URI) ?: return null
    val cursor = client.getCursor(
        CalendarContract.Calendars.CONTENT_URI, CopyCalendarsProjection,
        // Select the calendars that are in the id list
        /*  Using selectionArgs to pass the IDs didn't work.
        *   Tried this but also didn't work: https://stackoverflow.com/questions/7418849/in-clause-and-placeholders.
        *   That said, SQL injection shouldn't be possible because the resulting string will only contain decimal digit characters. */
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
        // Load the data about this calendar to memory
        val data = CopyCalendarsProjection.loadCursorData(cursor).apply {
            this.remove(CopyCalendarsProjection.ID.column)
            this.put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            this.put(CalendarContract.Calendars.ACCOUNT_NAME, accountName)
            this.put(CalendarContract.Calendars.OWNER_ACCOUNT, accountName)
            // Store which (if any) calendar this one was created from so that calendar can't be imported again.
            this.put(IMPORTED_FROM_COLUMN, cursor.getLong(CopyCalendarsProjection.ID.ordinal))
            // this.put(CalendarContract.Calendars._SYNC_ID, ???)
        }
        // Write the calendar data to the content provider
        client.insert(CalendarContract.Calendars.CONTENT_URI.asSyncAdapter(accountName), data)?.let { newCal ->
            successCals.add(UserCalendarListItem(
                id = ContentUris.parseId(newCal),
                name = data.getAsString(CopyCalendarsProjection.DISPLAY_NAME.column),
                accountName = accountName,
                // color = data[CalendarContract.Calendars.CALENDAR_COLOR]
                color = Color(data.getAsInteger(CopyCalendarsProjection.COLOR.column))
            ))
        } ?: run {
            Log.e("copyFromDevice", "Failed to insert Calendar from copied data.")
        }

        // IMPORT events from the copied calendar
        // TODO: do the same thing for events
        // val calId = cursor.getLong(CopyCalendarsProjection.ID.ordinal)
        // val events = this.context.getCursor(
        //     CalendarContract.Calendars.CONTENT_URI, CopyEventsProjection,
        //     "(${CalendarContract.Events.CALENDAR_ID} = ?)", arrayOf(calId.toString())
        // )
        // events.close()
    }

    cursor.close()
    client.close()
    return successCals
}

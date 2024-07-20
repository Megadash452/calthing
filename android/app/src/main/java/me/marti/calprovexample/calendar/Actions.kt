package me.marti.calprovexample.calendar

import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.provider.CalendarContract
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import androidx.core.database.getLongOrNull
import me.marti.calprovexample.Color
import me.marti.calprovexample.ElementExistsException
import me.marti.calprovexample.R
import me.marti.calprovexample.ui.CalendarPermissionScope
import me.marti.calprovexample.ui.DEFAULT_CALENDAR_COLOR
import java.io.File as Path

/** Display data about a calendar to the user.
 * @see ExternalUserCalendar
 * @see InternalUserCalendar */
open class UserCalendarListItem(
    val id: Long,
    val name: String,
    val accountName: String,
    val color: Color,
)
// Two sides of the same coin.
/** Display data about a calendar that the user can import (copy) to sync with this App.
 *
 * See the important field [importedTo]. */
class ExternalUserCalendar(
    id: Long,
    name: String,
    accountName: String,
    color: Color,
    /** The **name** of the Calendar that was created (*imported*, copied) from this one (if any).
     *
     * Obtained from a list of Calendars from [`internalUserCalendars()`][internalUserCalendars]
     * by finding a Calendar with its [**`importedFrom`**][InternalUserCalendar.importedFrom] field
     * set with the [`ID`][InternalUserCalendar.id] of this Calendar.
     * Convert the calendars obtained from [`externalUserCalendars()`][externalUserCalendars] to this class.
     *
     * ### Example
     * ```kt
     * val appCalendars = internalUserCalendars()!!
     * val otherCalendars = externalUserCalendars()!!
     *     .map { cal -> ExternalUserCalendar(
     *         cal,
     *         appCalendars.find { iCal -> cal.id == iCal.importedFrom }?.name
     *     ) }
     * ``` */
    val importedTo: String?
) : UserCalendarListItem(id, name, accountName, color) {
    constructor(parent: UserCalendarListItem, importedTo: String?)
        : this(parent.id, parent.name, parent.accountName, parent.color, importedTo)

    /** Create a copy of a Calendar object to edit some of its fields. */
    fun copy(id: Long? = null, name: String? = null, accountName: String? = null, color: Color? = null, importedTo: String? = null): ExternalUserCalendar {
        return ExternalUserCalendar(
            id = id ?: this.id,
            name = name ?: this.name,
            accountName = accountName ?: this.accountName,
            color = color ?: this.color,
            importedTo = importedTo ?: this.importedTo
        )
    }
}
/** Display data about a calendar that the user imported from the system to sync with this App. */
class InternalUserCalendar(
    id: Long,
    name: String,
    accountName: String,
    color: Color,
    /** Whether the user chose to sync this Calendar. Starts as `false` for new/imported Calendars. */
    val sync: Boolean,
    /** The **ID** of the external Calendar that was used to create this one (if any).
     * See [IMPORTED_FROM_COLUMN]. */
    val importedFrom: Long?
) : UserCalendarListItem(id, name, accountName, color) {
    constructor(parent: UserCalendarListItem, sync: Boolean, importedFrom: Long?)
            : this(parent.id, parent.name, parent.accountName, parent.color, sync, importedFrom)

    /** Get the data of a calendar by reading it from a cursor. */
    constructor(cur: Cursor): this(
        id = cur.getLong(DisplayCalendarProjection.ID.ordinal),
        name = cur.getString(DisplayCalendarProjection.DISPLAY_NAME.ordinal),
        accountName = cur.getString(DisplayCalendarProjection.ACCOUNT_NAME.ordinal),
        // The stored color is a 32bit ARGB, but the alpha is ignored.
        color = Color(cur.getInt(DisplayCalendarProjection.COLOR.ordinal)),
        sync = cur.getInt(DisplayCalendarProjection.SYNC.ordinal) != 0,
        importedFrom = cur.getLongOrNull(DisplayCalendarProjection.IMPORTED_FROM.ordinal),
    )

    /** Create a copy of a Calendar object to edit some of its fields. */
    fun copy(id: Long? = null, name: String? = null, accountName: String? = null, color: Color? = null, sync: Boolean? = null, importedFrom: Long? = null): InternalUserCalendar {
        return InternalUserCalendar(
            id = id ?: this.id,
            name = name ?: this.name,
            accountName = accountName ?: this.accountName,
            color = color ?: this.color,
            sync = sync ?: this.sync,
            importedFrom = importedFrom ?: this.importedFrom
        )
    }
}


/** Get a list of Calendars the user has on their device that can be *imported* to sync with this App.
 *
 * The resulting Calendars can then be converted to [ExternalUserCalendar].
 * For that, see [ExternalUserCalendar.importedTo].
 * @returns **NULL** on error. */
fun CalendarPermissionScope.externalUserCalendars(): List<UserCalendarListItem>? {
    // Calendars created by other apps (external
    // Whether the cursor will include or exclude calendars owned by this App
    val cur = this.context.getCursor<DisplayCalendarProjection>(
        CalendarContract.Calendars.CONTENT_URI,
        "NOT ((${CalendarContract.Calendars.ACCOUNT_TYPE} = ?) AND (${CalendarContract.Calendars.ACCOUNT_NAME} = ?))",
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

/** Get a list of Calendars owned by this App that the user can sync.
 * @returns **NULL** on error. */
fun CalendarPermissionScope.internalUserCalendars(): List<InternalUserCalendar>? {
    // Calendars created by this app (internal) are those with LOCAL account type and this app's account name
    val cur = this.context.getCursor<DisplayCalendarProjection>(
        CalendarContract.Calendars.CONTENT_URI,
        "(${CalendarContract.Calendars.ACCOUNT_TYPE} = ?) AND (${CalendarContract.Calendars.ACCOUNT_NAME} = ?)",
        arrayOf(CalendarContract.ACCOUNT_TYPE_LOCAL, this.context.getString(R.string.account_name))
    ) ?: return null

    val result = List(cur.count) {
        cur.moveToNext()
        InternalUserCalendar(cur)
    }

    cur.close()
    return result
}

/** Get data about a Calendar owned by this app. */
fun CalendarPermissionScope.getData(id: Long): InternalUserCalendar? {
    return this.context.getCursor<DisplayCalendarProjection>(
        CalendarContract.Calendars.CONTENT_URI.withId(id)
    )?.use { cursor ->
        cursor.moveToFirst()
        InternalUserCalendar(cursor)
    }
}
fun CalendarPermissionScope.getData(name: String): InternalUserCalendar? {
    return this.context.getCursor<DisplayCalendarProjection>(
        CalendarContract.Calendars.CONTENT_URI,
        "${DisplayCalendarProjection.DISPLAY_NAME.column} = ?",
        arrayOf(name)
    )?.use { cursor ->
        cursor.moveToFirst()
        InternalUserCalendar(cursor)
    }
}


/** Create a new Calendar entry in the Content Provider.
 * @return Returns the basic data about the Calendar so it can be added to the *`userCalendars`* list.
 *   **`Null`** or if adding the calendar failed.
 * @throws ElementExistsException if a calendar with this name already exists */
fun CalendarPermissionScope.newCalendar(name: String, color: Color): InternalUserCalendar? {
    val accountName = this.context.getString(R.string.account_name)
    val uri = CalendarContract.Calendars.CONTENT_URI.asSyncAdapter(accountName)
    return this.context.contentResolver.acquireContentProviderClient(uri)?.use { client ->
        client.getCursor<DisplayCalendarProjection>(uri,
            "${DisplayCalendarProjection.DISPLAY_NAME.column} = ?",
            arrayOf(name)
        )?.use { cursor ->
            // If there already exists a calendar with this name,
            if (cursor.moveToFirst())
                throw ElementExistsException(name)
        }

        val newCalUri = client.insert(uri, ContentValues().apply {
            // Required
            this.put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            this.put(CalendarContract.Calendars.ACCOUNT_NAME, accountName)
            this.put(CalendarContract.Calendars.OWNER_ACCOUNT, accountName)
            this.put(CalendarContract.Calendars.NAME, name) // Don't really know what this is for
            this.put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, name)
            this.put(CalendarContract.Calendars.CALENDAR_COLOR, color.toColor().toArgb())
            this.put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            // Not required, but recommended
            this.put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, "America/New_York") // TODO: get value from rust library
            // The calendar starts as not synced, then the user can choose whether to sync it or not.
            this.put(CalendarContract.Calendars.SYNC_EVENTS, 0)
            this.put(CalendarContract.Calendars.VISIBLE, 1)
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

        InternalUserCalendar(
            id = ContentUris.parseId(newCalUri),
            name = name,
            accountName = accountName,
            color = color,
            sync = false,
            importedFrom = null
        )
    }
}

/** Will create calendar in Content Provider if it doesn't yet exist */
fun CalendarPermissionScope.writeFileDataToCalendar(name: String, filesDir: Path) {
    try {
        this.newCalendar(name, Color(DEFAULT_CALENDAR_COLOR)) // TODO: use color from file
    } catch (e: ElementExistsException) {

    }
    // TODO: parse file contents and add them to the Content Provider
    // TODO: add to list without adding to provider
}

fun CalendarPermissionScope.editCalendar(id: Long, newName: String? = null, newColor: Color? = null, sync: Boolean? = null): Boolean {
    return this.context.updateCalendar(
        id = id,
        accountName = this.context.getString(R.string.account_name),
        values = ContentValues().apply {
            newName?.let { name -> this.put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, name) }
            newColor?.let { color -> this.put(CalendarContract.Calendars.CALENDAR_COLOR, color.toColor().toArgb()) }
            sync?.let { sync -> this.put(CalendarContract.Calendars.SYNC_EVENTS, if (sync) 1 else 0) }
        }
    )
}

/** Delete a Calendar from the System ContentProvider.
 * @return **`true`** if the Calendar was successfully deleted, **`false`** if it wasn't. */
fun CalendarPermissionScope.deleteCalendar(id: Long): Boolean {
    // Events are automatically deleted with the calendar
    val client = this.context.getClient()
    val calName = client.getCursor<DisplayCalendarProjection>(
        CalendarContract.Calendars.CONTENT_URI.withId(id)
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

/** The same as delete, but takes a **name** instead of a Calendar ID.
 * This works because calendar names are unique. */
fun CalendarPermissionScope.deleteCalendarByName(name: String): Boolean {
    return this.context.contentResolver.delete(
        CalendarContract.Calendars.CONTENT_URI.asSyncAdapter(this.context.getString(R.string.account_name)),
        "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} = ?",
        arrayOf(name)
    ).run {
        this != 0
    }
}

/** Copy a Calendar created by other apps (*external*) in the device so that they can be synced with this app.
 *  Also copies all the Events, Reminders, and Attendees.
 *
 *  Some data of [ExternalUserCalendar] is ignored (*id* and *accountName*).
 *  All other data that is not in [ExternalUserCalendar] will be copied from the Content Provider.
 *
 *  Note that some of what is in [ExternalUserCalendar] may be different from the data in the Content Provider because it may have been changed by the user.
 *  Fr example, [copyFromExternal][me.marti.calprovexample.ui.MutableCalendarsList.copyFromExternal] may prompt the user to change the *name* of the Calendar.
 *
 *  @returns Data of the newly added Calendar if successful.
 *          **`Null`** if there was an error setting up the contentProvider cursor. */
fun CalendarPermissionScope.copyExternalCalendar(calendar: ExternalUserCalendar): InternalUserCalendar? {
    val accountName = this.context.getString(R.string.account_name)
    // List of Calendars that were successfully copied (even if there were errors in copying other things, like Events).
    val client = this.context.contentResolver.acquireContentProviderClient(CalendarContract.CONTENT_URI) ?: return null

    val calendarsCursor = client.getCursor<CopyCalendarsProjection>(
        CalendarContract.Calendars.CONTENT_URI,
        selection = "${CalendarContract.Calendars._ID} = ?",
        selectionArgs = arrayOf(calendar.id.toString())
    ) ?: run {
        client.close()
        return null
    }

    if (!calendarsCursor.moveToFirst()) {
        Log.e("copyExternalCalendar", "Unable to find Calendar with ID ${calendar.id} and name \"${calendar.name}\" in Content Provider")
        return null
    }

    // Get events before writing calendar in case there is an error
    val eventsCursor = this.context.getCursor<CopyEventsProjection>(
        CalendarContract.Events.CONTENT_URI,
        "(${CalendarContract.Events.CALENDAR_ID} = ?)", arrayOf(calendar.id.toString())
    ) ?: run {
        Log.e("copyExternalCalendar", "Error getting Events for Calendar with ID ${calendar.id} and name \"${calendar.name}\" in Content Provider")
        return null
    }

    // -- COPY CALENDAR
    // Load the data about this calendar to memory
    val data = calendarsCursor.loadRowData<CopyCalendarsProjection>().apply {
        this.remove(CopyCalendarsProjection.ID.column)
        this.put(CopyCalendarsProjection.DISPLAY_NAME.column, calendar.name)
        this.put(CopyCalendarsProjection.COLOR.column, calendar.color.toColor().toArgb())
        this.put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
        this.put(CalendarContract.Calendars.ACCOUNT_NAME, accountName)
        this.put(CalendarContract.Calendars.OWNER_ACCOUNT, accountName)
        // The calendar starts as not synced, then the user can choose whether to sync it or not.
        this.put(CalendarContract.Calendars.SYNC_EVENTS, 0)
        // Store which (if any) calendar this one was created from so that calendar can't be imported again.
        this.put(IMPORTED_FROM_COLUMN, calendarsCursor.getLong(CopyCalendarsProjection.ID.ordinal))
        // this.put(CalendarContract.Calendars._SYNC_ID, ???)
    }
    // Write the calendar data to the content provider
    val newCalId = client.insert(CalendarContract.Calendars.CONTENT_URI.asSyncAdapter(accountName), data)
        ?.let { uri -> ContentUris.parseId(uri) }
        ?: run {
            Log.e("copyExternalCalendar", "Failed to insert Calendar from copied data.")
            return null
        }

    while (eventsCursor.moveToNext()) {
        val oldEventId = eventsCursor.getString(CopyCalendarsProjection.ID.ordinal)
        // -- COPY EVENTS from the copied Calendar
        val eventId = client.insert(
            CalendarContract.Events.CONTENT_URI.asSyncAdapter(accountName),
            eventsCursor.loadRowData<CopyEventsProjection>().apply {
                this.remove(CopyEventsProjection.ID.column)
                this.put(CalendarContract.Events.CALENDAR_ID, newCalId)
                // TODO: If event is an exception, find the ID of the copied event for which this will become an exception, and write it to ORIGINAL_ID
            }
        ) ?.let { uri -> ContentUris.parseId(uri) }
        if (eventId == null) {
            Log.e("copyExternalCalendar", "Failed to insert Events from copied data.")
            continue
        }

        // -- COPY REMINDERS from the copied Event
        this.context.getCursor<CopyRemindersProjection>(
            CalendarContract.Reminders.CONTENT_URI,
            "(${CalendarContract.Reminders.EVENT_ID} = ?)", arrayOf(oldEventId.toString())
        )?.use { remindersCursor ->
            while (remindersCursor.moveToNext())
                client.insert(
                    CalendarContract.Reminders.CONTENT_URI.asSyncAdapter(accountName),
                    remindersCursor.loadRowData<CopyRemindersProjection>().apply {
                        this.put(CalendarContract.Reminders.EVENT_ID, eventId)
                    }
                )
        } ?: continue

        // -- COPY ATTENDEES from the copied Event
        this.context.getCursor<CopyAttendeesProjection>(
            CalendarContract.Attendees.CONTENT_URI,
            "(${CalendarContract.Attendees.EVENT_ID} = ?)", arrayOf(oldEventId.toString())
        )?.use { attendeesCursor ->
            while (attendeesCursor.moveToNext())
                client.insert(
                    CalendarContract.Attendees.CONTENT_URI.asSyncAdapter(accountName),
                    attendeesCursor.loadRowData<CopyAttendeesProjection>().apply {
                        this.put(CalendarContract.Attendees.EVENT_ID, eventId)
                    }
                )
        } ?: continue
    }

    eventsCursor.close()
    calendarsCursor.close()
    client.close()
    return InternalUserCalendar(
        id = newCalId,
        name = calendar.name,
        accountName = accountName,
        color = calendar.color,
        sync = false,
        importedFrom = data.getAsLong(IMPORTED_FROM_COLUMN)
    )
}

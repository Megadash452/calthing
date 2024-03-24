package me.marti.calprovexample.calendar

import android.accounts.AccountManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.core.database.getStringOrNull
import androidx.loader.content.CursorLoader
import me.marti.calprovexample.R
import kotlin.concurrent.thread

const val ACCOUNT_NAME = "myuser"

internal fun Context.getCursor(uri: Uri, projection: QueryProjection, selection: String = "", selectionArgs: Array<String> = arrayOf(), sort: String = ""): Cursor? {
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
        Log.e("getCursor", "Exception occurred while loading calendar query cursor:\n$e")
        null
    }
}

internal fun Context.asSyncAdapter(uri: Uri): Uri {
    val accountType = this.getString(R.string.account_type)

    // For changes made as SyncAdapter, an account must be created in the device with this accountType.
    val manager = AccountManager.get(this)
    manager.getAccountsByType(accountType).let { accounts ->
        if (accounts.isEmpty())
            // Create new account if there isn't one already
            manager.addAccount(accountType, null, null, null, null, { result ->
                thread(start = true) {
                    result.result
                    Log.d(null, "Added account for '$accountType'")
                }
            }, null)
        // Expected behavior is for only 1 account to exist.
        // If multiple accounts exist something went wrong
        else
            throw Exception("Multiple accounts of '$accountType' were found when using asSyncAdapter().")
    }

    return uri.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, accountType)
        .build()
}

// This is used for showing debug data about calendars in the app
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
                    map[cursor.getColumnName(i)] = cursor.getStringOrNull(i) ?: ""
                return map
            }
        }
    }
}

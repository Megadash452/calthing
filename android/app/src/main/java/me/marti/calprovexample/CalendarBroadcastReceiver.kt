package me.marti.calprovexample

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CalendarBroadcastReceiver : BroadcastReceiver() {
    /** This is the entrypoint for when the content of the System calendar changes and needs to be synced. */
    override fun onReceive(context: Context, intent: Intent) {
        println("Calendar provider has broadcasted changes")
        // TODO("Not yet implemented. use goAsync() to write to sync files")
    }
}
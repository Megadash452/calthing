package me.marti.calprovexample

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.core.net.toUri

class CalendarBroadcastReceiver : BroadcastReceiver() {
    /** This is the entrypoint for when the content of the System calendar changes and needs to be synced. */
    override fun onReceive(context: Context, intent: Intent) {
        val pending = this.goAsync()

        println("Calendar provider has broadcasted changes")
        val preferences = context.getAppPreferences()

        val syncDir = StringLikeUserPreference(PreferenceKey.SYNC_DIR_URI) { uri -> uri.toUri() }
        val syncedCals = SetUserPreference(PreferenceKey.SYNCED_CALS) { id -> id.toInt() }
        val fragmentCals = BooleanUserPreference(PreferenceKey.FRAGMENT_CALS)
        syncDir.initStore(preferences)
        syncedCals.initStore(preferences)
        fragmentCals.initStore(preferences)

        println("User preferences:")
        println("\t${syncDir.value}")
        println("\t${syncedCals.getSet()}")
        println("\t${fragmentCals.value}")

        println("extras: ${intent.extras}")
        println("data: ${intent.data}")
        // println("extras keySet: ${intent.extras?.)}")
        for(key in intent.extras!!.keySet()){
            println("\t${key}: ${intent.extras!!.get(key)}")
        }

        pending.finish()
        // TODO("Not yet implemented. use goAsync() to write to sync files")
    }
}
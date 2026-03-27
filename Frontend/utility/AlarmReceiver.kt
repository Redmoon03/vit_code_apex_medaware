package deo.raghav.medaware.utility

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import deo.raghav.medaware.screens.AlarmActivity

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d("ALARM_SYSTEM", "Receiver triggered!")
        val ctx = context ?: return

        // 1. Extract data with fallback defaults
        val reminderId = intent?.getIntExtra("REMINDER_ID", -1) ?: -1
        val hour = intent?.getIntExtra("ALARM_HOUR", -1) ?: -1
        val minute = intent?.getIntExtra("ALARM_MINUTE", -1) ?: -1
        val mname = intent?.getStringExtra("MNAME") ?: "Medicine"
//        val mname = intent?.getStringExtra("MNAME") ?: ""

        if (reminderId == -1) return // Safety exit

        // 2. Schedule the next day's alarm IMMEDIATELY
        // We do this first so even if the Activity fails to launch,
        // the "chain" isn't broken for tomorrow.
        if (hour != -1 && minute != -1) {
//            Utilities.scheduleAlarm(ctx, mname, reminderId, hour, minute, isReschedule = true)
            Utilities.scheduleAlarm(ctx, mname, reminderId, hour, minute, isReschedule = true)
        }

        // 3. Attempt to launch the Activity
        val activityIntent = Intent(ctx, AlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("REMINDER_ID", reminderId)
            putExtra("MNAME", mname)
//            putExtra("MNAME", mname)
        }

        try {
            ctx.startActivity(activityIntent)
        } catch (e: Exception) {
            // If the activity launch is blocked, the user sees nothing.
            // This is why a Backup Notification is highly recommended!
            Log.e("AlarmReceiver", "Failed to start activity: ${e.message}")
        }
    }
}
package deo.raghav.medaware.utility

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.util.Calendar

object Utilities {

    fun checkOverlayPermission(context: Context) {
        if (!Settings.canDrawOverlays(context)) {
            // The permission is NOT granted, send user to Settings
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            // Note: From a Fragment/Activity, use startActivityForResult
            // if you want to know when they come back.
            context.startActivity(intent)
        } else {
            // Permission is already granted!
            // You can now start your Activity directly from the Receiver.
        }
    }

    //fun scheduleAlarm(context: Context, mname: String, reminderId: Int, hour: Int, minute: Int, isReschedule: Boolean = false) {
    fun scheduleAlarm(context: Context, mname: String, reminderId: Int, hour: Int, minute: Int, isReschedule: Boolean = false) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Check if we have permission (Only relevant for API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                // REDIRECT USER TO SETTINGS
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
                return // Stop here until permission is granted
            }
        }

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            if (isReschedule) {
                // Force it to tomorrow because we know today's alarm just fired
                add(Calendar.DAY_OF_YEAR, 1)
            } else if (before(Calendar.getInstance())) {
                // Initial setup logic: if user picks 10:00 AM and it's currently 11:00 AM,
                // set for tomorrow.
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("REMINDER_ID", reminderId)
            putExtra("ALARM_HOUR", hour)   // Pass these so we can reschedule
            putExtra("ALARM_MINUTE", minute)
            putExtra("MNAME", mname)
//            putExtra("MNAME", mname)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        android.util.Log.d("ALARM_CONFIRM", "Alarm ID $reminderId set for: ${calendar.time}")

        // Replace alarmManager.setExactAndAllowWhileIdle(...) with this:
        val alarmClockInfo = AlarmManager.AlarmClockInfo(calendar.timeInMillis, pendingIntent)
        alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
    }
}
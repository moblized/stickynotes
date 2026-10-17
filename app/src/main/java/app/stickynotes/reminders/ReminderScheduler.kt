package app.stickynotes.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/** Sets and cancels one alarm per note. Alarms are re-set after reboot by BootReceiver. */
class ReminderScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    fun schedule(noteId: Long, atMillis: Long) {
        val pending = pendingIntent(noteId, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        try {
            if (exactAllowed) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        }
    }

    fun cancel(noteId: Long) {
        val pending = pendingIntent(noteId, PendingIntent.FLAG_NO_CREATE) ?: return
        alarmManager.cancel(pending)
        pending.cancel()
    }

    private fun pendingIntent(noteId: Long, flag: Int): PendingIntent? {
        val intent = Intent(appContext, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_REMIND)
            .putExtra(ReminderReceiver.EXTRA_NOTE_ID, noteId)
        return PendingIntent.getBroadcast(
            appContext,
            requestCode(noteId),
            intent,
            flag or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        fun requestCode(noteId: Long): Int = (noteId xor (noteId ushr 32)).toInt()
    }
}

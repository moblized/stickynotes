package app.stickynotes.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.stickynotes.MainActivity
import app.stickynotes.R
import app.stickynotes.StickyApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Shows the notification when a reminder is due. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMIND) return
        val noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
        if (noteId < 0) return
        val repo = (context.applicationContext as StickyApp).container.repository
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val note = repo.get(noteId)
                if (note != null && note.deletedAt == null && note.reminderAt != null) {
                    val title = note.title.ifBlank { "Reminder" }
                    val text = when {
                        note.isChecklist -> note.items.filter { !it.checked && it.text.isNotBlank() }
                            .joinToString("\n") { "☐ ${it.text}" }
                        else -> note.body
                    }.trim()
                    Notifications.showReminder(context, noteId, title, text)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMIND = "app.stickynotes.action.REMIND"
        const val EXTRA_NOTE_ID = "note_id"
    }
}

/** Alarms are cleared on reboot and on app update; this sets them again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val repo = (context.applicationContext as StickyApp).container.repository
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                repo.rescheduleReminders()
            } finally {
                pending.finish()
            }
        }
    }
}

object Notifications {
    const val CHANNEL_ID = "reminders"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.reminder_channel_description)
            enableVibration(true)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun showReminder(context: Context, noteId: Long, title: String, text: String) {
        if (!canNotify(context)) return
        val open = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_NOTE)
            .putExtra(MainActivity.EXTRA_NOTE_ID, noteId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val contentIntent = PendingIntent.getActivity(
            context,
            ReminderScheduler.requestCode(noteId),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text.lineSequence().firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(ReminderScheduler.requestCode(noteId), notification)
        } catch (e: SecurityException) {
            // Permission was revoked between the check and the call.
        }
    }
}

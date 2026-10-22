package app.stickynotes.spen

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings as AndroidSettings
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import app.stickynotes.MainActivity
import app.stickynotes.StickyApp

/**
 * The S Pen helper. Two jobs, each switchable in Settings:
 *  1. Samsung announces the pen going in or out with a broadcast; when it comes out, open a new note.
 *  2. Optionally, when Samsung Notes comes to the front, back out of it and open a new sticky note instead.
 *
 * It is an accessibility service only because Android lets those open an app from the background.
 * It listens for window changes (which app is in front) and never reads screen content.
 */
class PenHelperService : AccessibilityService() {

    private val settings get() = (application as StickyApp).container.settings
    private val handler = Handler(Looper.getMainLooper())
    private var receiverRegistered = false
    private var foregroundPackage: String? = null
    private var lastLaunch = 0L

    private val penReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_PEN_INSERT) return
            val inserted = intent.getBooleanExtra(EXTRA_PEN_INSERT, true)
            settings.recordPenEvent(removed = !inserted)
            if (!inserted && settings.openOnPenRemoved.value) openNewNote()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                this,
                penReceiver,
                IntentFilter(ACTION_PEN_INSERT),
                ContextCompat.RECEIVER_EXPORTED,
            )
            receiverRegistered = true
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg in IGNORED_PACKAGES) return
        val previous = foregroundPackage
        foregroundPackage = pkg
        if (pkg == SAMSUNG_NOTES && previous != SAMSUNG_NOTES && settings.replaceSamsungNotes.value) {
            // Leave Samsung Notes (this also drops an empty "create note" page), then open ours.
            performGlobalAction(GLOBAL_ACTION_BACK)
            handler.postDelayed({ openNewNote() }, 250)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (receiverRegistered) {
            runCatching { unregisterReceiver(penReceiver) }
            receiverRegistered = false
        }
        super.onDestroy()
    }

    private fun openNewNote() {
        // Locked phone: Samsung's screen-off memo takes over, and our screen would be hidden anyway.
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard?.isKeyguardLocked == true) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastLaunch < 1500) return
        lastLaunch = now
        // PEN_NOTE rather than NEW_NOTE: if a note is already open in Sticky Notes, it stays open.
        val intent = Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ACTION_PEN_NOTE)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        runCatching { startActivity(intent) }
    }

    companion object {
        /** Samsung's S Pen broadcast; extra "penInsert" is false when the pen comes out. */
        const val ACTION_PEN_INSERT = "com.samsung.pen.INSERT"
        const val EXTRA_PEN_INSERT = "penInsert"
        const val SAMSUNG_NOTES = "com.samsung.android.app.notes"

        /** Keyboards and system overlays that briefly take focus without being "the app in front". */
        private val IGNORED_PACKAGES = setOf(
            "com.android.systemui",
            "com.samsung.android.honeyboard",
            "com.google.android.inputmethod.latin",
            "com.samsung.android.service.aircommand",
        )

        /** True when the user has switched the helper on in Accessibility settings. */
        fun isEnabled(context: Context): Boolean {
            val enabled = AndroidSettings.Secure.getString(
                context.contentResolver,
                AndroidSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val me = ComponentName(context, PenHelperService::class.java)
            return enabled.split(':').any { entry ->
                ComponentName.unflattenFromString(entry) == me
            }
        }
    }
}

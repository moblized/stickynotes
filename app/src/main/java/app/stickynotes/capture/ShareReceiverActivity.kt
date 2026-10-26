package app.stickynotes.capture

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import app.stickynotes.MainActivity
import app.stickynotes.StickyApp
import app.stickynotes.data.TextToNote
import kotlinx.coroutines.launch

/**
 * Turns things sent from other apps into sticky notes:
 *  - Share: text (e.g. a Gemini answer) and/or images. Opens the new note.
 *  - Text selection menu "Add to Sticky Notes": saves quietly and stays in the other app.
 */
class ShareReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            finish()
            return
        }
        val incoming = intent
        val action = incoming.action

        var subject: String? = null
        var text: String? = null
        val images = mutableListOf<Uri>()
        when (action) {
            Intent.ACTION_SEND -> {
                subject = incoming.getStringExtra(Intent.EXTRA_SUBJECT) ?: incoming.getStringExtra(Intent.EXTRA_TITLE)
                text = incoming.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                if (incoming.type?.startsWith("image/") == true) {
                    IntentCompat.getParcelableExtra(incoming, Intent.EXTRA_STREAM, Uri::class.java)?.let { images += it }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                text = incoming.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                IntentCompat.getParcelableArrayListExtra(incoming, Intent.EXTRA_STREAM, Uri::class.java)
                    ?.let { images += it }
            }
            Intent.ACTION_PROCESS_TEXT -> {
                text = incoming.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            }
        }

        val draft = TextToNote.fromShared(subject, text)
        if (draft.isEmpty && images.isEmpty()) {
            Toast.makeText(applicationContext, "Nothing to add", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val repo = (application as StickyApp).container.repository
        lifecycleScope.launch {
            val id = repo.createFrom(draft, images)
            if (action == Intent.ACTION_PROCESS_TEXT) {
                Toast.makeText(applicationContext, "Added to Sticky Notes", Toast.LENGTH_SHORT).show()
            } else {
                startActivity(
                    Intent(this@ShareReceiverActivity, MainActivity::class.java)
                        .setAction(MainActivity.ACTION_OPEN_NOTE)
                        .putExtra(MainActivity.EXTRA_NOTE_ID, id)
                        .addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP,
                        ),
                )
            }
            finish()
        }
    }
}

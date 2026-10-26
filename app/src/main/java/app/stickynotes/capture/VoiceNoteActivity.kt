package app.stickynotes.capture

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import app.stickynotes.MainActivity
import app.stickynotes.StickyApp
import app.stickynotes.data.TextToNote
import kotlinx.coroutines.launch

/**
 * "Sticky voice note": listens once, saves what you said as a sticky note, and closes.
 * Say "Hey Google, open Sticky voice note" for hands-free notes. Starting with
 * "shopping list" or "to-do list" makes a checklist.
 */
class VoiceNoteActivity : ComponentActivity() {

    private val listen = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()
        if (result.resultCode == RESULT_OK && !spoken.isNullOrEmpty()) save(spoken) else finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) startListening()
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say your note")
        try {
            listen.launch(intent)
        } catch (e: ActivityNotFoundException) {
            // No speech recognizer on this phone: open a blank note so the keyboard mic can be used.
            Toast.makeText(applicationContext, "Voice input isn't available, opening a blank note", Toast.LENGTH_SHORT).show()
            startActivity(
                Intent(this, MainActivity::class.java)
                    .setAction(MainActivity.ACTION_NEW_NOTE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
            finish()
        }
    }

    private fun save(spoken: String) {
        val repo = (application as StickyApp).container.repository
        lifecycleScope.launch {
            val draft = TextToNote.fromSpeech(spoken)
            if (draft.isEmpty) {
                finish()
                return@launch
            }
            repo.createFrom(draft)
            val what = if (draft.items.isNotEmpty()) "List" else "Note"
            Toast.makeText(applicationContext, "$what saved to Sticky Notes", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}

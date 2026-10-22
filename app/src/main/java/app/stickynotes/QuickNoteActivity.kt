package app.stickynotes

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * The "Quick note" icon: opens Sticky Notes straight into a blank note, then gets out of the way.
 * Has no screen of its own, so it can be added to Air command or a home screen for one-tap notes.
 */
class QuickNoteActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(this, MainActivity::class.java)
                .setAction(MainActivity.ACTION_NEW_NOTE)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                ),
        )
        finish()
    }
}

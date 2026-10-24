package app.stickynotes

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import app.stickynotes.ui.AppRoot
import app.stickynotes.ui.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by lazy { ViewModelProvider(this)[MainViewModel::class.java] }

    /** Whether the app is showing its dark theme, so the home-screen icon can match. */
    private var darkTheme: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            AppRoot(
                vm = viewModel,
                onDarkThemeChanged = { dark ->
                    darkTheme = dark
                    applySystemBars(dark)
                },
            )
        }
    }

    override fun onStop() {
        super.onStop()
        // Switch to the dark (or light) app icon once the app is out of sight.
        if (!isChangingConfigurations) darkTheme?.let { AppIcon.sync(this, it) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_OPEN_NOTE -> {
                val id = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
                if (id >= 0) viewModel.openFromNotification(id)
            }
            ACTION_NEW_NOTE -> viewModel.newNoteFromShortcut(isChecklist = false)
            ACTION_NEW_LIST -> viewModel.newNoteFromShortcut(isChecklist = true)
            ACTION_PEN_NOTE -> viewModel.newNoteFromPen()
        }
    }

    private fun applySystemBars(dark: Boolean) {
        val style = if (dark) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    companion object {
        const val ACTION_OPEN_NOTE = "app.stickynotes.action.OPEN_NOTE"
        const val ACTION_NEW_NOTE = "app.stickynotes.action.NEW_NOTE"
        const val ACTION_NEW_LIST = "app.stickynotes.action.NEW_LIST"
        const val ACTION_PEN_NOTE = "app.stickynotes.action.PEN_NOTE"
        const val EXTRA_NOTE_ID = "note_id"
    }
}

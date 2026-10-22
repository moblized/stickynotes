package app.stickynotes.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String) { SYSTEM("Follow system"), LIGHT("Light"), DARK("Dark") }

enum class NotesLayout { GRID, LIST }

/** The last time the phone said the S Pen came out or went back in. */
data class PenEvent(val at: Long, val removed: Boolean)

/** App settings, stored in SharedPreferences and exposed as StateFlows. */
class Settings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(readEnum(KEY_THEME, ThemeMode.SYSTEM))
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    /** Name of a NoteFont entry. Kept as a string so the data layer doesn't depend on UI resources. */
    private val _font = MutableStateFlow(prefs.getString(KEY_FONT, null) ?: DEFAULT_FONT)
    val font: StateFlow<String> = _font.asStateFlow()

    private val _undoSeconds = MutableStateFlow(prefs.getInt(KEY_UNDO, DEFAULT_UNDO_SECONDS))
    val undoSeconds: StateFlow<Int> = _undoSeconds.asStateFlow()

    private val _layout = MutableStateFlow(readEnum(KEY_LAYOUT, NotesLayout.GRID))
    val layout: StateFlow<NotesLayout> = _layout.asStateFlow()

    /** True-black background whenever the dark theme is showing (OLED friendly). */
    private val _pureBlack = MutableStateFlow(prefs.getBoolean(KEY_PURE_BLACK, false))
    val pureBlack: StateFlow<Boolean> = _pureBlack.asStateFlow()

    /** Open a new note when the S Pen is pulled out (needs the S Pen helper switched on). */
    private val _openOnPenRemoved = MutableStateFlow(prefs.getBoolean(KEY_PEN_OPEN, true))
    val openOnPenRemoved: StateFlow<Boolean> = _openOnPenRemoved.asStateFlow()

    /** Whenever Samsung Notes opens, close it and open a new sticky note instead. */
    private val _replaceSamsungNotes = MutableStateFlow(prefs.getBoolean(KEY_REPLACE_SAMSUNG_NOTES, false))
    val replaceSamsungNotes: StateFlow<Boolean> = _replaceSamsungNotes.asStateFlow()

    private val _lastPenEvent = MutableStateFlow(
        prefs.getLong(KEY_PEN_EVENT_AT, 0L).takeIf { it > 0 }?.let {
            PenEvent(it, prefs.getBoolean(KEY_PEN_EVENT_REMOVED, true))
        },
    )
    val lastPenEvent: StateFlow<PenEvent?> = _lastPenEvent.asStateFlow()

    fun setOpenOnPenRemoved(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PEN_OPEN, enabled).apply()
        _openOnPenRemoved.value = enabled
    }

    fun setReplaceSamsungNotes(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REPLACE_SAMSUNG_NOTES, enabled).apply()
        _replaceSamsungNotes.value = enabled
    }

    fun recordPenEvent(removed: Boolean) {
        val event = PenEvent(System.currentTimeMillis(), removed)
        prefs.edit().putLong(KEY_PEN_EVENT_AT, event.at).putBoolean(KEY_PEN_EVENT_REMOVED, removed).apply()
        _lastPenEvent.value = event
    }

    fun setTheme(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _theme.value = mode
    }

    fun setFont(name: String) {
        prefs.edit().putString(KEY_FONT, name).apply()
        _font.value = name
    }

    fun setUndoSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(MIN_UNDO_SECONDS, MAX_UNDO_SECONDS)
        prefs.edit().putInt(KEY_UNDO, clamped).apply()
        _undoSeconds.value = clamped
    }

    fun setPureBlack(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PURE_BLACK, enabled).apply()
        _pureBlack.value = enabled
    }

    fun setLayout(layout: NotesLayout) {
        prefs.edit().putString(KEY_LAYOUT, layout.name).apply()
        _layout.value = layout
    }

    private inline fun <reified T : Enum<T>> readEnum(key: String, default: T): T {
        val saved = prefs.getString(key, null)
        return enumValues<T>().firstOrNull { it.name == saved } ?: default
    }

    companion object {
        const val DEFAULT_FONT = "CAVEAT"
        const val DEFAULT_UNDO_SECONDS = 5
        const val MIN_UNDO_SECONDS = 2
        const val MAX_UNDO_SECONDS = 15
        private const val KEY_THEME = "theme"
        private const val KEY_FONT = "font"
        private const val KEY_UNDO = "undo_seconds"
        private const val KEY_LAYOUT = "layout"
        private const val KEY_PURE_BLACK = "pure_black"
        private const val KEY_PEN_OPEN = "pen_open_on_remove"
        private const val KEY_REPLACE_SAMSUNG_NOTES = "replace_samsung_notes"
        private const val KEY_PEN_EVENT_AT = "pen_event_at"
        private const val KEY_PEN_EVENT_REMOVED = "pen_event_removed"
    }
}

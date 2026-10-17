package app.stickynotes.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import app.stickynotes.data.Note

/** Shares a note as plain text: title, body, then ☐ / ☑ checklist lines. */
object NoteSharer {

    fun buildText(note: Note): String = buildString {
        if (note.title.isNotBlank()) appendLine(note.title.trim())
        if (!note.isChecklist && note.body.isNotBlank()) {
            if (isNotEmpty()) appendLine()
            appendLine(note.body.trim())
        }
        val items = if (note.isChecklist) note.items.filter { it.text.isNotBlank() } else emptyList()
        if (items.isNotEmpty()) {
            if (isNotEmpty()) appendLine()
            items.forEach { appendLine((if (it.checked) "☑ " else "☐ ") + it.text.trim()) }
        }
    }.trimEnd()

    /** Returns false when there's nothing to share. */
    fun share(context: Context, note: Note): Boolean {
        val text = buildText(note)
        if (text.isBlank()) return false
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            if (note.title.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, note.title.trim())
        }
        val chooser = Intent.createChooser(send, null)
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
        return true
    }
}

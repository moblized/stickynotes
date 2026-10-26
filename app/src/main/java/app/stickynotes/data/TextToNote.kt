package app.stickynotes.data

/** What spoken or shared text turns into. A non-empty [items] means a checklist. */
data class NoteDraft(
    val title: String = "",
    val body: String = "",
    val items: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = title.isBlank() && body.isBlank() && items.none { it.isNotBlank() }
}

/** Turns dictated speech or shared text (for example a Gemini answer) into a note or checklist. */
object TextToNote {

    /** Spoken phrases that start a checklist, and the title they give it. */
    private val listStarts = listOf(
        "shopping list" to "Shopping list",
        "grocery list" to "Grocery list",
        "groceries" to "Groceries",
        "to do list" to "To-do list",
        "to-do list" to "To-do list",
        "todo list" to "To-do list",
        "packing list" to "Packing list",
        "checklist" to "",
        "make a list" to "",
        "new list" to "",
    )

    /** Spoken phrases that just mean "this is a note"; they're dropped. */
    private val noteStarts = listOf("take a note", "note to self", "new note", "make a note", "remember to", "note")

    /** "- ", "* ", "• ", "1. ", "2) ", optionally followed by "[ ]" / "[x]", or a bare "[ ]". */
    private val bullet = Regex("""^\s*(?:(?:[-*•▪◦]|\d+[.)])\s+(?:\[[ xX]?]\s+)?|\[[ xX]?]\s+)""")

    /** "Shopping list milk, eggs and bread" -> checklist "Shopping list": Milk / Eggs / Bread. */
    fun fromSpeech(raw: String): NoteDraft {
        val text = raw.trim()
        if (text.isEmpty()) return NoteDraft()
        val lower = text.lowercase()

        listStarts.firstOrNull { (phrase, _) -> startsWithWord(lower, phrase) }?.let { (phrase, title) ->
            val rest = text.substring(phrase.length).trimStart(' ', ':', ',', '-', '.')
            val items = rest
                .split(Regex("""\s*[,;]\s*(?:and\s+|then\s+)?|\s+and\s+|\s+then\s+""", RegexOption.IGNORE_CASE))
                .map { it.trim().trimEnd('.') }
                .filter { it.isNotEmpty() }
                .map { it.capitalizeFirst() }
            return NoteDraft(title = title, items = items)
        }

        val stripped = noteStarts.firstOrNull { startsWithWord(lower, it) }?.let { phrase ->
            // "remember to call mom" keeps "call mom"; "take a note buy stamps" keeps "buy stamps".
            text.substring(phrase.length).trimStart(' ', ':', ',', '-', '.')
        } ?: text
        return NoteDraft(body = stripped.ifBlank { text }.capitalizeFirst())
    }

    /**
     * Shared text: bulleted or numbered lines become checklist items, with any line before them
     * as the title. Anything else stays a plain note. [subject] becomes the title when given.
     */
    fun fromShared(subject: String?, raw: String?): NoteDraft {
        val text = raw.orEmpty().replace("\r\n", "\n").trim()
        val title = subject?.trim().orEmpty()
        if (text.isEmpty()) return NoteDraft(title = title)

        val lines = text.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
        val bulletLines = lines.filter { bullet.containsMatchIn(it) }
        if (bulletLines.size >= 2 && bulletLines.size >= lines.size - 1) {
            val heading = lines.firstOrNull { !bullet.containsMatchIn(it) }
                ?.trim()
                ?.trim('*', '#', ' ', ':')
                ?.takeIf { it.length <= 80 }
            val items = bulletLines.map { line ->
                line.replace(bullet, "").replace("**", "").trim()
            }.filter { it.isNotEmpty() }
            return NoteDraft(title = title.ifBlank { heading.orEmpty() }, items = items)
        }
        return NoteDraft(title = title, body = text)
    }

    private fun startsWithWord(text: String, phrase: String): Boolean =
        text == phrase || (text.startsWith(phrase) && !text[phrase.length].isLetterOrDigit())

    private fun String.capitalizeFirst(): String = replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

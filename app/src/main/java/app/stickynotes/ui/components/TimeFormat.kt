package app.stickynotes.ui.components

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
private val sameYearDate = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
private val otherYearDate = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

/** "Today, 6:00 PM", "Tomorrow, 8:00 AM", "Oct 21, 8:00 AM". */
fun formatReminder(millis: Long): String {
    val zone = ZoneId.systemDefault()
    val dt = Instant.ofEpochMilli(millis).atZone(zone)
    val today = LocalDate.now(zone)
    val day = when (dt.toLocalDate()) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> dt.format(if (dt.year == today.year) sameYearDate else otherYearDate)
    }
    return "$day, ${dt.format(timeFormat)}"
}

/** "Edited 3:45 PM" today, "Edited Oct 12" otherwise. */
fun formatEdited(millis: Long): String {
    val zone = ZoneId.systemDefault()
    val dt = Instant.ofEpochMilli(millis).atZone(zone)
    val today = LocalDate.now(zone)
    val text = when {
        dt.toLocalDate() == today -> dt.format(timeFormat)
        dt.year == today.year -> dt.format(sameYearDate)
        else -> dt.format(otherYearDate)
    }
    return "Edited $text"
}

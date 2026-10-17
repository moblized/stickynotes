package app.stickynotes.ui.editor

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.stickynotes.ui.components.formatReminder
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/** Quick picks (later today, tomorrow, next week) or a custom date and time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderDialog(
    current: Long?,
    onSet: (Long) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }
    val zone = ZoneId.systemDefault()

    when (step) {
        0 -> {
            val presets = remember { quickPicks(zone) }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Remind me") },
                text = {
                    Column {
                        presets.forEach { (label, at) ->
                            OptionRow(Icons.Outlined.Schedule, label, formatReminder(at)) { onSet(at) }
                        }
                        OptionRow(Icons.Outlined.Event, "Pick date & time", null) { step = 1 }
                    }
                },
                confirmButton = {
                    if (current != null) {
                        TextButton(onClick = onRemove) { Text("Remove reminder") }
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                },
            )
        }

        1 -> {
            val todayUtc = LocalDate.now(zone).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            val initial = current?.let {
                Instant.ofEpochMilli(it).atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            }?.coerceAtLeast(todayUtc) ?: todayUtc
            val dateState = rememberDatePickerState(
                initialSelectedDateMillis = initial,
                selectableDates = object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= todayUtc
                },
            )
            DatePickerDialog(
                onDismissRequest = onDismiss,
                confirmButton = {
                    TextButton(
                        onClick = {
                            dateState.selectedDateMillis?.let {
                                pickedDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                                step = 2
                            }
                        },
                        enabled = dateState.selectedDateMillis != null,
                    ) { Text("Next") }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                },
            ) {
                DatePicker(state = dateState)
            }
        }

        else -> {
            val start = current?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() }
                ?: LocalTime.now(zone).plusHours(1).withMinute(0)
            val timeState = rememberTimePickerState(
                initialHour = start.hour,
                initialMinute = start.minute,
                is24Hour = DateFormat.is24HourFormat(context),
            )
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Pick a time") },
                text = {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        TimePicker(state = timeState)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val date = pickedDate ?: LocalDate.now(zone)
                        val at = LocalDateTime.of(date, LocalTime.of(timeState.hour, timeState.minute))
                            .atZone(zone)
                            .toInstant()
                            .toEpochMilli()
                        onSet(at)
                    }) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { step = 1 }) { Text("Back") }
                },
            )
        }
    }
}

private fun quickPicks(zone: ZoneId): List<Pair<String, Long>> {
    val now = ZonedDateTime.now(zone)
    val today = now.toLocalDate()
    val picks = mutableListOf<Pair<String, Long>>()
    if (now.hour < 17) {
        picks += "Later today" to today.atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
    }
    picks += "Tomorrow morning" to today.plusDays(1).atTime(8, 0).atZone(zone).toInstant().toEpochMilli()
    val monday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
    picks += "Next week" to monday.atTime(8, 0).atZone(zone).toInstant().toEpochMilli()
    return picks
}

@Composable
private fun OptionRow(icon: ImageVector, label: String, detail: String?, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (detail != null) {
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

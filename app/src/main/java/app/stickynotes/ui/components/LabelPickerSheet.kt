package app.stickynotes.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.stickynotes.data.NotesRepository
import app.stickynotes.data.LabelEntity

/**
 * Keep's "Label note" picker: type to search, tick labels on or off,
 * or create a new label from what you typed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelPickerSheet(
    allLabels: List<LabelEntity>,
    checkedLabelIds: Set<Long>,
    onCheckedChange: (labelId: Long, checked: Boolean) -> Unit,
    onCreateLabel: (name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by rememberSaveable { mutableStateOf("") }
    val typed = query.trim()
    val shown = remember(allLabels, typed) {
        if (typed.isEmpty()) allLabels else allLabels.filter { it.name.contains(typed, ignoreCase = true) }
    }
    val canCreate = typed.isNotEmpty() && allLabels.none { it.name.equals(typed, ignoreCase = true) }
    val create: () -> Unit = {
        if (canCreate) {
            onCreateLabel(typed)
            query = ""
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = "Label note",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(NotesRepository.MAX_LABEL_LENGTH) },
                placeholder = { Text("Enter label name") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { create() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )

            if (canCreate) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = create)
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = "Create \"$typed\"",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (allLabels.isEmpty() && !canCreate) {
                Text(
                    text = "No labels yet. Type a name to create one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }

            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(shown, key = { it.id }) { label ->
                    val checked = label.id in checkedLabelIds
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = checked,
                                role = Role.Checkbox,
                                onValueChange = { onCheckedChange(label.id, it) },
                            )
                            .padding(horizontal = 24.dp, vertical = 4.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.Label, contentDescription = null)
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = label.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Checkbox(checked = checked, onCheckedChange = null)
                    }
                }
            }
        }
    }
}

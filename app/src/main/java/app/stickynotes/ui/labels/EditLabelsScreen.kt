package app.stickynotes.ui.labels

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.stickynotes.data.NotesRepository
import app.stickynotes.data.LabelEntity
import app.stickynotes.data.LabelResult

/** Create, rename and delete labels. Hook the callbacks straight to KeepViewModel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditLabelsScreen(
    labels: List<LabelEntity>,
    onCreate: (name: String, onResult: (LabelResult) -> Unit) -> Unit,
    onRename: (id: Long, name: String, onResult: (LabelResult) -> Unit) -> Unit,
    onDelete: (LabelEntity) -> Unit,
    onBack: () -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<LabelEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit labels") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding(),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            item(key = "keep_new_label") { NewLabelRow(onCreate) }
            item(key = "keep_divider") { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
            items(labels, key = { it.id }) { label ->
                LabelRow(label = label, onRename = onRename, onDelete = { pendingDelete = label })
            }
        }
    }

    pendingDelete?.let { label ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete label?") },
            text = { Text("\"${label.name}\" will be removed from all notes. The notes themselves stay.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(label)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun NewLabelRow(onCreate: (String, (LabelResult) -> Unit) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val submit: () -> Unit = {
        onCreate(text) { result ->
            error = if (result == LabelResult.DUPLICATE) "That label already exists" else null
            if (result == LabelResult.OK) text = ""
        }
    }
    val err = error

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    ) {
        Icon(
            Icons.Outlined.Add,
            contentDescription = null,
            modifier = Modifier.padding(12.dp),
        )
        TextField(
            value = text,
            onValueChange = {
                text = it.take(NotesRepository.MAX_LABEL_LENGTH)
                error = null
            },
            placeholder = { Text("Create new label") },
            singleLine = true,
            isError = err != null,
            supportingText = if (err != null) { { Text(err) } } else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            colors = transparentFieldColors(),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = submit, enabled = text.isNotBlank()) {
            Icon(Icons.Outlined.Check, contentDescription = "Create label")
        }
    }
}

@Composable
private fun LabelRow(
    label: LabelEntity,
    onRename: (Long, String, (LabelResult) -> Unit) -> Unit,
    onDelete: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var text by remember(label.id, label.name) { mutableStateOf(label.name) }
    var error by remember(label.id) { mutableStateOf<String?>(null) }
    val changed = text.isNotBlank() && text.trim() != label.name
    val save: () -> Unit = {
        onRename(label.id, text) { result ->
            error = if (result == LabelResult.DUPLICATE) "That label already exists" else null
            if (result == LabelResult.BLANK) text = label.name
            if (result == LabelResult.OK) focusManager.clearFocus()
        }
    }
    val err = error

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    ) {
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, contentDescription = "Delete label ${label.name}")
        }
        TextField(
            value = text,
            onValueChange = {
                text = it.take(NotesRepository.MAX_LABEL_LENGTH)
                error = null
            },
            singleLine = true,
            isError = err != null,
            supportingText = if (err != null) { { Text(err) } } else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (changed) save() else focusManager.clearFocus() }),
            colors = transparentFieldColors(),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = save, enabled = changed) {
            Icon(Icons.Outlined.Check, contentDescription = "Save label name")
        }
    }
}

@Composable
private fun transparentFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    errorContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
)

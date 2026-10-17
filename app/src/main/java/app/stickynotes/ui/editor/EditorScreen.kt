package app.stickynotes.ui.editor

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.AddAlert
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.stickynotes.data.ChecklistItem
import app.stickynotes.data.Note
import app.stickynotes.data.NoteImageEntity
import app.stickynotes.reminders.Notifications
import app.stickynotes.ui.MainViewModel
import app.stickynotes.ui.components.ImageViewerDialog
import app.stickynotes.ui.components.LabelChips
import app.stickynotes.ui.components.LabelPickerSheet
import app.stickynotes.ui.components.NoteImagesSection
import app.stickynotes.ui.components.NoteSharer
import app.stickynotes.ui.components.ReminderTag
import app.stickynotes.ui.components.formatEdited
import app.stickynotes.ui.components.rememberImagePicker
import app.stickynotes.ui.theme.LocalDarkTheme
import app.stickynotes.ui.theme.LocalNoteTypography
import app.stickynotes.ui.theme.NoteColor
import app.stickynotes.ui.theme.noteInk
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop

/** Full-screen editor on the note's own color. Saves as you type. */
@Composable
fun EditorScreen(vm: MainViewModel, noteId: Long) {
    val dark = LocalDarkTheme.current
    val loaded by produceState<Note?>(initialValue = null, noteId) {
        val note = vm.loadNote(noteId)
        if (note == null) vm.dismissEditor(noteId) else value = note
    }
    val note = loaded
    if (note == null) {
        val color = vm.notes.value.firstOrNull { it.id == noteId }?.color ?: 0
        Surface(color = NoteColor.of(color).color(dark), modifier = Modifier.fillMaxSize()) {}
        return
    }
    EditorContent(vm, note)
}

@OptIn(FlowPreview::class, ExperimentalMaterial3Api::class)
@Composable
private fun EditorContent(vm: MainViewModel, initial: Note) {
    val context = LocalContext.current
    val dark = LocalDarkTheme.current
    val typo = LocalNoteTypography.current
    val noteId = initial.id

    var draft by remember { mutableStateOf(initial) }
    val isOpen = vm.editorNoteId == noteId

    // Save shortly after typing stops, and whenever the app goes to the background.
    LaunchedEffect(Unit) {
        snapshotFlow { draft }.drop(1).debounce(400).collect { vm.saveDraft(it) }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.saveDraft(draft) }
    BackHandler(enabled = isOpen) { vm.closeEditor(draft) }

    val notes by vm.notes.collectAsStateWithLifecycle()
    val allLabels by vm.labels.collectAsStateWithLifecycle()
    val labelsByNote by vm.labelsByNote.collectAsStateWithLifecycle()
    val imagesByNote by vm.imagesByNote.collectAsStateWithLifecycle()
    val noteLabels = labelsByNote[noteId].orEmpty()
    val noteImages = imagesByNote[noteId].orEmpty()
    val updatedAt = notes.firstOrNull { it.id == noteId }?.updatedAt ?: draft.updatedAt

    var showColors by remember { mutableStateOf(false) }
    var showLabels by remember { mutableStateOf(false) }
    var showReminder by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<NoteImageEntity?>(null) }

    val pickImages = rememberImagePicker { uris -> vm.addImages(noteId, uris) }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            Toast.makeText(context, "Notifications are off, so reminders won't pop up", Toast.LENGTH_LONG).show()
        }
    }

    val paper by animateColorAsState(NoteColor.of(draft.color).color(dark), label = "paper")
    val ink = noteInk(dark)

    val bodyFocus = remember { FocusRequester() }
    var focusItemId by remember { mutableStateOf<Long?>(null) }

    // A brand-new note opens with the cursor ready.
    LaunchedEffect(Unit) {
        if (vm.editorIsNew) {
            delay(220)
            if (draft.isChecklist) {
                focusItemId = draft.items.firstOrNull()?.id
            } else {
                runCatching { bodyFocus.requestFocus() }
            }
        }
    }

    fun focusContent() {
        if (draft.isChecklist) {
            val first = draft.items.firstOrNull { !it.checked }
            if (first != null) {
                focusItemId = first.id
            } else {
                val item = ChecklistItem()
                draft = draft.copy(items = listOf(item) + draft.items)
                focusItemId = item.id
            }
        } else {
            runCatching { bodyFocus.requestFocus() }
        }
    }

    Surface(color = paper, contentColor = ink, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            // ---------------------------------------------------------- Top bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
            ) {
                IconButton(onClick = { vm.closeEditor(draft) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(Modifier.weight(1f))
                if (!draft.isArchived) {
                    IconButton(onClick = { draft = draft.copy(isPinned = !draft.isPinned) }) {
                        Icon(
                            imageVector = if (draft.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = if (draft.isPinned) "Unpin" else "Pin",
                        )
                    }
                }
                IconButton(onClick = { showReminder = true }) {
                    Icon(Icons.Outlined.AddAlert, contentDescription = "Remind me")
                }
                IconButton(onClick = { vm.archiveFromEditor(draft) }) {
                    Icon(
                        imageVector = if (draft.isArchived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                        contentDescription = if (draft.isArchived) "Unarchive" else "Archive",
                    )
                }
            }

            // ---------------------------------------------------------- Content
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                NoteImagesSection(noteImages, vm.imageStore, onOpen = { viewing = it })
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    HandField(
                        value = draft.title,
                        onValueChange = { value ->
                            if (value.contains('\n')) {
                                draft = draft.copy(title = value.replace("\n", ""))
                                focusContent()
                            } else {
                                draft = draft.copy(title = value)
                            }
                        },
                        placeholder = "Title",
                        style = typo.title,
                        imeAction = ImeAction.Next,
                        onImeAction = { focusContent() },
                    )
                    Spacer(Modifier.height(10.dp))
                    if (draft.isChecklist) {
                        ChecklistEditor(
                            items = draft.items,
                            onItemsChange = { draft = draft.copy(items = it) },
                            style = typo.body,
                            paper = paper,
                            focusItemId = focusItemId,
                            onRequestFocus = { focusItemId = it },
                        )
                    } else {
                        HandField(
                            value = draft.body,
                            onValueChange = { draft = draft.copy(body = it) },
                            placeholder = "Note",
                            style = typo.body,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 220.dp)
                                .focusRequester(bodyFocus),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        draft.reminderAt?.let { at ->
                            ReminderTag(at, ink, Modifier.clickable { showReminder = true })
                        }
                        if (noteLabels.isNotEmpty()) {
                            LabelChips(noteLabels, maxShown = Int.MAX_VALUE, onClick = { showLabels = true })
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }

            // ---------------------------------------------------------- Bottom bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 4.dp),
            ) {
                IconButton(onClick = pickImages) {
                    Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = "Add image")
                }
                IconButton(onClick = { showColors = true }) {
                    Icon(Icons.Outlined.Palette, contentDescription = "Color")
                }
                IconButton(onClick = { draft = toggleChecklist(draft) }) {
                    if (draft.isChecklist) {
                        Icon(Icons.AutoMirrored.Outlined.Notes, contentDescription = "Hide checkboxes")
                    } else {
                        Icon(Icons.Outlined.CheckBox, contentDescription = "Show checkboxes")
                    }
                }
                Text(
                    text = formatEdited(updatedAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = ink.copy(alpha = 0.7f),
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                vm.deleteFromEditor(draft)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Make a copy") },
                            leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                vm.copyFromEditor(draft)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Send") },
                            leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                if (!NoteSharer.share(context, draft)) {
                                    Toast.makeText(context, "Nothing to send yet", Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Labels") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Label, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                showLabels = true
                            },
                        )
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------- Sheets and dialogs

    if (showColors) {
        ModalBottomSheet(onDismissRequest = { showColors = false }) {
            Text(
                text = "Color",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 20.dp),
            ) {
                NoteColor.entries.forEach { c ->
                    val selected = draft.color == c.ordinal
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(c.color(dark))
                            .border(
                                width = if (selected) 2.5.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape,
                            )
                            .clickable { draft = draft.copy(color = c.ordinal) },
                    ) {
                        if (selected) Icon(Icons.Outlined.Check, contentDescription = c.label, tint = noteInk(dark))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (showLabels) {
        LabelPickerSheet(
            allLabels = allLabels,
            checkedLabelIds = noteLabels.map { it.id }.toSet(),
            onCheckedChange = { labelId, on -> vm.setNoteLabel(noteId, labelId, on) },
            onCreateLabel = { name -> vm.createLabelOnNote(noteId, name) },
            onDismiss = { showLabels = false },
        )
    }

    if (showReminder) {
        ReminderDialog(
            current = draft.reminderAt,
            onSet = { at ->
                showReminder = false
                if (at <= System.currentTimeMillis()) {
                    Toast.makeText(context, "Pick a time in the future", Toast.LENGTH_SHORT).show()
                } else {
                    draft = draft.copy(reminderAt = at)
                    vm.saveDraft(draft)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifications.canNotify(context)) {
                        askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            },
            onRemove = {
                showReminder = false
                draft = draft.copy(reminderAt = null)
                vm.saveDraft(draft)
            },
            onDismiss = { showReminder = false },
        )
    }

    viewing?.let { image ->
        ImageViewerDialog(
            image = image,
            store = vm.imageStore,
            onDelete = { vm.removeImage(image) },
            onDismiss = { viewing = null },
        )
    }
}

/** Turns body lines into list items, or list items back into lines. */
private fun toggleChecklist(note: Note): Note =
    if (note.isChecklist) {
        note.copy(
            isChecklist = false,
            body = note.items.filter { it.text.isNotBlank() }.joinToString("\n") { it.text },
            items = emptyList(),
        )
    } else {
        val items = note.body.lines()
            .map { it.trim().removePrefix("- ").removePrefix("• ").trim() }
            .filter { it.isNotEmpty() }
            .map { ChecklistItem(text = it) }
        note.copy(isChecklist = true, items = items.ifEmpty { listOf(ChecklistItem()) }, body = "")
    }

/** A borderless text field in the handwriting font, with a faint placeholder. */
@Composable
internal fun HandField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: (() -> Unit)? = null,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        textStyle = style.copy(color = color),
        cursorBrush = SolidColor(color),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(
            onNext = { onImeAction?.invoke() },
            onDone = { onImeAction?.invoke() },
        ),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(placeholder, style = style, color = color.copy(alpha = 0.45f))
                }
                inner()
            }
        },
    )
}

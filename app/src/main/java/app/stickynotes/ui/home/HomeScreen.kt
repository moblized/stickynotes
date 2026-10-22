package app.stickynotes.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridItemScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.stickynotes.data.LabelEntity
import app.stickynotes.data.Note
import app.stickynotes.data.NoteImageEntity
import app.stickynotes.data.NotesLayout
import app.stickynotes.ui.MainViewModel
import app.stickynotes.ui.NoteSection
import app.stickynotes.ui.components.NoteCard
import app.stickynotes.ui.components.SwipeToArchive
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyStaggeredGridState
import sh.calvin.reorderable.rememberReorderableLazyStaggeredGridState

/** Notes, Reminders, Archive and label views: search bar, Pinned/Others grid, new-note buttons. */
@Composable
fun HomeScreen(
    vm: MainViewModel,
    section: NoteSection,
    snackbarHostState: SnackbarHostState,
    onOpenDrawer: () -> Unit,
) {
    val notes by vm.notes.collectAsStateWithLifecycle()
    val hidden by vm.hiddenIds.collectAsStateWithLifecycle()
    val labels by vm.labels.collectAsStateWithLifecycle()
    val labelsByNote by vm.labelsByNote.collectAsStateWithLifecycle()
    val imagesByNote by vm.imagesByNote.collectAsStateWithLifecycle()
    val layout by vm.settings.layout.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val query = vm.searchQuery

    val visible = remember(notes, hidden, section, query, labelsByNote) {
        filterNotes(notes, hidden, section, query, labelsByNote)
    }
    val canReorder = query.isBlank() && (section is NoteSection.Notes || section is NoteSection.Label)
    val showFabs = section is NoteSection.Notes || section is NoteSection.Label

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (showFabs) {
                Column(horizontalAlignment = Alignment.End) {
                    SmallFloatingActionButton(
                        onClick = { vm.newNote(isChecklist = true) },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ) { Icon(Icons.Outlined.CheckBox, contentDescription = "New list") }
                    Spacer(Modifier.height(14.dp))
                    FloatingActionButton(
                        onClick = { vm.newNote(isChecklist = false) },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ) { Icon(Icons.Outlined.Edit, contentDescription = "New note") }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
        ) {
            SearchBar(
                query = query,
                onQueryChange = { vm.searchQuery = it },
                placeholder = when (section) {
                    NoteSection.Archive -> "Search archive"
                    NoteSection.Reminders -> "Search reminders"
                    else -> "Search your notes"
                },
                layout = layout,
                onToggleLayout = vm::toggleLayout,
                onOpenDrawer = onOpenDrawer,
            )
            sectionTitle(section, labels)?.let { title ->
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 4.dp),
                )
            }
            if (loaded && visible.isEmpty()) {
                EmptyState(section, searching = query.isNotBlank())
            } else {
                NotesGrid(
                    vm = vm,
                    notes = visible,
                    sectioned = section is NoteSection.Notes || section is NoteSection.Label,
                    canReorder = canReorder,
                    layout = layout,
                    labelsByNote = labelsByNote,
                    imagesByNote = imagesByNote,
                    bottomPadding = padding.calculateBottomPadding(),
                )
            }
        }
    }
}

private fun filterNotes(
    notes: List<Note>,
    hidden: Set<Long>,
    section: NoteSection,
    query: String,
    labelsByNote: Map<Long, List<LabelEntity>>,
): List<Note> {
    val inSection = notes.filter { n ->
        n.id !in hidden && when (section) {
            NoteSection.Notes -> !n.isArchived
            NoteSection.Reminders -> n.reminderAt != null && !n.isArchived
            NoteSection.Archive -> n.isArchived
            is NoteSection.Label -> !n.isArchived && labelsByNote[n.id].orEmpty().any { it.id == section.labelId }
        }
    }
    val ordered = if (section == NoteSection.Reminders) inSection.sortedBy { it.reminderAt } else inSection
    val q = query.trim()
    if (q.isEmpty()) return ordered
    return ordered.filter { n ->
        n.title.contains(q, ignoreCase = true) ||
            n.body.contains(q, ignoreCase = true) ||
            n.items.any { it.text.contains(q, ignoreCase = true) } ||
            labelsByNote[n.id].orEmpty().any { it.name.contains(q, ignoreCase = true) }
    }
}

private fun sectionTitle(section: NoteSection, labels: List<LabelEntity>): String? = when (section) {
    NoteSection.Notes -> null
    NoteSection.Reminders -> "Reminders"
    NoteSection.Archive -> "Archive"
    is NoteSection.Label -> labels.firstOrNull { it.id == section.labelId }?.name
}

// ------------------------------------------------------------------ Search bar

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    layout: NotesLayout,
    onToggleLayout: () -> Unit,
    onOpenDrawer: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .height(52.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Outlined.Menu, contentDescription = "Open menu")
            }
            val textColor = MaterialTheme.colorScheme.onSurface
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    }
                },
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = {
                    onQueryChange("")
                    focusManager.clearFocus()
                }) {
                    Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                }
            }
            IconButton(onClick = onToggleLayout) {
                when (layout) {
                    NotesLayout.GRID -> Icon(Icons.Outlined.ViewAgenda, contentDescription = "Show as list")
                    NotesLayout.LIST -> Icon(Icons.Outlined.GridView, contentDescription = "Show as grid")
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Grid

@Composable
private fun NotesGrid(
    vm: MainViewModel,
    notes: List<Note>,
    sectioned: Boolean,
    canReorder: Boolean,
    layout: NotesLayout,
    labelsByNote: Map<Long, List<LabelEntity>>,
    imagesByNote: Map<Long, List<NoteImageEntity>>,
    bottomPadding: androidx.compose.ui.unit.Dp,
) {
    val haptics = LocalHapticFeedback.current

    // While dragging, the grid follows this order instead of the database order.
    var dragOrder by remember { mutableStateOf<List<Long>?>(null) }
    var dragActive by remember { mutableStateOf(false) }
    LaunchedEffect(notes) { if (!dragActive) dragOrder = null }

    val ordered = remember(notes, dragOrder) {
        val order = dragOrder
        if (order == null) {
            notes
        } else {
            val index = order.withIndex().associate { it.value to it.index }
            notes.sortedBy { index[it.id] ?: Int.MAX_VALUE }
        }
    }
    val pinned = if (sectioned) ordered.filter { it.isPinned } else emptyList()
    val others = if (sectioned) ordered.filterNot { it.isPinned } else ordered
    val currentOrdered by rememberUpdatedState(ordered)

    val gridState = rememberLazyStaggeredGridState()
    val reorderState = rememberReorderableLazyStaggeredGridState(gridState) { from, to ->
        val fromId = from.key as? Long ?: return@rememberReorderableLazyStaggeredGridState
        val toId = to.key as? Long ?: return@rememberReorderableLazyStaggeredGridState
        val list = currentOrdered
        val fromNote = list.firstOrNull { it.id == fromId } ?: return@rememberReorderableLazyStaggeredGridState
        val toNote = list.firstOrNull { it.id == toId } ?: return@rememberReorderableLazyStaggeredGridState
        if (fromNote.isPinned != toNote.isPinned) return@rememberReorderableLazyStaggeredGridState
        val ids = (dragOrder ?: list.map { it.id }).toMutableList()
        val fromIndex = ids.indexOf(fromId)
        val toIndex = ids.indexOf(toId)
        if (fromIndex < 0 || toIndex < 0) return@rememberReorderableLazyStaggeredGridState
        ids.add(toIndex, ids.removeAt(fromIndex))
        dragOrder = ids
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    val onDragStarted: () -> Unit = {
        dragActive = true
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    val onDragStopped: () -> Unit = {
        dragActive = false
        dragOrder?.let { vm.reorder(it) }
    }

    LazyVerticalStaggeredGrid(
        columns = when (layout) {
            NotesLayout.GRID -> StaggeredGridCells.Adaptive(minSize = 160.dp)
            NotesLayout.LIST -> StaggeredGridCells.Fixed(1)
        },
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = bottomPadding + 120.dp),
        verticalItemSpacing = 14.dp,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (pinned.isNotEmpty()) {
            item(key = "header_pinned", span = StaggeredGridItemSpan.FullLine) { SectionHeader("Pinned") }
            items(pinned, key = { it.id }) { note ->
                NoteItem(vm, note, reorderState, canReorder, labelsByNote, imagesByNote, onDragStarted, onDragStopped)
            }
            if (others.isNotEmpty()) {
                item(key = "header_others", span = StaggeredGridItemSpan.FullLine) { SectionHeader("Others") }
            }
        }
        items(others, key = { it.id }) { note ->
            NoteItem(vm, note, reorderState, canReorder, labelsByNote, imagesByNote, onDragStarted, onDragStopped)
        }
    }
}

@Composable
private fun LazyStaggeredGridItemScope.NoteItem(
    vm: MainViewModel,
    note: Note,
    reorderState: ReorderableLazyStaggeredGridState,
    canReorder: Boolean,
    labelsByNote: Map<Long, List<LabelEntity>>,
    imagesByNote: Map<Long, List<NoteImageEntity>>,
    onDragStarted: () -> Unit,
    onDragStopped: () -> Unit,
) {
    DisposableEffect(note.id) {
        onDispose { vm.cardBounds.remove(note.id) }
    }
    ReorderableItem(reorderState, key = note.id, enabled = canReorder) { isDragging ->
        SwipeToArchive(
            isArchived = note.isArchived,
            onSwiped = { vm.toggleArchived(note) },
            enabled = !isDragging,
        ) {
            NoteCard(
                note = note,
                labels = labelsByNote[note.id].orEmpty(),
                images = imagesByNote[note.id].orEmpty(),
                imageStore = vm.imageStore,
                lifted = isDragging,
                onClick = { vm.openNote(note.id) },
                onTogglePin = if (note.isArchived) null else ({ vm.togglePinned(note) }),
                onDelete = { vm.deleteFromCard(note) },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (canReorder) {
                            Modifier.longPressDraggableHandle(
                                onDragStarted = { onDragStarted() },
                                onDragStopped = { onDragStopped() },
                            )
                        } else {
                            Modifier
                        },
                    )
                    .onGloballyPositioned { vm.cardBounds[note.id] = it.boundsInRoot() },
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 6.dp, top = 6.dp, bottom = 0.dp),
    )
}

@Composable
private fun EmptyState(section: NoteSection, searching: Boolean) {
    val (icon: ImageVector, text: String) = when {
        searching -> Icons.Outlined.Search to "No matching notes"
        section == NoteSection.Notes -> Icons.Outlined.Lightbulb to "Notes you add appear here"
        section == NoteSection.Reminders -> Icons.Outlined.Notifications to "Notes with reminders appear here"
        section == NoteSection.Archive -> Icons.Outlined.Archive to "Your archived notes appear here"
        else -> Icons.AutoMirrored.Outlined.Label to "No notes with this label yet"
    }
    Box(
        Modifier
            .fillMaxSize()
            .padding(bottom = 80.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.size(110.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

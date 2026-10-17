package app.stickynotes.ui.trash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.stickynotes.data.Note
import app.stickynotes.data.NotesLayout
import app.stickynotes.data.NotesRepository
import app.stickynotes.ui.MainViewModel
import app.stickynotes.ui.components.NoteCard

/** Trash: notes stay 7 days. Tap one to restore it or delete it forever. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    vm: MainViewModel,
    snackbarHostState: SnackbarHostState,
    onOpenDrawer: () -> Unit,
) {
    val trashed by vm.trashedNotes.collectAsStateWithLifecycle()
    val labelsByNote by vm.labelsByNote.collectAsStateWithLifecycle()
    val imagesByNote by vm.imagesByNote.collectAsStateWithLifecycle()
    val layout by vm.settings.layout.collectAsStateWithLifecycle()

    var selected by remember { mutableStateOf<Note?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Trash") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Outlined.Menu, contentDescription = "Open menu")
                    }
                },
                actions = {
                    if (trashed.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Empty Trash") },
                                    onClick = {
                                        menuOpen = false
                                        confirmEmpty = true
                                    },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
        ) {
            if (trashed.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(bottom = 80.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.size(110.dp),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No notes in Trash",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Text(
                    text = "Notes in Trash are deleted after ${NotesRepository.TRASH_DAYS} days.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
                LazyVerticalStaggeredGrid(
                    columns = when (layout) {
                        NotesLayout.GRID -> StaggeredGridCells.Adaptive(minSize = 160.dp)
                        NotesLayout.LIST -> StaggeredGridCells.Fixed(1)
                    },
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 14.dp,
                        end = 14.dp,
                        top = 6.dp,
                        bottom = padding.calculateBottomPadding() + 24.dp,
                    ),
                    verticalItemSpacing = 14.dp,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(trashed, key = { it.id }) { note ->
                        NoteCard(
                            note = note,
                            labels = labelsByNote[note.id].orEmpty(),
                            images = imagesByNote[note.id].orEmpty(),
                            imageStore = vm.imageStore,
                            onClick = { selected = note },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    selected?.let { note ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("Note in Trash") },
            text = { Text("Restore it to your notes, or delete it forever? Deleting forever can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.restore(note.id)
                    selected = null
                }) { Text("Restore") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        vm.deleteForever(note.id)
                        selected = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete forever") }
            },
        )
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("Empty Trash?") },
            text = { Text("All notes in Trash will be permanently deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.emptyTrash()
                        confirmEmpty = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Empty Trash") }
            },
            dismissButton = {
                TextButton(onClick = { confirmEmpty = false }) { Text("Cancel") }
            },
        )
    }
}

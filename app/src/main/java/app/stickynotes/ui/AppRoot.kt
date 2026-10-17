package app.stickynotes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.stickynotes.data.ThemeMode
import app.stickynotes.ui.components.AppDrawer
import app.stickynotes.ui.components.NoteCard
import app.stickynotes.ui.crumple.CrumpleOverlay
import app.stickynotes.ui.editor.EditorScreen
import app.stickynotes.ui.home.HomeScreen
import app.stickynotes.ui.labels.EditLabelsScreen
import app.stickynotes.ui.settings.SettingsScreen
import app.stickynotes.ui.theme.NoteFont
import app.stickynotes.ui.theme.StickyTheme
import app.stickynotes.ui.trash.TrashScreen
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun AppRoot(vm: MainViewModel, onDarkThemeChanged: (Boolean) -> Unit) {
    val themeMode by vm.settings.theme.collectAsStateWithLifecycle()
    val fontName by vm.settings.font.collectAsStateWithLifecycle()
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    LaunchedEffect(dark) { onDarkThemeChanged(dark) }

    StickyTheme(darkTheme = dark, noteFont = NoteFont.of(fontName)) {
        val snackbarHostState = remember { SnackbarHostState() }
        val undoSeconds by vm.settings.undoSeconds.collectAsStateWithLifecycle()
        val undoMillis by rememberUpdatedState(undoSeconds * 1000L)

        // One snackbar at a time; a new message replaces the old one. Undo stays for the chosen time.
        LaunchedEffect(vm) {
            vm.events.collectLatest { event ->
                val result = withTimeoutOrNull(if (event.undo != null) undoMillis else 2500L) {
                    snackbarHostState.showSnackbar(
                        message = event.message,
                        actionLabel = if (event.undo != null) "Undo" else null,
                        duration = SnackbarDuration.Indefinite,
                    )
                }
                if (result == SnackbarResult.ActionPerformed) event.undo?.invoke()
            }
        }

        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val focusManager = LocalFocusManager.current
        val labels by vm.labels.collectAsStateWithLifecycle()
        val labelsByNote by vm.labelsByNote.collectAsStateWithLifecycle()
        val imagesByNote by vm.imagesByNote.collectAsStateWithLifecycle()
        val editorId = vm.editorNoteId
        val openDrawer: () -> Unit = {
            focusManager.clearFocus()
            scope.launch { drawerState.open() }
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = editorId == null,
                drawerContent = {
                    AppDrawer(
                        labels = labels,
                        current = vm.root,
                        onNavigate = { target ->
                            vm.navigate(target)
                            scope.launch { drawerState.close() }
                        },
                    )
                },
            ) {
                when (val root = vm.root) {
                    is Root.Section -> HomeScreen(vm, root.section, snackbarHostState, openDrawer)
                    Root.Trash -> TrashScreen(vm, snackbarHostState, openDrawer)
                    Root.EditLabels -> EditLabelsScreen(
                        labels = labels,
                        onCreate = vm::createLabel,
                        onRename = vm::renameLabel,
                        onDelete = { vm.deleteLabel(it.id) },
                        onBack = { vm.navigate(Root.Section(NoteSection.Notes)) },
                    )
                    Root.Settings -> SettingsScreen(vm, openDrawer)
                }
            }

            // Back from Archive, Trash etc. returns to Notes; from Notes it clears search, then leaves the app.
            val canGoBack = vm.root != Root.Section(NoteSection.Notes) || vm.searchQuery.isNotEmpty()
            BackHandler(enabled = editorId == null && canGoBack) { vm.back() }
            BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

            // The editor sits on top of the grid, so the grid keeps its scroll position.
            var lastEditorId by remember { mutableStateOf<Long?>(null) }
            LaunchedEffect(editorId) { if (editorId != null) lastEditorId = editorId }
            AnimatedVisibility(
                visible = editorId != null,
                enter = fadeIn(tween(170)) + scaleIn(initialScale = 0.94f, animationSpec = tween(220)),
                exit = fadeOut(tween(170)) + scaleOut(targetScale = 0.96f, animationSpec = tween(170)),
            ) {
                val id = editorId ?: lastEditorId
                if (id != null) key(id) { EditorScreen(vm, id) }
            }

            CrumpleOverlay(
                job = vm.crumple,
                cardContent = { job ->
                    NoteCard(
                        note = job.note,
                        labels = labelsByNote[job.note.id].orEmpty(),
                        images = imagesByNote[job.note.id].orEmpty(),
                        imageStore = vm.imageStore,
                        elevated = false,
                        modifier = Modifier.fillMaxSize(),
                    )
                },
                onFinished = vm::crumpleFinished,
            )
        }
    }
}

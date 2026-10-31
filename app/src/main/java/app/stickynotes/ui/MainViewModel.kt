package app.stickynotes.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.stickynotes.StickyApp
import app.stickynotes.data.LabelEntity
import app.stickynotes.data.LabelResult
import app.stickynotes.data.Note
import app.stickynotes.data.NoteImageEntity
import app.stickynotes.data.NotesLayout
import app.stickynotes.data.Settings
import app.stickynotes.data.ThemeMode
import app.stickynotes.data.mealie.MealieClient
import app.stickynotes.data.mealie.MealieException
import app.stickynotes.data.mealie.MealieState
import app.stickynotes.data.mealie.MealieSyncWorker
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the main grid is showing. */
sealed interface NoteSection {
    data object Notes : NoteSection
    data object Reminders : NoteSection
    data object Archive : NoteSection
    data class Label(val labelId: Long) : NoteSection
}

/** The screen behind the editor, picked from the drawer. */
sealed interface Root {
    data class Section(val section: NoteSection) : Root
    data object Trash : Root
    data object EditLabels : Root
    data object Settings : Root
}

/** A message for the snackbar, optionally with Undo. */
data class UiEvent(val message: String, val undo: (() -> Unit)? = null)


class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as StickyApp).container
    private val repo = container.repository
    val settings: Settings = container.settings
    val imageStore get() = repo.images

    private val sharing = SharingStarted.WhileSubscribed(5_000)

    // ---------------------------------------------------------------- Data

    val notes: StateFlow<List<Note>> = repo.aliveNotes.stateIn(viewModelScope, sharing, emptyList())
    val trashedNotes: StateFlow<List<Note>> = repo.trashedNotes.stateIn(viewModelScope, sharing, emptyList())
    val labels: StateFlow<List<LabelEntity>> = repo.labels.stateIn(viewModelScope, sharing, emptyList())
    val labelsByNote: StateFlow<Map<Long, List<LabelEntity>>> =
        repo.labelsByNote.stateIn(viewModelScope, sharing, emptyMap())
    val imagesByNote: StateFlow<Map<Long, List<NoteImageEntity>>> =
        repo.imagesByNote.stateIn(viewModelScope, sharing, emptyMap())

    /** False until the first database load, so empty-state text doesn't flash on launch. */
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    /** Notes just sent to Trash; hidden from the grid right away. */
    private val _hiddenIds = MutableStateFlow<Set<Long>>(emptySet())
    val hiddenIds: StateFlow<Set<Long>> = _hiddenIds.asStateFlow()

    // ---------------------------------------------------------------- UI state

    var root by mutableStateOf<Root>(Root.Section(NoteSection.Notes))
        private set

    private var _editorNoteId by mutableStateOf<Long?>(null)

    /** The note open in the editor, or null. */
    val editorNoteId: Long? get() = _editorNoteId

    /** True when the open note was just created, so the editor puts the cursor in it. */
    var editorIsNew by mutableStateOf(false)
        private set

    var searchQuery by mutableStateOf("")

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events: Flow<UiEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            repo.aliveNotes.first()
            _loaded.value = true
        }
        viewModelScope.launch { repo.cleanUp() }
    }

    // ---------------------------------------------------------------- Mealie

    private val mealieStore = container.mealieStore
    private val mealieSync = container.mealieSync
    val mealieState: StateFlow<MealieState> = mealieStore.state
    private var pollJob: Job? = null

    /**
     * Changes the open note. Mealie sync leaves an open note alone; after it closes,
     * sync waits until its last save has landed, then pushes your changes.
     */
    private fun openEditor(id: Long?) {
        val previous = _editorNoteId
        _editorNoteId = id
        if (id != null) mealieSync.hold(id)
        if (previous != null && previous != id) {
            viewModelScope.launch {
                delay(1_500)
                if (_editorNoteId != previous) {
                    mealieSync.release(previous)
                    if (previous in mealieSync.linkedNoteIds()) mealieSync.syncNow()
                }
            }
        }
    }

    /** While the app is on screen, check Mealie every 30 seconds. */
    fun onAppVisible() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                if (mealieStore.isConnected) mealieSync.syncNow()
                delay(30_000)
            }
        }
    }

    fun onAppHidden() {
        pollJob?.cancel()
        pollJob = null
    }

    /** Returns an error message, or null when connected. */
    suspend fun connectMealie(address: String, token: String): String? = withContext(Dispatchers.IO) {
        val client = MealieClient(address, token.trim())
        try {
            val name = client.whoAmI()
            client.lists()
            mealieStore.connect(client.root, token.trim(), name)
            MealieSyncWorker.schedule(getApplication())
            viewModelScope.launch { mealieSync.syncNow() }
            null
        } catch (e: MealieException) {
            e.message
        } catch (e: Exception) {
            "Couldn't connect (${e.javaClass.simpleName})."
        }
    }

    fun disconnectMealie() {
        MealieSyncWorker.cancel(getApplication())
        mealieStore.disconnect()
    }

    fun syncMealieNow() {
        viewModelScope.launch { mealieSync.syncNow() }
    }

    /** Switching a list back on after its note was trashed or deleted starts a fresh note. */
    fun setMealieListEnabled(listId: String, enabled: Boolean) {
        viewModelScope.launch {
            val link = mealieStore.links().firstOrNull { it.listId == listId } ?: return@launch
            var updated = link.copy(enabled = enabled)
            if (enabled) {
                val note = link.noteId?.let { repo.get(it) }
                if (note == null || note.deletedAt != null) {
                    updated = updated.copy(noteId = null, map = emptyMap(), base = emptyMap(), baseTitle = "")
                }
            }
            mealieStore.saveLinks(mealieStore.links().map { if (it.listId == listId) updated else it })
            if (enabled) mealieSync.syncNow()
        }
    }

    private fun emit(message: String, undo: (() -> Unit)? = null) {
        _events.trySend(UiEvent(message, undo))
    }

    // ---------------------------------------------------------------- Navigation

    fun navigate(target: Root) {
        root = target
        searchQuery = ""
    }

    /** Back from a drawer screen goes to Notes. Returns false when there's nowhere to go back to. */
    fun back(): Boolean {
        if (root != Root.Section(NoteSection.Notes)) {
            navigate(Root.Section(NoteSection.Notes))
            return true
        }
        if (searchQuery.isNotEmpty()) {
            searchQuery = ""
            return true
        }
        return false
    }

    fun openNote(id: Long) {
        editorIsNew = false
        openEditor(id)
    }

    fun openFromNotification(id: Long) {
        viewModelScope.launch {
            val note = repo.get(id) ?: return@launch
            if (note.deletedAt != null) return@launch
            editorIsNew = false
            openEditor(id)
        }
    }

    fun newNote(isChecklist: Boolean) {
        val labelId = ((root as? Root.Section)?.section as? NoteSection.Label)?.labelId
        viewModelScope.launch {
            val id = repo.create(isChecklist, labelId)
            editorIsNew = true
            openEditor(id)
        }
    }

    /** From the Quick note icon or a home-screen shortcut: go to Notes and start a blank note. */
    fun newNoteFromShortcut(isChecklist: Boolean) {
        if (root != Root.Section(NoteSection.Notes)) navigate(Root.Section(NoteSection.Notes))
        newNote(isChecklist)
    }

    /** S Pen pulled out: start a blank note, unless one is already open. */
    fun newNoteFromPen() {
        if (editorNoteId != null) return
        newNoteFromShortcut(isChecklist = false)
    }

    suspend fun loadNote(id: Long): Note? = repo.get(id)

    /** Closes the editor without saving, e.g. when its note no longer exists. */
    fun dismissEditor(id: Long) {
        if (editorNoteId == id) openEditor(null)
    }

    // ---------------------------------------------------------------- Editor

    fun saveDraft(note: Note) {
        viewModelScope.launch { repo.saveContent(note) }
    }

    /** Saves and closes. Throws away notes that ended up empty, like Keep. */
    fun closeEditor(draft: Note) {
        if (editorNoteId != draft.id) return
        openEditor(null)
        viewModelScope.launch {
            repo.saveContent(draft)
            if (repo.isEmpty(draft.id)) {
                repo.deleteForever(listOf(draft.id))
                emit("Empty note discarded")
            }
        }
    }

    fun archiveFromEditor(draft: Note) {
        if (editorNoteId != draft.id) return
        openEditor(null)
        viewModelScope.launch {
            repo.saveContent(draft)
            val archive = !draft.isArchived
            repo.setArchived(draft.id, archive, pinned = if (archive) false else draft.isPinned)
            emit(if (archive) "Note archived" else "Note unarchived") {
                viewModelScope.launch { repo.setArchived(draft.id, draft.isArchived, draft.isPinned) }
            }
        }
    }

    /** Closes the editor and moves the note to Trash, with Undo. */
    fun deleteFromEditor(draft: Note) {
        if (editorNoteId != draft.id) return
        openEditor(null)
        viewModelScope.launch { repo.saveContent(draft) }
        moveToTrash(draft.id)
    }

    fun copyFromEditor(draft: Note) {
        if (editorNoteId != draft.id) return
        viewModelScope.launch {
            repo.saveContent(draft)
            val newId = repo.copy(draft.id) ?: return@launch
            editorIsNew = false
            openEditor(newId)
        }
    }

    // ---------------------------------------------------------------- Grid actions

    fun toggleArchived(note: Note) {
        val archive = !note.isArchived
        viewModelScope.launch {
            repo.setArchived(note.id, archive, pinned = if (archive) false else note.isPinned)
        }
        emit(if (archive) "Note archived" else "Note unarchived") {
            viewModelScope.launch { repo.setArchived(note.id, note.isArchived, note.isPinned) }
        }
    }

    fun togglePinned(note: Note) {
        viewModelScope.launch { repo.setPinned(note.id, !note.isPinned) }
    }

    /** The trash button on a card: straight to Trash, with Undo. */
    fun deleteFromCard(note: Note) {
        if (note.id in _hiddenIds.value) return
        moveToTrash(note.id)
    }

    fun reorder(orderedIds: List<Long>) {
        val snapshot = notes.value
        viewModelScope.launch { repo.reorder(orderedIds, snapshot) }
    }

    // ---------------------------------------------------------------- Trash

    fun moveToTrash(id: Long) {
        _hiddenIds.update { it + id }
        viewModelScope.launch { repo.moveToTrash(id) }
        emit("Note moved to Trash") { restore(id, announce = false) }
    }

    fun restore(id: Long, announce: Boolean = true) {
        _hiddenIds.update { it - id }
        viewModelScope.launch { repo.restore(id) }
        if (announce) emit("Note restored")
    }

    fun deleteForever(id: Long) {
        viewModelScope.launch { repo.deleteForever(listOf(id)) }
        emit("Note deleted forever")
    }

    fun emptyTrash() {
        viewModelScope.launch { repo.emptyTrash() }
        emit("Trash emptied")
    }

    // ---------------------------------------------------------------- Labels

    fun setNoteLabel(noteId: Long, labelId: Long, attached: Boolean) {
        viewModelScope.launch { repo.setNoteLabel(noteId, labelId, attached) }
    }

    fun createLabelOnNote(noteId: Long, name: String) {
        viewModelScope.launch {
            repo.getOrCreateLabel(name)?.let { repo.setNoteLabel(noteId, it.id, attached = true) }
        }
    }

    fun createLabel(name: String, onResult: (LabelResult) -> Unit) {
        viewModelScope.launch { onResult(repo.createLabel(name)) }
    }

    fun renameLabel(id: Long, name: String, onResult: (LabelResult) -> Unit) {
        viewModelScope.launch { onResult(repo.renameLabel(id, name)) }
    }

    fun deleteLabel(id: Long) {
        val current = root
        if (current is Root.Section && current.section == NoteSection.Label(id)) {
            navigate(Root.Section(NoteSection.Notes))
        }
        viewModelScope.launch { repo.deleteLabel(id) }
    }

    // ---------------------------------------------------------------- Images

    fun addImages(noteId: Long, uris: List<Uri>) {
        viewModelScope.launch {
            val failed = repo.addImages(noteId, uris)
            if (failed > 0) emit(if (failed == 1) "Couldn't add 1 image" else "Couldn't add $failed images")
        }
    }

    fun removeImage(image: NoteImageEntity) {
        viewModelScope.launch { repo.removeImage(image) }
    }

    // ---------------------------------------------------------------- Settings

    fun setTheme(mode: ThemeMode) = settings.setTheme(mode)
    fun setFont(name: String) = settings.setFont(name)
    fun setUndoSeconds(seconds: Int) = settings.setUndoSeconds(seconds)
    fun toggleLayout() {
        settings.setLayout(if (settings.layout.value == NotesLayout.GRID) NotesLayout.LIST else NotesLayout.GRID)
    }
}

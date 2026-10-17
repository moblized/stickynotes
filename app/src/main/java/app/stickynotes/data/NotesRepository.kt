package app.stickynotes.data

import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import app.stickynotes.reminders.ReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class LabelResult { OK, BLANK, DUPLICATE }

class NotesRepository(
    private val db: AppDatabase,
    val images: ImageStore,
    private val reminders: ReminderScheduler,
) {
    private val noteDao = db.noteDao()
    private val labelDao = db.labelDao()
    private val imageDao = db.imageDao()
    private val converters = Converters()
    private val saveLock = Mutex()

    // ---------------------------------------------------------------- Notes

    val aliveNotes: Flow<List<Note>> = noteDao.observeAlive()
    val trashedNotes: Flow<List<Note>> = noteDao.observeTrashed()

    suspend fun get(id: Long): Note? = noteDao.get(id)

    suspend fun create(isChecklist: Boolean, labelId: Long? = null): Long {
        val position = (noteDao.minPosition() ?: 0L) - 1
        val now = System.currentTimeMillis()
        val id = noteDao.insert(
            Note(
                isChecklist = isChecklist,
                items = if (isChecklist) listOf(ChecklistItem()) else emptyList(),
                position = position,
                createdAt = now,
                updatedAt = now,
            ),
        )
        if (labelId != null) labelDao.attach(NoteLabelCrossRef(id, labelId))
        return id
    }

    /** Saves editor content. Bumps "edited" time only when the text, list or color changed. */
    suspend fun saveContent(note: Note) = saveLock.withLock {
        val old = noteDao.get(note.id) ?: return@withLock
        val contentChanged = old.title != note.title ||
            old.body != note.body ||
            old.items != note.items ||
            old.isChecklist != note.isChecklist ||
            old.color != note.color
        if (!contentChanged && old.isPinned == note.isPinned && old.reminderAt == note.reminderAt) return@withLock
        noteDao.updateContent(
            id = note.id,
            title = note.title,
            body = note.body,
            isChecklist = note.isChecklist,
            itemsJson = converters.itemsToJson(note.items),
            color = note.color,
            isPinned = note.isPinned,
            reminderAt = note.reminderAt,
            updatedAt = if (contentChanged) System.currentTimeMillis() else old.updatedAt,
        )
        if (old.reminderAt != note.reminderAt) {
            val at = note.reminderAt
            if (at != null && old.deletedAt == null) reminders.schedule(note.id, at) else reminders.cancel(note.id)
        }
    }

    suspend fun setArchived(id: Long, archived: Boolean, pinned: Boolean) =
        noteDao.setArchived(id, archived, pinned)

    suspend fun setPinned(id: Long, pinned: Boolean) = noteDao.setPinned(id, pinned)

    /** Gives [orderedIds] the same set of positions they had, in the new order. */
    suspend fun reorder(orderedIds: List<Long>, current: List<Note>) {
        val byId = current.associateBy { it.id }
        val positions = orderedIds.mapNotNull { byId[it]?.position }.sorted()
        if (positions.size != orderedIds.size) return
        val changes = orderedIds.zip(positions)
            .filter { (id, pos) -> byId[id]?.position != pos }
            .toMap()
        if (changes.isNotEmpty()) noteDao.setPositions(changes)
    }

    /** A note is "empty" when it has no text, images, labels or reminder. */
    suspend fun isEmpty(id: Long): Boolean {
        val note = noteDao.get(id) ?: return false
        return !note.hasText() &&
            note.reminderAt == null &&
            imageDao.imagesFor(id).isEmpty() &&
            labelDao.countFor(id) == 0
    }

    suspend fun copy(id: Long): Long? {
        val source = noteDao.get(id) ?: return null
        val now = System.currentTimeMillis()
        val newId = noteDao.insert(
            source.copy(
                id = 0,
                items = source.items.map { it.copy(id = kotlin.random.Random.nextLong()) },
                isPinned = false,
                isArchived = false,
                position = (noteDao.minPosition() ?: 0L) - 1,
                reminderAt = null,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
            ),
        )
        labelDao.labelIdsFor(id).forEach { labelDao.attach(NoteLabelCrossRef(newId, it)) }
        imageDao.imagesFor(id).forEach { image ->
            images.duplicate(image.fileName)?.let { imageDao.insert(NoteImageEntity(noteId = newId, fileName = it)) }
        }
        return newId
    }

    // ---------------------------------------------------------------- Trash

    suspend fun moveToTrash(id: Long) {
        noteDao.moveToTrash(id, System.currentTimeMillis())
        reminders.cancel(id)
    }

    suspend fun restore(id: Long) {
        noteDao.restore(id)
        val at = noteDao.get(id)?.reminderAt
        if (at != null && at > System.currentTimeMillis()) reminders.schedule(id, at)
    }

    /** Permanent: removes notes, their label links, image rows and image files. */
    suspend fun deleteForever(ids: List<Long>) {
        for (chunk in ids.chunked(500)) {
            val files = imageDao.fileNamesFor(chunk)
            chunk.forEach { reminders.cancel(it) }
            noteDao.deleteIds(chunk)
            files.forEach { images.delete(it) }
        }
    }

    suspend fun emptyTrash() = deleteForever(noteDao.trashedIds())

    /** Clears notes that have been in Trash for [TRASH_DAYS] days, and stray image files. */
    suspend fun cleanUp() {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(TRASH_DAYS.toLong())
        deleteForever(noteDao.expiredIds(cutoff))
        images.deleteOrphans(imageDao.allFileNames().toSet())
    }

    // ---------------------------------------------------------------- Reminders

    /** After a reboot or app update, alarms are gone; set them again. */
    suspend fun rescheduleReminders() {
        val now = System.currentTimeMillis()
        noteDao.withReminders().forEach { note ->
            val at = note.reminderAt ?: return@forEach
            if (at > now) reminders.schedule(note.id, at)
        }
    }

    // ---------------------------------------------------------------- Labels

    val labels: Flow<List<LabelEntity>> = labelDao.observeAll()

    /** noteId -> that note's labels, alphabetical. */
    val labelsByNote: Flow<Map<Long, List<LabelEntity>>> =
        combine(labels, labelDao.observeCrossRefs()) { all, refs ->
            val byId = all.associateBy { it.id }
            refs.groupBy { it.noteId }.mapValues { (_, noteRefs) ->
                noteRefs.mapNotNull { byId[it.labelId] }.sortedBy { it.name.lowercase() }
            }
        }

    suspend fun getOrCreateLabel(rawName: String): LabelEntity? {
        val name = cleanLabel(rawName) ?: return null
        labelDao.findByName(name)?.let { return it }
        return try {
            LabelEntity(id = labelDao.insert(LabelEntity(name = name)), name = name)
        } catch (e: SQLiteConstraintException) {
            labelDao.findByName(name)
        }
    }

    suspend fun createLabel(rawName: String): LabelResult {
        val name = cleanLabel(rawName) ?: return LabelResult.BLANK
        if (labelDao.findByName(name) != null) return LabelResult.DUPLICATE
        return try {
            labelDao.insert(LabelEntity(name = name))
            LabelResult.OK
        } catch (e: SQLiteConstraintException) {
            LabelResult.DUPLICATE
        }
    }

    suspend fun renameLabel(id: Long, rawName: String): LabelResult {
        val name = cleanLabel(rawName) ?: return LabelResult.BLANK
        val clash = labelDao.findByName(name)
        if (clash != null && clash.id != id) return LabelResult.DUPLICATE
        return try {
            labelDao.rename(id, name)
            LabelResult.OK
        } catch (e: SQLiteConstraintException) {
            LabelResult.DUPLICATE
        }
    }

    suspend fun deleteLabel(id: Long) = labelDao.delete(id)

    suspend fun setNoteLabel(noteId: Long, labelId: Long, attached: Boolean) {
        val ref = NoteLabelCrossRef(noteId = noteId, labelId = labelId)
        if (attached) labelDao.attach(ref) else labelDao.detach(ref)
    }

    private fun cleanLabel(raw: String): String? =
        raw.trim().replace(Regex("\\s+"), " ").take(MAX_LABEL_LENGTH).trimEnd().ifEmpty { null }

    // ---------------------------------------------------------------- Images

    val imagesByNote: Flow<Map<Long, List<NoteImageEntity>>> =
        imageDao.observeAll().map { all -> all.groupBy { it.noteId } }

    /** Returns how many images couldn't be added. */
    suspend fun addImages(noteId: Long, uris: List<Uri>): Int {
        var failed = 0
        for (uri in uris) {
            val fileName = try {
                images.copyIn(uri)
            } catch (e: IOException) {
                failed++
                continue
            } catch (e: SecurityException) {
                failed++
                continue
            }
            try {
                imageDao.insert(NoteImageEntity(noteId = noteId, fileName = fileName))
            } catch (e: SQLiteConstraintException) {
                images.delete(fileName)
                failed++
            }
        }
        return failed
    }

    suspend fun removeImage(image: NoteImageEntity) {
        imageDao.delete(image.id)
        images.delete(image.fileName)
    }

    companion object {
        const val TRASH_DAYS = 7
        const val MAX_LABEL_LENGTH = 50
    }
}

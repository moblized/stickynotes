package app.stickynotes.data.mealie

import app.stickynotes.data.ChecklistItem
import app.stickynotes.data.Note
import app.stickynotes.data.NotesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps each Mealie shopping list and its checklist note in step, both ways.
 * Runs from the app (every 30 s while open, and when you close a synced note)
 * and from a background job every 15 minutes.
 */
class MealieSync(
    private val store: MealieStore,
    private val repo: NotesRepository,
) {
    private val mutex = Mutex()

    /** Notes open in the editor (or just closed and still saving). Sync leaves them alone. */
    private val held = ConcurrentHashMap.newKeySet<Long>()

    fun hold(noteId: Long) {
        held += noteId
    }

    fun release(noteId: Long) {
        held -= noteId
    }

    /** Note ids that are synced with Mealie. */
    fun linkedNoteIds(): Set<Long> = store.links().mapNotNull { it.noteId }.toSet()

    /** Returns true when the sync went through (or there was nothing to do). */
    suspend fun syncNow(): Boolean {
        if (!store.isConnected) return true
        if (mutex.isLocked) return true // one already running
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                store.setSyncing(true)
                try {
                    syncAll(MealieClient(store.address, store.token))
                    store.syncSucceeded()
                    true
                } catch (e: MealieException) {
                    store.syncFailed(e.message ?: "Sync failed")
                    false
                } catch (e: Exception) {
                    store.syncFailed("Sync failed: ${e.javaClass.simpleName}")
                    false
                } finally {
                    store.setSyncing(false)
                }
            }
        }
    }

    private suspend fun syncAll(client: MealieClient) {
        val remoteLists = client.lists()
        val known = store.links().associateBy { it.listId }
        // Lists deleted in Mealie stop syncing; their notes stay.
        var links = remoteLists.map { (id, name) -> known[id]?.copy(name = name) ?: MealieLink(listId = id, name = name) }
        store.saveLinks(links)

        links = links.map { link ->
            if (!link.enabled) link else syncList(client, link) { updated -> saveLink(updated) }
        }
        store.saveLinks(links)
    }

    private fun saveLink(link: MealieLink) {
        store.saveLinks(store.links().map { if (it.listId == link.listId) link else it })
    }

    /** Syncs one list. [persist] saves progress mid-way (the Mealie ids given to new items). */
    private suspend fun syncList(client: MealieClient, start: MealieLink, persist: (MealieLink) -> Unit): MealieLink {
        var link = start
        val existing = link.noteId?.let { repo.get(it) }
        val note: Note = when {
            // Trashed here: stop syncing this list (switch it back on in Settings).
            existing != null && existing.deletedAt != null -> return link.copy(enabled = false)
            // Deleted for good: stop syncing; switching it back on makes a fresh note.
            existing == null && link.noteId != null -> return link.copy(enabled = false, noteId = null, map = emptyMap(), base = emptyMap())
            existing == null -> {
                val id = repo.createMealieChecklist(link.name)
                link = link.copy(noteId = id, map = emptyMap(), base = emptyMap(), baseTitle = link.name)
                persist(link)
                repo.get(id) ?: return link
            }
            else -> existing
        }
        // Open in the editor, or turned into a plain note: leave it alone for now.
        if (note.id in held || !note.isChecklist) return link

        // 1. Merge and push what changed here.
        val remote = client.list(link.listId)
        val local = note.items.map { LocalItem(it.id, it.text, it.checked) }
        val plan = MealieMerge.merge(local, link.map, link.base, remote.items)
        link = link.copy(map = plan.map)
        persist(link)

        val deleted = plan.deletes.toSet()
        val created = mutableSetOf<String>()
        var position = (remote.items.maxOfOrNull { it.position } ?: -1) + 1
        for ((rid, state) in plan.creates) {
            client.createItem(link.listId, rid, state, position++)
            created += rid
        }
        for ((rid, state) in plan.updates) {
            remote.raw[rid]?.let { client.updateItem(it, state) }
        }
        for (rid in plan.deletes) client.deleteItem(rid)

        // 2. Read Mealie again (it can merge duplicates) and take its word for the result.
        val remoteIds = remote.items.map { it.id }.toSet()
        val expected = buildMap {
            plan.items.forEach { item ->
                val rid = plan.map[item.id] ?: return@forEach
                if ((rid in remoteIds && rid !in deleted) || rid in created) put(rid, ItemState(item.text, item.checked))
            }
        }
        val after = client.list(link.listId)
        val settled = MealieMerge.merge(plan.items, plan.map, expected, after.items)

        // 3. Save the note, unless you opened it while this was running.
        if (note.id in held) return link
        val current = repo.get(note.id) ?: return link
        val title = if (current.title.isBlank() || current.title == link.baseTitle) after.name else current.title
        repo.saveContent(
            current.copy(
                title = title,
                isChecklist = true,
                items = settled.items.map { ChecklistItem(id = it.id, text = it.text, checked = it.checked) },
            ),
        )

        val mappedRemote = settled.map.values.toSet()
        return link.copy(
            name = after.name,
            map = settled.map,
            base = after.items.filter { it.id in mappedRemote }.associate { it.id to ItemState(it.text, it.checked) },
            baseTitle = after.name,
        )
    }
}

package app.stickynotes.data.mealie

import java.util.UUID
import kotlin.random.Random

/** One checklist line as both apps last agreed on it. */
data class ItemState(val text: String, val checked: Boolean)

/** A checklist line in the Sticky Notes note. */
data class LocalItem(val id: Long, val text: String, val checked: Boolean)

/** A shopping list item in Mealie. */
data class RemoteItem(val id: String, val text: String, val checked: Boolean, val position: Int)

data class MergeResult(
    /** The note's checklist after merging: unchecked first, in Mealie's order. */
    val items: List<LocalItem>,
    /** Sticky Notes item id -> Mealie item id, including items not yet created in Mealie. */
    val map: Map<Long, String>,
    /** New in Sticky Notes: create these in Mealie (with the id given here). */
    val creates: List<Pair<String, ItemState>>,
    /** Changed in Sticky Notes: push these to Mealie. */
    val updates: List<Pair<String, ItemState>>,
    /** Removed in Sticky Notes: delete these in Mealie. */
    val deletes: List<String>,
)

/**
 * Three-way merge of one shopping list: what Sticky Notes has now, what Mealie has now,
 * and [base], what both had after the last sync. Whichever side changed something wins;
 * if both changed the same item, Mealie wins. Pure logic, so it can be tested on its own.
 */
object MealieMerge {

    fun merge(
        local: List<LocalItem>,
        map: Map<Long, String>,
        base: Map<String, ItemState>,
        remote: List<RemoteItem>,
        newRemoteId: () -> String = { UUID.randomUUID().toString() },
        newLocalId: () -> Long = { Random.nextLong() },
    ): MergeResult {
        // Every local line gets a Mealie id up front, so a retry after a dropped connection
        // recognises the item instead of creating it twice.
        val fullMap = LinkedHashMap<Long, String>()
        local.forEach { fullMap[it.id] = map[it.id] ?: newRemoteId() }
        val localRids = local.map { fullMap.getValue(it.id) }.toSet()
        val remoteById = remote.associateBy { it.id }

        val creates = mutableListOf<Pair<String, ItemState>>()
        val updates = mutableListOf<Pair<String, ItemState>>()
        val deletes = mutableListOf<String>()
        val kept = LinkedHashMap<String, LocalItem>()

        for (item in local) {
            val rid = fullMap.getValue(item.id)
            val r = remoteById[rid]
            val b = base[rid]
            when {
                r != null -> {
                    val text = if (item.text.isBlank()) r.text else pick(item.text.trim(), r.text, b?.text)
                    val checked = pick(item.checked, r.checked, b?.checked)
                    if (text != r.text || checked != r.checked) updates += rid to ItemState(text, checked)
                    kept[rid] = item.copy(text = text, checked = checked)
                }
                // It was in Mealie last time and isn't now: removed in Mealie.
                b != null -> Unit
                // Never been in Mealie: new here.
                else -> {
                    if (item.text.isNotBlank()) creates += rid to ItemState(item.text.trim(), item.checked)
                    kept[rid] = item
                }
            }
        }

        for (r in remote) {
            if (r.id in localRids) continue
            val b = base[r.id]
            if (b != null && b.text == r.text && b.checked == r.checked) {
                // Removed here, untouched in Mealie: remove it there too.
                deletes += r.id
                continue
            }
            // New in Mealie (or changed there after being removed here): add it here.
            val item = LocalItem(newLocalId(), r.text, r.checked)
            fullMap[item.id] = r.id
            kept[r.id] = item
        }

        // Mealie's order, then lines that only exist here so far, in their order here.
        val ordered = mutableListOf<LocalItem>()
        remote.sortedBy { it.position }.forEach { r -> kept[r.id]?.let { ordered += it } }
        local.forEach { item ->
            val rid = fullMap.getValue(item.id)
            if (remoteById[rid] == null) kept[rid]?.let { ordered += it }
        }
        val items = ordered.filter { !it.checked } + ordered.filter { it.checked }
        val keptIds = items.map { it.id }.toSet()

        return MergeResult(
            items = items,
            map = fullMap.filterKeys { it in keptIds },
            creates = creates,
            updates = updates,
            deletes = deletes,
        )
    }

    /** Local wins only when it changed since the last sync and Mealie didn't. */
    private fun <T> pick(local: T, remote: T, base: T?): T =
        if (base != null && local != base && remote == base) local else remote
}

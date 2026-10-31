package app.stickynotes.data.mealie

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

/** One Mealie shopping list and the sticky note it's synced with. */
data class MealieLink(
    val listId: String,
    val name: String,
    /** The checklist note it syncs with; null until the first sync creates it. */
    val noteId: Long? = null,
    /** Off when you switch the list off, or trash its note. */
    val enabled: Boolean = true,
    /** Sticky Notes item id -> Mealie item id. */
    val map: Map<Long, String> = emptyMap(),
    /** Each item as both apps had it after the last sync. */
    val base: Map<String, ItemState> = emptyMap(),
    /** The list name at the last sync; if the note title still matches, renames in Mealie carry over. */
    val baseTitle: String = "",
)

data class MealieListSummary(val listId: String, val name: String, val enabled: Boolean)

data class MealieState(
    val address: String = "",
    val connected: Boolean = false,
    val userName: String = "",
    val syncing: Boolean = false,
    val lastSyncAt: Long = 0L,
    val lastError: String? = null,
    val lists: List<MealieListSummary> = emptyList(),
)

/** Mealie address, token and per-list sync state, kept in the app's private storage. */
class MealieStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("mealie", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(readState())
    val state: StateFlow<MealieState> = _state.asStateFlow()

    val address: String get() = prefs.getString(KEY_ADDRESS, "").orEmpty()
    val token: String get() = prefs.getString(KEY_TOKEN, "").orEmpty()
    val isConnected: Boolean get() = address.isNotBlank() && token.isNotBlank()

    fun connect(address: String, token: String, userName: String) {
        prefs.edit()
            .putString(KEY_ADDRESS, address)
            .putString(KEY_TOKEN, token)
            .putString(KEY_USER, userName)
            .remove(KEY_ERROR)
            .apply()
        _state.value = readState()
    }

    /** Stops syncing. The notes stay; reconnecting starts fresh lists. */
    fun disconnect() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_USER)
            .remove(KEY_LINKS)
            .remove(KEY_ERROR)
            .remove(KEY_LAST_SYNC)
            .apply()
        _state.value = readState()
    }

    // ------------------------------------------------------------------ links

    fun links(): List<MealieLink> {
        val json = prefs.getString(KEY_LINKS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(json)
            List(array.length()) { i -> decode(array.getJSONObject(i)) }
        }.getOrDefault(emptyList())
    }

    fun saveLinks(links: List<MealieLink>) {
        val array = JSONArray()
        links.forEach { array.put(encode(it)) }
        prefs.edit().putString(KEY_LINKS, array.toString()).apply()
        _state.update { it.copy(lists = summaries(links)) }
    }

    fun setEnabled(listId: String, enabled: Boolean) {
        saveLinks(links().map { if (it.listId == listId) it.copy(enabled = enabled) else it })
    }

    // ------------------------------------------------------------------ status

    fun setSyncing(syncing: Boolean) = _state.update { it.copy(syncing = syncing) }

    fun syncSucceeded() {
        val now = System.currentTimeMillis()
        prefs.edit().putLong(KEY_LAST_SYNC, now).remove(KEY_ERROR).apply()
        _state.update { it.copy(lastSyncAt = now, lastError = null) }
    }

    fun syncFailed(message: String) {
        prefs.edit().putString(KEY_ERROR, message).apply()
        _state.update { it.copy(lastError = message) }
    }

    private fun readState() = MealieState(
        address = address,
        connected = isConnected,
        userName = prefs.getString(KEY_USER, "").orEmpty(),
        lastSyncAt = prefs.getLong(KEY_LAST_SYNC, 0L),
        lastError = prefs.getString(KEY_ERROR, null),
        lists = summaries(links()),
    )

    private fun summaries(links: List<MealieLink>) =
        links.map { MealieListSummary(it.listId, it.name, it.enabled) }.sortedBy { it.name.lowercase() }

    // ------------------------------------------------------------------ JSON

    private fun encode(link: MealieLink): JSONObject {
        val map = JSONObject()
        link.map.forEach { (local, remote) -> map.put(local.toString(), remote) }
        val base = JSONObject()
        link.base.forEach { (remote, s) -> base.put(remote, JSONObject().put("t", s.text).put("c", s.checked)) }
        return JSONObject()
            .put("listId", link.listId)
            .put("name", link.name)
            .put("noteId", link.noteId ?: JSONObject.NULL)
            .put("enabled", link.enabled)
            .put("map", map)
            .put("base", base)
            .put("baseTitle", link.baseTitle)
    }

    private fun decode(o: JSONObject): MealieLink {
        val map = LinkedHashMap<Long, String>()
        o.optJSONObject("map")?.let { m -> m.keys().forEach { k -> k.toLongOrNull()?.let { map[it] = m.getString(k) } } }
        val base = LinkedHashMap<String, ItemState>()
        o.optJSONObject("base")?.let { b ->
            b.keys().forEach { k ->
                val s = b.getJSONObject(k)
                base[k] = ItemState(s.optString("t", ""), s.optBoolean("c", false))
            }
        }
        return MealieLink(
            listId = o.getString("listId"),
            name = o.optString("name", "Shopping list"),
            noteId = if (o.isNull("noteId")) null else o.optLong("noteId"),
            enabled = o.optBoolean("enabled", true),
            map = map,
            base = base,
            baseTitle = o.optString("baseTitle", ""),
        )
    }

    private companion object {
        const val KEY_ADDRESS = "address"
        const val KEY_TOKEN = "token"
        const val KEY_USER = "user"
        const val KEY_LINKS = "links"
        const val KEY_LAST_SYNC = "last_sync"
        const val KEY_ERROR = "last_error"
    }
}

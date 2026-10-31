package app.stickynotes.data.mealie

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class MealieException(message: String) : Exception(message)

data class RemoteList(
    val id: String,
    val name: String,
    val items: List<RemoteItem>,
    /** The items exactly as Mealie sent them; updates send the whole item back. */
    val raw: Map<String, JSONObject>,
)

/**
 * Talks to a Mealie server's shopping list API with an API token.
 * Blocking calls: run them off the main thread.
 */
class MealieClient(address: String, private val token: String) {

    val root: String = normalize(address)
    val host: String = runCatching { URL(root).host }.getOrDefault(root)

    /** "/api/households/shopping" on Mealie 2 and later, "/api/groups/shopping" on Mealie 1. */
    private var shopping: String? = null

    /** Checks the address and token. Returns the name of the Mealie user. */
    fun whoAmI(): String {
        val (code, body) = request("GET", "/api/users/self")
        if (code == 404) throw MealieException("$host doesn't look like a Mealie server. Check the address.")
        check(code, body)
        val me = JSONObject(body)
        return str(me, "fullName").ifBlank { str(me, "username") }.ifBlank { "you" }
    }

    /** id -> name of every shopping list. */
    fun lists(): List<Pair<String, String>> {
        val (code, body) = request("GET", "${shoppingBase()}/lists?page=1&perPage=-1")
        check(code, body)
        val items = JSONObject(body).optJSONArray("items") ?: JSONArray()
        return List(items.length()) { i ->
            val o = items.getJSONObject(i)
            str(o, "id") to str(o, "name").ifBlank { "Shopping list" }
        }.filter { it.first.isNotEmpty() }
    }

    fun list(id: String): RemoteList {
        val (code, body) = request("GET", "${shoppingBase()}/lists/$id")
        check(code, body)
        val o = JSONObject(body)
        val array = o.optJSONArray("listItems") ?: JSONArray()
        val raw = LinkedHashMap<String, JSONObject>()
        val items = List(array.length()) { i ->
            val item = array.getJSONObject(i)
            val itemId = str(item, "id")
            raw[itemId] = item
            RemoteItem(
                id = itemId,
                text = itemText(item),
                checked = item.optBoolean("checked", false),
                position = item.optInt("position", i),
            )
        }.filter { it.id.isNotEmpty() }
        return RemoteList(id = id, name = str(o, "name").ifBlank { "Shopping list" }, items = items, raw = raw)
    }

    fun createItem(listId: String, itemId: String, state: ItemState, position: Int) {
        val body = JSONObject()
            .put("id", itemId)
            .put("shoppingListId", listId)
            .put("note", state.text)
            .put("display", "")
            .put("quantity", 1)
            .put("checked", state.checked)
            .put("position", position)
            .put("isFood", false)
        val (code, response) = request("POST", "${shoppingBase()}/items", body.toString())
        check(code, response)
    }

    /** Sends the whole item back with the new text and tick, as Mealie's own app does. */
    fun updateItem(raw: JSONObject, state: ItemState) {
        val body = JSONObject(raw.toString())
        body.put("checked", state.checked)
        if (state.text != itemText(raw)) {
            // Edited here: it becomes a plain-text item with exactly what was typed.
            body.put("note", state.text)
            body.put("display", "")
            if (!raw.isNull("food") || !raw.isNull("unit")) {
                body.put("quantity", 0)
                body.put("foodId", JSONObject.NULL)
                body.put("food", JSONObject.NULL)
                body.put("unitId", JSONObject.NULL)
                body.put("unit", JSONObject.NULL)
                body.put("isFood", false)
            }
        }
        val id = str(raw, "id")
        val (code, response) = request("PUT", "${shoppingBase()}/items/$id", body.toString())
        if (code == 404) return // already gone in Mealie; the next pull drops it here too
        check(code, response)
    }

    fun deleteItem(itemId: String) {
        val (code, response) = request("DELETE", "${shoppingBase()}/items/$itemId")
        if (code == 404) return
        check(code, response)
    }

    // ------------------------------------------------------------------ plumbing

    private fun shoppingBase(): String {
        shopping?.let { return it }
        val (code, _) = request("GET", "/api/households/shopping/lists?page=1&perPage=1")
        val base = if (code == 404) "/api/groups/shopping" else "/api/households/shopping"
        shopping = base
        return base
    }

    private fun check(code: Int, body: String) {
        when {
            code in 200..299 -> return
            code == 401 || code == 403 -> throw MealieException("Mealie didn't accept the API token. Make a new one and connect again.")
            else -> {
                val detail = runCatching { JSONObject(body).opt("detail")?.toString() }.getOrNull()
                    ?.take(120)
                    ?.let { ": $it" }
                    .orEmpty()
                throw MealieException("Mealie answered with error $code$detail")
            }
        }
    }

    private fun request(method: String, path: String, body: String? = null): Pair<Int, String> {
        val connection = try {
            URL(root + path).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw MealieException("That Mealie address isn't valid.")
        }
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code >= 400) connection.errorStream else connection.inputStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return code to text
        } catch (e: UnknownHostException) {
            throw MealieException("Can't find $host. Check the address, and that your phone can reach it.")
        } catch (e: SSLException) {
            throw MealieException("Secure connection to $host failed (certificate problem).")
        } catch (e: ConnectException) {
            throw MealieException("Can't reach Mealie at $host. Is it running, and is your phone on a network that can reach it?")
        } catch (e: SocketTimeoutException) {
            throw MealieException("Mealie at $host took too long to answer.")
        } catch (e: IOException) {
            throw MealieException("Couldn't talk to Mealie at $host (${e.javaClass.simpleName}).")
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        /** "mealie.example.com/g/home" -> "https://mealie.example.com". Keeps a port and any sub-path before /g/ or /api. */
        fun normalize(address: String): String {
            var a = address.trim()
            if (a.isEmpty()) return a
            if (!a.startsWith("http://", ignoreCase = true) && !a.startsWith("https://", ignoreCase = true)) a = "https://$a"
            listOf("/g/", "/api", "/#", "/login", "/user", "/shopping-lists").forEach { marker ->
                val i = a.indexOf(marker, startIndex = a.indexOf("://") + 3)
                if (i > 0) a = a.substring(0, i)
            }
            return a.trimEnd('/')
        }

        /** What a line says: plain items show their note, food items Mealie's "2 cups flour" text. */
        fun itemText(item: JSONObject): String {
            val note = str(item, "note").trim()
            val plain = item.isNull("food") && item.isNull("unit")
            if (plain && note.isNotEmpty()) return note
            val display = str(item, "display").trim()
            if (display.isNotEmpty()) return display
            val food = item.optJSONObject("food")?.let { str(it, "name") }.orEmpty()
            return listOf(food, note).filter { it.isNotBlank() }.joinToString(" ").trim()
        }

        private fun str(o: JSONObject, key: String): String = if (o.isNull(key)) "" else o.optString(key, "")
    }
}

package app.stickynotes.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val body: String = "",
    val isChecklist: Boolean = false,
    val items: List<ChecklistItem> = emptyList(),
    /** Index into NoteColor. */
    val color: Int = 0,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    /** Lower comes first. New notes get the lowest value so they appear at the top. */
    val position: Long = 0,
    val reminderAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** Null for normal notes; the time it went to Trash otherwise. */
    val deletedAt: Long? = null,
)

fun Note.hasText(): Boolean = title.isNotBlank() || body.isNotBlank() || items.any { it.text.isNotBlank() }

data class ChecklistItem(
    val id: Long = Random.nextLong(),
    val text: String = "",
    val checked: Boolean = false,
)

@Entity(
    tableName = "labels",
    indices = [Index(value = ["name"], unique = true)],
)
data class LabelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(
    tableName = "note_label_cross_ref",
    primaryKeys = ["noteId", "labelId"],
    foreignKeys = [
        ForeignKey(
            entity = Note::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LabelEntity::class,
            parentColumns = ["id"],
            childColumns = ["labelId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["labelId"])],
)
data class NoteLabelCrossRef(
    val noteId: Long,
    val labelId: Long,
)

@Entity(
    tableName = "note_images",
    foreignKeys = [
        ForeignKey(
            entity = Note::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["noteId"])],
)
data class NoteImageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val fileName: String,
    val createdAt: Long = System.currentTimeMillis(),
)

class Converters {
    @TypeConverter
    fun itemsToJson(items: List<ChecklistItem>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("text", item.text)
                    .put("checked", item.checked),
            )
        }
        return array.toString()
    }

    @TypeConverter
    fun jsonToItems(json: String): List<ChecklistItem> {
        if (json.isBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                ChecklistItem(
                    id = o.optLong("id", Random.nextLong()),
                    text = o.optString("text", ""),
                    checked = o.optBoolean("checked", false),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

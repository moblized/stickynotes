package app.stickynotes.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class NoteDao {
    /** Every note that isn't in Trash (archived ones included), in display order. */
    @Query("SELECT * FROM notes WHERE deletedAt IS NULL ORDER BY isPinned DESC, position ASC, id DESC")
    abstract fun observeAlive(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    abstract fun observeTrashed(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id")
    abstract suspend fun get(id: Long): Note?

    @Insert
    abstract suspend fun insert(note: Note): Long

    @Query("SELECT MIN(position) FROM notes")
    abstract suspend fun minPosition(): Long?

    /** Saves what the editor changes. Never touches archive/trash state or position. */
    @Query(
        "UPDATE notes SET title = :title, body = :body, isChecklist = :isChecklist, items = :itemsJson, " +
            "color = :color, isPinned = :isPinned, reminderAt = :reminderAt, updatedAt = :updatedAt " +
            "WHERE id = :id",
    )
    abstract suspend fun updateContent(
        id: Long,
        title: String,
        body: String,
        isChecklist: Boolean,
        itemsJson: String,
        color: Int,
        isPinned: Boolean,
        reminderAt: Long?,
        updatedAt: Long,
    )

    @Query("UPDATE notes SET isArchived = :archived, isPinned = :pinned WHERE id = :id")
    abstract suspend fun setArchived(id: Long, archived: Boolean, pinned: Boolean)

    @Query("UPDATE notes SET isPinned = :pinned WHERE id = :id")
    abstract suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("UPDATE notes SET position = :position WHERE id = :id")
    abstract suspend fun setPosition(id: Long, position: Long)

    @Transaction
    open suspend fun setPositions(positions: Map<Long, Long>) {
        positions.forEach { (id, position) -> setPosition(id, position) }
    }

    @Query("UPDATE notes SET deletedAt = :now WHERE id = :id")
    abstract suspend fun moveToTrash(id: Long, now: Long)

    @Query("UPDATE notes SET deletedAt = NULL WHERE id = :id")
    abstract suspend fun restore(id: Long)

    @Query("SELECT id FROM notes WHERE deletedAt IS NOT NULL AND deletedAt <= :cutoff")
    abstract suspend fun expiredIds(cutoff: Long): List<Long>

    @Query("SELECT id FROM notes WHERE deletedAt IS NOT NULL")
    abstract suspend fun trashedIds(): List<Long>

    @Query("DELETE FROM notes WHERE id IN (:ids)")
    abstract suspend fun deleteIds(ids: List<Long>)

    @Query("SELECT * FROM notes WHERE reminderAt IS NOT NULL AND deletedAt IS NULL")
    abstract suspend fun withReminders(): List<Note>
}

@Dao
interface LabelDao {
    @Query("SELECT * FROM labels ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<LabelEntity>>

    @Query("SELECT * FROM note_label_cross_ref")
    fun observeCrossRefs(): Flow<List<NoteLabelCrossRef>>

    @Query("SELECT * FROM labels WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): LabelEntity?

    @Query("SELECT labelId FROM note_label_cross_ref WHERE noteId = :noteId")
    suspend fun labelIdsFor(noteId: Long): List<Long>

    @Query("SELECT COUNT(*) FROM note_label_cross_ref WHERE noteId = :noteId")
    suspend fun countFor(noteId: Long): Int

    @Insert
    suspend fun insert(label: LabelEntity): Long

    @Query("UPDATE labels SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM labels WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun attach(ref: NoteLabelCrossRef)

    @Delete
    suspend fun detach(ref: NoteLabelCrossRef)
}

@Dao
interface NoteImageDao {
    @Query("SELECT * FROM note_images ORDER BY createdAt, id")
    fun observeAll(): Flow<List<NoteImageEntity>>

    @Query("SELECT * FROM note_images WHERE noteId = :noteId ORDER BY createdAt, id")
    suspend fun imagesFor(noteId: Long): List<NoteImageEntity>

    @Insert
    suspend fun insert(image: NoteImageEntity): Long

    @Query("DELETE FROM note_images WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT fileName FROM note_images WHERE noteId IN (:noteIds)")
    suspend fun fileNamesFor(noteIds: List<Long>): List<String>

    @Query("SELECT fileName FROM note_images")
    suspend fun allFileNames(): List<String>
}

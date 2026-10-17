package app.stickynotes.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        Note::class,
        LabelEntity::class,
        NoteLabelCrossRef::class,
        NoteImageEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun labelDao(): LabelDao
    abstract fun imageDao(): NoteImageDao
}

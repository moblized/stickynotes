package app.stickynotes

import android.app.Application
import android.content.Context
import androidx.room.Room
import app.stickynotes.data.AppDatabase
import app.stickynotes.data.ImageStore
import app.stickynotes.data.NotesRepository
import app.stickynotes.data.Settings
import app.stickynotes.data.mealie.MealieStore
import app.stickynotes.data.mealie.MealieSync
import app.stickynotes.data.mealie.MealieSyncWorker
import app.stickynotes.reminders.Notifications
import app.stickynotes.reminders.ReminderScheduler

class StickyApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannel(this)
        if (container.mealieStore.isConnected) MealieSyncWorker.schedule(this)
    }
}

/** The app's single instances of the database, settings and repository. */
class AppContainer(context: Context) {
    private val database: AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "sticky-notes.db").build()

    val settings = Settings(context)

    val repository = NotesRepository(
        db = database,
        images = ImageStore(context),
        reminders = ReminderScheduler(context),
    )

    val mealieStore = MealieStore(context)
    val mealieSync = MealieSync(mealieStore, repository)
}

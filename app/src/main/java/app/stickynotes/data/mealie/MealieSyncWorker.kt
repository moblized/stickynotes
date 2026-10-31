package app.stickynotes.data.mealie

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.stickynotes.StickyApp
import java.util.concurrent.TimeUnit

/** Syncs Mealie shopping lists in the background every 15 minutes (Android's shortest interval). */
class MealieSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sync = (applicationContext as StickyApp).container.mealieSync
        sync.syncNow()
        // Failures are shown in Settings and retried on the next run; no extra retries needed.
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "mealie-sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MealieSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}

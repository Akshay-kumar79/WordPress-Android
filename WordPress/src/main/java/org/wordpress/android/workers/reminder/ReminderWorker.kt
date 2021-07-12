package org.wordpress.android.workers.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import kotlinx.coroutines.coroutineScope
import org.wordpress.android.workers.reminder.ReminderNotifier.Companion.NO_SITE_ID
import org.wordpress.android.workers.reminder.ReminderScheduler.Companion.REMINDER_SITE_ID

class ReminderWorker(
    val context: Context,
    val scheduler: ReminderScheduler,
    val notifier: ReminderNotifier,
    workerParameters: WorkerParameters
) : CoroutineWorker(context, workerParameters) {
    override suspend fun doWork(): Result = coroutineScope {
        val siteId = inputData.getInt(REMINDER_SITE_ID, NO_SITE_ID)
        val reminderConfig = ReminderConfig.fromMap(inputData.keyValueMap)

        if (notifier.shouldNotify(siteId)) {
            notifier.notify(siteId)
            scheduler.schedule(siteId, reminderConfig)
        }

        Result.success()
    }

    class Factory(
        private val scheduler: ReminderScheduler,
        private val notifier: ReminderNotifier
    ) : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters
        ) = if (workerClassName == ReminderWorker::class.java.name) {
            ReminderWorker(appContext, scheduler, notifier, workerParameters)
        } else {
            null
        }
    }
}

package com.focused.app.work

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.focused.app.data.db.FocusedDatabase
import com.focused.app.util.ActivityLogger
import kotlinx.coroutines.runBlocking
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * MidnightResetWorker
 *
 * Runs once every 24 hours, scheduled to fire shortly after midnight.
 * Closes all open/paused sessions so the daily budget resets correctly.
 *
 * Without this, a user who hits their limit on Day 1 stays blocked on Day 2
 * because their ACTIVE/PAUSED sessions from yesterday are still counted.
 *
 * Scheduled from FocusedAccessibilityService.onServiceConnected() and
 * BootReceiver so it survives restarts.
 */
class MidnightResetWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        Log.d("MidnightReset", "Running daily reset")
        runBlocking {
            val db = FocusedDatabase.get(applicationContext)
            db.appSessionDao().closeAllOpenSessions()
            Log.d("MidnightReset", "All open sessions closed")
        }
        // Reschedule for next midnight
        schedule(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "midnight_reset"

        fun schedule(context: Context) {
            val now = System.currentTimeMillis()
            val cal = Calendar.getInstance().apply {
                timeInMillis = now
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val delayMs = (cal.timeInMillis - now).coerceAtLeast(0L)

            val request = PeriodicWorkRequestBuilder<MidnightResetWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .addTag(WORK_NAME)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
            Log.d("MidnightReset", "Scheduled in ${delayMs / 60000} minutes")
        }
    }
}

package com.focused.app.work

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.focused.app.util.FocusBus

/**
 * FocusEndWorker
 *
 * Scheduled by FocusSessionManager when a session starts.
 * Fires after the planned duration expires.
 * Signals FocusBus so FocusSessionManager ends the session as completed.
 *
 * Using WorkManager means this fires even if the user force-quits the app.
 */
class FocusEndWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        FocusBus.notifyComplete()
        return Result.success()
    }

    companion object {
        const val KEY_SESSION_ID = "session_id"
    }
}

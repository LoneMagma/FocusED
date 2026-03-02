package com.focused.app.work

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.focused.app.util.FrictionBus

/**
 * FrictionUnlockWorker
 *
 * Scheduled by FrictionManager when Tier 3 begins.
 * Runs exactly 150 seconds (2min 30sec) after enqueue.
 * When it fires, it signals FrictionBus so FrictionManager
 * knows the countdown is complete and can disable enforcement.
 *
 * Using WorkManager guarantees this fires even if the user
 * closes the app — they can't escape by force-quitting.
 *
 * The work tag is "friction_unlock_{packageName}" so it can
 * be cancelled if the user abandons the friction flow before
 * the countdown completes.
 */
class FrictionUnlockWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        val pkg = inputData.getString(KEY_PACKAGE) ?: ""
        FrictionBus.notifyUnlocked(pkg)
        return Result.success()
    }

    companion object {
        const val KEY_PACKAGE = "package_name"
        const val WORK_TAG_PREFIX = "friction_unlock_"
    }
}

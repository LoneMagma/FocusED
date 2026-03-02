package com.focused.app.manager

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.focused.app.data.db.FocusedDatabase
import com.focused.app.data.model.FocusSession
import com.focused.app.util.ActivityLogger
import com.focused.app.util.DateUtil
import com.focused.app.util.FocusBus
import com.focused.app.util.NotificationHelper
import com.focused.app.work.FocusEndWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * FocusSessionManager
 *
 * Single source of truth for whether focus mode is active.
 * Held by the AccessibilityService; BudgetEnforcer queries it on
 * every foreground event to decide whether to apply focus blocking
 * instead of normal budget logic.
 *
 * Responsibilities:
 *   - Start/end focus sessions
 *   - Persist session records to Room
 *   - Schedule WorkManager job for natural end-of-session
 *   - Maintain in-memory state (isActive, task, planned end time)
 *   - Notify listeners (BudgetEnforcer, overlay banner) on state change
 */
class FocusSessionManager(private val context: Context) {

    companion object {
        private const val TAG = "FocusSessionMgr"
        const val WORK_TAG = "focus_end"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    // In-memory state — fast path for BudgetEnforcer checks
    @Volatile var isActive: Boolean = false
        private set
    @Volatile var currentTask: String = ""
        private set
    @Volatile var sessionEndTime: Long = 0L
        private set

    private var currentSessionId: Long = -1L

    init {
        FocusBus.registerCompleteListener { onTimerComplete() }
        // Restore in-memory state if service was killed mid-session
        restoreActiveSession()
    }

    // Listeners — called on main thread
    private val onStartListeners  = mutableListOf<(task: String, endsAt: Long) -> Unit>()
    private val onEndListeners    = mutableListOf<(completed: Boolean) -> Unit>()

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    fun registerOnStart(l: (String, Long) -> Unit)  { onStartListeners.add(l) }
    fun registerOnEnd(l: (Boolean) -> Unit)          { onEndListeners.add(l) }

    /**
     * Start a focus session.
     * Called from FocusActivity when the user taps "Start Focus".
     */
    /**
     * Called on init — checks if there's an ACTIVE focus session in the DB
     * (from before a service restart) and restores in-memory state.
     * Without this, a force-kill of the service resets focus mode to off
     * even though the DB still has an active session and WorkManager will fire.
     */
    private fun restoreActiveSession() {
        scope.launch {
            val session = FocusedDatabase.get(context).focusSessionDao().getActiveSession()
                ?: return@launch
            val endsAt = session.startTime + session.plannedDurationMs
            if (endsAt <= System.currentTimeMillis()) {
                // Session should have ended — close it
                FocusedDatabase.get(context).focusSessionDao().update(
                    session.copy(
                        endTime = endsAt,
                        actualDurationMs = session.plannedDurationMs,
                        status = com.focused.app.data.model.FocusSession.STATUS_COMPLETED
                    )
                )
                return@launch
            }
            // Restore live state
            isActive = true
            currentTask = session.taskLabel
            sessionEndTime = endsAt
            currentSessionId = session.id
            Log.d(TAG, "Restored active focus session: ${session.taskLabel}")
            mainHandler.post { onStartListeners.forEach { it(session.taskLabel, endsAt) } }
        }
    }

    fun start(taskLabel: String, durationMs: Long) {
        if (isActive) return

        val endsAt = System.currentTimeMillis() + durationMs
        isActive = true
        currentTask = taskLabel
        sessionEndTime = endsAt

        scope.launch {
            val session = FocusSession(
                taskLabel = taskLabel,
                plannedDurationMs = durationMs,
                dayKey = DateUtil.todayKey()
            )
            currentSessionId = FocusedDatabase.get(context).focusSessionDao().insert(session)
            ActivityLogger.log(context, ActivityLogger.FOCUS_STARTED, detail = taskLabel)
            Log.d(TAG, "Focus started: $taskLabel for ${DateUtil.formatMinutes(durationMs)}")
        }

        // Schedule natural end via WorkManager
        val data = Data.Builder().putLong(FocusEndWorker.KEY_SESSION_ID, 0L).build()
        val work = OneTimeWorkRequestBuilder<FocusEndWorker>()
            .setInitialDelay(durationMs, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag(WORK_TAG)
            .build()
        WorkManager.getInstance(context).enqueue(work)

        mainHandler.post { onStartListeners.forEach { it(taskLabel, endsAt) } }
    }

    /**
     * End focus session — either naturally (completed = true) or early by user.
     */
    fun end(completed: Boolean) {
        if (!isActive) return

        isActive = false
        val task = currentTask
        currentTask = ""
        sessionEndTime = 0L

        // Cancel any pending WorkManager job
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)

        val sessionId = currentSessionId
        scope.launch {
            val dao = FocusedDatabase.get(context).focusSessionDao()
            val session = dao.getById(sessionId) ?: return@launch
            val now = System.currentTimeMillis()
            dao.update(session.copy(
                endTime = now,
                actualDurationMs = now - session.startTime,
                status = if (completed) FocusSession.STATUS_COMPLETED else FocusSession.STATUS_ABANDONED
            ))
            ActivityLogger.log(
                context,
                if (completed) ActivityLogger.FOCUS_ENDED else ActivityLogger.FOCUS_ABANDONED,
                detail = task
            )
            Log.d(TAG, "Focus ended: completed=$completed task=$task")
        }

        if (completed) {
            mainHandler.post { NotificationHelper.notifyFocusComplete(context, task) }
        }
        mainHandler.post { onEndListeners.forEach { it(completed) } }
    }

    /** Remaining ms in the current session, or 0 if not active. */
    fun remainingMs(): Long {
        if (!isActive) return 0L
        return (sessionEndTime - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    /**
     * Called by FocusEndWorker when the WorkManager job fires.
     * Ends the session as completed.
     */
    fun onTimerComplete() {
        end(completed = true)
    }
}

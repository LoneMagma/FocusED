package com.focused.app.util

import android.content.Context
import com.focused.app.data.db.FocusedDatabase
import com.focused.app.data.model.ActivityLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * ActivityLogger
 *
 * Fire-and-forget logger. Call ActivityLogger.log(...) from anywhere —
 * services, managers, UI — without worrying about coroutine scopes.
 *
 * Uses a dedicated IO coroutine scope with SupervisorJob so a failed
 * insert never crashes the caller.
 *
 * Usage:
 *   ActivityLogger.log(context, "APP_BLOCKED", "com.instagram.android", "Session 3 of 3")
 */
object ActivityLogger {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Event type constants — import these instead of typing strings raw
    const val APP_BLOCKED         = "APP_BLOCKED"
    const val FRICTION_STARTED    = "FRICTION_STARTED"
    const val FRICTION_TIER_1     = "FRICTION_TIER_1"
    const val FRICTION_TIER_2     = "FRICTION_TIER_2"
    const val FRICTION_TIER_3     = "FRICTION_TIER_3"
    const val FRICTION_COMPLETED  = "FRICTION_COMPLETED"
    const val FRICTION_ABANDONED  = "FRICTION_ABANDONED"
    const val FOCUS_STARTED       = "FOCUS_STARTED"
    const val FOCUS_ENDED         = "FOCUS_ENDED"
    const val FOCUS_ABANDONED     = "FOCUS_ABANDONED"
    const val SCROLL_INTERVENED   = "SCROLL_INTERVENED"
    const val INTENTION_SET       = "INTENTION_SET"
    const val SERVICE_STARTED     = "SERVICE_STARTED"
    const val SERVICE_STOPPED     = "SERVICE_STOPPED"

    fun log(
        context: Context,
        eventType: String,
        packageName: String? = null,
        detail: String? = null
    ) {
        scope.launch {
            try {
                FocusedDatabase.get(context)
                    .activityLogDao()
                    .insert(
                        ActivityLog(
                            eventType = eventType,
                            packageName = packageName,
                            detail = detail
                        )
                    )
            } catch (e: Exception) {
                // Silent fail — logging should never crash the app
            }
        }
    }
}

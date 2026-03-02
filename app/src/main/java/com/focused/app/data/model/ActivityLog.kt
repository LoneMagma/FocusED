package com.focused.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * ActivityLog
 *
 * Every action Focused takes is recorded here.
 * This table is the trust backbone of the app — it is surfaced in the UI
 * as an audit trail the user can browse at any time.
 *
 * Nothing is transmitted off-device. This is local-only, forever.
 *
 * Event types:
 *   APP_BLOCKED          — budget or session limit enforced
 *   FRICTION_STARTED     — user initiated disable flow
 *   FRICTION_TIER_1      — passed cooldown wait
 *   FRICTION_TIER_2      — passed random string
 *   FRICTION_COMPLETED   — all tiers passed, app disabled
 *   FRICTION_ABANDONED   — user backed out of friction flow
 *   FOCUS_STARTED        — focus session began
 *   FOCUS_ENDED          — focus session completed
 *   SCROLL_INTERVENED    — doom-scroll card shown
 *   INTENTION_SET        — user tapped an intention chip
 *   SERVICE_STARTED      — accessibility service connected
 *   SERVICE_STOPPED      — accessibility service disconnected
 */
@Entity(tableName = "activity_log")
data class ActivityLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val timestamp: Long = System.currentTimeMillis(),

    val eventType: String,          // one of the types listed above

    val packageName: String? = null, // which app triggered this, if any

    val detail: String? = null       // optional human-readable detail
                                     // e.g. "Session 3 of 3 reached"
)

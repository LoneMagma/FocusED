package com.focused.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * FocusSession
 *
 * One row per focus session the user has started.
 *
 * Status lifecycle:
 *   ACTIVE    → focus mode is currently running
 *   COMPLETED → timer ran to zero naturally
 *   ABANDONED → user ended early
 *
 * taskLabel: what the user committed to doing
 * plannedDurationMs: what they set at the start
 * actualDurationMs: how long they actually stayed in focus
 */
@Entity(tableName = "focus_sessions")
data class FocusSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val taskLabel: String,

    val startTime: Long = System.currentTimeMillis(),

    var endTime: Long? = null,

    val plannedDurationMs: Long,

    var actualDurationMs: Long = 0L,

    var status: String = STATUS_ACTIVE,

    val dayKey: String = ""
) {
    companion object {
        const val STATUS_ACTIVE    = "ACTIVE"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_ABANDONED = "ABANDONED"
    }
}

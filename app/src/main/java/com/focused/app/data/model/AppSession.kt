package com.focused.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_sessions")
data class AppSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val sessionStart: Long = System.currentTimeMillis(),
    var sessionEnd: Long? = null,
    var pausedAt: Long? = null,
    var status: String = STATUS_ACTIVE,
    val sessionNumber: Int = 1,
    val dayKey: String = "",
    var intentionLabel: String? = null,
    var durationMs: Long = 0L,
    var resumedAt: Long = System.currentTimeMillis(),
    // NEW: accumulated ms spent in short-form content (Reels/Shorts) this session
    var shortFormMs: Long = 0L
) {
    companion object {
        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_PAUSED = "PAUSED"
        const val STATUS_CLOSED = "CLOSED"
    }
}

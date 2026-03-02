package com.focused.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * FrictionAttempt
 *
 * One row per time the user taps "Disable Focused".
 * Records which tier they reached and whether they completed or abandoned.
 * Surfaced in the Activity Log as a self-reflection metric:
 * "You tried to disable Focused 6 times today and stopped each time."
 *
 * tiersReached: highest tier the user completed (1, 2, or 3)
 * completed: true only if they waited out the full Tier 3 countdown
 */
@Entity(tableName = "friction_attempts")
data class FrictionAttempt(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val timestamp: Long = System.currentTimeMillis(),

    val packageName: String? = null,   // which app was blocked when this happened

    val tiersReached: Int = 0,         // 0 = abandoned before tier 1 even started

    val completed: Boolean = false,

    val dayKey: String = ""
)

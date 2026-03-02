package com.focused.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "budget_rules")
data class BudgetRule(
    @PrimaryKey
    val packageName: String,
    val appLabel: String,
    val maxSessionsPerDay: Int = 3,
    val maxSessionDurationMs: Long = 20 * 60 * 1000L,  // 20 min default

    // NEW: max app opens per day (0 = unlimited)
    val maxOpensPerDay: Int = 0,

    // NEW: separate short-form content limit (0 = same as total)
    val shortFormLimitMs: Long = 0L,

    // NEW: scheduled downtime window — minutes from midnight (-1 = disabled)
    val downtimeStartMin: Int = -1,
    val downtimeEndMin: Int   = -1,

    val intentionEnabled: Boolean = true,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    /** Returns true if current time falls inside the downtime window */
    fun isInDowntime(): Boolean {
        if (downtimeStartMin < 0 || downtimeEndMin < 0) return false
        val cal = java.util.Calendar.getInstance()
        val nowMin = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        return if (downtimeStartMin <= downtimeEndMin) {
            nowMin in downtimeStartMin until downtimeEndMin
        } else {
            // Wraps midnight: e.g. 22:00 → 07:00
            nowMin >= downtimeStartMin || nowMin < downtimeEndMin
        }
    }
}

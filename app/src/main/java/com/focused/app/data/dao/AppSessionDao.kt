package com.focused.app.data.dao

import androidx.room.*
import com.focused.app.data.model.AppSession

@Dao
interface AppSessionDao {

    @Insert
    suspend fun insert(session: AppSession): Long

    @Update
    suspend fun update(session: AppSession)

    @Query("SELECT * FROM app_sessions WHERE packageName = :pkg AND status != 'CLOSED' ORDER BY sessionStart DESC LIMIT 1")
    suspend fun getOpenSession(pkg: String): AppSession?

    @Query("SELECT COUNT(*) FROM app_sessions WHERE packageName = :pkg AND dayKey = :dayKey AND status = 'CLOSED'")
    suspend fun closedSessionsToday(pkg: String, dayKey: String): Int

    /** Total opens today (all sessions regardless of status) */
    @Query("SELECT COUNT(*) FROM app_sessions WHERE packageName = :pkg AND dayKey = :dayKey")
    suspend fun opensTodayCount(pkg: String, dayKey: String): Int

    @Query("UPDATE app_sessions SET status = 'CLOSED', sessionEnd = :now WHERE packageName = :pkg AND status != 'CLOSED'")
    suspend fun closeAllOpen(pkg: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE app_sessions SET status = 'CLOSED', sessionEnd = :now WHERE status != 'CLOSED'")
    suspend fun closeAllOpenSessions(now: Long = System.currentTimeMillis())

    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM app_sessions WHERE packageName = :pkg AND dayKey = :dayKey AND status != 'ACTIVE'")
    suspend fun totalDurationTodayMs(pkg: String, dayKey: String): Long

    /** Total short-form ms today */
    @Query("SELECT COALESCE(SUM(shortFormMs), 0) FROM app_sessions WHERE packageName = :pkg AND dayKey = :dayKey")
    suspend fun totalShortFormTodayMs(pkg: String, dayKey: String): Long

    /** For weekly heatmap: sum durationMs grouped by dayKey for last 7 days */
    @Query("SELECT packageName, dayKey, SUM(durationMs) as totalMs FROM app_sessions WHERE sessionStart >= :since GROUP BY packageName, dayKey")
    suspend fun weeklyUsage(since: Long): List<DayUsage>

    /** Per-hour usage for heatmap */
    @Query("SELECT * FROM app_sessions WHERE sessionStart >= :since ORDER BY sessionStart ASC")
    suspend fun sessionsAfter(since: Long): List<AppSession>

    @Query("DELETE FROM app_sessions WHERE sessionStart < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    /** Earliest session ever recorded — used to cap streak count */
    @Query("SELECT MIN(sessionStart) FROM app_sessions")
    suspend fun firstSessionStart(): Long?

    /**
     * Days where total usage for any package exceeded its budget.
     * Used for the correct streak definition: days where the app was used
     * and limits were respected. Returns dayKey strings e.g. "2026-03-01".
     */
    @Query("""
        SELECT DISTINCT s.dayKey
        FROM app_sessions s
        INNER JOIN budget_rules r ON s.packageName = r.packageName
        WHERE r.isActive = 1
        GROUP BY s.packageName, s.dayKey
        HAVING SUM(s.durationMs) > r.maxSessionDurationMs
    """)
    suspend fun daysOverLimit(): List<String>
}

data class DayUsage(val packageName: String, val dayKey: String, val totalMs: Long)

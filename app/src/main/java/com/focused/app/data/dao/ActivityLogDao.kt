package com.focused.app.data.dao

import androidx.room.*
import com.focused.app.data.model.ActivityLog
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityLogDao {

    @Insert
    suspend fun insert(log: ActivityLog)

    // All logs, newest first — used for the audit trail screen
    @Query("SELECT * FROM activity_log ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<ActivityLog>>

    // Today's logs only (midnight to now)
    @Query("""
        SELECT * FROM activity_log 
        WHERE timestamp >= :startOfDay 
        ORDER BY timestamp DESC
    """)
    fun getTodayFlow(startOfDay: Long): Flow<List<ActivityLog>>

    // How many times friction was started today — shown as self-reflection metric
    @Query("""
        SELECT COUNT(*) FROM activity_log 
        WHERE eventType = 'FRICTION_STARTED' 
        AND timestamp >= :startOfDay
    """)
    suspend fun frictionAttemptsToday(startOfDay: Long): Int

    // How many times friction was completed (app actually disabled)
    @Query("""
        SELECT COUNT(*) FROM activity_log 
        WHERE eventType = 'FRICTION_COMPLETED' 
        AND timestamp >= :startOfDay
    """)
    suspend fun frictionCompletionsToday(startOfDay: Long): Int

    // Clean up logs older than 30 days (keep the device lean)
    @Query("DELETE FROM activity_log WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}
